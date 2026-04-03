package top.wu949.dsbr.client;

import top.wu949.dsbr.DragonSurvivalBedrockRenderer;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

public final class DSBRClient {
    private static boolean bootstrapped;

    private DSBRClient() {
    }

    public static void bootstrap(final ModContainer modContainer) {
        if (bootstrapped) {
            return;
        }

        bootstrapped = true;
        modContainer.registerConfig(ModConfig.Type.CLIENT, DSBRRenderConfig.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.register(new DragonPlayerRenderHook());
        DragonSurvivalBedrockRenderer.LOGGER.info("DSBR 客户端已初始化，常规龙渲染将优先尝试 Bedrock 后端");
    }
}
