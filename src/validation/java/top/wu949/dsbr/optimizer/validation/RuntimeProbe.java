package top.wu949.dsbr.optimizer.validation;

import by.dragonsurvivalteam.dragonsurvival.common.capability.*;
import by.dragonsurvivalteam.dragonsurvival.network.syncing.SyncComplete;
import by.dragonsurvivalteam.dragonsurvival.registry.dragon.DragonSpecies;
import by.dragonsurvivalteam.dragonsurvival.registry.dragon.body.DragonBody;
import by.dragonsurvivalteam.dragonsurvival.client.skin_editor_system.SkinLayer;
import top.wu949.dsbr.optimizer.*;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import top.wu949.dsbr.optimizer.diagnostics.ClientBenchmark;
import top.wu949.dsbr.optimizer.texture.TextureCache;
import top.wu949.dsbr.optimizer.gpu.GpuDispatcher;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.CameraType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Test-only driver; never packaged in the optimizer. Creates its own fresh flat save. */
@Mod(value = "dsbr_validation", dist = Dist.CLIENT)
@EventBusSubscriber(modid = "dsbr_validation", value = Dist.CLIENT)
public final class RuntimeProbe {
    private static boolean creating, done;
    private static int ticks, step;
    private static CompletableFuture<Void> setup;
    private static float oldHue;
    private static boolean oldModified, oldGlow;
    private static long textureSteadyBytes, meshSteadyBytes;
    private static List<String> shaderPacks;
    private static int shaderIndex;
    private static final long started = System.nanoTime();
    private static final String WORLD = "render-validation";
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("beloongrender.probe") || done) return;
        var mc = Minecraft.getInstance();
        try {
            if (!mc.gameDirectory.getName().startsWith("dsbr-validation-")) throw new IllegalStateException("Probe requires its own dsbr-validation-* directory");
            if (System.nanoTime() - started > 600_000_000_000L) throw new IllegalStateException("10 minute probe deadline");
            if (!creating) {
                if (!(mc.screen instanceof TitleScreen)) return;
                if (top.wu949.dsbr.client.DSBRRenderConfig.legacyActive()) throw new IllegalStateException("Old saved settings unexpectedly activated legacy takeover on DS 2.0.71");
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false; mc.options.framerateLimit().set(120); mc.options.renderDistance().set(6);
                if (Files.exists(mc.gameDirectory.toPath().resolve("saves/" + WORLD))) throw new IllegalStateException("Refusing to reuse an existing save");
                creating = true;
                mc.createWorldOpenFlows().createFreshLevel(WORLD, new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(2067, false, false), registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
                return;
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            if (mc.screen != null) mc.setScreen(null);
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            if (setup == null) {
                setup = CompletableFuture.runAsync(() -> {
                    var server = mc.getSingleplayerServer(); var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    var h = DragonStateProvider.getData(player);
                    var species = player.registryAccess().registryOrThrow(DragonSpecies.REGISTRY).getHolderOrThrow(ResourceKey.create(DragonSpecies.REGISTRY, ResourceLocation.parse("dragonsurvival:cave_dragon")));
                    var body = player.registryAccess().registryOrThrow(DragonBody.REGISTRY).getHolderOrThrow(ResourceKey.create(DragonBody.REGISTRY, ResourceLocation.parse("dragonsurvival:east")));
                    h.setSpecies(player, species); h.setBody(player, body); h.setGrowth(player, 40); h.refreshSkinPresetForSpecies(species, body);
                    h.getCurrentSkinPreset().setAllStagesToUseDefaultSkin(false); h.getSkinData().blankSkin = false; h.recompileCurrentSkin();
                    player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                    SyncComplete.handleDragonSync(player, false);
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SyncComplete(player.getId(), h.serializeNBT(player.registryAccess())));
                    player.serverLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server); player.serverLevel().setDayTime(6000);
                }, mc.getSingleplayerServer()); return;
            }
            if (!setup.isDone()) return; setup.join();
            ticks++;
            if (step == 0 && ticks >= 200) {
                Diagnostics.INSTANCE.export(mc.gameDirectory.toPath().resolve("baseline-textures.json"));
                if (!TextureCache.enabled()) throw new IllegalStateException("Texture patches did not enable");
                Diagnostics.INSTANCE.reset(); ticks = 0; step++;
            } else if (step == 1) {
                // Repeated equivalent state invalidation must restore flags without synthesis.
                var h = DragonStateProvider.getData(mc.player);
                h.getSkinData().deserializeNBT(mc.player.registryAccess(), h.getSkinData().serializeNBT(mc.player.registryAccess()), h.body());
                h.recompileCurrentSkin();
                mc.player.getItemBySlot(EquipmentSlot.CHEST).setDamageValue(ticks % 30);
                mc.player.setYRot(ticks * 2);
                if (ticks == 100) {
                    if (generated() != 0) throw new IllegalStateException("Identical appearance kept rebuilding textures");
                    Diagnostics.INSTANCE.export(mc.gameDirectory.toPath().resolve("equivalent-state.json"));
                    var eyes = h.getCurrentStageCustomization().layerSettings.get(SkinLayer.EYES).get();
                    oldHue = eyes.hue; oldModified = eyes.isModified; oldGlow = eyes.isGlowing;
                    eyes.hue += .125f; eyes.isModified = true; eyes.isGlowing = true; h.recompileCurrentSkin();
                    mc.player.setYRot(0); Diagnostics.INSTANCE.reset(); ticks = 0; step = 10;
                }
            } else if (step == 10 && ticks >= 30) {
                if (generated() != 1) throw new IllegalStateException("Skin content change must generate exactly once: " + generated());
                Diagnostics.INSTANCE.export(mc.gameDirectory.toPath().resolve("changed-skin.json"));
                mc.player.getItemBySlot(EquipmentSlot.CHEST).set(DataComponents.DYED_COLOR, new DyedItemColor(0xff3366, false));
                Diagnostics.INSTANCE.reset(); ticks = 0; step = 11;
            } else if (step == 11 && ticks >= 30) {
                if (generated() != 1) throw new IllegalStateException("Armor dye change must generate exactly once: " + generated());
                Diagnostics.INSTANCE.export(mc.gameDirectory.toPath().resolve("changed-armor.json"));
                var h = DragonStateProvider.getData(mc.player); var eyes = h.getCurrentStageCustomization().layerSettings.get(SkinLayer.EYES).get();
                eyes.hue = oldHue; eyes.isModified = oldModified; eyes.isGlowing = oldGlow; h.recompileCurrentSkin();
                Diagnostics.INSTANCE.reset(); ticks = 0; step = 12;
            } else if (step == 12 && ticks >= 30) {
                if (generated() != 0) throw new IllegalStateException("Retained prior skin failed to hit cache");
                Diagnostics.INSTANCE.export(mc.gameDirectory.toPath().resolve("restored-skin.json"));
                var h = DragonStateProvider.getData(mc.player); h.getCurrentStageCustomization().layerSettings.get(SkinLayer.EYES).get().isGlowing = true; h.recompileCurrentSkin();
                OptimizerConfig.MODE.set(OptimizerConfig.Mode.GPU); ticks = 0; step = 2;
            } else if (step == 2 && ticks >= 160) {
                Diagnostics.INSTANCE.export(mc.gameDirectory.toPath().resolve("gpu-mode.json"));
                if (!GpuDispatcher.enabled()) throw new IllegalStateException("GPU backend fell back: " + Diagnostics.INSTANCE.snapshot());
                var total = (Map<?, ?>)Diagnostics.INSTANCE.snapshot().get("totals");
                if (!total.containsKey("GPU_PASS")) throw new IllegalStateException("No GPU dragon commands were drawn");
                var attributed = (Map<?, ?>)Diagnostics.INSTANCE.snapshot().get("attribution");
                if (!attributed.containsKey("glow/entity")) throw new IllegalStateException("Additive glow did not use the GPU path");
                textureSteadyBytes = TextureCache.bytes(); meshSteadyBytes = GpuDispatcher.bytes();
                mc.reloadResourcePacks(); ticks = 0; step++;
            } else if (step == 3 && ticks >= 160) {
                Diagnostics.INSTANCE.export(mc.gameDirectory.toPath().resolve("after-reload.json"));
                OptimizerConfig.MODE.set(OptimizerConfig.Mode.VANILLA); ticks = 0; step++;
            } else if (step == 4 && ticks >= 40) {
                OptimizerConfig.MODE.set(OptimizerConfig.Mode.TEXTURES); ticks = 0; step++;
            } else if (step == 5 && ticks >= 100) {
                screenshot(mc, "plain-textures");
                ClientBenchmark.start(1, 1);
                if (OptimizerConfig.MODE.get() != OptimizerConfig.Mode.VANILLA) throw new IllegalStateException("Benchmark did not select baseline mode");
                ClientBenchmark.cancel("validation command test");
                if (OptimizerConfig.MODE.get() != OptimizerConfig.Mode.TEXTURES) throw new IllegalStateException("Benchmark did not restore previous mode");
                if (!Boolean.getBoolean("beloongrender.shaders")) { finish(mc, null); return; }
                try (var files = Files.list(mc.gameDirectory.toPath().resolve("shaderpacks"))) { shaderPacks = files.filter(p -> p.toString().endsWith(".zip")).map(p -> p.getFileName().toString()).sorted().toList(); }
                if (shaderPacks.size() != 8) throw new IllegalStateException("Shader regression requires all eight shipped packs");
                loadShader(mc); ticks = 0; step = 6;
            } else if (step == 6 && ticks >= 120) {
                var iris = Class.forName("net.irisshaders.iris.Iris");
                if ((boolean)iris.getMethod("loadedIncompatiblePack").invoke(null) || !(boolean)iris.getMethod("isPackInUseQuick").invoke(null)
                    || !shaderPacks.get(shaderIndex).equals(iris.getMethod("getCurrentPackName").invoke(null))) throw new IllegalStateException("Shader pipeline failed or wrong pack loaded for " + shaderPacks.get(shaderIndex));
                screenshot(mc, "shader-" + shaderIndex + "-textures");
                Diagnostics.INSTANCE.reset(); OptimizerConfig.MODE.set(OptimizerConfig.Mode.GPU); ticks = 0; step = 7;
            } else if (step == 7 && ticks >= 120) {
                Diagnostics.INSTANCE.export(mc.gameDirectory.toPath().resolve("shader-" + shaderIndex + "-gpu.json"));
                if (!GpuDispatcher.enabled() || !((Map<?, ?>)Diagnostics.INSTANCE.snapshot().get("totals")).containsKey("GPU_PASS")) throw new IllegalStateException("Shader GPU path failed: " + shaderPacks.get(shaderIndex));
                screenshot(mc, "shader-" + shaderIndex + "-gpu");
                Files.writeString(mc.gameDirectory.toPath().resolve("shader-" + shaderIndex + "-name.txt"), shaderPacks.get(shaderIndex));
                shaderIndex++;
                if (shaderIndex == shaderPacks.size()) { finish(mc, null); return; }
                OptimizerConfig.MODE.set(OptimizerConfig.Mode.TEXTURES); loadShader(mc); ticks = 0; step = 6;
            }
        } catch (Throwable e) { finish(mc, e); }
    }
    private static void loadShader(Minecraft mc) throws Exception {
        var iris = Class.forName("net.irisshaders.iris.Iris"); var config = iris.getMethod("getIrisConfig").invoke(null);
        config.getClass().getMethod("setShaderPackName", String.class).invoke(config, shaderPacks.get(shaderIndex));
        config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, true);
        config.getClass().getMethod("save").invoke(config);
        iris.getMethod("reload").invoke(null);
    }
    private static void screenshot(Minecraft mc, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(mc.gameDirectory.toPath().resolve(name + ".png")); }
    }
    private static long generated() { var totals = (Map<?, ?>)Diagnostics.INSTANCE.snapshot().get("totals"); return ((Number)totals.getOrDefault("GENERATED", null) == null) ? 0 : ((Number)totals.get("GENERATED")).longValue(); }
    private static void finish(Minecraft mc, Throwable error) {
        done = true;
        try {
            var result = new LinkedHashMap<String, Object>(); result.put("pass", error == null); result.put("error", error == null ? null : error.toString()); result.put("stats", Diagnostics.INSTANCE.snapshot());
            result.put("textureBytes", TextureCache.bytes()); result.put("meshBytes", GpuDispatcher.bytes());
            result.put("beforeReloadTextureBytes", textureSteadyBytes); result.put("beforeReloadMeshBytes", meshSteadyBytes);
            RenderOptimizer.clear(); result.put("afterClearTextureBytes", TextureCache.bytes()); result.put("afterClearMeshBytes", GpuDispatcher.bytes());
            if (TextureCache.bytes() != 0 || GpuDispatcher.bytes() != 0) { result.put("pass", false); result.put("error", "Resources remained after clear"); }
            result.put("compatibility", top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin.status);
            result.put("modId", "dsbr"); result.put("legacyActive", top.wu949.dsbr.client.DSBRRenderConfig.legacyActive());
            result.put("legacyConfigFlag", top.wu949.dsbr.client.DSBRRenderConfig.LEGACY_BACKEND_ENABLED.get());
            result.put("savedOldRenderMode", top.wu949.dsbr.client.DSBRRenderConfig.NORMAL_RENDER_MODE.get().name());
            Files.writeString(mc.gameDirectory.toPath().resolve("probe-result.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
            if (error != null) RenderOptimizer.LOGGER.error("Runtime probe failed", error);
        } catch (Exception e) { e.printStackTrace(); }
        mc.stop();
    }
}
