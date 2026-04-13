package top.wu949.dsbr.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import top.wu949.dsbr.DragonSurvivalBedrockRenderer;
import top.wu949.dsbr.client.bridge.DSRuntimeBridge;
import top.wu949.dsbr.client.render.BedrockDragonRenderer;

public final class DragonPlayerRenderHook {
    private final DSRuntimeBridge bridge = new DSRuntimeBridge();
    private final BedrockDragonRenderer renderer = new BedrockDragonRenderer(bridge);

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderPlayer(final RenderPlayerEvent.Pre event) {
        if (event.isCanceled()) {
            return;
        }

        if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }

        if (!shouldRenderWithBedrock(player)) {
            return;
        }

        DSRuntimeBridge.PreparedDragonRender prepared = bridge.prepare(player, event.getPartialTick());
        if (prepared == null || !renderer.canRender(prepared)) {
            return;
        }

        try {
            renderer.render(prepared, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
            event.setCanceled(true);
        } catch (Throwable throwable) {
            DragonSurvivalBedrockRenderer.LOGGER.error(
                    "DSBR failed to render the dragon model, falling back to Dragon Survival's original renderer for this frame.",
                    throwable
            );
        }
    }

    private boolean shouldRenderWithBedrock(final AbstractClientPlayer player) {
        if (DSBRRenderConfig.useBedrockRendererForNormalDragonRender()) {
            return true;
        }

        if (!DSBRRenderConfig.useYsmRendererForNormalDragonRender()) {
            return false;
        }

        return Minecraft.getInstance().player != player;
    }
}