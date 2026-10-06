package top.wu949.dsbr.optimizer.validation;

import by.dragonsurvivalteam.dragonsurvival.common.capability.*;
import by.dragonsurvivalteam.dragonsurvival.network.syncing.SyncComplete;
import by.dragonsurvivalteam.dragonsurvival.registry.dragon.DragonSpecies;
import by.dragonsurvivalteam.dragonsurvival.registry.dragon.body.DragonBody;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import top.wu949.dsbr.optimizer.*;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import top.wu949.dsbr.optimizer.gpu.GpuDispatcher;
import top.wu949.dsbr.optimizer.texture.TextureCache;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Render-only synthetic players. No accounts, remote server, or real-player network benchmark. */
@EventBusSubscriber(modid = "dsbr_validation", value = Dist.CLIENT)
public final class MultiDragonProbe {
    private static final int[] COUNTS = {1, 4, 12};
    private static final OptimizerConfig.Mode[] MODES = OptimizerConfig.Mode.values();
    private static final long WARM = 5_000_000_000L, SAMPLE = 15_000_000_000L;
    private static final List<Player> actors = new ArrayList<>();
    private static final List<Map<String, Object>> rows = new ArrayList<>();
    private static boolean creating, done, sampling;
    private static CompletableFuture<Void> setup;
    private static int countIndex, repeat, phase;
    private static long deadline, sampleStart;
    private static int settleTicks;
    private static double x, y, z;
    private static ArmorStand camera;
    private static final long START = System.nanoTime();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("beloongrender.multiBenchmark") || done) return;
        var mc = Minecraft.getInstance();
        try {
            if (!mc.gameDirectory.getName().startsWith("dsbr-validation-")) throw new IllegalStateException("Fresh validation instance required");
            if (System.nanoTime() - START > 900_000_000_000L) throw new IllegalStateException("15 minute benchmark deadline");
            if (!creating) {
                if (!(mc.screen instanceof TitleScreen)) return;
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false; mc.options.hideGui = true; mc.options.framerateLimit().set(260); mc.options.enableVsync().set(false); mc.options.renderDistance().set(6);
                if (Files.exists(mc.gameDirectory.toPath().resolve("saves/multi-render-validation"))) throw new IllegalStateException("Existing save refused");
                creating = true;
                mc.createWorldOpenFlows().createFreshLevel("multi-render-validation", new LevelSettings("multi-render-validation", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(2067, false, false), r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
                return;
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            if (mc.screen != null) mc.setScreen(null);
            if (setup == null) {
                x = mc.player.getX(); y = mc.player.getY(); z = mc.player.getZ();
                setup = CompletableFuture.runAsync(() -> {
                    var server = mc.getSingleplayerServer(); var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    var h = DragonStateProvider.getData(player);
                    var species = player.registryAccess().registryOrThrow(DragonSpecies.REGISTRY).getHolderOrThrow(ResourceKey.create(DragonSpecies.REGISTRY, ResourceLocation.parse("dragonsurvival:cave_dragon")));
                    var body = player.registryAccess().registryOrThrow(DragonBody.REGISTRY).getHolderOrThrow(ResourceKey.create(DragonBody.REGISTRY, ResourceLocation.parse("dragonsurvival:east")));
                    h.setSpecies(player, species); h.setBody(player, body); h.setDesiredGrowth(player, 40); h.setGrowth(player, 40); h.isGrowthStopped = true; h.refreshSkinPresetForSpecies(species, body);
                    h.getCurrentSkinPreset().setAllStagesToUseDefaultSkin(false); h.getSkinData().blankSkin = false; h.recompileCurrentSkin();
                    player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                    SyncComplete.handleDragonSync(player, false);
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SyncComplete(player.getId(), h.serializeNBT(player.registryAccess())));
                    player.serverLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server); player.serverLevel().setDayTime(6000);
                    player.serverLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                }, mc.getSingleplayerServer()); return;
            }
            if (!setup.isDone()) return; setup.join();
            if (!DragonStateProvider.getData(mc.player).isDragon()) return;
            if (camera == null && ++settleTicks < 100) return;
            if (camera == null) {
                camera = new ArmorStand(mc.level, x, y + 5, z + 14);
                camera.moveTo(x, y + 5, z + 14, 180, 12); camera.setOldPosAndRot();
                mc.setCameraEntity(mc.player); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                spawn(mc); begin();
            }
            for (var actor : actors) { actor.setYRot(0); actor.setXRot(0); actor.yBodyRot = actor.yHeadRot = 0; }
            if (System.nanoTime() < deadline) return;
            if (!sampling) {
                if (mc.level.players().size() != COUNTS[countIndex]) throw new IllegalStateException("Wrong simulated player count: " + mc.level.players().size());
                try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { screenshot.writeToFile(mc.gameDirectory.toPath().resolve("players-" + COUNTS[countIndex] + ".png")); }
                Diagnostics.INSTANCE.reset(); sampling = true; sampleStart = System.nanoTime(); deadline = sampleStart + SAMPLE; return;
            }
            record(mc); phase++;
            if (phase == 3) { phase = 0; repeat++; }
            if (repeat == 2) { repeat = 0; countIndex++; if (countIndex < COUNTS.length) spawn(mc); }
            if (countIndex == COUNTS.length) { finish(mc, null); return; }
            begin();
        } catch (Throwable error) { finish(mc, error); }
    }
    private static OptimizerConfig.Mode mode() { return MODES[repeat == 0 ? phase : 2 - phase]; }
    private static void begin() { OptimizerConfig.MODE.set(mode()); sampling = false; deadline = System.nanoTime() + WARM; }
    private static void spawn(Minecraft mc) {
        for (var actor : actors) if (actor != mc.player) mc.level.removeEntity(actor.getId(), Entity.RemovalReason.DISCARDED);
        actors.clear(); actors.add(mc.player);
        var template = DragonStateProvider.getData(mc.player).serializeNBT(mc.player.registryAccess());
        for (int i = 1; i < COUNTS[countIndex]; i++) {
            var actor = new RemotePlayer(mc.level, new GameProfile(UUID.nameUUIDFromBytes(("DSBR-render-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8)), "RenderDragon" + i));
            DragonStateProvider.getData(actor).deserializeNBT(actor.registryAccess(), template.copy());
            actor.getAttribute(Attributes.SCALE).setBaseValue(mc.player.getScale());
            actor.refreshDimensions();
            actor.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            actors.add(actor); mc.level.addEntity(actor);
        }
        for (int i = 0; i < actors.size(); i++) {
            var actor = actors.get(i); actor.moveTo(i == 0 ? x : x - 2.6 + (i - 1) % 4 * 1.75, y, i == 0 ? z : z - 2 - (i - 1) / 4 * 2, 0, 0);
            actor.setNoGravity(true); actor.setDeltaMovement(0, 0, 0);
            DragonStateProvider.getData(actor).recompileCurrentSkin();
        }
    }
    private static void record(Minecraft mc) throws Exception {
        var stats = Diagnostics.INSTANCE.snapshot(); var totals = (Map<?, ?>)stats.get("totals");
        if (!((Map<?, ?>)stats.get("fallbacks")).isEmpty()) throw new IllegalStateException("Fallback during benchmark: " + stats.get("fallbacks"));
        if (mode() == OptimizerConfig.Mode.GPU && (!GpuDispatcher.enabled() || !totals.containsKey("GPU_PASS"))) throw new IllegalStateException("GPU did not run");
        if (mode() == OptimizerConfig.Mode.GPU) {
            var phases = (Map<?, ?>)stats.get("attribution"); var entity = (Map<?, ?>)phases.get("entity");
            double passesPerFrame = entity == null ? 0 : ((Number)entity.get("GPU_PASS")).doubleValue() / ((Number)stats.get("frameSamples")).doubleValue();
            if (passesPerFrame < COUNTS[countIndex] * 1.8) throw new IllegalStateException("Some dragons were not drawn: GPU entity passes/frame=" + passesPerFrame);
        }
        var row = new LinkedHashMap<String, Object>();
        row.put("mode", mode().name()); row.put("dragonPlayers", COUNTS[countIndex]); row.put("repetition", repeat + 1);
        row.put("warmupSeconds", 5); row.put("samplingSeconds", (System.nanoTime() - sampleStart) / 1e9);
        row.put("width", mc.getWindow().getWidth()); row.put("height", mc.getWindow().getHeight()); row.put("fpsLimit", mc.options.framerateLimit().get());
        row.put("renderDistance", mc.options.renderDistance().get()); row.put("camera", mc.gameRenderer.getMainCamera().getPosition().toString());
        row.put("textureBytes", TextureCache.bytes()); row.put("meshBytes", GpuDispatcher.bytes()); row.put("stats", stats);
        row.put("actorScales", actors.stream().map(Player::getScale).toList());
        row.put("actorGrowth", actors.stream().map(p -> DragonStateProvider.getData(p).getGrowth()).toList());
        row.put("actorStages", actors.stream().map(p -> DragonStateProvider.getData(p).stageId().toString()).toList());
        if (actors.stream().anyMatch(p -> Math.abs(p.getScale() - mc.player.getScale()) > 1e-5)) throw new IllegalStateException("Actor scales differ");
        if (actors.stream().anyMatch(p -> !Objects.equals(DragonStateProvider.getData(p).stageId(), DragonStateProvider.getData(mc.player).stageId()))) throw new IllegalStateException("Actor stages differ");
        rows.add(row); save(mc);
        RenderOptimizer.LOGGER.info("Multi-dragon benchmark: players={}, repetition={}, mode={}, frame={}", COUNTS[countIndex], repeat + 1, mode(), stats.get("frameTimeMs"));
    }
    private static void save(Minecraft mc) throws Exception {
        Files.writeString(mc.gameDirectory.toPath().resolve("multi-comparison.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rows));
    }
    private static void finish(Minecraft mc, Throwable error) {
        done = true;
        try {
            save(mc); RenderOptimizer.clear();
            var result = new LinkedHashMap<String, Object>(); result.put("pass", error == null); result.put("error", error == null ? null : error.toString()); result.put("rows", rows.size());
            result.put("textureBytesAfterClear", TextureCache.bytes()); result.put("meshBytesAfterClear", GpuDispatcher.bytes()); result.put("compatibility", top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin.status);
            Files.writeString(mc.gameDirectory.toPath().resolve("multi-result.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
            if (error != null) RenderOptimizer.LOGGER.error("Multi-dragon benchmark failed", error);
        } catch (Throwable other) { other.printStackTrace(); }
        mc.stop();
    }
}
