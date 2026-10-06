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
    }
    private record Command(StaticMesh mesh, BudgetCache.Entry<StaticMesh> entry, PoseSnapshot pose, IrisBridge.Attributes attributes, boolean glow) {}
    private static final ComputeProgram compute = new ComputeProgram();
    private static final BudgetCache<ModelKey, StaticMesh> meshes = new BudgetCache<>(StaticMesh::close);
    private static final Map<VertexConsumer, Binding> bindings = new WeakHashMap<>();
    private static final Map<Object, Map<RenderType, List<Command>>> queues = new IdentityHashMap<>();
    private static final Deque<Context> contexts = new ArrayDeque<>();
    private static boolean failed;
    private static int glowDepth;
    public static void enterGlow() { glowDepth++; }
    public static void exitGlow() { glowDepth--; }
    private static long pendingBytes;
    private static long workingBytes;
    private static int poseBuffer, outputBuffer, arrayObject;
    public static void remember(Object source, RenderType type, VertexConsumer buffer) {
        // Unknown buffer sources are never admitted. Iris's unflushable wrapper delegates here.
        if (source.getClass() == MultiBufferSource.BufferSource.class || source.getClass().getName().equals("net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource"))
            bindings.put(buffer, new Binding(source, type));
    }
    public static Context begin(Object renderer, BakedGeoModel model, RenderType type, VertexConsumer buffer) {
        var c = new Context(); contexts.push(c);
        if (!enabled() || !(renderer instanceof DragonRenderer) || type == null || !(buffer instanceof BufferBuilder)) return c;
        if ((type.sortOnUpload() && glowDepth == 0) || type.mode() != VertexFormat.Mode.QUADS || !type.format().equals(DefaultVertexFormat.NEW_ENTITY)) { fallback("sorted material or custom vertex format"); return c; }
        var binding = bindings.get(buffer); if (binding == null) { fallback("unrecognized buffer source/consumer"); return c; }
        try {
            if (!GL.getCapabilities().OpenGL43) { disable("OpenGL 4.3 unavailable", null); return c; }
            if (renderer.getClass().getMethod("renderCube", PoseStack.class, GeoCube.class, VertexConsumer.class, int.class, int.class, int.class).getDeclaringClass() != software.bernie.geckolib.renderer.GeoRenderer.class
                || renderer.getClass().getMethod("createVerticesOfQuad", GeoQuad.class, org.joml.Matrix4f.class, org.joml.Vector3f.class, VertexConsumer.class, int.class, int.class, int.class).getDeclaringClass() != software.bernie.geckolib.renderer.GeoRenderer.class) {
                fallback("custom cube/vertex geometry"); return c;
            }
            compute.compile(); // Compile before cancelling any CPU geometry.
            var attributes = IrisBridge.capture(buffer);
            var key = new ModelKey(model); var entry = meshes.get(key, System.nanoTime());
            if (entry == null) {
                var mesh = StaticMesh.bake(model);
                if (mesh.quads.isEmpty() || mesh.bytes() > OptimizerConfig.MESH_MIB.get() * 1048576L) { fallback("mesh exceeds budget or empty model"); return c; }
                long available = OptimizerConfig.MESH_MIB.get() * 1048576L - workingBytes - pendingBytes - mesh.bytes();
                meshes.prune(System.nanoTime(), Long.MAX_VALUE, available);
                if (meshes.bytes() > available) { fallback("leased meshes exhaust budget"); return c; }
                mesh.upload(); meshes.put(key, mesh, mesh.bytes(), System.nanoTime()); entry = meshes.get(key, System.nanoTime());
            }
            long reserve = (long)entry.value.quads.size() * attributes.format().getVertexSize() * 4 + entry.value.bones.size() * 144L;
            if (meshes.bytes() + workingBytes + pendingBytes + reserve > OptimizerConfig.MESH_MIB.get() * 1048576L) { fallback("pending pose/vertex budget exhausted"); return c; }
            c.mesh = entry.value; c.entry = entry; c.binding = binding; c.attributes = attributes;
            c.pose = new PoseSnapshot(c.mesh.bones.size()); c.start = System.nanoTime(); entry.leases++; pendingBytes += reserve;
            c.glow = glowDepth > 0;
            Diagnostics.INSTANCE.peak("GPU_RESOURCE_BYTES", bytes());
        } catch (Exception | LinkageError e) { disable("GPU initialization", e); }
        return c;
    }
    public static boolean bone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay, int colour) {
        var c = contexts.peek(); if (c == null || c.mesh == null) return false;
        var binding = bindings.get(buffer);
        // A per-bone layer can replace a consumer. Leave such geometry on its original path.
        if (binding == null || binding.source != c.binding.source || binding.type != c.binding.type) { fallback("bone consumer changed"); return false; }
        var index = c.mesh.bones.get(bone); if (index == null) return false;
        if (!bone.isHidden() && c.pose.positions[index] != null) { fallback("bone submitted twice in a single pass"); return false; }
        if (!bone.isHidden()) c.pose.bone(index, pose.last(), colour, light, overlay);
        return true;
    }
    public static void end(Context context, boolean completed) {
        if (contexts.pop() != context) throw new IllegalStateException("GPU pose stack mismatch");
        if (context.mesh == null) return;
        Diagnostics.INSTANCE.nanos("GPU_POSE_CAPTURE", System.nanoTime() - context.start);
        if (completed && context.pose.anyVisible()) {
            queues.computeIfAbsent(context.binding.source, x -> new IdentityHashMap<>()).computeIfAbsent(context.binding.type, x -> new ArrayList<>())
                    .add(new Command(context.mesh, context.entry, context.pose, context.attributes, context.glow));
        } else release(new Command(context.mesh, context.entry, context.pose, context.attributes, context.glow));
    }
    public static boolean has(Object source, RenderType type) { var map = queues.get(source); return map != null && map.containsKey(type); }
    /** Caller owns RenderType state and the original Iris phase. */
    public static void draw(Object source, RenderType type) {
        var map = queues.get(source); if (map == null) return;
        var commands = map.remove(type); if (commands == null) return;
        try {
            for (var command : commands) {
                try { drawCommand(command); }
                catch (RuntimeException | LinkageError e) { disable("GPU dispatch/draw", e); }
                finally { release(command); }
            }
        } finally { if (map.isEmpty()) queues.remove(source); }
    }
    public static void drawWithState(Object source, RenderType type) {
        if (!has(source, type)) return;
        type.setupRenderState(); try { draw(source, type); } finally { type.clearRenderState(); }
    }
    private static void drawCommand(Command c) {
        long drawStart = System.nanoTime();
        try (var restore = new GlBindings()) {
            if (poseBuffer == 0) { poseBuffer = GL15.glGenBuffers(); outputBuffer = GL15.glGenBuffers(); arrayObject = GL30.glGenVertexArrays(); }
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, poseBuffer); GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, c.pose.data, GL15.GL_STREAM_DRAW);
            int stride = c.attributes.format().getVertexSize();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, outputBuffer); GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long)c.mesh.quads.size() * 4 * stride, GL15.GL_STREAM_DRAW);
            workingBytes = (long)c.mesh.quads.size() * 4 * stride + c.pose.data.capacity();
            Diagnostics.INSTANCE.peak("GPU_RESOURCE_BYTES", bytes());
            try {
                if (failed) throw new IllegalStateException("GPU was disabled earlier in this batch");
                compute.dispatch(c.mesh.buffer, poseBuffer, outputBuffer, c.mesh.quads.size(),
                        new ComputeProgram.IrisIds(c.attributes.entity(), c.attributes.blockEntity(), c.attributes.item(), c.attributes.recalculateNormal()), stride);
                int error = GL11.glGetError(); if (error != GL11.GL_NO_ERROR) throw new IllegalStateException("OpenGL compute error 0x" + Integer.toHexString(error));
            } catch (RuntimeException | LinkageError e) {
                disable("compute dispatch; replaying captured geometry on CPU", e);
                var cpu = CpuVertexEncoder.encode(c.mesh, c.pose, c.attributes);
                try { GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, outputBuffer); GL15.glBufferData(GL15.GL_ARRAY_BUFFER, cpu, GL15.GL_STREAM_DRAW); }
                finally { MemoryUtil.memFree(cpu); }
            }
            // Re-select the shader chosen by the surrounding RenderType, including Iris shadows.
            var shader = RenderSystem.getShader(); if (shader == null) throw new IllegalStateException("no entity shader in active render phase");
            GlStateManager._glBindVertexArray(arrayObject); GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, outputBuffer);
            c.attributes.format().setupBufferState();
            var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS); indices.bind(c.mesh.quads.size() * 6);
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), Minecraft.getInstance().getWindow());
            // glUseProgram(compute) bypassed the cache; force the raw driver back before shader.apply().
            GL20.glUseProgram(0); GlStateManager._glUseProgram(0);
            shader.apply();
            try { RenderSystem.drawElements(GL11.GL_TRIANGLES, c.mesh.quads.size() * 6, indices.type().asGLType); }
            finally { shader.clear(); c.attributes.format().clearBufferState(); }
            int error = GL11.glGetError(); if (error != GL11.GL_NO_ERROR) throw new IllegalStateException("OpenGL draw error 0x" + Integer.toHexString(error));
            Diagnostics.INSTANCE.count(Diagnostics.Counter.GPU_PASS, (c.glow ? "glow/" : "") + (c.attributes.shadow() ? "shadow" : "entity"));
            Diagnostics.INSTANCE.vertices(c.mesh.quads.size() * 4);
        } finally { Diagnostics.INSTANCE.nanos("GPU_DRAW_CPU", System.nanoTime() - drawStart); }
    }
    private static void release(Command c) {
        c.entry.leases--; pendingBytes -= (long)c.mesh.quads.size() * c.attributes.format().getVertexSize() * 4 + c.mesh.bones.size() * 144L; c.pose.close();
    }
    public static boolean enabled() { return !failed && OptimizerMixinPlugin.gpuCompatible && OptimizerConfig.MODE.get() == OptimizerConfig.Mode.GPU; }
    private static void fallback(String reason) { Diagnostics.INSTANCE.count(Diagnostics.Counter.CPU_FALLBACK, reason); }
    public static void disable(String reason, Throwable error) { failed = true; Diagnostics.INSTANCE.reason("GPU", reason + (error == null ? "" : ": " + error)); if (error != null) RenderOptimizer.LOGGER.warn("GPU fallback: {}", reason, error); }
    public static long bytes() { return meshes.bytes() + pendingBytes + workingBytes; }
    public static void finishFrame() {
        if (!queues.isEmpty()) {
            disable("buffer source left commands unflushed at end of frame", null);
            queues.values().forEach(m -> m.values().forEach(l -> l.forEach(GpuDispatcher::release))); queues.clear();
        }
    }
    public static void maintenance() { meshes.prune(System.nanoTime(), 30_000_000_000L, OptimizerConfig.MESH_MIB.get() * 1048576L - workingBytes - pendingBytes); }
    public static void clear() {
        queues.values().forEach(m -> m.values().forEach(l -> l.forEach(GpuDispatcher::release))); queues.clear(); bindings.clear();
        meshes.clear(); compute.close();
        if (poseBuffer != 0) { GL15.glDeleteBuffers(poseBuffer); GL15.glDeleteBuffers(outputBuffer); GL30.glDeleteVertexArrays(arrayObject); }
        poseBuffer = outputBuffer = arrayObject = 0; pendingBytes = workingBytes = 0;
    }
}
