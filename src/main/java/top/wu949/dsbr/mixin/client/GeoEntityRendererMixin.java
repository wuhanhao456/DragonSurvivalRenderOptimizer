package top.wu949.dsbr.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.wu949.dsbr.DragonSurvivalBedrockRenderer;
import top.wu949.dsbr.client.DSBRRenderConfig;
import top.wu949.dsbr.client.bridge.DSRuntimeBridge;
import top.wu949.dsbr.client.render.BedrockDragonRenderer;

@Pseudo
@Mixin(targets = "software.bernie.geckolib.renderer.GeoEntityRenderer")
public abstract class GeoEntityRendererMixin {
    @Unique
    private static final String DS_DRAGON_RENDERER_CLASS = "by.dragonsurvivalteam.dragonsurvival.client.render.entity.dragon.DragonRenderer";
    @Unique
    private static final DSRuntimeBridge DSBR_BRIDGE = new DSRuntimeBridge();
    @Unique
    private static final BedrockDragonRenderer DSBR_RENDERER = new BedrockDragonRenderer(DSBR_BRIDGE);

    @Inject(
            method = "render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void dsbr(
            final Entity entity,
            final float entityYaw,
            final float partialTick,
            final PoseStack poseStack,
            final MultiBufferSource bufferSource,
            final int packedLight,
            final CallbackInfo callbackInfo
    ) {
        if (!DSBRRenderConfig.useBedrockRendererForNonLocalDragonRenders()) {
            return;
        }

        if (!DS_DRAGON_RENDERER_CLASS.equals(this.getClass().getName())) {
            return;
        }

        if (DSBRRenderConfig.takeoverScope() != DSBRRenderConfig.TakeoverScope.GLOBAL) {
            return;
        }

        DSRuntimeBridge.PreparedDragonRender prepared = DSBR_BRIDGE.prepareExternalDragonRender(entity, partialTick);
        if (prepared == null || !DSBR_RENDERER.canRender(prepared)) {
            return;
        }

        try {
            DSBR_RENDERER.render(prepared, poseStack, bufferSource, packedLight);
            callbackInfo.cancel();
        } catch (Throwable throwable) {
            DragonSurvivalBedrockRenderer.LOGGER.error(
                    "DSBR failed to replace DragonEntity rendering, continuing with Dragon Survival's original renderer for this frame.",
                    throwable
            );
        }
    }
}