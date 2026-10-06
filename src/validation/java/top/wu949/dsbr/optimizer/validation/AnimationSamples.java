package top.wu949.dsbr.optimizer.validation;

import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;
import by.dragonsurvivalteam.dragonsurvival.client.util.FakeClientPlayer;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.renderer.GeoRenderer;
import java.util.*;

/** Sample the real Gecko controller and model after animation evaluation, including each soul. */
public final class AnimationSamples {
    private static final List<Map<String, Object>> samples = new ArrayList<>();
    private static final Map<String, Long> last = new HashMap<>();
    public static String label = "";
    public static void begin(String phase) { label = phase; samples.clear(); last.clear(); }
    public static void capture(GeoRenderer<?> renderer, DragonEntity dragon, BakedGeoModel model) {
        if (label.isEmpty() || dragon.getPlayer() == null) return;
        var player = dragon.getPlayer();
        String actor = player instanceof FakeClientPlayer fake ? "soul-" + fake.number : "player";
        long window = System.nanoTime() / 50_000_000L;
        if (Objects.equals(last.put(actor, window), window)) return;
        long hash = 1;
        var pending = new ArrayDeque<>(model.topLevelBones());
        while (!pending.isEmpty()) {
            var bone = pending.removeFirst(); pending.addAll(bone.getChildBones());
            for (float value : new float[]{bone.getRotX(),bone.getRotY(),bone.getRotZ(),bone.getPosX(),bone.getPosY(),bone.getPosZ(),bone.getScaleX(),bone.getScaleY(),bone.getScaleZ()}) {
                if (!Float.isFinite(value)) throw new IllegalStateException("Non-finite animated bone " + bone.getName());
                hash = hash * 31 + Float.floatToIntBits(value);
            }
        }
        var row = new LinkedHashMap<String,Object>();
        row.put("phase", label); row.put("actor", actor); row.put("tick", dragon.getTick(dragon)); row.put("poseHash", hash);
        var controllers = new TreeMap<String,Object>();
        if (player instanceof FakeClientPlayer fake && fake.animationController != null) {
            var c = fake.animationController;
            controllers.put("soul", Map.of("animation", c.getCurrentAnimation() == null ? "" : c.getCurrentAnimation().animation().name(), "speed", c.getAnimationSpeed()));
        } else {
            dragon.getAnimatableInstanceCache().getManagerForId(dragon.getId()).getAnimationControllers().forEach((name,c) ->
                controllers.put(name, Map.of("animation", c.getCurrentAnimation() == null ? "" : c.getCurrentAnimation().animation().name(), "speed", c.getAnimationSpeed())));
        }
        row.put("controllers", controllers);
        if (samples.size() < 4096) samples.add(row);
    }
    public static List<Map<String,Object>> snapshot() { label = ""; return new ArrayList<>(samples); }
}
