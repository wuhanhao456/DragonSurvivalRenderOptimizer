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
    public PoseSnapshot(int bones) {
        data = MemoryUtil.memCalloc(bones * 144);
        positions = new Matrix4f[bones]; normals = new Matrix3f[bones];
        colours = new int[bones]; lights = new int[bones]; overlays = new int[bones];
    }
    public void bone(int index, PoseStack.Pose pose, int colour, int light, int overlay) {
        positions[index] = new Matrix4f(pose.pose()); normals[index] = new Matrix3f(pose.normal());
        colours[index] = colour; lights[index] = light; overlays[index] = overlay;
        positions[index].get(index * 144, data);
        new Matrix4f(normals[index]).get(index * 144 + 64, data);
        data.putInt(index * 144 + 128, colour).putInt(index * 144 + 132, light).putInt(index * 144 + 136, overlay).putInt(index * 144 + 140, 1);
    }
    public boolean anyVisible() { for (var p : positions) if (p != null) return true; return false; }
    public void close() { MemoryUtil.memFree(data); }
}
