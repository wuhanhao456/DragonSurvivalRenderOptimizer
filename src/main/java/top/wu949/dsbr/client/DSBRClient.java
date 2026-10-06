package top.wu949.dsbr.client;

import top.wu949.dsbr.DragonSurvivalBedrockRenderer;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import top.wu949.dsbr.optimizer.RenderOptimizer;
import top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin;

public final class DSBRClient {
    private static boolean bootstrapped;

    private DSBRClient() {
    }

    public static void bootstrap(final IEventBus modBus, final ModContainer modContainer) {
        if (bootstrapped) {
            return;
        }

        bootstrapped = true;
        modContainer.registerConfig(ModConfig.Type.CLIENT, DSBRRenderConfig.SPEC);
        new RenderOptimizer(modBus, modContainer);
        if (OptimizerMixinPlugin.legacyCompatible) NeoForge.EVENT_BUS.register(new DragonPlayerRenderHook());
        DragonSurvivalBedrockRenderer.LOGGER.info("DSBR 客户端已初始化：默认 GPU 与纹理优化；旧 Bedrock/YSM 仅作默认关闭的兼容路径");
    }
}
