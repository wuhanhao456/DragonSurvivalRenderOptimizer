package top.wu949.dsbr.optimizer.validation;

import by.dragonsurvivalteam.dragonsurvival.common.capability.*;
import by.dragonsurvivalteam.dragonsurvival.client.render.ClientDragonRenderer;
import by.dragonsurvivalteam.dragonsurvival.client.render.entity.dragon.DragonRenderer;
import by.dragonsurvivalteam.dragonsurvival.network.syncing.SyncComplete;
import by.dragonsurvivalteam.dragonsurvival.registry.dragon.DragonSpecies;
import by.dragonsurvivalteam.dragonsurvival.registry.dragon.body.DragonBody;
import top.wu949.dsbr.optimizer.*;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Test-only model/pose matrix, after ordinary cache and inventory checks. */
final class ExtraVisualScenes {
    private static final String[][] MODELS = {{"tundra_dragon_human", "tundra_body_human"}, {"aether_dragon_human", "aether_body_human"}, {"cave_dragon", "east"}};
    private static final String[] POSES = {"idle", "flight", "first-person", "inventory", "hidden-head"};
    private static final List<Map<String,Object>> rows = new ArrayList<>();
    private static int model, pose, mode, ticks;
    private static CompletableFuture<Void> sync;
    private static double x, y, z;
    static boolean tick(Minecraft mc) throws Exception {
        if (model == MODELS.length) return true;
        if (sync == null) {
            x = mc.player.getX(); y = mc.player.getY(); z = mc.player.getZ();
            int index = model;
            sync = CompletableFuture.runAsync(() -> {
                var server = mc.getSingleplayerServer(); var player = server.getPlayerList().getPlayer(mc.player.getUUID()); var h = DragonStateProvider.getData(player);
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                player.getAbilities().invulnerable = true; player.getAbilities().mayfly = true; player.onUpdateAbilities();
                player.fallDistance = 0; player.setHealth(player.getMaxHealth());
                var species = player.registryAccess().registryOrThrow(DragonSpecies.REGISTRY).getHolderOrThrow(ResourceKey.create(DragonSpecies.REGISTRY, ResourceLocation.fromNamespaceAndPath("dragonsurvival", MODELS[index][0])));
                var body = player.registryAccess().registryOrThrow(DragonBody.REGISTRY).getHolderOrThrow(ResourceKey.create(DragonBody.REGISTRY, ResourceLocation.fromNamespaceAndPath("dragonsurvival", MODELS[index][1])));
                h.setSpecies(player, species); h.setBody(player, body); h.setDesiredGrowth(player, 40); h.setGrowth(player, 40); h.isGrowthStopped = true;
                h.refreshSkinPresetForSpecies(species, body); h.getCurrentSkinPreset().setAllStagesToUseDefaultSkin(true); h.recompileCurrentSkin();
                SyncComplete.handleDragonSync(player, false);
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SyncComplete(player.getId(), h.serializeNBT(player.registryAccess())));
            }, mc.getSingleplayerServer()); return false;
        }
        if (!sync.isDone()) return false; sync.join();
        if (!DragonStateProvider.getData(mc.player).speciesId().toString().equals("dragonsurvival:" + MODELS[model][0])) return false;
        if (ticks++ == 0) {
            OptimizerConfig.MODE.set(mode == 0 ? OptimizerConfig.Mode.TEXTURES : OptimizerConfig.Mode.GPU); Diagnostics.INSTANCE.reset();
            mc.setScreen(pose == 3 ? new InventoryScreen(mc.player) : null);
            mc.options.setCameraType(pose == 2 ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_FRONT);
        }
        double height = pose == 1 ? 80 : y;
        mc.player.fallDistance = 0;
        mc.player.setPos(x, height, z); mc.player.setDeltaMovement(pose == 1 ? .3 : 0, 0, 0); mc.player.getAbilities().flying = pose == 1;
        var movement = by.dragonsurvivalteam.dragonsurvival.registry.attachments.MovementData.getData(mc.player);
        movement.set(0, 0, 0, new net.minecraft.world.phys.Vec3(pose == 1 ? .3 : 0,0,0));
        movement.setDesiredMoveVec(new net.minecraft.world.phys.Vec3(pose == 1 ? 1 : 0,0,0));
        var server = mc.getSingleplayerServer(); server.execute(() -> { var p = server.getPlayerList().getPlayer(mc.player.getUUID()); p.fallDistance = 0; p.setPos(x, height, z); p.getAbilities().flying = pose == 1; });
        var dragon = ClientDragonRenderer.getDragon(mc.player);
        if (dragon == null) return false;
        var renderer = mc.getEntityRenderDispatcher().getRenderer(dragon);
        if (!(renderer instanceof DragonRenderer r)) throw new IllegalStateException("Expected DS renderer for visual model");
        var geo = r.getGeoModel();
        geo.getBone("BreathSource").ifPresent(b -> b.setTrackingMatrices(true));
        geo.getBone("Head").ifPresent(b -> b.setHidden(pose == 4));
        if (ticks < 60) return false;
        var stats = Diagnostics.INSTANCE.snapshot(); var totals = (Map<?,?>)stats.get("totals");
        if (mode == 1 && pose != 2 && pose != 3 && !totals.containsKey("GPU_PASS")) throw new IllegalStateException("Model not GPU rendered: " + MODELS[model][0]);
        if (mode == 1 && pose == 3 && !totals.containsKey("GUI_CPU_PASS")) throw new IllegalStateException("Model inventory lost CPU protection");
        if (pose == 3 && !(mc.screen instanceof by.dragonsurvivalteam.dragonsurvival.client.gui.screens.DragonInventoryScreen)) throw new IllegalStateException("Dragon inventory did not stay open");
        var faults = new HashMap<>((Map<?,?>)stats.get("fallbacks")); faults.remove("benchmark cancelled", "validation command test");
        if (!faults.isEmpty()) throw new IllegalStateException("Visual scene backend fault: " + faults);
        String name = "scene-" + model + "-" + POSES[pose] + "-" + mode;
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(mc.gameDirectory.toPath().resolve(name + ".png")); }
        var row = new LinkedHashMap<String,Object>(); row.put("species", MODELS[model][0]); row.put("pose", POSES[pose]); row.put("mode", OptimizerConfig.MODE.get().name()); row.put("stats", stats);
        row.put("cameraType", mc.options.getCameraType().name()); row.put("screen", mc.screen == null ? null : mc.screen.getClass().getName());
        row.put("windowFpsLimit", mc.getWindow().getFramerateLimit()); row.put("swapInterval", org.lwjgl.opengl.WGLEXTSwapControl.wglGetSwapIntervalEXT());
        if (pose == 0 || pose == 1) {
            var bone = geo.getBone("BreathSource");
            if (model < 2 && bone.isEmpty()) throw new IllegalStateException("Dragon girl breath locator missing");
            if (bone.isPresent()) {
                var location = bone.get().getWorldPosition();
                if (!location.isFinite() || location.distance(x,height,z) > 16) throw new IllegalStateException("Invalid breath locator: " + location);
                row.put("breathWorldPosition", List.of(location.x,location.y,location.z));
            }
        }
        rows.add(row); Files.writeString(mc.gameDirectory.toPath().resolve("visual-scenes.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rows));
        geo.getBone("Head").ifPresent(b -> b.setHidden(false));
        mc.setScreen(null); mc.player.setPos(x,y,z); mc.player.fallDistance = 0; mc.player.getAbilities().flying = false;
        ticks = 0; if (++mode == 2) { mode = 0; if (++pose == POSES.length) { pose = 0; model++; sync = null; } }
        return model == MODELS.length;
    }
}
