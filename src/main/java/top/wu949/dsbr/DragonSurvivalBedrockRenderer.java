package top.wu949.dsbr;

import top.wu949.dsbr.client.DSBRClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(DragonSurvivalBedrockRenderer.MOD_ID)
public final class DragonSurvivalBedrockRenderer {
    public static final String MOD_ID = "dsbr";
    public static final Logger LOGGER = LogManager.getLogger("Dragon Survival Bedrock Renderer");

    public DragonSurvivalBedrockRenderer(final IEventBus modBus, final ModContainer modContainer) {
        if (FMLLoader.getDist().isClient()) {
            DSBRClient.bootstrap(modContainer);
        }
    }
}
