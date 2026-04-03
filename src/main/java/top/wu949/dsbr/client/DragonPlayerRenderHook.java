package top.wu949.dsbr.client;

import top.wu949.dsbr.DragonSurvivalBedrockRenderer;
import top.wu949.dsbr.client.bridge.DSRuntimeBridge;
import top.wu949.dsbr.client.render.BedrockDragonRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

public final class DragonPlayerRenderHook {
    private final DSRuntimeBridge bridge = new DSRuntimeBridge();
    private final BedrockDragonRenderer renderer = new BedrockDragonRenderer(bridge);

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderPlayer(final RenderPlayerEvent.Pre event) {
        if (!DSBRRenderConfig.useBedrockRendererForNormalDragonRender() || event.isCanceled()) {
            return;
        }

        if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
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
            DragonSurvivalBedrockRenderer.LOGGER.error("DSBR 渲染龙模型失败，已自动回退到 Dragon Survival 原版渲染", throwable);
        }
    }
}
