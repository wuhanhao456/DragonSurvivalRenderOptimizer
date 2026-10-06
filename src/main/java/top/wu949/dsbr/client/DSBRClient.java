package top.wu949.dsbr.client;

import top.wu949.dsbr.DragonSurvivalBedrockRenderer;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;
import top.wu949.dsbr.optimizer.RenderOptimizer;

public final class DSBRClient {
    private static boolean bootstrapped;

    private DSBRClient() {
    }

    public static void bootstrap(final IEventBus modBus, final ModContainer modContainer) {
        if (bootstrapped) {
            return;
        }

        bootstrapped = true;
        new RenderOptimizer(modBus, modContainer);
        DragonSurvivalBedrockRenderer.LOGGER.info("DSRO 客户端已初始化：GPU、纹理优化与龙魂合批");
    }
}
