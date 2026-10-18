package top.wu949.dsbr.optimizer;

import top.wu949.dsbr.optimizer.compat.Contracts;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ContractTest {
    private ClassNode load(String name) {
        try (var in = getClass().getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            return in == null ? null : Contracts.read(in.readAllBytes());
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
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
    @Test void everyMixinGroupMatchesTheBuildClasspath() {
        for (var group : List.of(Contracts.TEXTURE_GROUP, Contracts.SOUL_GROUP, Contracts.LOW_END_GROUP, Contracts.GPU_GROUP))
            assertEquals(List.of(), Contracts.check(this::load, group));
    }
    @Test void rerenderReuseContractHoldsAndFailsClosedOnExtraWork() {
        assertEquals(List.of(), Contracts.rerender(this::load));
        var geo = load("software.bernie.geckolib.renderer.GeoRenderer");
        var method = geo.methods.stream().filter(m -> m.name.equals("actuallyRender")).findFirst().orElseThrow();
        method.instructions.insert(new org.objectweb.asm.tree.MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC, "some/Mod", "sideEffect", "()V", false));
        var problems = Contracts.rerender(name -> name.equals("software.bernie.geckolib.renderer.GeoRenderer") ? geo : load(name));
        assertTrue(problems.contains("GeoRenderer#actuallyRender -> some/Mod.sideEffect"), problems.toString());
    }
    @Test void absentModReportsMissingClassesInsteadOfThrowing() {
        var problems = Contracts.check(name -> null, Contracts.IRIS_GROUP);
        assertTrue(problems.contains("class FullyBufferedMultiBufferSource"), problems.toString());
    }
    @Test void removedCallSiteFailsClosed() {
        var entry = Contracts.LOW_END_GROUP.calls().entrySet().iterator().next();
        var call = entry.getValue().getFirst(); var node = load(entry.getKey());
        assertTrue(Contracts.invokes(node, call));
        node.methods.removeIf(m -> m.name.equals(call.method()));
        assertFalse(Contracts.check(name -> name.equals(entry.getKey()) ? node : load(name), Contracts.LOW_END_GROUP).isEmpty());
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
