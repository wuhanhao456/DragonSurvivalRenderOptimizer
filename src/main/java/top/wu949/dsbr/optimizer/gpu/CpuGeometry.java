package top.wu949.dsbr.optimizer.gpu;

import com.mojang.blaze3d.vertex.*;
import org.joml.*;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.util.RenderUtil;

/** Same arithmetic, order and consumer calls as GeckoLib's audited default geometry. */
public final class CpuGeometry {
    public static void cube(GeoRenderer<?> renderer, PoseStack pose, GeoCube cube, VertexConsumer buffer, int light, int overlay, int colour) {
        var slot = CpuVertexScratch.INSTANCE.acquire();
        try {
            RenderUtil.translateToPivotPoint(pose, cube); RenderUtil.rotateMatrixAroundCube(pose, cube); RenderUtil.translateAwayFromPivotPoint(pose, cube);
            slot.matrix.set(pose.last().pose());
            for (var quad : cube.quads()) if (quad != null) {
                pose.last().normal().transform(slot.normal.set(quad.normal())); RenderUtil.fixInvertedFlatCube(cube, slot.normal);
                renderer.createVerticesOfQuad(quad, slot.matrix, slot.normal, buffer, light, overlay, colour);
            }
        } finally { CpuVertexScratch.INSTANCE.release(slot); }
    }
    public static void quad(GeoQuad quad, Matrix4f matrix, Vector3f normal, VertexConsumer buffer, int light, int overlay, int colour) {
        var slot = CpuVertexScratch.INSTANCE.acquire();
        try {
            for (var vertex : quad.vertices()) {
                var p = vertex.position(); matrix.transform(slot.vertex.set(p.x(), p.y(), p.z(), 1));
                buffer.addVertex(slot.vertex.x(), slot.vertex.y(), slot.vertex.z(), colour, vertex.texU(), vertex.texV(), overlay, light, normal.x(), normal.y(), normal.z());
            }
        } finally { CpuVertexScratch.INSTANCE.release(slot); }
    }
    private CpuGeometry() {}
}
