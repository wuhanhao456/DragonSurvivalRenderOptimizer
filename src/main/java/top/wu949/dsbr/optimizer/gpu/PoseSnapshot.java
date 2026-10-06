package top.wu949.dsbr.optimizer.gpu;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.*;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;

/** Owns copies, never references a mutable Gecko bone or another player's PoseStack. */
public final class PoseSnapshot implements AutoCloseable {
    public final ByteBuffer data;
    public final Matrix4f[] positions;
    public final Matrix3f[] normals;
    public final int[] colours, lights, overlays;
    public final boolean[] visible;
    private final Matrix4f normalUpload = new Matrix4f();
    private int visibleCount;
    private boolean closed;
    public PoseSnapshot(int bones) {
        data = MemoryUtil.memCalloc(bones * 144);
        positions = new Matrix4f[bones]; normals = new Matrix3f[bones];
        colours = new int[bones]; lights = new int[bones]; overlays = new int[bones];
        visible = new boolean[bones];
        if (top.wu949.dsbr.optimizer.OptimizationStage.VALUE >= 3) for (int i = 0; i < bones; i++) { positions[i] = new Matrix4f(); normals[i] = new Matrix3f(); }
    }
    public void bone(int index, PoseStack.Pose pose, int colour, int light, int overlay) {
        if (closed) throw new IllegalStateException("closed pose");
        if (top.wu949.dsbr.optimizer.OptimizationStage.VALUE >= 3) { positions[index].set(pose.pose()); normals[index].set(pose.normal()); }
        else { positions[index] = new Matrix4f(pose.pose()); normals[index] = new Matrix3f(pose.normal()); }
        if (!visible[index]) { visible[index] = true; visibleCount++; }
        colours[index] = colour; lights[index] = light; overlays[index] = overlay;
        positions[index].get(index * 144, data);
        (top.wu949.dsbr.optimizer.OptimizationStage.VALUE >= 3 ? normalUpload.set(normals[index]) : new Matrix4f(normals[index])).get(index * 144 + 64, data);
        data.putInt(index * 144 + 128, colour).putInt(index * 144 + 132, light).putInt(index * 144 + 136, overlay).putInt(index * 144 + 140, 1);
    }
    public boolean anyVisible() { return visibleCount > 0; }
    /** Conservative retained native + heap estimate with uncompressed references. */
    public long retainedBytes() { return retainedBytes(visible.length); }
    public static long retainedBytes(int bones) { return 512L + bones * 368L; }
    public void reset() {
        if (closed) throw new IllegalStateException("closed pose");
        for (int i = 0; i < visible.length; i++) if (visible[i]) { visible[i] = false; data.putInt(i * 144 + 140, 0); }
        visibleCount = 0; data.clear();
    }
    public void close() { if (!closed) { closed = true; MemoryUtil.memFree(data); } }
}
