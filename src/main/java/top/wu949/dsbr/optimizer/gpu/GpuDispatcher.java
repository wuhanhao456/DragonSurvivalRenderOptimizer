package top.wu949.dsbr.optimizer.gpu;

import by.dragonsurvivalteam.dragonsurvival.client.render.entity.dragon.DragonRenderer;
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
    }
    private record Command(StaticMesh mesh, BudgetCache.Entry<StaticMesh> entry, PoseSnapshot pose, IrisBridge.Attributes attributes, boolean glow, boolean soul) {}
    private static final ComputeProgram compute = new ComputeProgram();
    private static final PosePool poses = new PosePool();
    private static boolean originalGeometry(Class<?> type) {
        try { return type.getMethod("renderCube", PoseStack.class, GeoCube.class, VertexConsumer.class, int.class, int.class, int.class).getDeclaringClass() == software.bernie.geckolib.renderer.GeoRenderer.class
            && type.getMethod("createVerticesOfQuad", GeoQuad.class, org.joml.Matrix4f.class, org.joml.Vector3f.class, VertexConsumer.class, int.class, int.class, int.class).getDeclaringClass() == software.bernie.geckolib.renderer.GeoRenderer.class;
        } catch (ReflectiveOperationException e) { return false; }
    }
    private static final ClassValue<Boolean> geometry = new ClassValue<>() { protected Boolean computeValue(Class<?> type) { return originalGeometry(type); } };
    private static final BudgetCache<ModelKey, StaticMesh> meshes = new BudgetCache<>(StaticMesh::close);
    private static final Map<VertexConsumer, Binding> bindings = new WeakHashMap<>();
    private static final DeferredBatches<Object, RenderType, Command> queues = new DeferredBatches<>();
    private static final Deque<Context> contexts = new ArrayDeque<>();
    private static boolean failed;
    private static int glowDepth;
    private static int worldDepth;
    public static void enterWorld() { worldDepth++; }
    public static void exitWorld() { worldDepth--; }
    public static void enterGlow() { glowDepth++; }
    public static void exitGlow() { glowDepth--; }
    private static long pendingBytes;
    private static long workingBytes;
    private static int poseBuffer, outputBuffer, arrayObject;
    private static long ssboLimit, workGroupLimit;
    public static void remember(Object source, RenderType type, VertexConsumer buffer) {
        // Unknown buffer sources are never admitted. Iris's unflushable wrapper delegates here.
        if (source.getClass() == MultiBufferSource.BufferSource.class || source.getClass().getName().equals("net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource"))
            bindings.put(buffer, new Binding(source, type));
    }
    public static Context begin(Object renderer, BakedGeoModel model, RenderType type, VertexConsumer buffer) {
        var c = new Context(); contexts.push(c);
        c.soul = top.wu949.dsbr.optimizer.soul.SoulRenderContext.active();
        if (!enabled() || !(renderer instanceof DragonRenderer) || type == null || !(buffer instanceof BufferBuilder)) return c;
        // GUI previews temporarily replace projection, lighting and model-view state. A deferred
        // world batch cannot retain that state. Keep their original immediate CPU submission.
        if (worldDepth == 0) { Diagnostics.INSTANCE.count(Diagnostics.Counter.GUI_CPU_PASS, "gui"); return c; }
        if ((type.sortOnUpload() && glowDepth == 0) || type.mode() != VertexFormat.Mode.QUADS || !type.format().equals(DefaultVertexFormat.NEW_ENTITY)) { fallback("sorted material or custom vertex format"); return c; }
        var binding = bindings.get(buffer); if (binding == null) { fallback("unrecognized buffer source/consumer"); return c; }
        try {
            if (!GL.getCapabilities().OpenGL43) { disable("OpenGL 4.3 unavailable", null); return c; }
            if (!(OptimizationStage.VALUE >= 4 ? geometry.get(renderer.getClass()) : originalGeometry(renderer.getClass()))) {
                fallback("custom cube/vertex geometry"); return c;
            }
            compute.compile(); // Compile before cancelling any CPU geometry.
            if (ssboLimit == 0) { ssboLimit = GL32.glGetInteger64(GL43.GL_MAX_SHADER_STORAGE_BLOCK_SIZE); workGroupLimit = GL30.glGetIntegeri(GL43.GL_MAX_COMPUTE_WORK_GROUP_COUNT, 0); }
            var attributes = IrisBridge.capture(buffer);
            var key = new ModelKey(model); var entry = meshes.get(key, System.nanoTime());
            if (entry == null) {
                var mesh = StaticMesh.bake(model);
                if (mesh.quads.isEmpty() || mesh.bytes() > OptimizerConfig.MESH_MIB.get() * 1048576L) { fallback("mesh exceeds budget or empty model"); return c; }
                poses.prune(System.nanoTime(), OptimizerConfig.MESH_MIB.get() * 1048576L - meshes.bytes() - workingBytes - pendingBytes - mesh.bytes());
                long available = OptimizerConfig.MESH_MIB.get() * 1048576L - workingBytes - pendingBytes - poses.idleBytes() - mesh.bytes();
                meshes.prune(System.nanoTime(), Long.MAX_VALUE, available);
                if (meshes.bytes() > available) { fallback("leased meshes exhaust budget"); return c; }
                mesh.upload(); meshes.put(key, mesh, mesh.bytes(), System.nanoTime()); entry = meshes.get(key, System.nanoTime());
            }
            long reserve = (long)entry.value.quads.size() * attributes.format().getVertexSize() * 4 + PoseSnapshot.retainedBytes(entry.value.bones.size());
            if (BatchLimits.instances(entry.value.quads.size(), entry.value.bones.size(), attributes.format().getVertexSize(), OptimizerConfig.MESH_MIB.get() * 1048576L,
                    ssboLimit, workGroupLimit) < 1) { fallback("geometry exceeds GL dispatch limits"); return c; }
            poses.prune(System.nanoTime(), OptimizerConfig.MESH_MIB.get() * 1048576L - meshes.bytes() - workingBytes - pendingBytes - reserve);
            long singleWorkingBytes = (long)entry.value.quads.size() * attributes.format().getVertexSize() * 4 + (long)entry.value.bones.size() * 144;
            if (bytes() + reserve + singleWorkingBytes > OptimizerConfig.MESH_MIB.get() * 1048576L) { fallback("pending pose/vertex budget exhausted"); return c; }
            c.mesh = entry.value; c.entry = entry; c.binding = binding; c.attributes = attributes;
            c.pose = OptimizationStage.VALUE >= 3 ? poses.acquire(c.mesh.bones.size()) : new PoseSnapshot(c.mesh.bones.size()); c.start = System.nanoTime(); entry.leases++; pendingBytes += reserve;
            c.glow = glowDepth > 0;
            Diagnostics.INSTANCE.peak("GPU_RESOURCE_BYTES", bytes());
        } catch (Exception | LinkageError e) { disable("GPU initialization", e); }
        return c;
    }
    public static boolean bone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay, int colour) {
        var c = contexts.peek(); if (c == null || c.mesh == null) return false;
        var binding = bindings.get(buffer);
        // A per-bone layer can replace a consumer. Leave such geometry on its original path.
        if (binding == null || binding.source != c.binding.source || !Objects.equals(binding.type, c.binding.type)) { fallback("bone consumer changed"); return false; }
        var index = c.mesh.bones.get(bone); if (index == null) return false;
        if (!bone.isHidden() && c.pose.visible[index]) { fallback("bone submitted twice in a single pass"); return false; }
        if (!bone.isHidden()) c.pose.bone(index, pose.last(), colour, light, overlay);
        return true;
    }
    public static void end(Context context, boolean completed) {
        if (contexts.pop() != context) throw new IllegalStateException("GPU pose stack mismatch");
        if (context.mesh == null) return;
        Diagnostics.INSTANCE.nanos("GPU_POSE_CAPTURE", System.nanoTime() - context.start);
        if (completed && context.pose.anyVisible()) {
            queues.add(context.binding.source, context.binding.type, new Command(context.mesh, context.entry, context.pose, context.attributes, context.glow, context.soul));
        } else release(new Command(context.mesh, context.entry, context.pose, context.attributes, context.glow, context.soul));
    }
    public static boolean has(Object source, RenderType type) { return queues.has(source, type); }
    /** Caller owns RenderType state and the original Iris phase. */
    public static void draw(Object source, RenderType type) {
        var commands = queues.take(source, type); if (commands == null) return;
        long stateStart = System.nanoTime();
        try (var restore = OptimizationStage.VALUE >= 4 ? new GlBindings() : null) {
            for (int i = 0; i < commands.size();) {
                var command = commands.get(i); int end = i + 1;
                long available = OptimizerConfig.MESH_MIB.get() * 1048576L - meshes.bytes() - pendingBytes - poses.idleBytes();
                int max = Math.max(1, BatchLimits.instances(command.mesh.quads.size(), command.mesh.bones.size(), command.attributes.format().getVertexSize(), available,
                        ssboLimit, workGroupLimit));
                if (command.soul) while (end < commands.size() && end - i < max && compatible(command, commands.get(end))) end++;
                var batch = commands.subList(i, end); i = end;
                try { if (OptimizationStage.VALUE >= 4) drawCommands(batch); else try (var perCommand = new GlBindings()) { drawCommands(batch); } }
                catch (RuntimeException | LinkageError e) { disable("GPU dispatch/draw", e); }
            }
        } catch (RuntimeException | LinkageError e) { disable("GPU batch state", e); }
        finally { for (var command : commands) release(command); Diagnostics.INSTANCE.nanos("GPU_BATCH_CPU", System.nanoTime() - stateStart); }
    }
    public static void drawWithState(Object source, RenderType type) {
        if (!has(source, type)) return;
        type.setupRenderState(); try { draw(source, type); } finally { type.clearRenderState(); }
    }
    private static boolean compatible(Command a, Command b) { return b.soul && a.mesh == b.mesh && a.glow == b.glow && a.attributes.equals(b.attributes); }
    private static void drawCommands(List<Command> batch) {
        var c = batch.getFirst(); int count = batch.size(), quads = c.mesh.quads.size();
        long drawStart = System.nanoTime();
        try {
            if (poseBuffer == 0) { poseBuffer = GL15.glGenBuffers(); outputBuffer = GL15.glGenBuffers(); arrayObject = GL30.glGenVertexArrays(); }
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, poseBuffer); GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long)c.pose.data.capacity() * count, GL15.GL_STREAM_DRAW);
            for (int i = 0; i < count; i++) GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, (long)i * c.pose.data.capacity(), batch.get(i).pose.data);
            int stride = c.attributes.format().getVertexSize();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, outputBuffer); GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long)quads * count * 4 * stride, GL15.GL_STREAM_DRAW);
            workingBytes = ((long)quads * 4 * stride + c.pose.data.capacity()) * count;
            Diagnostics.INSTANCE.peak("GPU_RESOURCE_BYTES", bytes());
            try {
                if (failed) throw new IllegalStateException("GPU was disabled earlier in this batch");
                compute.dispatch(c.mesh.buffer, poseBuffer, outputBuffer, quads, count, c.mesh.bones.size(),
                        new ComputeProgram.IrisIds(c.attributes.entity(), c.attributes.blockEntity(), c.attributes.item(), c.attributes.recalculateNormal()), stride);
                int error = GL11.glGetError(); if (error != GL11.GL_NO_ERROR) throw new IllegalStateException("OpenGL compute error 0x" + Integer.toHexString(error));
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
            int error = GL11.glGetError(); if (error != GL11.GL_NO_ERROR) throw new IllegalStateException("OpenGL draw error 0x" + Integer.toHexString(error));
            Diagnostics.INSTANCE.count(Diagnostics.Counter.GPU_DRAW_CALLS, "draw");
            if (c.soul) Diagnostics.INSTANCE.count(Diagnostics.Counter.SOUL_GPU_DRAW_CALLS, "soul");
            for (var command : batch) {
                Diagnostics.INSTANCE.count(Diagnostics.Counter.GPU_PASS, c.glow ? (c.attributes.shadow() ? "glow/shadow" : "glow/entity") : (c.attributes.shadow() ? "shadow" : "entity"));
                if (command.soul) Diagnostics.INSTANCE.count(Diagnostics.Counter.SOUL_GPU_PASS, "soul");
            }
            Diagnostics.INSTANCE.vertices(quads * count * 4);
        } finally { Diagnostics.INSTANCE.nanos("GPU_DRAW_CPU", System.nanoTime() - drawStart); }
    }
    private static void release(Command c) {
        c.entry.leases--; pendingBytes -= (long)c.mesh.quads.size() * c.attributes.format().getVertexSize() * 4 + c.pose.retainedBytes();
        if (OptimizationStage.VALUE >= 3) poses.release(c.pose); else c.pose.close();
    }
    public static boolean enabled() { return !failed && OptimizerMixinPlugin.gpuCompatible && OptimizerConfig.MODE.get() == OptimizerConfig.Mode.GPU; }
    private static void fallback(String reason) { Diagnostics.INSTANCE.count(Diagnostics.Counter.CPU_FALLBACK, reason); if (top.wu949.dsbr.optimizer.soul.SoulRenderContext.active()) Diagnostics.INSTANCE.count(Diagnostics.Counter.SOUL_CPU_FALLBACK, reason); }
    public static void disable(String reason, Throwable error) { failed = true; Diagnostics.INSTANCE.reason("GPU", reason + (error == null ? "" : ": " + error)); if (error != null) RenderOptimizer.LOGGER.warn("GPU fallback: {}", reason, error); }
    public static long bytes() { return meshes.bytes() + pendingBytes + workingBytes + poses.idleBytes(); }
    public static void finishFrame() {
        if (!queues.isEmpty()) {
            disable("buffer source left commands unflushed at end of frame", null);
            queues.forEach(GpuDispatcher::release); queues.clear();
        }
    }
    public static void maintenance() { poses.prune(System.nanoTime(), OptimizerConfig.MESH_MIB.get() * 1048576L - meshes.bytes() - workingBytes - pendingBytes); meshes.prune(System.nanoTime(), 30_000_000_000L, OptimizerConfig.MESH_MIB.get() * 1048576L - workingBytes - pendingBytes - poses.idleBytes()); }
    public static void clear() {
        queues.forEach(GpuDispatcher::release); queues.clear(); bindings.clear();
        poses.clear(); meshes.clear(); compute.close();
        if (poseBuffer != 0) { GL15.glDeleteBuffers(poseBuffer); GL15.glDeleteBuffers(outputBuffer); GL30.glDeleteVertexArrays(arrayObject); }
        poseBuffer = outputBuffer = arrayObject = 0; pendingBytes = workingBytes = 0;
    }
}
