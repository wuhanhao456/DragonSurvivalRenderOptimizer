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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BedrockDragonRenderer {
    private static final List<String> FIRST_PERSON_HIDDEN_BONES = List.of("Neck", "Head");

    private final DSRuntimeBridge bridge;
    private final DragonAnimationEngine animationEngine = new DragonAnimationEngine();
    private final Map<ResourceLocation, DragonBedrockModel> modelCache = new HashMap<>();

    public BedrockDragonRenderer(final DSRuntimeBridge bridge) {
        this.bridge = bridge;
    }

    public boolean canRender(final DSRuntimeBridge.PreparedDragonRender prepared) {
        if (prepared.textureLocation() == null || prepared.modelLocation() == null || prepared.animationLocation() == null) {
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
}
