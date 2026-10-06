package top.wu949.dsbr.optimizer.gpu;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.*;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.util.RenderUtil;
import java.nio.ByteBuffer;
import java.util.*;

/** Immutable geometry extracted from Gecko's baked cubes, retaining cube pivot rotations. */
public final class StaticMesh implements AutoCloseable {
    public record Quad(int bone, Vector3f[] positions, Vector3f normal, float[] uv, int flatMask) {}
    public final IdentityHashMap<GeoBone, Integer> bones = new IdentityHashMap<>();
    public final List<Quad> quads = new ArrayList<>();
    public int buffer;
    public static StaticMesh bake(BakedGeoModel model) {
        var mesh = new StaticMesh(); for (var bone : model.topLevelBones()) mesh.bakeBone(bone); return mesh;
    }
    private void bakeBone(GeoBone bone) {
        int index = bones.size(); bones.put(bone, index);
        for (var cube : bone.getCubes()) {
            var stack = new PoseStack(); RenderUtil.translateToPivotPoint(stack, cube);
            RenderUtil.rotateMatrixAroundCube(stack, cube); RenderUtil.translateAwayFromPivotPoint(stack, cube);
            int flat = (cube.size().y == 0 || cube.size().z == 0 ? 1 : 0)
                    | (cube.size().x == 0 || cube.size().z == 0 ? 2 : 0) | (cube.size().x == 0 || cube.size().y == 0 ? 4 : 0);
            for (var quad : cube.quads()) {
                if (quad == null) continue;
                if (quad.vertices().length != 4) throw new IllegalArgumentException("non-quad Gecko geometry");
                var positions = new Vector3f[4]; var uv = new float[8];
                for (int i = 0; i < 4; i++) {
                    var v = quad.vertices()[i]; positions[i] = stack.last().pose().transformPosition(new Vector3f(v.position()));
                    uv[i * 2] = v.texU(); uv[i * 2 + 1] = v.texV();
                }
                quads.add(new Quad(index, positions, stack.last().normal().transform(new Vector3f(quad.normal())), uv, flat));
            }
        }
        for (var child : bone.getChildBones()) bakeBone(child);
    }
    public ByteBuffer encoded() {
        var data = MemoryUtil.memAlloc(quads.size() * 128);
        for (var q : quads) {
            for (var p : q.positions) data.putFloat(p.x).putFloat(p.y).putFloat(p.z).putFloat(1);
            data.putFloat(q.normal.x).putFloat(q.normal.y).putFloat(q.normal.z).putFloat(0);
            for (float f : q.uv) data.putFloat(f);
            data.putInt(q.bone).putInt(q.flatMask).putInt(0).putInt(0);
        }
        return data.flip();
    }
    public void upload() {
        buffer = GL15.glGenBuffers(); var data = encoded(); int old = GL15.glGetInteger(org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        try { GL15.glBindBuffer(org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER, buffer); GL15.glBufferData(org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER, data, GL15.GL_STATIC_DRAW); }
        finally { GL15.glBindBuffer(org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER, old); MemoryUtil.memFree(data); }
    }
    public long bytes() { return quads.size() * 256L + bones.size() * 144L; }
    public void close() { if (buffer != 0) GL15.glDeleteBuffers(buffer); buffer = 0; }
}
