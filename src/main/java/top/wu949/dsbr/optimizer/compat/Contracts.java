package top.wu949.dsbr.optimizer.compat;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Bytecode contracts for every mixin target. Mod versions are not checked: a class that still provides the
 * members, fields and call sites an injector needs is patched, anything else keeps the original renderer.
 */
public final class Contracts {
    private static final String DS = "by.dragonsurvivalteam.dragonsurvival.";
    private static final String EDITOR = DS + "client.skin_editor_system.DragonEditorHandler";
    private static final String ARMOR = DS + "client.render.entity.dragon.DragonArmorRenderLayer";
    private static final String MODEL = DS + "client.models.DragonModel";
    private static final String UTILS = DS + "client.util.RenderingUtils";
    private static final String GLOW = DS + "client.render.entity.dragon.DragonGlowLayerRenderer";
    private static final String SKIN_DATA = DS + "common.capability.SkinData";
    private static final String SOUL = DS + "client.render.blocks.DragonSoulRenderer";
    private static final String DRAGON_RENDERER = DS + "client.render.entity.dragon.DragonRenderer";
    private static final String GEO_RENDERER = "software.bernie.geckolib.renderer.GeoRenderer";
    private static final String GEO_ENTITY_RENDERER = "software.bernie.geckolib.renderer.GeoEntityRenderer";
    private static final String GEO_MODEL = "software.bernie.geckolib.model.GeoModel";
    private static final String BUFFER_SOURCE = "net.minecraft.client.renderer.MultiBufferSource$BufferSource";
    private static final String GAME_RENDERER = "net.minecraft.client.renderer.GameRenderer";
    private static final String IRIS_SOURCE = "net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource";
    private static final String IRIS_STATE = "net.irisshaders.iris.uniforms.CapturedRenderingState";
    private static final String IRIS_IMMEDIATE = "net.irisshaders.iris.vertices.ImmediateState";
    private static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";

    private static final String PLAYER = "Lnet/minecraft/world/entity/player/Player;";
    private static final String HANDLER = "Lby/dragonsurvivalteam/dragonsurvival/common/capability/DragonStateHandler;";
    private static final String PRE = "(Lnet/neoforged/neoforge/client/event/RenderFrameEvent$Pre;)V";
    private static final String POSE = "Lcom/mojang/blaze3d/vertex/PoseStack;";
    private static final String RENDER_TYPE = "Lnet/minecraft/client/renderer/RenderType;";
    private static final String SOURCE = "Lnet/minecraft/client/renderer/MultiBufferSource;";
    private static final String CONSUMER = "Lcom/mojang/blaze3d/vertex/VertexConsumer;";
    private static final String BAKED = "Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;";
    private static final String ANIMATION_STATE = "Lsoftware/bernie/geckolib/animation/AnimationState;";
    private static final String ANIMATABLE = "Lsoftware/bernie/geckolib/animatable/GeoAnimatable;";
    private static final String TEXTURE_TARGET = "com/mojang/blaze3d/pipeline/TextureTarget";
    private static final String RENDER_TARGET = "com/mojang/blaze3d/pipeline/RenderTarget";

    /** A method; a {@code null} descriptor matches by name (reflective lookups, name-only injector targets). */
    public record Method(String name, String descriptor) {}

    /** A field; a {@code null} descriptor matches by name. */
    public record Field(String name, String descriptor) {}

    /** A call an injector targets inside {@code method}; a {@code null} method descriptor matches any overload. */
    public record Call(String method, String methodDescriptor, String owner, String name, String descriptor) {}

    /** One mixin group: everything its injectors, shadows, accessors and invokers need. */
    public record Group(Map<String, List<Method>> methods, Map<String, List<Field>> fields, Map<String, List<Call>> calls) {}

    /** Versions the releases were validated with; others are allowed and only reported as untested. */
    public static final Map<String, String> VALIDATED = Map.of(
            "dragonsurvival", "2.0.71",
            "geckolib", "4.9.3",
            "iris", "1.8.14-beta.1+mc1.21.1",
            "sodium", "0.8.13+mc1.21.1",
            "beloong", "0.10.1");

    public static final Map<String, List<Method>> TEXTURES = Map.of(
            EDITOR, List.of(
                    new Method("generateSkinTextures", "(" + PLAYER + HANDLER + ")V"),
                    new Method("purgeUnusedSkinTextures", PRE)),
            ARMOR, List.of(
                    new Method("prepareArmorTexture", "(" + PLAYER + ")V"),
                    new Method("generateArmorTexture", "(" + PLAYER + "Lnet/minecraft/resources/ResourceLocation;)V"),
                    new Method("constructTrimmedDragonArmorTexture", "(" + PLAYER + ")Ljava/util/Optional;"),
                    new Method("buildUniqueArmorUUID", "(" + PLAYER + ")Ljava/lang/String;"),
                    new Method("purgeUnusedArmorTextures", PRE)),
            MODEL, List.of(
                    new Method("dynamicTexture", "(" + PLAYER + HANDLER + "Z)Lnet/minecraft/resources/ResourceLocation;"),
                    new Method("getTextureResource", "(Lby/dragonsurvivalteam/dragonsurvival/common/entity/DragonEntity;)Lnet/minecraft/resources/ResourceLocation;")),
            UTILS, List.of(
                    new Method("copyTextureFromRenderTarget", "(Lcom/mojang/blaze3d/pipeline/RenderTarget;Lnet/minecraft/resources/ResourceLocation;)V")),
            GLOW, List.of(
                    new Method("render", "(" + POSE + "Lby/dragonsurvivalteam/dragonsurvival/common/entity/DragonEntity;" + BAKED + RENDER_TYPE + SOURCE + CONSUMER + "FII)V")),
            SKIN_DATA, List.of(
                    new Method("compileSkin", "(Lnet/minecraft/resources/ResourceKey;)V"),
                    new Method("deserializeNBT", "(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V"),
                    new Method("deserializeNBT", "(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/Holder;)V")));

    public static final Group TEXTURE_GROUP = new Group(TEXTURES,
            Map.of(
                    EDITOR, List.of(new Field("generatedSkinTextures", "Ljava/util/Set;"), new Field("usedSkinTextures", "Ljava/util/Set;")),
                    ARMOR, List.of(new Field("generatedArmorTextures", "Ljava/util/Set;"), new Field("usedArmorTextures", "Ljava/util/Set;")),
                    MODEL, List.of(new Field("overrideTexture", "Lnet/minecraft/resources/ResourceLocation;"))),
            Map.of(
                    EDITOR, List.of(
                            new Call("generateSkinTextures", null, TEXTURE_TARGET, "<init>", "(IIZZ)V"),
                            new Call("generateSkinTextures", null, RENDER_TARGET, "destroyBuffers", "()V")),
                    ARMOR, List.of(
                            new Call("generateArmorTexture", null, TEXTURE_TARGET, "<init>", "(IIZZ)V"),
                            new Call("generateArmorTexture", null, RENDER_TARGET, "destroyBuffers", "()V"))));

    private static final String SOUL_RENDER = "(Lby/dragonsurvivalteam/dragonsurvival/server/tileentity/DragonSoulBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;" + SOURCE + "II)V";

    public static final Group SOUL_GROUP = new Group(
            Map.of(SOUL, List.of(new Method("render", SOUL_RENDER))),
            Map.of(),
            Map.of(SOUL, List.of(
                    new Call("render", SOUL_RENDER, "by/dragonsurvivalteam/dragonsurvival/client/util/FakeClientPlayerUtils", "getFakeDragon",
                            "(I" + HANDLER + ")Lby/dragonsurvivalteam/dragonsurvival/common/entity/DragonEntity;"),
                    new Call("render", SOUL_RENDER, "by/dragonsurvivalteam/dragonsurvival/util/AnimationUtils", "doesAnimationExist",
                            "(Lsoftware/bernie/geckolib/model/GeoModel;" + ANIMATABLE + "Ljava/lang/String;)Z"))));

    private static final String HANDLE_ANIMATIONS = "(" + ANIMATABLE + "J" + ANIMATION_STATE + "F)V";
    private static final String LAYER_ARGS = POSE + ANIMATABLE;

    public static final Group LOW_END_GROUP = new Group(
            Map.of(
                    DRAGON_RENDERER, List.of(new Method("renderRecursively", "(" + POSE + "Lby/dragonsurvivalteam/dragonsurvival/common/entity/DragonEntity;"
                            + "Lsoftware/bernie/geckolib/cache/object/GeoBone;" + RENDER_TYPE + SOURCE + CONSUMER + "ZFIII)V")),
                    GEO_MODEL, List.of(new Method("handleAnimations", HANDLE_ANIMATIONS)),
                    GEO_ENTITY_RENDERER, List.of(new Method("getRenderLayers", "()Ljava/util/List;")),
                    SOUL, List.of(new Method("renderBlock", "(Lnet/minecraft/world/level/block/state/BlockState;" + POSE + SOURCE + "II)V"))),
            Map.of(),
            Map.of(
                    GEO_MODEL, List.of(
                            new Call("handleAnimations", HANDLE_ANIMATIONS, "software/bernie/geckolib/animation/AnimationProcessor", "preAnimationSetup", "(" + ANIMATION_STATE + "D)V"),
                            new Call("handleAnimations", HANDLE_ANIMATIONS, "software/bernie/geckolib/animation/AnimationProcessor", "tickAnimation",
                                    "(" + ANIMATABLE + "Lsoftware/bernie/geckolib/model/GeoModel;Lsoftware/bernie/geckolib/animation/AnimatableManager;D" + ANIMATION_STATE + "Z)V"),
                            new Call("handleAnimations", HANDLE_ANIMATIONS, "software/bernie/geckolib/model/GeoModel", "setCustomAnimations", "(" + ANIMATABLE + "J" + ANIMATION_STATE + ")V")),
                    GEO_RENDERER, List.of(
                            new Call("applyRenderLayers", null, "software/bernie/geckolib/renderer/GeoRenderer", "getRenderLayers", "()Ljava/util/List;"),
                            new Call("applyRenderLayersForBone", null, "software/bernie/geckolib/renderer/GeoRenderer", "getRenderLayers", "()Ljava/util/List;"),
                            new Call("preApplyRenderLayers", null, "software/bernie/geckolib/renderer/GeoRenderer", "getRenderLayers", "()Ljava/util/List;"))));

    public static final Group GPU_GROUP = new Group(
            Map.of(
                    GEO_RENDERER, List.of(
                            new Method("renderCubesOfBone", "(" + POSE + "Lsoftware/bernie/geckolib/cache/object/GeoBone;" + CONSUMER + "III)V"),
                            new Method("renderCube", "(" + POSE + "Lsoftware/bernie/geckolib/cache/object/GeoCube;" + CONSUMER + "III)V"),
                            new Method("createVerticesOfQuad", "(Lsoftware/bernie/geckolib/cache/object/GeoQuad;Lorg/joml/Matrix4f;Lorg/joml/Vector3f;" + CONSUMER + "III)V"),
                            new Method("actuallyRender", "(" + LAYER_ARGS + BAKED + RENDER_TYPE + SOURCE + CONSUMER + "ZFIII)V")),
                    BUFFER_SOURCE, List.of(
                            new Method("getBuffer", "(" + RENDER_TYPE + ")" + CONSUMER),
                            new Method("endBatch", "(" + RENDER_TYPE + "Lcom/mojang/blaze3d/vertex/BufferBuilder;)V")),
                    GAME_RENDERER, List.of(new Method("renderLevel", "(Lnet/minecraft/client/DeltaTracker;)V"))),
            Map.of(),
            Map.of());

    public static final Group IRIS_GROUP = new Group(
            Map.of(
                    IRIS_SOURCE, List.of(
                            new Method("readyUp", "()V"),
                            new Method("endBatch", "()V"),
                            new Method("endBatchWithType", "(Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;)V"),
                            new Method("getBuffer", "(" + RENDER_TYPE + ")" + CONSUMER)),
                    IRIS_STATE, List.of(new Method("getCurrentRenderedEntity", null), new Method("getCurrentRenderedBlockEntity", null), new Method("getCurrentRenderedItem", null)),
                    IRIS_API, List.of(new Method("getInstance", null), new Method("isRenderingShadowPass", null))),
            Map.of(
                    IRIS_SOURCE, List.of(new Field("typeToSegment", "Ljava/util/Map;"), new Field("renderOrder", "Ljava/util/List;")),
                    IRIS_STATE, List.of(new Field("INSTANCE", null)),
                    IRIS_IMMEDIATE, List.of(new Field("isRenderingLevel", "Z"))),
            Map.of(IRIS_SOURCE, List.of(
                    new Call("getBuffer", null, "net/irisshaders/batchedentityrendering/impl/SegmentedBufferBuilder", "getBuffer", "(" + RENDER_TYPE + ")" + CONSUMER),
                    new Call("endBatch", "()V", "net/minecraft/client/renderer/RenderType", "clearRenderState", "()V"),
                    new Call("endBatchWithType", null, "net/minecraft/client/renderer/RenderType", "clearRenderState", "()V"))));

    private Contracts() {}

    public static ClassNode read(byte[] data) {
        var node = new ClassNode();
        new ClassReader(data).accept(node, 0);
        return node;
    }

    /** Every unmet requirement of {@code group}; empty means its mixins can be applied. {@code loader} returns null for absent classes. */
    public static List<String> check(Function<String, ClassNode> loader, Group group) {
        var problems = new ArrayList<String>();
        var classes = new TreeSet<String>();
        classes.addAll(group.methods().keySet());
        classes.addAll(group.fields().keySet());
        classes.addAll(group.calls().keySet());
        for (var name : classes) {
            String simple = name.substring(name.lastIndexOf('.') + 1);
            var node = loader.apply(name);
            if (node == null) {
                problems.add("class " + simple);
                continue;
            }
            for (var m : group.methods().getOrDefault(name, List.of())) {
                if (!method(node, m)) problems.add(simple + "#" + m.name() + (m.descriptor() == null ? "" : m.descriptor()));
            }
            for (var f : group.fields().getOrDefault(name, List.of())) {
                if (!field(node, f.name(), f.descriptor())) problems.add(simple + "." + f.name());
            }
            for (var c : group.calls().getOrDefault(name, List.of())) {
                if (!invokes(node, c)) problems.add(simple + "#" + c.method() + " -> " + c.owner().substring(c.owner().lastIndexOf('/') + 1) + "." + c.name());
            }
        }
        return problems;
    }

    public static boolean methods(ClassNode node, List<Method> required) {
        return required.stream().allMatch(m -> method(node, m));
    }

    private static boolean method(ClassNode node, Method required) {
        return node.methods.stream().anyMatch(m -> m.name.equals(required.name()) && (required.descriptor() == null || m.desc.equals(required.descriptor())));
    }

    public static boolean framebufferCalls(ClassNode node, String generator) {
        return invokes(node, new Call(generator, null, TEXTURE_TARGET, "<init>", "(IIZZ)V"))
                && invokes(node, new Call(generator, null, RENDER_TARGET, "destroyBuffers", "()V"));
    }

    public static boolean invokes(ClassNode node, Call call) {
        for (MethodNode m : node.methods) {
            if (!m.name.equals(call.method())) continue;
            if (call.methodDescriptor() != null && !m.desc.equals(call.methodDescriptor())) continue;
            for (var insn : m.instructions) {
                if (insn instanceof MethodInsnNode invoke && invoke.owner.equals(call.owner())
                        && invoke.name.equals(call.name()) && invoke.desc.equals(call.descriptor())) return true;
            }
        }
        return false;
    }

    public static boolean field(ClassNode node, String name, String descriptor) {
        return node.fields.stream().anyMatch(f -> f.name.equals(name) && (descriptor == null || f.desc.equals(descriptor)));
    }
}
