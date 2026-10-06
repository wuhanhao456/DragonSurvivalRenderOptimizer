package top.wu949.dsbr.optimizer.texture;

import by.dragonsurvivalteam.dragonsurvival.client.models.DragonModel;
import by.dragonsurvivalteam.dragonsurvival.client.skin_editor_system.DragonEditorHandler;
import by.dragonsurvivalteam.dragonsurvival.client.util.FakeClientPlayer;
import by.dragonsurvivalteam.dragonsurvival.common.capability.*;
import top.wu949.dsbr.optimizer.*;
import top.wu949.dsbr.optimizer.cache.BudgetCache;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import top.wu949.dsbr.optimizer.mixin.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import java.util.*;
import static top.wu949.dsbr.optimizer.diagnostics.Diagnostics.Counter.*;

public final class TextureCache {
    private record SkinEntry(AppearanceKey content, ResourceLocation normal, ResourceLocation glow) {}
    private static final Map<ResourceLocation, SkinEntry> skins = new HashMap<>();
    private static final BudgetCache<ResourceLocation, ResourceLocation> textures = new BudgetCache<>(TextureCache::dispose);
    private static final Set<ResourceLocation> pinned = new HashSet<>();
    private static boolean failed;
    private static boolean deferredFailureCleanup;
    public static boolean enabled() { return !failed && top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin.texturesCompatible && OptimizerConfig.MODE.get() != OptimizerConfig.Mode.VANILLA; }
    public static DragonStateHandler handler(Player p) { return p instanceof FakeClientPlayer fake ? fake.handler : DragonStateProvider.getData(p); }
    public static void demand(Player player) {
        if (!enabled() || player == null) return;
        try {
            var h = handler(player);
            if (h == null || !h.isDragon() || h.body() == null || h.getSkinData().blankSkin || h.getCurrentStageCustomization().defaultSkin) return;
            // Call the public generator even for a newly synchronized handler with no compilation flags.
            // Its HEAD deduplicates by content, its RETURN commits flags only after both textures exist.
            DragonEditorHandler.generateSkinTextures(player, h);
        } catch (RuntimeException | LinkageError e) { fail("appearance cache", e); }
    }
    public static boolean reuse(Player p, DragonStateHandler h) {
        if (p == null || h == null || h.body() == null) return false;
        if (h.needsSkinRecompilation()) Diagnostics.INSTANCE.count(REQUEST, attribution(p, h, DragonModel.dynamicTexture(p, h, false)));
        if (!enabled()) return false;
        try {
            var normal = DragonModel.dynamicTexture(p, h, false);
            var entry = skins.get(normal);
            if (entry != null && entry.content.equals(AppearanceKey.skin(h)) && valid(entry.normal) && valid(entry.glow)) {
                mark(h); touch(entry.normal); touch(entry.glow);
                Diagnostics.INSTANCE.count(HIT, attribution(p, h, normal)); return true;
            }
        } catch (RuntimeException | LinkageError e) { fail("skin key", e); }
        return false;
    }
    public static void compiled(Player p, DragonStateHandler h, long nanos) {
        if (p == null || h == null || h.body() == null) return;
        var normal = DragonModel.dynamicTexture(p, h, false); var glow = DragonModel.dynamicTexture(p, h, true);
        if (!valid(normal) || !valid(glow)) { if (enabled()) fail("incomplete texture synthesis", new IllegalStateException(normal.toString())); return; }
        Diagnostics.INSTANCE.count(GENERATED, attribution(p, h, normal)); Diagnostics.INSTANCE.nanos("SKIN_GENERATE", nanos, attribution(p, h, normal));
        if (!enabled()) return;
        var key = AppearanceKey.skin(h);
        skins.put(normal, new SkinEntry(key, normal, glow)); mark(h);
        register(normal, (long)key.width() * key.height() * 4); register(glow, (long)key.width() * key.height() * 4);
    }
    private static void mark(DragonStateHandler h) { h.getSkinData().isCompiled.put(h.stageKey(), true); h.getSkinData().recompileSkin.put(h.stageKey(), false); }
    public static String attribution(Player p, DragonStateHandler h, ResourceLocation texture) { return p.getUUID() + "/" + h.stageKey().location() + "/" + texture; }
    public static boolean valid(ResourceLocation key) { return Minecraft.getInstance().getTextureManager().getTexture(key, null) != null; }
    public static void register(ResourceLocation key, long bytes) {
        if (textures.get(key, System.nanoTime()) == null) textures.put(key, key, bytes, System.nanoTime());
        else touch(key);
        if (enabled()) pin(key);
        Diagnostics.INSTANCE.peak("TEXTURE_RESOURCE_BYTES", bytes());
    }
    public static void touch(ResourceLocation key) { textures.get(key, System.nanoTime()); if (enabled()) pin(key); }
    public static void pin(ResourceLocation key) { if (pinned.add(key)) { var e = textures.get(key, System.nanoTime()); if (e != null) e.leases++; } }
    public static void unpinAll() { for (var key : pinned) { var e = textures.get(key, System.nanoTime()); if (e != null) e.leases--; } pinned.clear(); }
    public static long bytes() { return textures.bytes() + FramebufferPool.bytes(); }
    public static void maintenance() {
        // Keep resources used by already queued draw commands alive until this frame finishes.
        if (deferredFailureCleanup) { clear(); return; }
        if (enabled()) textures.prune(System.nanoTime(), OptimizerConfig.RETENTION_SECONDS.get() * 1_000_000_000L, OptimizerConfig.TEXTURE_MIB.get() * 1048576L - FramebufferPool.bytes());
    }
    private static void dispose(ResourceLocation key) {
        Minecraft.getInstance().getTextureManager().release(key);
        SkinRegistryAccessor.beloong$generated().remove(key); SkinRegistryAccessor.beloong$used().remove(key);
        ArmorRegistryAccessor.beloong$generated().remove(key); ArmorRegistryAccessor.beloong$used().remove(key);
        skins.entrySet().removeIf(e -> e.getValue().normal.equals(key) || e.getValue().glow.equals(key));
        Diagnostics.INSTANCE.count(RELEASE, key.toString());
    }
    public static void clear() { unpinAll(); textures.clear(); skins.clear(); FramebufferPool.clear(); deferredFailureCleanup = false; }
    public static void fail(String feature, Throwable e) { failed = true; deferredFailureCleanup = true; Diagnostics.INSTANCE.reason(feature, e.toString()); RenderOptimizer.LOGGER.error("Texture optimization disabled: {}", feature, e); }
}
