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
    private static final int[] COUNTS = Arrays.stream(System.getProperty("beloongrender.counts", "1,4,12").split(",")).mapToInt(Integer::parseInt).toArray();
    private static final OptimizerConfig.Mode[] MODES = Boolean.getBoolean("beloongrender.gpuOnly") ? new OptimizerConfig.Mode[]{OptimizerConfig.Mode.GPU} : OptimizerConfig.Mode.values();
    private static final long WARM = Long.getLong("beloongrender.warm", 5L) * 1_000_000_000L, SAMPLE = Long.getLong("beloongrender.sample", 15L) * 1_000_000_000L;
    private static final int REPEATS = Integer.getInteger("beloongrender.repeats", 2);
    private static final String SCENARIO = System.getProperty("beloongrender.scenario", "fixed");
    private static int appearanceStep = -1;
    private static String worldName = "multi-render-validation";
    private static int worldAttempts;
    private static long worldStarted;
    private static final List<Player> actors = new ArrayList<>();
    private static final List<Map<String, Object>> rows = new ArrayList<>();
    private static boolean creating, done, sampling;
    private static CompletableFuture<Void> setup;
    private static int countIndex, repeat, phase;
    private static long deadline, sampleStart;
    private static int settleTicks;
    private static double x, y, z;
    private static ArmorStand camera;
    private static CompletableFuture<Void> preloaded;
    private static boolean preloading;
    private static final long START = System.nanoTime();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("beloongrender.multiBenchmark") || done) return;
        var mc = Minecraft.getInstance();
        try {
            if (!mc.gameDirectory.getName().startsWith("dsbr-validation-")) throw new IllegalStateException("Fresh validation instance required");
            if (System.nanoTime() - START > Math.max(1800_000_000_000L, COUNTS.length * MODES.length * REPEATS * (WARM + SAMPLE) + 1800_000_000_000L)) throw new IllegalStateException("Benchmark deadline");
            if (!creating) {
                if (!(mc.screen instanceof TitleScreen)) return;
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false; mc.options.hideGui = true; mc.options.framerateLimit().set(260); mc.options.enableVsync().set(false); mc.options.renderDistance().set(6);
                creating = true; worldStarted = System.nanoTime(); worldAttempts++;
                if (Boolean.getBoolean("beloongrender.fixtureWorld")) {
                    mc.createWorldOpenFlows().openWorld("multi-render-validation", () -> { throw new IllegalStateException("Fixture world failed to open"); }); return;
                }
                if (Files.exists(mc.gameDirectory.toPath().resolve("saves/" + worldName))) throw new IllegalStateException("Existing save refused");
                mc.createWorldOpenFlows().createFreshLevel(worldName, new LevelSettings(worldName, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(2067, SCENARIO.startsWith("flight"), false), r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(SCENARIO.startsWith("flight") ? WorldPresets.NORMAL : WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
                return;
            }
            if (mc.level == null && mc.getSingleplayerServer() == null && mc.screen instanceof TitleScreen && System.nanoTime() - worldStarted > 15_000_000_000L) {
                if (worldAttempts >= 2 || Boolean.getBoolean("beloongrender.fixtureWorld")) throw new IllegalStateException("World creation returned to title; datapack load failed");
                creating = false; worldName = "multi-render-validation-retry"; RenderOptimizer.LOGGER.warn("Retrying isolated world creation after initial resource load"); return;
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            if (mc.screen != null) mc.setScreen(null);
            if (setup == null) {
                x = .5; y = SCENARIO.startsWith("flight") ? 256 : -60; z = .5;
                setup = CompletableFuture.runAsync(() -> {
                    var server = mc.getSingleplayerServer(); var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    player.connection.teleport(x, y, z, 0, 0);
                    player.getAbilities().flying = SCENARIO.startsWith("flight"); player.onUpdateAbilities();
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
            if (SCENARIO.equals("flight-preloaded") && preloaded == null) {
                preloading = true;
                preloaded = CompletableFuture.runAsync(() -> {
                    var server = mc.getSingleplayerServer(); var level = server.overworld();
                    int first = net.minecraft.util.Mth.floor(x) >> 4, last = net.minecraft.util.Mth.floor(x + SAMPLE / 1e9 * 8) >> 4, middle = net.minecraft.util.Mth.floor(z) >> 4;
                    for (int cx = first - 6; cx <= last + 6; cx++) for (int cz = middle - 6; cz <= middle + 6; cz++) level.getChunk(cx, cz);
                    level.getChunkSource().save(true);
                }, mc.getSingleplayerServer()); return;
            }
            if (preloading) { if (!preloaded.isDone()) return; preloaded.join(); preloading = false; begin(); }
            if (Boolean.getBoolean("beloongrender.prepareWorld")) { finish(mc, null); return; }
            for (var actor : actors) { actor.setYRot(0); actor.setXRot(0); actor.yBodyRot = actor.yHeadRot = 0; }
            if (sampling || SCENARIO.startsWith("flight")) updateScenario(mc);
            if (System.nanoTime() < deadline) return;
            if (!sampling) {
                if (mc.level.players().size() != COUNTS[countIndex]) throw new IllegalStateException("Wrong simulated player count: " + mc.level.players().size());
                try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { screenshot.writeToFile(mc.gameDirectory.toPath().resolve("players-" + COUNTS[countIndex] + ".png")); }
                Diagnostics.INSTANCE.reset(); ProbeFrames.begin(); sampling = true; appearanceStep = -1; sampleStart = System.nanoTime(); deadline = sampleStart + SAMPLE; return;
            }
            record(mc); phase++;
            if (phase == MODES.length) { phase = 0; repeat++; }
            if (repeat == REPEATS) { repeat = 0; countIndex++; if (countIndex < COUNTS.length) spawn(mc); }
            if (countIndex == COUNTS.length) { finish(mc, null); return; }
            begin();
        } catch (Throwable error) { finish(mc, error); }
    }
    private static OptimizerConfig.Mode mode() { return MODES[repeat % 2 == 0 ? phase : MODES.length - 1 - phase]; }
    private static void begin() {
        var window = Minecraft.getInstance().getWindow(); window.setFramerateLimit(260); window.updateVsync(false);
        OptimizerConfig.MODE.set(mode()); sampling = false; deadline = System.nanoTime() + WARM;
    }
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
        String rawFile = "frames-p" + COUNTS[countIndex] + "-r" + (repeat + 1) + "-" + mode() + ".csv";
        ProbeFrames.save(mc.gameDirectory.toPath().resolve(rawFile));
        var stats = Diagnostics.INSTANCE.snapshot(); var totals = (Map<?, ?>)stats.get("totals");
        if (mode() != OptimizerConfig.Mode.VANILLA && counter(totals, "READBACK") != 0) throw new IllegalStateException("Readback during normal rendering");
        if (SCENARIO.equals("fixed") && counter(totals, "GENERATED") != 0) throw new IllegalStateException("Steady scene kept generating textures");
        if (!((Map<?, ?>)stats.get("fallbacks")).isEmpty()) throw new IllegalStateException("Fallback during benchmark: " + stats.get("fallbacks"));
        if (mode() == OptimizerConfig.Mode.GPU && (!GpuDispatcher.enabled() || !totals.containsKey("GPU_PASS"))) throw new IllegalStateException("GPU did not run");
        if (mode() == OptimizerConfig.Mode.GPU) {
            var phases = (Map<?, ?>)stats.get("attribution"); var entity = (Map<?, ?>)phases.get("entity");
            double passesPerFrame = entity == null ? 0 : ((Number)entity.get("GPU_PASS")).doubleValue() / ((Number)stats.get("frameSamples")).doubleValue();
            if (!SCENARIO.startsWith("flight") && passesPerFrame < COUNTS[countIndex] * 1.8) throw new IllegalStateException("Some dragons were not drawn: GPU entity passes/frame=" + passesPerFrame);
        }
        var row = new LinkedHashMap<String, Object>();
        row.put("mode", mode().name()); row.put("dragonPlayers", COUNTS[countIndex]); row.put("repetition", repeat + 1);
        row.put("warmupSeconds", WARM / 1e9); row.put("samplingSeconds", SAMPLE / 1e9); row.put("rawFrames", rawFile); row.put("scenario", SCENARIO); row.put("frameTimelineOriginUtc", ProbeFrames.origin());
        row.put("width", mc.getWindow().getWidth()); row.put("height", mc.getWindow().getHeight()); row.put("fpsLimit", mc.options.framerateLimit().get());
        row.put("renderDistance", mc.options.renderDistance().get()); row.put("camera", mc.gameRenderer.getMainCamera().getPosition().toString());
        row.put("vsync", mc.options.enableVsync().get());
        row.put("windowFpsLimit", mc.getWindow().getFramerateLimit());
        row.put("effectiveFpsLimit", ((top.wu949.dsbr.optimizer.validation.mixin.MinecraftFrameLimitAccess)(Object)mc).dsbr$getEffectiveFramerateLimit());
        row.put("swapInterval", org.lwjgl.opengl.WGLEXTSwapControl.wglGetSwapIntervalEXT());
        if (Boolean.getBoolean("beloongrender.shaders")) {
            var iris = Class.forName("net.irisshaders.iris.Iris");
            Object shader = iris.getMethod("getCurrentPackName").invoke(null);
            if (!(boolean)iris.getMethod("isPackInUseQuick").invoke(null) || !"ComplementaryReimagined_r5.9.zip".equals(shader)) throw new IllegalStateException("Controlled shader not active: " + shader);
            row.put("shaderPack", shader);
        }
        row.put("textureBytes", TextureCache.bytes()); row.put("meshBytes", GpuDispatcher.bytes()); row.put("stats", stats);
        row.put("actorScales", actors.stream().map(Player::getScale).toList());
        row.put("actorGrowth", actors.stream().map(p -> DragonStateProvider.getData(p).getGrowth()).toList());
        row.put("actorStages", actors.stream().map(p -> DragonStateProvider.getData(p).stageId().toString()).toList());
        if (actors.stream().anyMatch(p -> Math.abs(p.getScale() - mc.player.getScale()) > 1e-5)) throw new IllegalStateException("Actor scales differ");
        if (actors.stream().anyMatch(p -> !Objects.equals(DragonStateProvider.getData(p).stageId(), DragonStateProvider.getData(mc.player).stageId()))) throw new IllegalStateException("Actor stages differ");
        rows.add(row); save(mc);
        RenderOptimizer.LOGGER.info("Multi-dragon benchmark: players={}, repetition={}, mode={}, frame={}", COUNTS[countIndex], repeat + 1, mode(), stats.get("frameTimeMs"));
    }
    private static void updateScenario(Minecraft mc) {
        double elapsed = sampling ? (System.nanoTime() - sampleStart) / 1e9 : 0;
        if (SCENARIO.equals("appearance")) {
            int step = (int)(elapsed / 10);
            if (step != appearanceStep) {
                appearanceStep = step;
                for (var actor : actors) {
                    actor.setItemSlot(EquipmentSlot.CHEST, new ItemStack(step % 2 == 0 ? Items.LEATHER_CHESTPLATE : Items.DIAMOND_CHESTPLATE));
                    var h = DragonStateProvider.getData(actor); h.getCurrentStageCustomization().wings = step % 2 == 0; h.recompileCurrentSkin();
                }
            }
            for (var actor : actors) {
                actor.getItemBySlot(EquipmentSlot.CHEST).setDamageValue((int)elapsed % 40);
                if ((int)elapsed % 3 == 0 && mc.level.getGameTime() % 20 == 0) {
                    var h = DragonStateProvider.getData(actor); h.deserializeNBT(actor.registryAccess(), h.serializeNBT(actor.registryAccess()));
                }
            }
        } else if (SCENARIO.startsWith("flight")) {
            double targetX = x + elapsed * 8;
            for (int i = 0; i < actors.size(); i++) {
                var actor = actors.get(i);
                actor.setPos(i == 0 ? targetX : targetX - 2 - (i - 1) / 4 * 2, y, i == 0 ? z : z + 2.6 - (i - 1) % 4 * 1.75);
                actor.setYRot(-90); actor.yBodyRot = actor.yHeadRot = -90;
                actor.setDeltaMovement(.4, 0, 0); actor.getAbilities().flying = true;
                var movement = by.dragonsurvivalteam.dragonsurvival.registry.attachments.MovementData.getData(actor);
                movement.set(-90, -90, 0, new net.minecraft.world.phys.Vec3(.4,0,0)); movement.setDesiredMoveVec(new net.minecraft.world.phys.Vec3(1,0,0));
            }
            var server = mc.getSingleplayerServer();
            server.execute(() -> { var player = server.getPlayerList().getPlayer(mc.player.getUUID()); if (player != null) { player.setPos(targetX, y, z); player.getAbilities().flying = true; } });
        }
    }
    private static void save(Minecraft mc) throws Exception {
        Files.writeString(mc.gameDirectory.toPath().resolve("multi-comparison.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rows));
    }
    private static long counter(Map<?, ?> values, String key) { var value = values.get(key); return value instanceof Number n ? n.longValue() : 0; }
    private static void finish(Minecraft mc, Throwable error) {
        done = true;
        try {
            save(mc); RenderOptimizer.clear();
            var result = new LinkedHashMap<String, Object>(); result.put("pass", error == null); result.put("error", error == null ? null : error.toString()); result.put("rows", rows.size());
            result.put("worldName", worldName);
            result.put("textureBytesAfterClear", TextureCache.bytes()); result.put("meshBytesAfterClear", GpuDispatcher.bytes()); result.put("compatibility", top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin.status);
            Files.writeString(mc.gameDirectory.toPath().resolve("multi-result.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
            if (error != null) RenderOptimizer.LOGGER.error("Multi-dragon benchmark failed", error);
        } catch (Throwable other) { other.printStackTrace(); }
        mc.stop();
    }
}
