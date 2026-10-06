package top.wu949.dsbr.optimizer;

import top.wu949.dsbr.optimizer.compat.IrisBridge;
import top.wu949.dsbr.optimizer.gpu.*;
import top.wu949.dsbr.optimizer.texture.GpuComposedTexture;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;

/** Hidden real GL context. Verifies binary shader output and GPU texture copies on this driver. */
public final class GlIntegrationCheck {
    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new IllegalStateException("GLFW initialization failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4); GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(64, 64, "BeLoong GL validation", 0, 0);
        if (window == 0) throw new IllegalStateException("No OpenGL 4.3 context");
        GLFW.glfwMakeContextCurrent(window); GL.createCapabilities(); RenderSystem.initRenderThread();
        var results = new LinkedHashMap<String, Object>();
        results.put("vendor", GL11.glGetString(GL11.GL_VENDOR)); results.put("renderer", GL11.glGetString(GL11.GL_RENDERER)); results.put("version", GL11.glGetString(GL11.GL_VERSION));
        try {
            textureCheck(); results.put("gpuTextureCopyAndLazyReadback", "pass");
            shaderCheck(); results.put("computeVanillaAndIris54ByteOutput", "pass"); results.put("threeIndependentAnimatedInstances", "pass");
            bindingCheck(); results.put("batchBindingsRestoredAfterException", "pass");
            int error = GL11.glGetError(); if (error != GL11.GL_NO_ERROR) throw new AssertionError("GL error " + error);
            results.put("glError", error);
        } finally { GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
        var directory = Path.of("validation/alpha4"); Files.createDirectories(directory);
        Files.writeString(directory.resolve("gl-results.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results));
        System.out.println(results);
    }
    private static void textureCheck() {
        var target = new TextureTarget(32, 16, false, false); var texture = new GpuComposedTexture();
        try {
            target.setClearColor(.25f, .5f, .75f, .125f); target.clear(false);
            int oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            long reads = ((Map<?, ?>)top.wu949.dsbr.optimizer.diagnostics.Diagnostics.INSTANCE.snapshot().get("totals")).size();
            texture.copy(target);
            if (GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) != oldRead) throw new AssertionError("read framebuffer not restored");
            var counters = (Map<?, ?>)top.wu949.dsbr.optimizer.diagnostics.Diagnostics.INSTANCE.snapshot().get("totals");
            if (counters.containsKey("READBACK")) throw new AssertionError("normal copy read back pixels");
            var pixels = texture.getPixels(); int rgba = pixels.getPixelRGBA(0, 0);
            if (Math.abs((rgba & 255) - 64) > 1 || Math.abs((rgba >>> 8 & 255) - 128) > 1 || Math.abs((rgba >>> 16 & 255) - 191) > 1 || Math.abs((rgba >>> 24 & 255) - 32) > 1)
                throw new AssertionError("GPU copy channel/orientation mismatch: " + Integer.toHexString(rgba));
            if (pixels != texture.getPixels()) throw new AssertionError("lazy image not reused");
            target.setClearColor(1, 0, 0, 1); target.clear(false); texture.copy(target);
            if (texture.getPixels().getPixelRGBA(0, 0) != 0xff0000ff) throw new AssertionError("readback was not invalidated");
            try (var image = new com.mojang.blaze3d.platform.NativeImage(4, 4, false)) {
                image.fillRect(0, 0, 4, 4, 0xff00ff00); texture.setPixels(image); texture.upload();
            }
            if (texture.getPixels().getPixelRGBA(0, 0) != 0xff00ff00) throw new AssertionError("explicit upload retained a disposed DS input image");
        } finally { texture.close(); texture.close(); target.destroyBuffers(); }
    }
    private static void bindingCheck() {
        int vao = GL30.glGenVertexArrays(), changedVao = GL30.glGenVertexArrays();
        int original = GL15.glGenBuffers(), changed = GL15.glGenBuffers();
        int alignment = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT);
        try {
            GL30.glBindVertexArray(vao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, original);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, alignment * 3L, GL15.GL_STATIC_DRAW);
            for (int i = 0; i < 3; i++) GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, i, original, alignment, alignment);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, original);
            try (var restore = new GlBindings()) {
                for (int command = 0; command < 3; command++) {
                    GL30.glBindVertexArray(changedVao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, changed);
                    GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, command, changed);
                }
                throw new IllegalArgumentException("simulated failed command");
            } catch (IllegalArgumentException expected) { }
            if (GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) != vao || GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) != original
                || GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING) != original) throw new AssertionError("batch did not restore bindings");
            for (int i = 0; i < 3; i++) if (GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, i) != original
                || GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START, i) != alignment
                || GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, i) != alignment) throw new AssertionError("indexed range not restored");
        } finally {
            for (int i = 0; i < 3; i++) GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, i, 0);
            GL30.glBindVertexArray(0); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0); GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
            GL15.glDeleteBuffers(original); GL15.glDeleteBuffers(changed); GL30.glDeleteVertexArrays(vao); GL30.glDeleteVertexArrays(changedVao);
        }
    }
    private static VertexFormat irisFormat() {
        return VertexFormat.builder().add("Position", VertexFormatElement.POSITION).add("Color", VertexFormatElement.COLOR).add("UV0", VertexFormatElement.UV0)
                .add("UV1", VertexFormatElement.UV1).add("UV2", VertexFormatElement.UV2).add("Normal", VertexFormatElement.NORMAL).padding(1)
                .add("iris_Entity", VertexFormatElement.register(10, 3, VertexFormatElement.Type.USHORT, VertexFormatElement.Usage.UV, 3))
                .add("mc_midTexCoord", VertexFormatElement.register(11, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 2))
                .add("at_tangent", VertexFormatElement.register(12, 0, VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.GENERIC, 4)).build();
    }
    private static void shaderCheck() throws Exception {
        var extended = irisFormat(); var mesh = new StaticMesh(); var random = new Random(124091);
        // Odd/even quads exercise Iris's 54-byte two-byte alignment; hidden bones and flat wings included.
        for (int i = 0; i < 129; i++) {
            float z = random.nextFloat(); var positions = new Vector3f[]{new Vector3f(-1, -1, z), new Vector3f(1, -1, z), new Vector3f(1, 1, z), new Vector3f(-1, 1, z)};
            mesh.quads.add(new StaticMesh.Quad(i % 3, positions, new Vector3f(0, 0, -1), new float[]{0, 0, 1, 0, 1, 1, 0, 1}, i % 2 == 0 ? 7 : 0));
        }
        mesh.upload(); int poses = GL15.glGenBuffers(), output = GL15.glGenBuffers();
        try (var compute = new ComputeProgram(); var snapshot = new PoseSnapshot(3)) {
            compute.compile(); var stack = new PoseStack(); stack.translate(5, -3, .25); stack.mulPose(new Quaternionf().rotationXYZ(.4f, .6f, -.3f)); stack.scale(-.75f, 1.25f, 2f);
            snapshot.bone(0, stack.last(), 0x80123456, 0xf000f0, 0x12340056);
            stack.translate(-4, 3, 2); snapshot.bone(1, stack.last(), 0xff765432, 0x120078, 0x000a000b);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, poses); GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, snapshot.data, GL15.GL_STREAM_DRAW);
            for (var format : List.of(DefaultVertexFormat.NEW_ENTITY, extended)) for (boolean recalc : List.of(false, true)) {
                var attributes = new IrisBridge.Attributes(format, 65530, 124, 25000, recalc, false);
                int size = mesh.quads.size() * 4 * format.getVertexSize();
                GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, output); GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, size, GL15.GL_STREAM_DRAW);
                compute.dispatch(mesh.buffer, poses, output, mesh.quads.size(), new ComputeProgram.IrisIds(65530, 124, 25000, recalc), format.getVertexSize());
                GL42.glMemoryBarrier(GL43.GL_BUFFER_UPDATE_BARRIER_BIT);
                var actual = MemoryUtil.memAlloc(size); var expected = CpuVertexEncoder.encode(mesh, snapshot, attributes);
                try {
                    GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, output); GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, actual);
                    compare(actual, expected, format.getVertexSize(), mesh);
                } finally { MemoryUtil.memFree(actual); MemoryUtil.memFree(expected); }
            }
            var snapshots = List.of(snapshot, new PoseSnapshot(3), new PoseSnapshot(3));
            try {
                for (int i = 1; i < snapshots.size(); i++) {
                    var independent = new PoseStack(); independent.translate(i * 7, i * 3, -i);
                    independent.mulPose(new Quaternionf().rotationXYZ(i * .3f, -.2f, i * .8f)); independent.scale(i, .5f, -1);
                    snapshots.get(i).bone(0, independent.last(), 0xff112233 + i, 0x500060 + i, i);
                    independent.translate(0, 5, 2); snapshots.get(i).bone(1, independent.last(), 0x80112233 + i, 0x700080, 0x55);
                }
                GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, poses); GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long)snapshot.data.capacity() * snapshots.size(), GL15.GL_STREAM_DRAW);
                for (int i = 0; i < snapshots.size(); i++) GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, (long)i * snapshot.data.capacity(), snapshots.get(i).data);
                for (var format : List.of(DefaultVertexFormat.NEW_ENTITY, extended)) for (boolean recalc : List.of(false, true)) {
                    var attributes = new IrisBridge.Attributes(format, 65530, 124, 25000, recalc, false);
                    int size = mesh.quads.size() * 4 * format.getVertexSize();
                    GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, output); GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long)size * snapshots.size(), GL15.GL_STREAM_DRAW);
                    compute.dispatch(mesh.buffer, poses, output, mesh.quads.size(), snapshots.size(), 3, new ComputeProgram.IrisIds(65530, 124, 25000, recalc), format.getVertexSize());
                    GL42.glMemoryBarrier(GL43.GL_BUFFER_UPDATE_BARRIER_BIT);
                    var actual = MemoryUtil.memAlloc(size * snapshots.size());
                    try {
                        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, output); GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, actual);
                        for (int i = 0; i < snapshots.size(); i++) {
                            var expected = CpuVertexEncoder.encode(mesh, snapshots.get(i), attributes);
                            try { compare(actual.slice(i * size, size).order(java.nio.ByteOrder.nativeOrder()), expected, format.getVertexSize(), mesh); }
                            finally { MemoryUtil.memFree(expected); }
                        }
                    } finally { MemoryUtil.memFree(actual); }
                }
            } finally { snapshots.get(1).close(); snapshots.get(2).close(); }
        } finally { GL15.glDeleteBuffers(poses); GL15.glDeleteBuffers(output); mesh.close(); }
    }
    private static void compare(ByteBuffer actual, ByteBuffer expected, int stride, StaticMesh mesh) {
        for (int v = 0; v < mesh.quads.size() * 4; v++) {
            if (mesh.quads.get(v / 4).bone() == 2) {
                for (int b = 0; b < 16; b++) if (actual.get(v * stride + b) != 0) throw new AssertionError("hidden bone emitted position/colour");
                continue;
            }
            for (int offset : stride == 54 ? new int[]{0, 4, 8, 16, 20, 42, 46} : new int[]{0, 4, 8, 16, 20}) {
                float a = actual.getFloat(v * stride + offset), e = expected.getFloat(v * stride + offset);
                if (!Float.isFinite(a) || Math.abs(a - e) > .00001f) throw new AssertionError("float mismatch vertex=" + v + " offset=" + offset + " actual=" + a + " expected=" + e);
            }
            for (int offset : new int[]{12, 24, 28}) if (actual.getInt(v * stride + offset) != expected.getInt(v * stride + offset)) throw new AssertionError("colour/light/overlay mismatch");
            for (int offset : stride == 54 ? new int[]{32, 33, 34, 50, 51, 52, 53} : new int[]{32, 33, 34})
                if (Math.abs(actual.get(v * stride + offset) - expected.get(v * stride + offset)) > 1) throw new AssertionError("normal/tangent mismatch");
            if (stride == 54) for (int offset : new int[]{36, 38, 40}) if (actual.getShort(v * stride + offset) != expected.getShort(v * stride + offset)) throw new AssertionError("Iris entity ID mismatch");
        }
    }
}
