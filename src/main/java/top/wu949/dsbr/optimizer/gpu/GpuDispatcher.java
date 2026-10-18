package top.wu949.dsbr.optimizer.gpu;

import top.wu949.dsbr.optimizer.*;
import top.wu949.dsbr.optimizer.cache.BudgetCache;
import top.wu949.dsbr.optimizer.compat.*;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import top.wu949.dsbr.optimizer.texture.TextureCache;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import software.bernie.geckolib.cache.object.*;
import java.nio.ByteBuffer;
import java.util.*;

public final class GpuDispatcher {
    private record ModelKey(BakedGeoModel model) {
        @Override public boolean equals(Object other) { return other instanceof ModelKey k && k.model == model; }
        @Override public int hashCode() { return System.identityHashCode(model); }
    }
    private record Binding(Object source, RenderType type) {}
    public static final class Context {
        StaticMesh mesh;
        BudgetCache.Entry<StaticMesh> entry;
        PoseSnapshot pose;
        Binding binding;
        IrisBridge.Attributes attributes;
        long start;
        boolean glow;
        boolean soul;
        RendererAdmission.Kind kind;
        Class<?> family;
        /** Same-frame re-render reuse: only DS's own renderer, whose traversal and layers are audited. */
        boolean reusable, rerender, clean = true, reused;
        Object renderer, animatable;
        BakedGeoModel model;
        int light, overlay, colour;
        Matrix4f root;
        Matrix3f rootNormal;
        PoseSnapshot expected;
        List<Tracked> tracked;
        RerenderPoses.Template template;
        VertexConsumer lastBuffer;
        Binding lastBinding;
        long bindingGeneration = -1;
    }
    private record Command(StaticMesh mesh, BudgetCache.Entry<StaticMesh> entry, PoseSnapshot pose, IrisBridge.Attributes attributes, boolean glow, boolean soul, RendererAdmission.Kind kind, Class<?> family) {}
    private static final ComputeProgram compute = new ComputeProgram();
    private static final PosePool poses = new PosePool();
    private static final PoseStaging staging = new PoseStaging();
    private static final BudgetCache<ModelKey, StaticMesh> meshes = new BudgetCache<>(StaticMesh::close);
    private static final Map<VertexConsumer, Binding> bindings = new WeakHashMap<>();
    private static final DeferredBatches<Object, RenderType, Command> queues = new DeferredBatches<>();
    private static final Deque<Context> contexts = new ArrayDeque<>();
    private static final RerenderPoses rerenders = new RerenderPoses();
    private static boolean failed;
    private static int glowDepth;
    private static long bindingGeneration;
    private static int worldDepth;
    public static void enterWorld() { if (worldDepth++ == 0) rerenders.clear(); }
    public static void exitWorld() { worldDepth--; }
    public static void enterGlow() { glowDepth++; }
    public static void exitGlow() { glowDepth--; }
    private static long pendingBytes;
    private static long workingBytes;
    private static int poseBuffer, outputBuffer, arrayObject;
    private static long ssboLimit, workGroupLimit;
    /** Batches checked with glGetError after a (re)start; later batches are only sampled to avoid a CPU/GPU sync each time. */
    private static final int VALIDATED_BATCHES = 64, SAMPLED_BATCH_INTERVAL = 256;
    private static long checkedBatches;
    /** Unknown buffer sources are never admitted. Iris's unflushable wrapper delegates here. */
    private static final ClassValue<Boolean> ADMITTED_SOURCE = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            return type == MultiBufferSource.BufferSource.class || type.getName().equals("net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource");
        }
    };
    /** Commands that can share one dispatch and draw; order inside a (source, type) queue does not matter (opaque or additive only). */
    private record BatchKey(RendererAdmission.Kind kind, Class<?> family, StaticMesh mesh, boolean glow, IrisBridge.Attributes attributes) {
        static BatchKey of(Command c) { return new BatchKey(c.kind, c.family, c.mesh, c.glow, c.attributes); }
    }
    public static void remember(Object source, RenderType type, VertexConsumer buffer) {
        // Runs for every getBuffer in the game: stay allocation-free unless the GPU path is live.
        if (!enabled()) return;
        if (ADMITTED_SOURCE.get(source.getClass())) { bindings.put(buffer, new Binding(source, type)); bindingGeneration++; }
    }
    private static boolean checkErrors() {
        long n = checkedBatches++;
        return n < VALIDATED_BATCHES || n % SAMPLED_BATCH_INTERVAL == 0;
    }
    private static void throwOnError(String what) {
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) throw new IllegalStateException(what + " 0x" + Integer.toHexString(error));
    }
    private static boolean reuseEnabled() {
        return OptimizationStage.VALUE >= 5 && OptimizerMixinPlugin.rerenderCompatible && OptimizerConfig.REUSE_RERENDER_POSES.get() && enabled();
    }
    private static boolean reusable(Object renderer, RendererAdmission.Kind kind) {
        return kind != RendererAdmission.Kind.NPC && renderer.getClass() == by.dragonsurvivalteam.dragonsurvival.client.render.entity.dragon.DragonRenderer.class;
    }
    public static Context begin(Object renderer, PoseStack stack, Object animatable, BakedGeoModel model, RenderType type, VertexConsumer buffer,
                                boolean rerender, int light, int overlay, int colour) {
        boolean reuse = reuseEnabled();
        // Any render of a shared baked model, even one that stays on the CPU, may rewrite its bones' tracked matrices.
        if (reuse) rerenders.touch(model, animatable);
        var c = begin(renderer, model, type, buffer);
        if (!reuse || c.mesh == null || !reusable(renderer, c.kind)) return c;
        c.reusable = true; c.rerender = rerender; c.renderer = renderer; c.animatable = animatable; c.model = model;
        c.light = light; c.overlay = overlay; c.colour = colour;
        c.root = new Matrix4f(stack.last().pose()); c.rootNormal = new Matrix3f(stack.last().normal());
        if (!rerender) {
            // Templates count against the geometry budget; over budget the re-render simply traverses.
            if (bytes() + RerenderPoses.estimate(c.mesh.order.size()) <= OptimizerConfig.meshMiB() * 1048576L) c.template = rerenders.prepare(model, animatable, c.mesh);
            return c;
        }
        // Verification still traverses and compares the traversal's capture with the reused bones at end().
        var target = VERIFY_RERENDER ? new PoseSnapshot(c.mesh.bones.size()) : c.pose;
        var miss = rerenders.reuse(renderer, animatable, model, c.mesh, c.root, c.rootNormal, target, colour, light, overlay);
        if (miss == null) {
            Diagnostics.INSTANCE.count(Diagnostics.Counter.RERENDER_POSE_REUSED, "reuse");
            if (VERIFY_RERENDER) { c.expected = target; c.tracked = tracked(c.mesh); } else c.reused = true;
        } else {
            if (target != c.pose) target.close();
            Diagnostics.INSTANCE.count(Diagnostics.Counter.RERENDER_POSE_MISS, "miss/" + miss.name());
        }
        return c;
    }
    /** Validation only: traverse anyway and compare with the reused bones and the tracked matrices left in place. */
    private static final boolean VERIFY_RERENDER = Boolean.getBoolean("dsbr.verifyRerenderPoses");
    private record Tracked(GeoBone bone, Matrix4f model, Matrix4f local, Matrix4f world) {}
    private static List<Tracked> tracked(StaticMesh mesh) {
        var out = new ArrayList<Tracked>();
        for (var b : mesh.order) if (b.isTrackingMatrices())
            out.add(new Tracked(b, copy(b.getModelSpaceMatrix()), copy(b.getLocalSpaceMatrix()), copy(b.getWorldSpaceMatrix())));
        return out;
    }
    private static Matrix4f copy(Matrix4f m) { return m == null ? null : new Matrix4f(m); }
    private static void verify(Context c, boolean completed) {
        try {
            boolean same = completed && Arrays.equals(c.expected.visible, c.pose.visible);
            for (int i = 0; same && i < c.pose.visible.length; i++)
                if (c.pose.visible[i] && c.pose.data.slice(i * PoseSnapshot.STRIDE, PoseSnapshot.STRIDE).compareTo(c.expected.data.slice(i * PoseSnapshot.STRIDE, PoseSnapshot.STRIDE)) != 0) same = false;
            Diagnostics.INSTANCE.count(same ? Diagnostics.Counter.RERENDER_POSE_VERIFIED : Diagnostics.Counter.RERENDER_POSE_MISMATCH, "verify");
            // Skipping the traversal leaves the tracked matrices of the base pass; the traversal must not have changed them.
            for (var t : c.tracked) {
                boolean kept = Objects.equals(t.model, t.bone.getModelSpaceMatrix()) && Objects.equals(t.local, t.bone.getLocalSpaceMatrix()) && Objects.equals(t.world, t.bone.getWorldSpaceMatrix());
                Diagnostics.INSTANCE.count(kept ? Diagnostics.Counter.RERENDER_TRACKED_VERIFIED : Diagnostics.Counter.RERENDER_TRACKED_MISMATCH, "verify");
            }
        } finally { c.expected.close(); c.expected = null; c.tracked = null; }
    }
    /** True when this re-render already holds the base pass's bones and must not traverse the skeleton again. */
    public static boolean reused(Context c) { return c.reused; }
    private static Context begin(Object renderer, BakedGeoModel model, RenderType type, VertexConsumer buffer) {
        var c = new Context(); contexts.push(c);
        c.soul = top.wu949.dsbr.optimizer.soul.SoulRenderContext.active();
        c.kind = RendererAdmission.kind(renderer); c.family = renderer.getClass();
        if (!enabled() || !RendererAdmission.gpu(renderer, c.kind) || type == null) return c;
        if (!(buffer instanceof BufferBuilder)) { fallback("unrecognized buffer consumer"); return c; }
        // GUI previews temporarily replace projection, lighting and model-view state. A deferred
        // world batch cannot retain that state. Keep their original immediate CPU submission.
        if (worldDepth == 0) { Diagnostics.INSTANCE.count(Diagnostics.Counter.GUI_CPU_PASS, "gui"); return c; }
        if ((type.sortOnUpload() && glowDepth == 0) || type.mode() != VertexFormat.Mode.QUADS || !type.format().equals(DefaultVertexFormat.NEW_ENTITY)) { fallback("sorted material or custom vertex format"); return c; }
        var binding = bindings.get(buffer); if (binding == null) { fallback("unrecognized buffer source/consumer"); return c; }
        try {
            if (!GL.getCapabilities().OpenGL43) { disable("OpenGL 4.3 unavailable", null); return c; }
            if (!RendererAdmission.originalGeometry(renderer)) {
                fallback("custom cube/vertex geometry"); return c;
            }
            compute.compile(); // Compile before cancelling any CPU geometry.
            if (ssboLimit == 0) { ssboLimit = GL32.glGetInteger64(GL43.GL_MAX_SHADER_STORAGE_BLOCK_SIZE); workGroupLimit = GL30.glGetIntegeri(GL43.GL_MAX_COMPUTE_WORK_GROUP_COUNT, 0); }
            var attributes = IrisBridge.capture(buffer);
            var key = new ModelKey(model); var entry = meshes.get(key, System.nanoTime());
            if (entry == null) {
                StaticMesh mesh;
                try { mesh = StaticMesh.bake(model); }
                catch (RuntimeException | LinkageError e) {
                    if (c.kind != RendererAdmission.Kind.NPC) throw e;
                    RendererAdmission.quarantine(renderer, e); fallback("NPC mesh rejected"); return c;
                }
                if (mesh.quads.isEmpty() || mesh.bytes() > OptimizerConfig.meshMiB() * 1048576L) { fallback("mesh exceeds budget or empty model"); return c; }
                poses.prune(System.nanoTime(), OptimizerConfig.meshMiB() * 1048576L - meshes.bytes() - workingBytes - pendingBytes - mesh.bytes());
                long available = OptimizerConfig.meshMiB() * 1048576L - workingBytes - pendingBytes - poses.idleBytes() - mesh.bytes() - auxiliaryBytes() - top.wu949.dsbr.optimizer.lowend.LowEndAnimations.bytes();
                meshes.prune(System.nanoTime(), Long.MAX_VALUE, available);
                if (meshes.bytes() > available) { fallback("leased meshes exhaust budget"); return c; }
                mesh.upload(); meshes.put(key, mesh, mesh.bytes(), System.nanoTime()); entry = meshes.get(key, System.nanoTime());
            }
            long reserve = (long)entry.value.quads.size() * attributes.format().getVertexSize() * 4 + PoseSnapshot.retainedBytes(entry.value.bones.size());
            if (BatchLimits.instances(entry.value.quads.size(), entry.value.bones.size(), attributes.format().getVertexSize(), OptimizerConfig.meshMiB() * 1048576L,
                    ssboLimit, workGroupLimit) < 1) { fallback("geometry exceeds GL dispatch limits"); return c; }
            poses.prune(System.nanoTime(), OptimizerConfig.meshMiB() * 1048576L - meshes.bytes() - workingBytes - pendingBytes - reserve);
            long singleWorkingBytes = (long)entry.value.quads.size() * attributes.format().getVertexSize() * 4 + (long)entry.value.bones.size() * 288;
            if (bytes() + reserve + singleWorkingBytes > OptimizerConfig.meshMiB() * 1048576L) { fallback("pending pose/vertex budget exhausted"); return c; }
            c.mesh = entry.value; c.entry = entry; c.binding = binding; c.attributes = attributes;
            c.pose = OptimizationStage.VALUE >= 3 ? poses.acquire(c.mesh.bones.size()) : new PoseSnapshot(c.mesh.bones.size()); c.start = System.nanoTime(); entry.leases++; pendingBytes += reserve;
            c.glow = glowDepth > 0;
            Diagnostics.INSTANCE.peak("GPU_RESOURCE_BYTES", bytes());
        } catch (Exception | LinkageError e) { disable("GPU initialization", e); }
        return c;
    }
    public static boolean bone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay, int colour) {
        var c = contexts.peek(); if (c == null || c.mesh == null) return false;
        if (c.reused) throw new IllegalStateException("bone traversal after re-render pose reuse");
        // Cached per pass until any getBuffer rebinds a consumer.
        var binding = buffer == c.lastBuffer && c.bindingGeneration == bindingGeneration ? c.lastBinding : bindings.get(buffer);
        c.lastBuffer = buffer; c.lastBinding = binding; c.bindingGeneration = bindingGeneration;
        // A per-bone layer can replace a consumer. Leave such geometry on its original path.
        if (binding == null || binding.source != c.binding.source || !Objects.equals(binding.type, c.binding.type)) { c.clean = false; fallback("bone consumer changed"); return false; }
        var index = c.mesh.bones.get(bone); if (index == null) { c.clean = false; return false; }
        if (!bone.isHidden() && c.pose.visible[index]) { c.clean = false; fallback("bone submitted twice in a single pass"); return false; }
        if (colour != c.colour || light != c.light || overlay != c.overlay) c.clean = false;
        if (!bone.isHidden()) c.pose.bone(index, pose.last(), colour, light, overlay);
        return true;
    }
    public static void end(Context context, boolean completed) {
        if (contexts.pop() != context) throw new IllegalStateException("GPU pose stack mismatch");
        if (context.mesh == null) return;
        if (context.expected != null) verify(context, completed);
        Diagnostics.INSTANCE.nanos("GPU_POSE_CAPTURE", System.nanoTime() - context.start);
        Diagnostics.INSTANCE.nanos(context.kind == RendererAdmission.Kind.NPC ? "NPC_GPU_POSE_CAPTURE" : context.kind == RendererAdmission.Kind.SOUL ? "SOUL_GPU_POSE_CAPTURE" : "PLAYER_GPU_POSE_CAPTURE", System.nanoTime() - context.start);
        if (context.template != null) {
            if (completed && context.clean && context.pose.anyVisible()) rerenders.commit(context.template, context.renderer, context.root, context.rootNormal, context.pose);
            else rerenders.discard(context.template);
            context.template = null;
        }
        if (completed && context.pose.anyVisible()) {
            queues.add(context.binding.source, context.binding.type, new Command(context.mesh, context.entry, context.pose, context.attributes, context.glow, context.soul, context.kind, context.family));
        } else release(new Command(context.mesh, context.entry, context.pose, context.attributes, context.glow, context.soul, context.kind, context.family));
    }
    public static boolean has(Object source, RenderType type) { return queues.has(source, type); }
    /** Caller owns RenderType state and the original Iris phase. */
    public static void draw(Object source, RenderType type) {
        var commands = queues.take(source, type); if (commands == null) return;
        long stateStart = System.nanoTime();
        try (var restore = OptimizationStage.VALUE >= 4 ? new GlBindings() : null) {
            // Group compatible commands even when other models are interleaved between them.
            var groups = new LinkedHashMap<BatchKey, List<Command>>();
            for (var command : commands) groups.computeIfAbsent(BatchKey.of(command), k -> new ArrayList<>()).add(command);
            for (var group : groups.values()) {
                var command = group.getFirst();
                long available = OptimizerConfig.meshMiB() * 1048576L - meshes.bytes() - pendingBytes - poses.idleBytes() - auxiliaryBytes() - top.wu949.dsbr.optimizer.lowend.LowEndAnimations.bytes();
                int max = Math.max(1, BatchLimits.instances(command.mesh.quads.size(), command.mesh.bones.size(), command.attributes.format().getVertexSize(), available,
                        ssboLimit, workGroupLimit));
                for (int i = 0; i < group.size(); i += max) {
                    var batch = group.subList(i, Math.min(group.size(), i + max));
                    try {
                        if (OptimizationStage.VALUE >= 4) drawCommands(batch);
                        else try (var perCommand = new GlBindings()) { drawCommands(batch); }
                    } catch (RuntimeException | LinkageError e) {
                        disable("GPU dispatch/draw", e);
                    }
                }
            }
        } catch (RuntimeException | LinkageError e) { disable("GPU batch state", e); }
        finally { for (var command : commands) release(command); Diagnostics.INSTANCE.nanos("GPU_BATCH_CPU", System.nanoTime() - stateStart); }
    }
    public static void drawWithState(Object source, RenderType type) {
        if (!has(source, type)) return;
        type.setupRenderState(); try { draw(source, type); } finally { type.clearRenderState(); }
    }
    private static void drawCommands(List<Command> batch) {
        var c = batch.getFirst(); int count = batch.size(), quads = c.mesh.quads.size();
        long drawStart = System.nanoTime();
        boolean check = checkErrors();
        try {
            // Errors left by unrelated earlier GL calls must not be blamed on (and disable) the GPU path.
            if (check) for (int i = 0; i < 16 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {}
            if (poseBuffer == 0) { poseBuffer = GL15.glGenBuffers(); outputBuffer = GL15.glGenBuffers(); arrayObject = GL30.glGenVertexArrays(); }
            var packed = staging.prepare(Math.multiplyExact(c.pose.data.capacity(), count));
            for (int i = 0; i < count; i++) PoseStaging.copy(packed, i * c.pose.data.capacity(), batch.get(i).pose.data);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, poseBuffer); GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, packed, GL15.GL_STREAM_DRAW);
            Diagnostics.INSTANCE.count(Diagnostics.Counter.POSE_UPLOAD, "upload");
            int stride = c.attributes.format().getVertexSize();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, outputBuffer); GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long)quads * count * 4 * stride, GL15.GL_STREAM_DRAW);
            workingBytes = ((long)quads * 4 * stride + c.pose.data.capacity()) * count;
            Diagnostics.INSTANCE.peak("GPU_RESOURCE_BYTES", bytes());
            try {
                if (failed) throw new IllegalStateException("GPU was disabled earlier in this batch");
                compute.dispatch(c.mesh.buffer, poseBuffer, outputBuffer, quads, count, c.mesh.bones.size(),
                        new ComputeProgram.IrisIds(c.attributes.entity(), c.attributes.blockEntity(), c.attributes.item(), c.attributes.recalculateNormal()), stride);
                if (check) throwOnError("OpenGL compute error");
            } catch (RuntimeException | LinkageError e) {
                disable("compute dispatch; replaying captured geometry on CPU", e);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, outputBuffer);
                for (int i = 0; i < count; i++) {
                    var cpu = CpuVertexEncoder.encode(c.mesh, batch.get(i).pose, c.attributes);
                    try { GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, (long)i * quads * 4 * stride, cpu); }
                    finally { MemoryUtil.memFree(cpu); }
                }
            }
            // Re-select the shader chosen by the surrounding RenderType, including Iris shadows.
            var shader = RenderSystem.getShader(); if (shader == null) throw new IllegalStateException("no entity shader in active render phase");
            GlStateManager._glBindVertexArray(arrayObject); GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, outputBuffer);
            c.attributes.format().setupBufferState();
            var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS); indices.bind(quads * count * 6);
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), Minecraft.getInstance().getWindow());
            // glUseProgram(compute) bypassed the cache; force the raw driver back before shader.apply().
            GL20.glUseProgram(0); GlStateManager._glUseProgram(0);
            shader.apply();
            try { RenderSystem.drawElements(GL11.GL_TRIANGLES, quads * count * 6, indices.type().asGLType); }
            finally { shader.clear(); c.attributes.format().clearBufferState(); }
            if (check) throwOnError("OpenGL draw error");
            Diagnostics.INSTANCE.count(Diagnostics.Counter.GPU_DRAW_CALLS, "draw");
            Diagnostics.INSTANCE.count(c.kind == RendererAdmission.Kind.NPC ? Diagnostics.Counter.NPC_GPU_DRAW_CALLS : c.kind == RendererAdmission.Kind.SOUL ? Diagnostics.Counter.SOUL_GPU_DRAW_CALLS : Diagnostics.Counter.PLAYER_GPU_DRAW_CALLS, "family");
            for (var command : batch) {
                Diagnostics.INSTANCE.count(Diagnostics.Counter.GPU_PASS, c.glow ? (c.attributes.shadow() ? "glow/shadow" : "glow/entity") : (c.attributes.shadow() ? "shadow" : "entity"));
                Diagnostics.INSTANCE.count(command.kind == RendererAdmission.Kind.NPC ? Diagnostics.Counter.NPC_GPU_PASS : command.kind == RendererAdmission.Kind.SOUL ? Diagnostics.Counter.SOUL_GPU_PASS : Diagnostics.Counter.PLAYER_GPU_PASS, "family");
            }
            Diagnostics.INSTANCE.vertices(quads * count * 4);
        } finally { Diagnostics.INSTANCE.nanos("GPU_DRAW_CPU", System.nanoTime() - drawStart); }
    }
    private static void release(Command c) {
        c.entry.leases--; pendingBytes -= (long)c.mesh.quads.size() * c.attributes.format().getVertexSize() * 4 + c.pose.retainedBytes();
        if (OptimizationStage.VALUE >= 3) poses.release(c.pose); else c.pose.close();
    }
    public static boolean enabled() { return !failed && OptimizerMixinPlugin.gpuCompatible && OptimizerConfig.MODE.get() == OptimizerConfig.Mode.GPU; }
    private static void fallback(String reason) {
        Diagnostics.INSTANCE.count(Diagnostics.Counter.CPU_FALLBACK, reason);
        var c = contexts.peek();
        if (c != null && c.kind != RendererAdmission.Kind.OTHER) Diagnostics.INSTANCE.count(c.kind == RendererAdmission.Kind.NPC ? Diagnostics.Counter.NPC_CPU_FALLBACK : c.kind == RendererAdmission.Kind.SOUL ? Diagnostics.Counter.SOUL_CPU_FALLBACK : Diagnostics.Counter.PLAYER_CPU_FALLBACK, reason);
    }
    public static void disable(String reason, Throwable error) { failed = true; Diagnostics.INSTANCE.reason("GPU", reason + (error == null ? "" : ": " + error)); if (error != null) RenderOptimizer.LOGGER.warn("GPU fallback: {}", reason, error); }
    private static long auxiliaryBytes() { return staging.bytes() + CpuVertexScratch.INSTANCE.bytes() + rerenders.bytes(); }
    /** Live same-frame re-render templates; for diagnostics and tests. */
    public static int rerenderTemplates() { return rerenders.size(); }
    public static long bytes() { return meshes.bytes() + pendingBytes + workingBytes + poses.idleBytes() + auxiliaryBytes() + top.wu949.dsbr.optimizer.lowend.LowEndAnimations.bytes(); }
    public static void finishFrame() {
        rerenders.clear();
        if (!queues.isEmpty()) {
            disable("buffer source left commands unflushed at end of frame", null);
            queues.forEach(GpuDispatcher::release); queues.clear();
        }
    }
    public static void maintenance() { poses.prune(System.nanoTime(), OptimizerConfig.meshMiB() * 1048576L - meshes.bytes() - workingBytes - pendingBytes - auxiliaryBytes() - top.wu949.dsbr.optimizer.lowend.LowEndAnimations.bytes()); meshes.prune(System.nanoTime(), OptimizerConfig.LOW_END.get() ? 10_000_000_000L : 30_000_000_000L, OptimizerConfig.meshMiB() * 1048576L - workingBytes - pendingBytes - poses.idleBytes() - auxiliaryBytes() - top.wu949.dsbr.optimizer.lowend.LowEndAnimations.bytes()); }
    public static void clear() {
        queues.forEach(GpuDispatcher::release); queues.clear(); bindings.clear();
        poses.clear(); rerenders.close(); meshes.clear(); compute.close(); staging.close(); CpuVertexScratch.INSTANCE.clear();
        if (poseBuffer != 0) { GL15.glDeleteBuffers(poseBuffer); GL15.glDeleteBuffers(outputBuffer); GL30.glDeleteVertexArrays(arrayObject); }
        poseBuffer = outputBuffer = arrayObject = 0; pendingBytes = workingBytes = 0; checkedBatches = 0;
    }
}
