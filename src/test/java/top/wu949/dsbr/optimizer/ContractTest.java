package top.wu949.dsbr.optimizer;

import top.wu949.dsbr.optimizer.compat.Contracts;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContractTest {
    @Test void pinnedDsJarHasAllTextureInjectionContractsIncludingFramebufferOwner() throws Exception {
        for (var required : Contracts.TEXTURES.entrySet()) {
            try (var in = getClass().getResourceAsStream("/" + required.getKey().replace('.', '/') + ".class")) {
                assertNotNull(in, required.getKey()); var node = Contracts.read(in.readAllBytes());
                assertTrue(Contracts.methods(node, required.getValue()), required.getKey());
                if (node.name.endsWith("DragonEditorHandler")) assertTrue(Contracts.framebufferCalls(node, "generateSkinTextures"));
                if (node.name.endsWith("DragonArmorRenderLayer")) assertTrue(Contracts.framebufferCalls(node, "generateArmorTexture"));
            }
        }
    }
    @Test void modifiedSignatureFailsClosed() throws Exception {
        var entry = Contracts.TEXTURES.entrySet().iterator().next();
        try (var in = getClass().getResourceAsStream("/" + entry.getKey().replace('.', '/') + ".class")) {
            var node = Contracts.read(in.readAllBytes());
            node.methods.removeIf(m -> m.name.equals(entry.getValue().getFirst().name()));
            assertFalse(Contracts.methods(node, entry.getValue()));
        }
    }
}
