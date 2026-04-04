package top.wu949.dsbr.client.render;

import top.wu949.dsbr.client.DSBRRenderConfig;
import top.wu949.dsbr.client.animation.DragonAnimationEngine;
import top.wu949.dsbr.client.bridge.DSRuntimeBridge;
import top.wu949.dsbr.client.render.model.DragonBedrockModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BedrockDragonRenderer {
    private static final List<String> FIRST_PERSON_HIDDEN_BONES = List.of("Neck", "Head");

    private final DSRuntimeBridge bridge;
    private final DragonAnimationEngine animationEngine = new DragonAnimationEngine();
    private final Map<ResourceLocation, DragonBedrockModel> modelCache = new HashMap<>();
    private ItemDisplayContext backpackDisplayContext;

    public BedrockDragonRenderer(final DSRuntimeBridge bridge) {
        this.bridge = bridge;
    }

    public boolean canRender(final DSRuntimeBridge.PreparedDragonRender prepared) {
        if (prepared.textureLocation() == null || prepared.modelLocation() == null || prepared.animationLocation() == null) {
            return false;
        }

        if (shouldFallbackToOriginalArmorRenderer(prepared)) {
            return false;
        }

        return !prepared.usesSpecialAnimation() || DSBRRenderConfig.useBedrockRendererForSpecialAnimations();
    }

    public void render(final DSRuntimeBridge.PreparedDragonRender prepared, final PoseStack poseStack, final MultiBufferSource bufferSource, final int packedLight) throws Exception {
        if (prepared.player().isSpectator()) {
            return;
        }

        if (prepared.player().isInvisibleTo(Minecraft.getInstance().player)) {
            return;
        }

        DragonBedrockModel model = modelCache.get(prepared.modelLocation());
        if (model == null) {
            model = new DragonBedrockModel(Minecraft.getInstance().getResourceManager().getResource(prepared.modelLocation()).orElseThrow().open());
            modelCache.put(prepared.modelLocation(), model);
        }

        animationEngine.animate(model, prepared);
        hideFirstPersonBones(model, prepared);

        poseStack.pushPose();
        setupPose(prepared, poseStack);

        RenderType renderType = prepared.player().isInvisible() && !prepared.player().isInvisibleTo(Minecraft.getInstance().player)
                ? RenderType.entityTranslucentCull(prepared.textureLocation())
                : RenderType.entityCutoutNoCull(prepared.textureLocation());
        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        float alpha = prepared.player().isInvisible() && !prepared.player().isInvisibleTo(Minecraft.getInstance().player) ? 0.15F : 1.0F;
        int packedOverlay = LivingEntityRenderer.getOverlayCoords(prepared.player(), 0.0F);

        model.renderWithColor(poseStack, consumer, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, alpha);
        renderGlowOverlay(model, prepared, poseStack, bufferSource, packedLight, alpha);
        renderArmorOverlay(model, prepared, poseStack, bufferSource, packedLight, packedOverlay, alpha);
        renderHeldItems(model, prepared, poseStack, bufferSource, packedLight, packedOverlay);
        renderBackpack(model, prepared, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private void hideFirstPersonBones(final DragonBedrockModel model, final DSRuntimeBridge.PreparedDragonRender prepared) {
        if (DSBRRenderConfig.renderHeadInFirstPerson() || isDetachedFirstPersonCamera(prepared)) {
            return;
        }

        if (!isAttachedLocalFirstPersonWorldRender(prepared)) {
            return;
        }

        model.hideBones(FIRST_PERSON_HIDDEN_BONES);
    }

    private void setupPose(final DSRuntimeBridge.PreparedDragonRender prepared, final PoseStack poseStack) {
        Vec3 offset = getModelOffset(prepared);
        poseStack.translate(-offset.x, -offset.y, -offset.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - (float) prepared.bodyYaw()));

        if (prepared.gliding()) {
            poseStack.mulPose(Axis.XN.rotationDegrees(prepared.dragonPrevXRot()));
            poseStack.mulPose(Axis.ZP.rotation(prepared.dragonPrevZRot()));
        }

        poseStack.scale(-prepared.visualScale(), -prepared.visualScale(), prepared.visualScale());
        poseStack.translate(0.0F, -1.501F, 0.0F);

        if (isAttachedLocalFirstPersonWorldRender(prepared)) {
            poseStack.translate(
                    DSBRRenderConfig.firstPersonModelOffsetX(),
                    DSBRRenderConfig.firstPersonModelOffsetY(),
                    DSBRRenderConfig.firstPersonModelOffsetZ()
            );
        }
    }

    private Vec3 getModelOffset(final DSRuntimeBridge.PreparedDragonRender prepared) {
        float angle = -(float) prepared.bodyYaw() * Mth.DEG_TO_RAD;
        float x = Mth.sin(angle);
        float z = Mth.cos(angle);
        float scale = (float) (prepared.visualScale() * prepared.bodyScaleMultiplier());
        return new Vec3(x * scale, 0, z * scale);
    }

    private boolean isAttachedLocalFirstPersonWorldRender(final DSRuntimeBridge.PreparedDragonRender prepared) {
        Minecraft minecraft = Minecraft.getInstance();
        return isLocalFirstPersonWorldRender(prepared)
                && minecraft.getCameraEntity() == prepared.player();
    }

    private boolean isDetachedFirstPersonCamera(final DSRuntimeBridge.PreparedDragonRender prepared) {
        Minecraft minecraft = Minecraft.getInstance();
        return isLocalFirstPersonWorldRender(prepared)
                && minecraft.getCameraEntity() != null
                && minecraft.getCameraEntity() != prepared.player();
    }

    private boolean isLocalFirstPersonWorldRender(final DSRuntimeBridge.PreparedDragonRender prepared) {
        Minecraft minecraft = Minecraft.getInstance();
        return prepared.player() == minecraft.player
                && minecraft.options.getCameraType().isFirstPerson()
                && !InventoryEntityRenderContext.isRenderingInventoryEntity();
    }

    private boolean hasArmorAppearanceChanges(final DSRuntimeBridge.PreparedDragonRender prepared) {
        return bridge.hasVisibleArmor(prepared.player());
    }

    private boolean shouldFallbackToOriginalArmorRenderer(final DSRuntimeBridge.PreparedDragonRender prepared) {
        return hasArmorAppearanceChanges(prepared)
                && (!DSBRRenderConfig.useBedrockRendererForArmor() || !bridge.canRenderArmorWithBedrock());
    }

    private void renderArmorOverlay(final DragonBedrockModel model, final DSRuntimeBridge.PreparedDragonRender prepared, final PoseStack poseStack, final MultiBufferSource bufferSource, final int packedLight, final int packedOverlay, final float alpha) {
        if (!hasArmorAppearanceChanges(prepared) || !DSBRRenderConfig.useBedrockRendererForArmor()) {
            return;
        }

        ResourceLocation armorTexture = bridge.resolveArmorTexture(prepared);
        if (armorTexture == null) {
            return;
        }

        RenderType armorRenderType = alpha < 1.0F
                ? RenderType.entityTranslucentCull(armorTexture)
                : RenderType.entityCutoutNoCullZOffset(armorTexture);
        VertexConsumer armorConsumer = bufferSource.getBuffer(armorRenderType);
        model.renderWithColor(poseStack, armorConsumer, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, alpha);
    }

    private void renderGlowOverlay(final DragonBedrockModel model, final DSRuntimeBridge.PreparedDragonRender prepared, final PoseStack poseStack, final MultiBufferSource bufferSource, final int packedLight, final float alpha) {
        ResourceLocation glowTexture = bridge.resolveGlowTexture(prepared);
        if (glowTexture == null) {
            return;
        }

        RenderType glowRenderType = RenderType.EYES.apply(glowTexture, RenderType.LIGHTNING_TRANSPARENCY);
        VertexConsumer glowConsumer = bufferSource.getBuffer(glowRenderType);
        model.renderWithColor(poseStack, glowConsumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha);
    }

    private void renderHeldItems(final DragonBedrockModel model, final DSRuntimeBridge.PreparedDragonRender prepared, final PoseStack poseStack, final MultiBufferSource bufferSource, final int packedLight, final int packedOverlay) {
        if (!bridge.shouldRenderHeldItems() || bridge.isBetterCombatAttacking(prepared.player()) || isAttachedLocalFirstPersonWorldRender(prepared)) {
            return;
        }

        boolean renderItemsInMouth = switch (DSBRRenderConfig.heldItemRenderMode()) {
            case ALWAYS_MOUTH -> true;
            case ALWAYS_HAND -> false;
            case ORIGINAL -> prepared.renderItemsInMouth();
        };

        String rightBone = renderItemsInMouth ? "RightItem_jaw" : "RightItem";
        String leftBone = renderItemsInMouth ? "LeftItem_jaw" : "LeftItem";

        if (!renderItemOnBone(model, poseStack, bufferSource, packedLight, packedOverlay, prepared.player().getMainHandItem(), prepared.player(), rightBone)
                && renderItemsInMouth) {
            renderItemOnBone(model, poseStack, bufferSource, packedLight, packedOverlay, prepared.player().getMainHandItem(), prepared.player(), "RightItem");
        }

        if (!renderItemOnBone(model, poseStack, bufferSource, packedLight, packedOverlay, prepared.player().getOffhandItem(), prepared.player(), leftBone)
                && renderItemsInMouth) {
            renderItemOnBone(model, poseStack, bufferSource, packedLight, packedOverlay, prepared.player().getOffhandItem(), prepared.player(), "LeftItem");
        }
    }

    private boolean renderItemOnBone(final DragonBedrockModel model, final PoseStack poseStack, final MultiBufferSource bufferSource, final int packedLight, final int packedOverlay, final ItemStack stack, final net.minecraft.world.entity.player.Player player, final String boneName) {
        if (stack.isEmpty()) {
            return false;
        }

        poseStack.pushPose();
        if (!model.applyBoneTransform(boneName, poseStack)) {
            poseStack.popPose();
            return false;
        }

        applyItemBoneAdjustments(poseStack, boneName);
        Minecraft.getInstance().getItemRenderer().renderStatic(player, stack, resolveItemDisplayContext(boneName), false, poseStack, bufferSource, player.level(), packedLight, packedOverlay, player.getId());
        poseStack.popPose();
        return true;
    }

    private void applyItemBoneAdjustments(final PoseStack poseStack, final String boneName) {
        if (boneName.equals("RightItem")) {
            Quaternionf rotation = new Quaternionf();
            rotation.rotateY((float) Math.toRadians(90));
            rotation.rotateX((float) Math.toRadians(60));
            poseStack.rotateAround(rotation, 0, 0, 0);
            poseStack.scale(0.75F, 0.75F, 0.75F);
        } else if (boneName.equals("LeftItem")) {
            Quaternionf rotation = new Quaternionf();
            rotation.rotateZ((float) Math.toRadians(90));
            rotation.rotateY((float) Math.toRadians(90));
            rotation.rotateX((float) Math.toRadians(-120));
            poseStack.rotateAround(rotation, 0, 0, 0);
            poseStack.scale(0.75F, 0.75F, 0.75F);
        }
    }

    private ItemDisplayContext resolveItemDisplayContext(final String boneName) {
        return switch (boneName) {
            case "RightItem" -> ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            case "LeftItem" -> ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            default -> ItemDisplayContext.GROUND;
        };
    }

    private void renderBackpack(final DragonBedrockModel model, final DSRuntimeBridge.PreparedDragonRender prepared, final PoseStack poseStack, final MultiBufferSource bufferSource, final int packedLight, final int packedOverlay) {
        DSRuntimeBridge.PreparedBackpackRender backpackRender = bridge.resolveBackpackRender(prepared);
        ItemDisplayContext displayContext = resolveBackpackDisplayContext();
        if (backpackRender == null || displayContext == null) {
            return;
        }

        poseStack.pushPose();
        if (!model.applyBoneTransform("BackpackBone", poseStack)) {
            poseStack.popPose();
            return;
        }

        Quaternionf rotation = new Quaternionf().rotationZYX(
                (float) Math.toRadians(backpackRender.rotOffset().z),
                (float) Math.toRadians(backpackRender.rotOffset().y),
                (float) Math.toRadians(backpackRender.rotOffset().x)
        );
        poseStack.rotateAround(rotation, 0, 0, 0);
        poseStack.translate(backpackRender.posOffset().x, backpackRender.posOffset().y, backpackRender.posOffset().z);
        poseStack.scale((float) backpackRender.scale().x, (float) backpackRender.scale().y, (float) backpackRender.scale().z);
        Minecraft.getInstance().getItemRenderer().renderStatic(backpackRender.stack(), displayContext, packedLight, packedOverlay, poseStack, bufferSource, prepared.player().level(), 0);
        poseStack.popPose();
    }

    private ItemDisplayContext resolveBackpackDisplayContext() {
        if (backpackDisplayContext != null) {
            return backpackDisplayContext;
        }

        try {
            backpackDisplayContext = ItemDisplayContext.valueOf("SOPHISTICATEDBACKPACKS_WORN");
            return backpackDisplayContext;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
