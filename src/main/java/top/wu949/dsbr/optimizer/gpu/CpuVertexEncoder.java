package top.wu949.dsbr.optimizer.gpu;

import top.wu949.dsbr.optimizer.compat.IrisBridge;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;

/** Emergency replay using already captured matrices; also serves as an independent GL test oracle. */
public final class CpuVertexEncoder {
    public static ByteBuffer encode(StaticMesh mesh, PoseSnapshot pose, IrisBridge.Attributes attributes) {
        int stride = attributes.format().getVertexSize();
        var out = MemoryUtil.memCalloc(mesh.quads.size() * 4 * stride);
        int quadIndex = 0;
        for (var q : mesh.quads) {
            int bone = q.bone(); if (!pose.visible[bone]) { quadIndex++; continue; }
            var p = new Vector3f[4]; for (int i = 0; i < 4; i++) p[i] = pose.positions[bone].transformPosition(new Vector3f(q.positions()[i]));
            var normal = pose.normals[bone].transform(new Vector3f(q.normal()));
            if ((q.flatMask() & 1) != 0) normal.x = Math.abs(normal.x);
            if ((q.flatMask() & 2) != 0) normal.y = Math.abs(normal.y);
            if ((q.flatMask() & 4) != 0) normal.z = Math.abs(normal.z);
            var face = unit(new Vector3f(p[2]).sub(p[0]).cross(new Vector3f(p[3]).sub(p[1])));
            if (stride == 54 && attributes.recalculateNormal()) normal = face;
            var uv = q.uv(); float au = uv[2] - uv[0], av = uv[3] - uv[1], cu = uv[4] - uv[0], cv = uv[5] - uv[1];
            float determinant = au * cv - cu * av, f = determinant == 0 ? 1 : 1 / determinant;
            var edge1 = new Vector3f(p[1]).sub(p[0]); var edge2 = new Vector3f(p[2]).sub(p[0]);
            var tangent = unit(new Vector3f(edge1).mul(cv).sub(new Vector3f(edge2).mul(av)).mul(f));
            var bitangent = unit(new Vector3f(edge1).mul(-cu).add(new Vector3f(edge2).mul(au)).mul(f));
            float sign = bitangent.dot(new Vector3f(tangent).cross(face)) < 0 ? -1 : 1;
            float midU = (uv[0] + uv[2] + uv[4] + uv[6]) / 4, midV = (uv[1] + uv[3] + uv[5] + uv[7]) / 4;
            int colour = pose.colours[bone]; int rgba = (colour >>> 16 & 255) | (colour & 65280) | ((colour & 255) << 16) | (colour & 0xff000000);
            for (int i = 0; i < 4; i++) {
                int at = (quadIndex * 4 + i) * stride;
                out.putFloat(at, p[i].x).putFloat(at + 4, p[i].y).putFloat(at + 8, p[i].z).putInt(at + 12, rgba);
                out.putFloat(at + 16, uv[i * 2]).putFloat(at + 20, uv[i * 2 + 1]);
                out.putInt(at + 24, pose.overlays[bone]).putInt(at + 28, pose.lights[bone]);
                out.put(at + 32, packed(normal.x)).put(at + 33, packed(normal.y)).put(at + 34, packed(normal.z));
                if (stride == 54) {
                    out.putShort(at + 36, (short)attributes.entity()).putShort(at + 38, (short)attributes.blockEntity()).putShort(at + 40, (short)attributes.item());
                    out.putFloat(at + 42, midU).putFloat(at + 46, midV);
                    out.put(at + 50, packed(tangent.x)).put(at + 51, packed(tangent.y)).put(at + 52, packed(tangent.z)).put(at + 53, packed(sign));
                }
            }
            quadIndex++;
        }
        return out;
    }
    private static Vector3f unit(Vector3f v) { return v.lengthSquared() > 1e-30 ? v.normalize() : v.zero(); }
    private static byte packed(float v) { return (byte)((int)(Math.clamp(v, -1f, 1f) * 127)); }
}
