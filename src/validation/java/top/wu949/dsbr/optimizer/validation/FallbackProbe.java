package top.wu949.dsbr.optimizer.validation;

import top.wu949.dsbr.optimizer.RenderOptimizer;
import top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.Files;
import java.util.Map;

/** Dedicated fallback jar has no references to DS/Gecko classes. */
@Mod(value = "dsbr_fallback_validation", dist = Dist.CLIENT)
@EventBusSubscriber(modid = "dsbr_fallback_validation", value = Dist.CLIENT)
public final class FallbackProbe {
    private static boolean done;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("beloongrender.compatProbe") || done || !(mc.screen instanceof TitleScreen)) return;
        done = true;
        try {
            RenderOptimizer.clear();
            Files.writeString(mc.gameDirectory.toPath().resolve("probe-result.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(
                    Map.of("pass", !OptimizerMixinPlugin.texturesCompatible && !OptimizerMixinPlugin.gpuCompatible,
                            "compatibility", OptimizerMixinPlugin.status, "scenario", "DS and Gecko absent; startup and cleanup remain usable")));
        } catch (Throwable e) { RenderOptimizer.LOGGER.error("Fallback probe failed", e); }
        mc.stop();
    }
}
