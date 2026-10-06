package top.wu949.dsbr.optimizer.compat;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import org.spongepowered.asm.service.MixinService;
import java.util.*;

/** Checks discovery metadata and untransformed bytecode without initializing target classes. */
public final class OptimizerMixinPlugin implements IMixinConfigPlugin {
    public static boolean texturesCompatible, gpuCompatible, irisCompatible, soulCompatible;
    public static String status = "not checked";
    private static final String DS = "by.dragonsurvivalteam.dragonsurvival.";
    public void onLoad(String pkg) {
        if (!FMLEnvironment.dist.isClient()) { status = "server: no client patches"; return; }
        var mods = LoadingModList.get();
        var versions = new TreeMap<String, String>();
        if (mods != null) mods.getMods().forEach(m -> versions.put(m.getModId(), m.getVersion().toString()));
        texturesCompatible = "2.0.71".equals(versions.get("dragonsurvival")) && "4.9.3".equals(versions.get("geckolib"));
        texturesCompatible &= !versions.containsKey("beloong_render_optimizer");
        texturesCompatible &= contract(DS + "client.skin_editor_system.DragonEditorHandler", "generateSkinTextures", "(Lnet/minecraft/world/entity/player/Player;Lby/dragonsurvivalteam/dragonsurvival/common/capability/DragonStateHandler;)V");
        texturesCompatible &= contract(DS + "client.render.entity.dragon.DragonArmorRenderLayer", "prepareArmorTexture", "(Lnet/minecraft/world/entity/player/Player;)V");
        texturesCompatible &= contract(DS + "client.util.RenderingUtils", "copyTextureFromRenderTarget", "(Lcom/mojang/blaze3d/pipeline/RenderTarget;Lnet/minecraft/resources/ResourceLocation;)V");
        texturesCompatible &= textureContracts();
        soulCompatible = texturesCompatible && contract(DS + "client.render.blocks.DragonSoulRenderer", "render", "(Lby/dragonsurvivalteam/dragonsurvival/server/tileentity/DragonSoulBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V");
        gpuCompatible = texturesCompatible && contract("software.bernie.geckolib.renderer.GeoRenderer", "renderCubesOfBone", "(Lcom/mojang/blaze3d/vertex/PoseStack;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V");
        gpuCompatible &= contract("software.bernie.geckolib.renderer.GeoRenderer", "actuallyRender", "(Lcom/mojang/blaze3d/vertex/PoseStack;Lsoftware/bernie/geckolib/animatable/GeoAnimatable;Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIII)V")
                && contract("net.minecraft.client.renderer.MultiBufferSource$BufferSource", "getBuffer", "(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;")
                && contract("net.minecraft.client.renderer.MultiBufferSource$BufferSource", "endBatch", "(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/BufferBuilder;)V")
                && contract("net.minecraft.client.renderer.GameRenderer", "renderLevel", "(Lnet/minecraft/client/DeltaTracker;)V");
        // Do not combine competing geometry backends.
        if (versions.keySet().stream().anyMatch(id -> !id.equals("dsbr") && id.contains("bedrockrenderer"))) gpuCompatible = false;
        if (versions.containsKey("iris")) {
            irisCompatible = "1.8.14-beta.1+mc1.21.1".equals(versions.get("iris"))
                    && "0.8.13+mc1.21.1".equals(versions.get("sodium"))
                    && contract("net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource", "readyUp", "()V")
                    && contract("net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource", "endBatch", "()V")
                    && contract("net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource", "endBatchWithType", "(Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;)V");
            gpuCompatible &= irisCompatible;
        }
        status = "versions=" + versions.entrySet().stream().filter(e -> Set.of("dragonsurvival", "geckolib", "iris", "sodium", "dsbr").contains(e.getKey())).toList()
                + "; texture=" + texturesCompatible + "; GPU=" + gpuCompatible + "; Iris=" + irisCompatible + "; souls=" + soulCompatible
                + (versions.containsKey("beloong_render_optimizer") ? "; duplicate standalone optimizer detected: integrated patches disabled" : "");
        org.slf4j.LoggerFactory.getLogger("RenderOptimizer").info(status);
    }
    private static boolean textureContracts() {
        try {
            for (var entry : Contracts.TEXTURES.entrySet()) {
                try (var in = MixinService.getService().getResourceAsStream(entry.getKey().replace('.', '/') + ".class")) {
                    if (in == null) return false;
                    var node = Contracts.read(in.readAllBytes());
                    if (!Contracts.methods(node, entry.getValue())) return false;
                    if (node.name.endsWith("DragonEditorHandler") && (!Contracts.framebufferCalls(node, "generateSkinTextures") || !Contracts.field(node, "generatedSkinTextures", "Ljava/util/Set;") || !Contracts.field(node, "usedSkinTextures", "Ljava/util/Set;"))) return false;
                    if (node.name.endsWith("DragonArmorRenderLayer") && (!Contracts.framebufferCalls(node, "generateArmorTexture") || !Contracts.field(node, "generatedArmorTextures", "Ljava/util/Set;") || !Contracts.field(node, "usedArmorTextures", "Ljava/util/Set;"))) return false;
                }
            }
            return true;
        } catch (Exception | LinkageError e) { return false; }
    }
    private static boolean contract(String name, String method, String descriptor) {
        try (var stream = MixinService.getService().getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (stream == null) return false;
            var node = new ClassNode(); new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
            return node.methods.stream().anyMatch(m -> m.name.equals(method) && m.desc.equals(descriptor));
        } catch (Exception | LinkageError e) { return false; }
    }
    public boolean shouldApplyMixin(String target, String mixin) {
        if (mixin.endsWith("SoulRendererMixin")) return texturesCompatible && soulCompatible;
        if (mixin.endsWith("IrisBufferSourceMixin")) return gpuCompatible && irisCompatible;
        if (mixin.endsWith("GeoRendererMixin") || mixin.endsWith("BufferSourceMixin") || mixin.endsWith("DragonGlowMixin") || mixin.endsWith("GameRendererScopeMixin")) return gpuCompatible;
        return texturesCompatible;
    }
    public String getRefMapperConfig() { return null; }
    public List<String> getMixins() { return null; }
    public void acceptTargets(Set<String> mine, Set<String> others) {}
    public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}
