package top.wu949.dsbr.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.wu949.dsbr.DragonSurvivalBedrockRenderer;
import top.wu949.dsbr.client.DSBRRenderConfig;
import top.wu949.dsbr.client.bridge.DSRuntimeBridge;
import top.wu949.dsbr.client.render.BedrockDragonRenderer;

@Pseudo
@Mixin(remap = false, targets = "by.dragonsurvivalteam.dragonsurvival.client.render.blocks.DragonSoulRenderer")
public abstract class DragonSoulRendererMixin {
    @Unique
    private static final DSRuntimeBridge DSBR_BRIDGE = new DSRuntimeBridge();
    @Unique
    private static final BedrockDragonRenderer DSBR_RENDERER = new BedrockDragonRenderer(DSBR_BRIDGE);

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
            ),
            require = 0
    )
    private void dsbr(
            final EntityRenderer renderer,
            final Entity entity,
            final float entityYaw,
            final float partialTick,
            final PoseStack poseStack,
            final MultiBufferSource bufferSource,
            final int packedLight
    ) {
        if (!DSBRRenderConfig.useBedrockRendererForNonLocalDragonRenders()
                || DSBRRenderConfig.takeoverScope() == DSBRRenderConfig.TakeoverScope.PLAYER_ONLY) {
            renderer.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        DSRuntimeBridge.PreparedDragonRender prepared = DSBR_BRIDGE.prepareExternalDragonRender(entity, partialTick);
        if (prepared == null || !DSBR_RENDERER.canRender(prepared)) {
            renderer.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        try {
            DSBR_RENDERER.render(prepared, poseStack, bufferSource, packedLight);
        } catch (Throwable throwable) {
            DragonSurvivalBedrockRenderer.LOGGER.error(
                    "DSBR failed to replace DragonSoulRenderer rendering, falling back to Dragon Survival's original renderer for this frame.",
                    throwable
            );
            renderer.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        }
    }
}