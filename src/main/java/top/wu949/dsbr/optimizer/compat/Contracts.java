package top.wu949.dsbr.optimizer.compat;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;

public final class Contracts {
    private static final String DS = "by.dragonsurvivalteam.dragonsurvival.";
    private static final String PLAYER = "Lnet/minecraft/world/entity/player/Player;";
    private static final String HANDLER = "Lby/dragonsurvivalteam/dragonsurvival/common/capability/DragonStateHandler;";
    private static final String PRE = "(Lnet/neoforged/neoforge/client/event/RenderFrameEvent$Pre;)V";
    public record Method(String name, String descriptor) {}
    public static final Map<String, List<Method>> TEXTURES = Map.of(
        DS + "client.skin_editor_system.DragonEditorHandler", List.of(new Method("generateSkinTextures", "(" + PLAYER + HANDLER + ")V"), new Method("purgeUnusedSkinTextures", PRE)),
        DS + "client.render.entity.dragon.DragonArmorRenderLayer", List.of(new Method("prepareArmorTexture", "(" + PLAYER + ")V"), new Method("generateArmorTexture", "(" + PLAYER + "Lnet/minecraft/resources/ResourceLocation;)V"),
                new Method("constructTrimmedDragonArmorTexture", "(" + PLAYER + ")Ljava/util/Optional;"), new Method("buildUniqueArmorUUID", "(" + PLAYER + ")Ljava/lang/String;"), new Method("purgeUnusedArmorTextures", PRE)),
        DS + "client.models.DragonModel", List.of(new Method("dynamicTexture", "(" + PLAYER + HANDLER + "Z)Lnet/minecraft/resources/ResourceLocation;"), new Method("getTextureResource", "(Lby/dragonsurvivalteam/dragonsurvival/common/entity/DragonEntity;)Lnet/minecraft/resources/ResourceLocation;")),
        DS + "client.util.RenderingUtils", List.of(new Method("copyTextureFromRenderTarget", "(Lcom/mojang/blaze3d/pipeline/RenderTarget;Lnet/minecraft/resources/ResourceLocation;)V")),
        DS + "client.render.entity.dragon.DragonGlowLayerRenderer", List.of(new Method("render", "(Lcom/mojang/blaze3d/vertex/PoseStack;Lby/dragonsurvivalteam/dragonsurvival/common/entity/DragonEntity;Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;FII)V")),
        DS + "common.capability.SkinData", List.of(new Method("compileSkin", "(Lnet/minecraft/resources/ResourceKey;)V"), new Method("deserializeNBT", "(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V"),
                new Method("deserializeNBT", "(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/Holder;)V"))
    );
    public static ClassNode read(byte[] data) { var node = new ClassNode(); new ClassReader(data).accept(node, 0); return node; }
    public static boolean methods(ClassNode node, List<Method> required) {
        return required.stream().allMatch(c -> node.methods.stream().anyMatch(m -> m.name.equals(c.name) && m.desc.equals(c.descriptor)));
    }
    public static boolean framebufferCalls(ClassNode node, String generator) {
        return node.methods.stream().filter(m -> m.name.equals(generator)).anyMatch(m -> {
            boolean create = false, release = false;
            for (var insn : m.instructions) if (insn instanceof MethodInsnNode call) {
                create |= call.owner.equals("com/mojang/blaze3d/pipeline/TextureTarget") && call.name.equals("<init>") && call.desc.equals("(IIZZ)V");
                release |= call.owner.equals("com/mojang/blaze3d/pipeline/RenderTarget") && call.name.equals("destroyBuffers") && call.desc.equals("()V");
            }
            return create && release;
        });
    }
    public static boolean field(ClassNode node, String name, String descriptor) { return node.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(descriptor)); }
}
