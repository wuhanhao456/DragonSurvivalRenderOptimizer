package top.wu949.dsbr.optimizer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import top.wu949.dsbr.optimizer.gpu.GpuDispatcher;
import top.wu949.dsbr.optimizer.lowend.LowEndAnimations;
import top.wu949.dsbr.optimizer.lowend.LowEndSupport;
import top.wu949.dsbr.optimizer.soul.SoulRenderContext;
import top.wu949.dsbr.optimizer.texture.AppearanceCache;
import top.wu949.dsbr.optimizer.texture.TextureCache;

import java.nio.file.Path;
import java.util.Locale;

public final class RenderOptimizer {
    public static final String ID = "dsbr";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    private long frameStart;
    private boolean inWorld;
    private OptimizerConfig.Mode previousMode;
    private Boolean previousLowEnd;

    public RenderOptimizer(IEventBus bus, ModContainer mod) {
        mod.registerConfig(ModConfig.Type.CLIENT, OptimizerConfig.SPEC, "dsbr-optimizer-client.toml");
        mod.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        bus.addListener(this::reload);
        NeoForge.EVENT_BUS.addListener(this::commands);
        NeoForge.EVENT_BUS.addListener(this::pre);
        NeoForge.EVENT_BUS.addListener(this::post);
        NeoForge.EVENT_BUS.addListener(this::logout);
        if (!OptimizerMixinPlugin.texturesCompatible) Diagnostics.INSTANCE.reason("compatibility", OptimizerMixinPlugin.status);
        if (!OptimizerMixinPlugin.gpuCompatible) Diagnostics.INSTANCE.reason("GPU compatibility", OptimizerMixinPlugin.status);
    }

    private void reload(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((ResourceManagerReloadListener) manager -> onRenderThread(RenderOptimizer::clear));
    }

    private void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        onRenderThread(RenderOptimizer::clear);
    }

    private static void onRenderThread(Runnable r) {
        if (RenderSystem.isOnRenderThread()) r.run();
        else RenderSystem.recordRenderCall(r::run);
    }

    public static void clear() {
        if (OptimizerMixinPlugin.lowEndCompatible) LowEndSupport.clear();
        if (OptimizerMixinPlugin.texturesCompatible) {
            GpuDispatcher.clear();
            TextureCache.clear();
            SoulRenderContext.clear();
        }
    }

    private void pre(RenderFrameEvent.Pre e) {
        Diagnostics.INSTANCE.detailed(OptimizerConfig.DETAILED_DIAGNOSTICS.get());
        if (OptimizerMixinPlugin.texturesCompatible) AppearanceCache.nextFrame();
        var mode = OptimizerConfig.MODE.get();
        if (previousMode != null && previousMode != mode) clear();
        previousMode = mode;
        boolean low = OptimizerConfig.LOW_END.get();
        if (previousLowEnd != null && previousLowEnd != low) clear();
        previousLowEnd = low;
        if (OptimizerMixinPlugin.lowEndCompatible) LowEndAnimations.maintenance();
        long now = System.nanoTime();
        if (frameStart != 0 && Minecraft.getInstance().level != null) Diagnostics.INSTANCE.frame((now - frameStart) / 1e6);
        frameStart = now;
    }

    private void post(RenderFrameEvent.Post e) {
        boolean active = Minecraft.getInstance().level != null;
        if (active) Diagnostics.INSTANCE.nanos("FRAME_CPU", System.nanoTime() - frameStart);
        if (inWorld && !active) clear();
        inWorld = active;
        if (OptimizerMixinPlugin.texturesCompatible) {
            Diagnostics.INSTANCE.peak("TEXTURE_RESOURCE_BYTES", TextureCache.bytes());
            GpuDispatcher.finishFrame();
            TextureCache.maintenance();
            TextureCache.unpinAll();
            GpuDispatcher.maintenance();
        }
    }

    private static void message(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }

    private static String describe() {
        return "lowEnd=" + (OptimizerMixinPlugin.lowEndCompatible && LowEndSupport.enabled())
                + "; mode=" + OptimizerConfig.MODE.get()
                + "; textures=" + (OptimizerMixinPlugin.texturesCompatible && TextureCache.enabled())
                + "; GPU=" + (OptimizerMixinPlugin.gpuCompatible && GpuDispatcher.enabled())
                + "; " + OptimizerMixinPlugin.status;
    }

    /** Explains a mode that was accepted but cannot be fully honoured in this pack or session. */
    private static String limitation(OptimizerConfig.Mode mode) {
        if (mode == OptimizerConfig.Mode.VANILLA) return null;
        if (!OptimizerMixinPlugin.texturesCompatible) return "Texture patches are not active here; the original renderer is used. " + OptimizerMixinPlugin.status;
        if (!TextureCache.enabled()) return "Texture optimization was disabled earlier this session; restart after fixing the cause (see /dsbr stats).";
        if (mode != OptimizerConfig.Mode.GPU) return null;
        if (!OptimizerMixinPlugin.gpuCompatible) return "GPU backend is not available here; only texture optimization is active. " + OptimizerMixinPlugin.status;
        if (!GpuDispatcher.enabled()) return "GPU backend was disabled earlier this session; restart after fixing the cause (see /dsbr stats).";
        return null;
    }

    private void commands(RegisterClientCommandsEvent event) {
        var root = LiteralArgumentBuilder.<CommandSourceStack>literal("dsbr").executes(c -> {
            message(c.getSource(), describe());
            return 1;
        });
        for (var mode : OptimizerConfig.Mode.values()) {
            root.then(LiteralArgumentBuilder.<CommandSourceStack>literal(mode.name().toLowerCase(Locale.ROOT)).executes(c -> {
                clear();
                OptimizerConfig.MODE.set(mode);
                OptimizerConfig.SPEC.save();
                message(c.getSource(), "DSRO render mode=" + mode);
                var limitation = limitation(mode);
                if (limitation != null) message(c.getSource(), limitation);
                return 1;
            }));
        }
        root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("stats").executes(c -> {
            var stats = Diagnostics.INSTANCE.snapshot();
            message(c.getSource(), stats.get("totals") + "; frameMs=" + stats.get("frameTimeMs") + "; fallback=" + stats.get("fallbacks")
                    + "; textureBytes=" + (OptimizerMixinPlugin.texturesCompatible ? TextureCache.bytes() : 0)
                    + "; meshBytes=" + (OptimizerMixinPlugin.gpuCompatible ? GpuDispatcher.bytes() : 0));
            return 1;
        }));
        root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("reset").executes(c -> {
            Diagnostics.INSTANCE.reset();
            message(c.getSource(), "DSRO statistics reset (fallback reasons kept)");
            return 1;
        }));
        root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("export").executes(c -> {
            Path path = Minecraft.getInstance().gameDirectory.toPath().resolve("logs/dsbr-render-stats.json");
            try {
                Diagnostics.INSTANCE.export(path);
                message(c.getSource(), "Saved " + path);
                return 1;
            } catch (java.io.IOException e) {
                message(c.getSource(), e.toString());
                return 0;
            }
        }));
        event.getDispatcher().register(root);
    }
}
