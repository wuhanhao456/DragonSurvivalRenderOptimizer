package top.wu949.dsbr.optimizer.texture;

import by.dragonsurvivalteam.dragonsurvival.client.skin_editor_system.SkinLayer;
import by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateHandler;
import java.util.List;

public record AppearanceKey(String species, String body, String model, String stage, int width, int height,
                            boolean custom, boolean blank, boolean defaultSkin, boolean wings, List<Layer> layers) {
    public record Layer(String name, String part, int hue, int saturation, int brightness, boolean modified, boolean glow) {}
    public String digest() {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    public static AppearanceKey skin(DragonStateHandler handler) {
        var c = handler.getCurrentStageCustomization(); var size = handler.body().value().textureSize();
        var layers = java.util.Arrays.stream(SkinLayer.values()).map(layer -> {
            var settings = c.layerSettings.get(layer).get();
            return new Layer(layer.name(), settings.partKey, Float.floatToIntBits(settings.hue), Float.floatToIntBits(settings.saturation),
                    Float.floatToIntBits(settings.brightness), settings.isModified, settings.isGlowing);
        }).toList();
        return new AppearanceKey(handler.speciesId().toString(), handler.body().unwrapKey().map(k -> k.location().toString()).orElse(handler.body().value().toString()),
                handler.getModel().toString(), handler.stageKey().location().toString(), size.width(), size.height(),
                handler.getSkinData().renderCustomSkin, handler.getSkinData().blankSkin, c.defaultSkin, c.wings, layers);
    }
}
