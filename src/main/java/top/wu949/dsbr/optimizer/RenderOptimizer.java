package top.wu949.dsbr.optimizer;

import top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import top.wu949.dsbr.optimizer.diagnostics.ClientBenchmark;
import top.wu949.dsbr.optimizer.gpu.GpuDispatcher;
import top.wu949.dsbr.optimizer.texture.TextureCache;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import static net.minecraft.commands.Commands.argument;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

public final class RenderOptimizer {
    public static final String ID = "dsbr";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    private long frameStart;
    private boolean inWorld;
    private OptimizerConfig.Mode previousMode;
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
    private void reload(RegisterClientReloadListenersEvent e) { e.registerReloadListener((ResourceManagerReloadListener) manager -> onRenderThread(() -> { ClientBenchmark.cancel("resources reloaded"); clear(); })); }
    private void logout(ClientPlayerNetworkEvent.LoggingOut e) { onRenderThread(() -> { ClientBenchmark.cancel("disconnected"); clear(); }); }
    private static void onRenderThread(Runnable r) { if (RenderSystem.isOnRenderThread()) r.run(); else RenderSystem.recordRenderCall(r::run); }
    public static void clear() { if (OptimizerMixinPlugin.texturesCompatible) { GpuDispatcher.clear(); TextureCache.clear(); } }
    private void pre(RenderFrameEvent.Pre e) {
        Diagnostics.INSTANCE.detailed(OptimizerConfig.DETAILED_DIAGNOSTICS.get());
        if (OptimizerMixinPlugin.texturesCompatible) top.wu949.dsbr.optimizer.texture.AppearanceCache.nextFrame();
        if (previousMode != null && previousMode != OptimizerConfig.MODE.get()) clear(); previousMode = OptimizerConfig.MODE.get();
        long now = System.nanoTime();
        if (frameStart != 0 && Minecraft.getInstance().level != null) Diagnostics.INSTANCE.frame((now - frameStart) / 1e6);
        frameStart = now;
    }
    private void post(RenderFrameEvent.Post e) {
        boolean active = Minecraft.getInstance().level != null;
        if (active) Diagnostics.INSTANCE.nanos("FRAME_CPU", System.nanoTime() - frameStart);
        if (inWorld && !active) clear(); inWorld = active;
        if (OptimizerMixinPlugin.texturesCompatible) { Diagnostics.INSTANCE.peak("TEXTURE_RESOURCE_BYTES", TextureCache.bytes()); GpuDispatcher.finishFrame(); TextureCache.maintenance(); TextureCache.unpinAll(); GpuDispatcher.maintenance(); }
        ClientBenchmark.frame();
    }
    private static void message(CommandSourceStack source, String text) { source.sendSuccess(() -> Component.literal(text), false); }
    private void commands(RegisterClientCommandsEvent event) {
        var root = LiteralArgumentBuilder.<CommandSourceStack>literal("dsbr").executes(c -> {
            message(c.getSource(), "mode=" + OptimizerConfig.MODE.get() + "; textures=" + (OptimizerMixinPlugin.texturesCompatible && TextureCache.enabled()) + "; GPU=" + (OptimizerMixinPlugin.gpuCompatible && GpuDispatcher.enabled()) + "; legacy=" + top.wu949.dsbr.client.DSBRRenderConfig.legacyActive() + "; " + OptimizerMixinPlugin.status); return 1;
        });
        for (var mode : OptimizerConfig.Mode.values()) root.then(LiteralArgumentBuilder.<CommandSourceStack>literal(mode.name().toLowerCase(java.util.Locale.ROOT)).executes(c -> {
            ClientBenchmark.cancel("manual mode switch"); clear(); top.wu949.dsbr.client.DSBRRenderConfig.LEGACY_BACKEND_ENABLED.set(false); top.wu949.dsbr.client.DSBRRenderConfig.SPEC.save(); OptimizerConfig.MODE.set(mode); OptimizerConfig.SPEC.save(); message(c.getSource(), "DSBR render mode=" + mode); return 1;
        }));
        root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("legacy").executes(c -> {
            if (!OptimizerMixinPlugin.legacyCompatible) { message(c.getSource(), "Legacy Bedrock/YSM requires DS 2.0.67 and its old bridge; " + OptimizerMixinPlugin.status); return 0; }
            ClientBenchmark.cancel("legacy backend selected"); clear(); OptimizerConfig.MODE.set(OptimizerConfig.Mode.TEXTURES);
            top.wu949.dsbr.client.DSBRRenderConfig.LEGACY_BACKEND_ENABLED.set(true); top.wu949.dsbr.client.DSBRRenderConfig.SPEC.save(); OptimizerConfig.SPEC.save();
            message(c.getSource(), "DSBR legacy backend enabled; old normal_render_mode setting applies"); return 1;
        }));
        root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("benchmark").executes(c -> { message(c.getSource(), ClientBenchmark.status()); return 1; })
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("stop").executes(c -> { ClientBenchmark.cancel("manual stop"); return 1; }))
                .then(argument("players", IntegerArgumentType.integer(1, 12)).then(argument("repetition", IntegerArgumentType.integer(1, 3)).executes(c -> {
                    message(c.getSource(), ClientBenchmark.start(IntegerArgumentType.getInteger(c, "players"), IntegerArgumentType.getInteger(c, "repetition"))); return 1;
                }))));
        root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("stats").executes(c -> { var stats = Diagnostics.INSTANCE.snapshot(); message(c.getSource(), stats.get("totals") + "; frameMs=" + stats.get("frameTimeMs") + "; fallback=" + stats.get("fallbacks") + "; textureBytes=" + (OptimizerMixinPlugin.texturesCompatible ? TextureCache.bytes() : 0) + "; meshBytes=" + (OptimizerMixinPlugin.gpuCompatible ? GpuDispatcher.bytes() : 0)); return 1; }));
        root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("reset").executes(c -> { Diagnostics.INSTANCE.reset(); return 1; }));
        root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("export").executes(c -> {
            Path path = Minecraft.getInstance().gameDirectory.toPath().resolve("logs/dsbr-render-stats.json");
            try { Diagnostics.INSTANCE.export(path); message(c.getSource(), "Saved " + path); return 1; }
            catch (java.io.IOException e) { message(c.getSource(), e.toString()); return 0; }
        }));
        var registered = event.getDispatcher().register(root);
        event.getDispatcher().register(LiteralArgumentBuilder.<CommandSourceStack>literal("beloongrender").executes(registered.getCommand()).redirect(registered));
    }
}
