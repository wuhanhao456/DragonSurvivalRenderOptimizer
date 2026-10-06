package top.wu949.dsbr.optimizer.texture;

import by.dragonsurvivalteam.dragonsurvival.client.skin_editor_system.SkinLayer;
import by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateHandler;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Actual fields are checked once per frame. Dirty notifications never force a new digest. */
public final class AppearanceCache {
    private static final SkinLayer[] LAYERS = SkinLayer.values();
    private static final Map<DragonStateHandler, Entry> entries = new IdentityHashMap<>();
    private static long frame;
    public static long frame() { return frame; }
    public static void nextFrame() { frame++; }
    public static long revision(DragonStateHandler h) { return h.getSkinData() instanceof SkinRevision v ? v.dsbr$revision() : 0; }
    private static final class Entry {
        Object species, body, model, stage;
        int width, height;
        boolean custom, blank, defaultSkin, wings;
        final String[] parts = new String[LAYERS.length];
        final int[] colours = new int[LAYERS.length * 3];
        final boolean[] flags = new boolean[LAYERS.length * 2];
        long checked = -1, revision, touched;
        AppearanceKey key;
        String digest;
        final ResourceLocation[] bases = new ResourceLocation[2], textures = new ResourceLocation[2];
        boolean same(DragonStateHandler h) {
            var c = h.getCurrentStageCustomization(); var size = h.body().value().textureSize();
            if (key == null || !Objects.equals(species, h.speciesId()) || !Objects.equals(body, h.body()) || !Objects.equals(model, h.getModel()) || !Objects.equals(stage, h.stageKey())
                || width != size.width() || height != size.height() || custom != h.getSkinData().renderCustomSkin || blank != h.getSkinData().blankSkin || defaultSkin != c.defaultSkin || wings != c.wings) return false;
            for (int i = 0; i < LAYERS.length; i++) {
                var s = c.layerSettings.get(LAYERS[i]).get();
                if (!Objects.equals(parts[i], s.partKey) || colours[i*3] != Float.floatToIntBits(s.hue) || colours[i*3+1] != Float.floatToIntBits(s.saturation) || colours[i*3+2] != Float.floatToIntBits(s.brightness)
                    || flags[i*2] != s.isModified || flags[i*2+1] != s.isGlowing) return false;
            }
            return true;
        }
        void update(DragonStateHandler h) {
            key = AppearanceKey.skin(h); digest = key.digest(); Arrays.fill(bases, null); Arrays.fill(textures, null);
            species = h.speciesId(); body = h.body(); model = h.getModel(); stage = h.stageKey();
            width = key.width(); height = key.height(); custom = key.custom(); blank = key.blank(); defaultSkin = key.defaultSkin(); wings = key.wings();
            for (int i = 0; i < LAYERS.length; i++) {
                var s = key.layers().get(i); parts[i] = s.part(); colours[i*3] = s.hue(); colours[i*3+1] = s.saturation(); colours[i*3+2] = s.brightness(); flags[i*2] = s.modified(); flags[i*2+1] = s.glow();
            }
        }
    }
    private static Entry entry(DragonStateHandler h) {
        var e = entries.get(h);
        if (e == null) {
            if (entries.size() >= 512) entries.remove(entries.entrySet().stream().min(Comparator.comparingLong(v -> v.getValue().touched)).orElseThrow().getKey());
            e = new Entry(); entries.put(h, e);
        }
        long rev = revision(h); e.touched = System.nanoTime();
        if (e.checked != frame || e.revision != rev) {
            long start = System.nanoTime(); if (!e.same(h)) e.update(h);
            e.checked = frame; e.revision = rev; Diagnostics.INSTANCE.nanos("SKIN_CHECK", System.nanoTime() - start);
        }
        return e;
    }
    public static AppearanceKey skin(DragonStateHandler h) { return entry(h).key; }
    public static ResourceLocation texture(DragonStateHandler h, ResourceLocation base, boolean glow) {
        var e = entry(h); int i = glow ? 1 : 0;
        if (!base.equals(e.bases[i])) { e.bases[i] = base; e.textures[i] = base.withSuffix("_bro_" + e.digest); }
        return e.textures[i];
    }
    public static void maintenance() { long cutoff = System.nanoTime() - 30_000_000_000L; entries.values().removeIf(e -> e.touched < cutoff); ArmorAppearance.maintenance(cutoff); }
    public static void clear() { entries.clear(); ArmorAppearance.clear(); }
}
