package top.wu949.dsbr.optimizer.texture;

import by.dragonsurvivalteam.dragonsurvival.client.models.DragonModel;
import by.dragonsurvivalteam.dragonsurvival.client.skin_editor_system.DragonEditorHandler;
import by.dragonsurvivalteam.dragonsurvival.client.util.FakeClientPlayer;
import by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateHandler;
import by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import top.wu949.dsbr.optimizer.OptimizationStage;
import top.wu949.dsbr.optimizer.OptimizerConfig;
import top.wu949.dsbr.optimizer.RenderOptimizer;
import top.wu949.dsbr.optimizer.cache.BudgetCache;
import top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import top.wu949.dsbr.optimizer.mixin.ArmorRegistryAccessor;
import top.wu949.dsbr.optimizer.mixin.SkinRegistryAccessor;
import top.wu949.dsbr.optimizer.soul.SoulRenderContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static top.wu949.dsbr.optimizer.diagnostics.Diagnostics.Counter.*;

public final class TextureCache {
    private record SkinEntry(AppearanceKey content, ResourceLocation normal, ResourceLocation glow) {}

    private static final Map<ResourceLocation, SkinEntry> skins = new HashMap<>();
    private static final BudgetCache<ResourceLocation, ResourceLocation> textures = new BudgetCache<>(TextureCache::dispose);
    /** Entries leased for the current frame; pinning the entry itself keeps lease counts paired with unpinAll. */
    private static final Set<BudgetCache.Entry<ResourceLocation>> pinned = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Players whose appearance data could not be keyed; they keep the original DS path for this session. */
    private static final Set<UUID> quarantined = new HashSet<>();
    private static boolean failed;
    private static boolean deferredFailureCleanup;

    public static boolean enabled() {
        return !failed && OptimizerMixinPlugin.texturesCompatible && OptimizerConfig.MODE.get() != OptimizerConfig.Mode.VANILLA;
    }

    /** Whether the content-keyed path applies to this player. */
    public static boolean enabled(Player player) {
        return enabled() && (player == null || quarantined.isEmpty() || !quarantined.contains(player.getUUID()));
    }

    public static DragonStateHandler handler(Player p) {
        return p instanceof FakeClientPlayer fake ? fake.handler : DragonStateProvider.getData(p);
    }

    public static void demand(Player player) {
        if (player == null || !enabled(player)) return;
        try {
            var h = handler(player);
            if (h == null || !h.isDragon() || h.body() == null || h.getSkinData().blankSkin || h.getCurrentStageCustomization().defaultSkin) return;
            // Call the public generator even for a newly synchronized handler with no compilation flags.
            // Its HEAD deduplicates by content, its RETURN commits flags only after both textures exist.
            DragonEditorHandler.generateSkinTextures(player, h);
        } catch (RuntimeException | LinkageError e) {
            problem(player, "appearance cache", e);
        }
    }

    public static boolean reuse(Player p, DragonStateHandler h) {
        if (p == null || h == null || h.body() == null) return false;
        try {
            if (h.needsSkinRecompilation()) Diagnostics.INSTANCE.count(REQUEST, attribution(p, h, DragonModel.dynamicTexture(p, h, false)));
            if (!enabled(p)) return false;
            var normal = DragonModel.dynamicTexture(p, h, false);
            var entry = skins.get(normal);
            var content = OptimizationStage.VALUE >= 2 ? AppearanceCache.skin(h) : AppearanceKey.skin(h);
            if (entry != null && entry.content.equals(content) && valid(entry.normal) && valid(entry.glow)) {
                mark(h);
                touch(entry.normal);
                touch(entry.glow);
                Diagnostics.INSTANCE.count(HIT, attribution(p, h, normal));
                if (SoulRenderContext.sharedTexture(p)) Diagnostics.INSTANCE.count(SOUL_TEXTURE_HIT, "soul");
                return true;
            }
        } catch (RuntimeException | LinkageError e) {
            problem(p, "skin key", e);
        }
        return false;
    }

    public static void compiled(Player p, DragonStateHandler h, long nanos) {
        if (p == null || h == null || h.body() == null) return;
        try {
            var normal = DragonModel.dynamicTexture(p, h, false);
            var glow = DragonModel.dynamicTexture(p, h, true);
            if (!valid(normal) || !valid(glow)) {
                if (enabled(p)) fail("incomplete texture synthesis", new IllegalStateException(normal.toString()));
                return;
            }
            Diagnostics.INSTANCE.count(GENERATED, attribution(p, h, normal));
            Diagnostics.INSTANCE.nanos("SKIN_GENERATE", nanos, attribution(p, h, normal));
            if (!enabled(p)) return;
            var key = AppearanceCache.skin(h);
            skins.put(normal, new SkinEntry(key, normal, glow));
            mark(h);
            long bytes = (long) key.width() * key.height() * 4;
            register(normal, bytes);
            register(glow, bytes);
        } catch (RuntimeException | LinkageError e) {
            problem(p, "skin cache", e);
        }
    }

    private static void mark(DragonStateHandler h) {
        h.getSkinData().isCompiled.put(h.stageKey(), true);
        h.getSkinData().recompileSkin.put(h.stageKey(), false);
    }

    public static String attribution(Player p, DragonStateHandler h, ResourceLocation texture) {
        return Diagnostics.INSTANCE.detailed() ? p.getUUID() + "/" + h.stageKey().location() + "/" + texture : "skin";
    }

    public static boolean valid(ResourceLocation key) {
        return Minecraft.getInstance().getTextureManager().getTexture(key, null) != null;
    }

    public static void register(ResourceLocation key, long bytes) {
        if (textures.get(key, System.nanoTime()) == null) textures.put(key, key, bytes, System.nanoTime());
        touch(key);
        Diagnostics.INSTANCE.peak("TEXTURE_RESOURCE_BYTES", bytes());
    }

    /** Marks a cached texture as used this frame; it cannot be evicted until {@link #unpinAll()}. */
    public static void touch(ResourceLocation key) {
        var e = textures.get(key, System.nanoTime());
        if (e != null && enabled() && pinned.add(e)) e.leases++;
    }

    public static void unpinAll() {
        for (var e : pinned) e.leases--;
        pinned.clear();
    }

    public static long bytes() { return textures.bytes() + FramebufferPool.bytes(); }

    public static void maintenance() {
        AppearanceCache.maintenance();
        // Keep resources used by already queued draw commands alive until this frame finishes.
        if (deferredFailureCleanup) {
            clear();
            return;
        }
        if (enabled()) {
            textures.prune(System.nanoTime(), OptimizerConfig.retentionSeconds() * 1_000_000_000L,
                    OptimizerConfig.textureMiB() * 1048576L - FramebufferPool.bytes());
        }
    }

    private static void dispose(ResourceLocation key) {
        Minecraft.getInstance().getTextureManager().release(key);
        SkinRegistryAccessor.beloong$generated().remove(key);
        SkinRegistryAccessor.beloong$used().remove(key);
        ArmorRegistryAccessor.beloong$generated().remove(key);
        ArmorRegistryAccessor.beloong$used().remove(key);
        skins.entrySet().removeIf(e -> e.getValue().normal.equals(key) || e.getValue().glow.equals(key));
        Diagnostics.INSTANCE.count(RELEASE, key.toString());
    }

    public static void clear() {
        unpinAll();
        textures.clear();
        skins.clear();
        AppearanceCache.clear();
        FramebufferPool.clear();
        deferredFailureCleanup = false;
    }

    /** Disables texture optimization for everyone; used for GL/synthesis failures and changed DS internals. */
    public static void fail(String feature, Throwable e) {
        failed = true;
        deferredFailureCleanup = true;
        Diagnostics.INSTANCE.reason(feature, e.toString());
        RenderOptimizer.LOGGER.error("Texture optimization disabled: {}", feature, e);
    }

    /**
     * A data problem of one player sends only that player back to the original DS path. Linkage errors mean
     * DS itself changed shape, which affects everyone.
     */
    public static void problem(Player p, String feature, Throwable e) {
        if (p == null || e instanceof LinkageError) {
            fail(feature, e);
            return;
        }
        if (quarantined.add(p.getUUID())) {
            Diagnostics.INSTANCE.reason(feature + "/" + p.getUUID(), e.toString());
            RenderOptimizer.LOGGER.warn("Texture optimization disabled for player {} ({}): {}", p.getGameProfile().getName(), p.getUUID(), feature, e);
        }
    }
}
