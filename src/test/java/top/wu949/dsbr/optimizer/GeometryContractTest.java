package top.wu949.dsbr.optimizer;

import org.junit.jupiter.api.Test;
import software.bernie.geckolib.renderer.GeoRenderer;
import top.wu949.dsbr.optimizer.compat.Contracts;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GeometryContractTest {
    @Test void pinnedLibraryHasBothCpuHookSignaturesAndMissingEitherFailsClosed() throws Exception {
        var required=List.of(
            new Contracts.Method("renderCube","(Lcom/mojang/blaze3d/vertex/PoseStack;Lsoftware/bernie/geckolib/cache/object/GeoCube;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
            new Contracts.Method("createVerticesOfQuad","(Lsoftware/bernie/geckolib/cache/object/GeoQuad;Lorg/joml/Matrix4f;Lorg/joml/Vector3f;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"));
        byte[] bytes;
        try(var in=GeoRenderer.class.getResourceAsStream("GeoRenderer.class")){assertNotNull(in);bytes=in.readAllBytes();}
        assertTrue(Contracts.methods(Contracts.read(bytes),required));
        for(var missing:required){var node=Contracts.read(bytes);node.methods.removeIf(m->m.name.equals(missing.name())&&m.desc.equals(missing.descriptor()));assertFalse(Contracts.methods(node,required));}
    }
}
