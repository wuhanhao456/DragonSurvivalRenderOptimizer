package top.wu949.dsbr.optimizer.lowend;

import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;
import by.dragonsurvivalteam.dragonsurvival.common.codecs.StageResources;
import by.dragonsurvivalteam.dragonsurvival.registry.dragon.body.DragonBody;
import by.dragonsurvivalteam.dragonsurvival.client.skin_editor_system.SkinLayer;
import by.dragonsurvivalteam.dragonsurvival.client.skin_editor_system.loader.*;
import by.dragonsurvivalteam.dragonsurvival.client.gui.screens.dragon_editor.DragonEditorScreen;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import top.wu949.dsbr.optimizer.OptimizerConfig;
import top.wu949.dsbr.optimizer.compat.*;
import top.wu949.dsbr.optimizer.texture.TextureCache;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import java.util.*;

public final class LowEndSupport {
    private record SkinKey(String body, String model, String species, String stage) {}
    private static final Map<SkinKey, Optional<ResourceLocation>> skins = new LinkedHashMap<>();
    public static boolean enabled() { return OptimizerMixinPlugin.lowEndCompatible && OptimizerConfig.LOW_END.get(); }
    public static boolean editor() { return Minecraft.getInstance().screen instanceof DragonEditorScreen; }
    public static ResourceLocation skin(DragonEntity dragon) {
        if (editor()) return null;
        var p = dragon.overrideUUIDWithLocalPlayerForTextureFetch ? Minecraft.getInstance().player : dragon.getPlayer();
        if (p == null) return null;
        var h = TextureCache.handler(p); if (h == null || h.body() == null) return null;
        var k = new SkinKey(h.body().getKey().location().toString(), h.getModel().toString(), h.speciesId().toString(), h.stageKey().location().toString());
        var found = skins.get(k);
        if (found == null) {
            ResourceLocation texture = null;
            if (h.getModel().equals(DragonBody.DEFAULT_MODEL)) texture = StageResources.getDefaultSkin(h.species(), h.stageKey(), false);
            else {
                String key = DefaultPartLoader.getDefaultPartKey(h.speciesKey(), h.stageKey(), Either.left(h.body().getKey()), SkinLayer.BASE);
                if (key == null || key.equals(DefaultPartLoader.NO_PART)) key = DefaultPartLoader.getDefaultPartKey(h.speciesKey(), h.stageKey(), Either.right(h.getModel()), SkinLayer.BASE);
                var part = DragonPartLoader.getDragonPart(SkinLayer.BASE, h.speciesKey(), h.body(), key);
                if (part != null) texture = part.texture();
            }
            if (texture != null && Minecraft.getInstance().getResourceManager().getResource(texture).isEmpty()) texture = null;
            if (skins.size() >= 64) skins.remove(skins.keySet().iterator().next());
            skins.put(k, found = Optional.ofNullable(texture));
            if (texture == null) Diagnostics.INSTANCE.reason("low-end skin/" + k.model(), "No matching static base material; original cached skin retained");
        }
        if (found.isPresent()) Diagnostics.INSTANCE.count(Diagnostics.Counter.LOW_END_SKIN_BASE, "skin");
        return found.orElse(null);
    }
    public static void clear() { skins.clear(); LowEndAnimations.clear(); LowEndLayers.clear(); }
    private LowEndSupport() {}
}
