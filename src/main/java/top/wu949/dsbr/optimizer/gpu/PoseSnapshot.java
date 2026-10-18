package top.wu949.dsbr.optimizer.gpu;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.*;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;

/**
 * Owns copies, never references a mutable Gecko bone or another player's PoseStack.
 * Per bone, {@link #data} holds the position matrix (64 bytes), the normal matrix padded to 4x4 (64 bytes),
 * then colour, light, overlay and a visible flag; it is the only copy of the matrices.
 */
public final class PoseSnapshot implements AutoCloseable {
    public static final int STRIDE = 144;
    public final ByteBuffer data;
    public final int[] colours, lights, overlays;
    public final boolean[] visible;
    private final Matrix4f normalUpload = new Matrix4f();
    private int visibleCount;
    private boolean closed;
    public PoseSnapshot(int bones) {
        data = MemoryUtil.memCalloc(bones * STRIDE);
        colours = new int[bones]; lights = new int[bones]; overlays = new int[bones];
        visible = new boolean[bones];
    }
    public void bone(int index, PoseStack.Pose pose, int colour, int light, int overlay) {
        if (closed) throw new IllegalStateException("closed pose");
        if (!visible[index]) { visible[index] = true; visibleCount++; }
        colours[index] = colour; lights[index] = light; overlays[index] = overlay;
        int at = index * STRIDE;
        // Stage < 3 isolates the cost of the earlier allocating capture for validation runs.
        if (top.wu949.dsbr.optimizer.OptimizationStage.VALUE >= 3) { pose.pose().get(at, data); normalUpload.set(pose.normal()).get(at + 64, data); }
        else { new Matrix4f(pose.pose()).get(at, data); new Matrix4f(new Matrix3f(pose.normal())).get(at + 64, data); }
        data.putInt(at + 128, colour).putInt(at + 132, light).putInt(at + 136, overlay).putInt(at + 140, 1);
    }
    /** Copies bones captured for the same mesh and applies one colour/light/overlay to every visible bone. */
    public void copyFrom(ByteBuffer source, boolean[] sourceVisible, int colour, int light, int overlay) {
        if (closed) throw new IllegalStateException("closed pose");
        if (sourceVisible.length != visible.length || source.capacity() != data.capacity()) throw new IllegalArgumentException("pose size mismatch");
        MemoryUtil.memCopy(MemoryUtil.memAddress0(source), MemoryUtil.memAddress0(data), data.capacity());
        visibleCount = 0;
        for (int i = 0; i < visible.length; i++) {
            visible[i] = sourceVisible[i];
            if (!visible[i]) continue;
            visibleCount++; colours[i] = colour; lights[i] = light; overlays[i] = overlay;
            data.putInt(i * STRIDE + 128, colour).putInt(i * STRIDE + 132, light).putInt(i * STRIDE + 136, overlay);
        }
    }
    public Matrix4f position(int index, Matrix4f dest) { return dest.set(index * STRIDE, data); }
    public Matrix3f normal(int index, Matrix3f dest) { return dest.set(new Matrix4f().set(index * STRIDE + 64, data)); }
    public boolean anyVisible() { return visibleCount > 0; }
    /** Conservative retained native + heap estimate with uncompressed references. */
    public long retainedBytes() { return retainedBytes(visible.length); }
    public static long retainedBytes(int bones) { return 512L + bones * 160L; }
    public void reset() {
        if (closed) throw new IllegalStateException("closed pose");
        for (int i = 0; i < visible.length; i++) if (visible[i]) { visible[i] = false; data.putInt(i * STRIDE + 140, 0); }
        visibleCount = 0; data.clear();
    }
    public void close() { if (!closed) { closed = true; MemoryUtil.memFree(data); } }
}
