package top.wu949.dsbr.optimizer.animation;

import java.lang.invoke.*;
import java.util.*;
import java.security.MessageDigest;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import top.wu949.dsbr.optimizer.OptimizerConfig;
import top.wu949.dsbr.optimizer.compat.OptionalNpcAdapter;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import top.wu949.dsbr.optimizer.gpu.GpuDispatcher;
import top.wu949.dsbr.optimizer.texture.AppearanceCache;

/** Reuses only reviewed, inactive-emote NPC controllers. Unknown/player/eventful paths run normally. */
public final class FrameAnimations {
    private record Inputs(MethodHandle state, MethodHandle emote) {}
    private static final ClassValue<Inputs> inputs = new ClassValue<>() {
        @Override protected Inputs computeValue(Class<?> type) {
            try {
                var lookup = MethodHandles.publicLookup(); var signature = MethodType.methodType(Object.class, Object.class);
                return new Inputs(lookup.unreflect(type.getMethod("state")).asType(signature), lookup.unreflect(type.getMethod("emote")).asType(signature));
            } catch (ReflectiveOperationException e) { return new Inputs(null, null); }
        }
    };
    private static final IdentityHashMap<AnimatableManager<?>, Entry> entries = new IdentityHashMap<>();
    private static final IdentityHashMap<GeoModel<?>, Boolean> auditedResources = new IdentityHashMap<>();
    private static long bytes;
    private static final class Entry {
        final GeoAnimatable actor;
        final GeoModel<?> model;
        final AnimationProcessor<?> processor;
        final LocalBonePose pose;
        final AnimationController<?>[] controllers;
        final long[] revisions;
        long frame = -1, touched;
        double time, tick, speed, baseSpeed;
        float partial, swing, amount;
        boolean moving;
        EntityModelData modelData;
        Object state;
        Entry(GeoAnimatable actor, GeoModel<?> model, AnimationProcessor<?> processor, AnimatableManager<?> manager) {
            this.actor = actor; this.model = model; this.processor = processor; pose = new LocalBonePose(processor.getRegisteredBones());
            controllers = manager.getAnimationControllers().values().toArray(AnimationController<?>[]::new); revisions = new long[controllers.length];
        }
        long bytes() { return pose.bytes() + 512 + controllers.length * 24L; }
        boolean controllersMatch(AnimatableManager<?> manager) {
            if (controllers.length != manager.getAnimationControllers().size()) return false;
            int i = 0; for (var c : manager.getAnimationControllers().values()) if (c != controllers[i] || ((ControllerRevision)c).dsbr$revision() != revisions[i++]) return false;
            return true;
        }
    }
    public static boolean eligible(GeoAnimatable actor, GeoModel<?> model, AnimatableManager<?> manager, AnimationState<?> animation) {
        if (!OptimizerConfig.FRAME_ANIMATIONS.get() || !GpuDispatcher.enabled() || !OptionalNpcAdapter.compatible) return false;
        String name = actor.getClass().getName(), modelName = model.getClass().getName();
        if (!(name.equals("com.zonlong.beloong.entity.MoEntity") && modelName.equals("com.zonlong.beloong.client.model.MoModel")
                || name.equals("com.zonlong.beloong.entity.DihuangLoongEntity") && modelName.equals("com.zonlong.beloong.client.model.DihuangLoongModel"))) return false;
        if (!auditedResources.computeIfAbsent(model, m -> resourceVerified(actor, m))) return false;
        if (manager.getClass() != AnimatableManager.class || manager.isFirstTick() || manager.getAnimationControllers().size() != 3 || model.getAnimationProcessor().reloadAnimations) return false;
        for (var c : manager.getAnimationControllers().values()) {
            if (c.getClass() != AnimationController.class || !(c instanceof ControllerRevision r) || r.dsbr$eventful() || c.getTriggeredAnimation() != null
                || !c.getStateHandler().getClass().getName().startsWith("com.zonlong.beloong.entity.NpcEntity$$Lambda")) return false;
        }
        for (var key : animation.getExtraData().keySet()) if (key != DataTickets.TICK && key != DataTickets.ENTITY && key != DataTickets.ENTITY_MODEL_DATA) return false;
        return animation.getData(DataTickets.TICK) != null && animation.getData(DataTickets.ENTITY) == actor && actor instanceof LivingEntity;
    }
    public static boolean restore(GeoAnimatable actor, GeoModel<?> model, AnimationProcessor<?> processor, AnimatableManager<?> manager, double time, AnimationState<?> animation) {
        if (!eligible(actor, model, manager, animation)) { if (entries.remove(manager) != null) recount(); return false; }
        try {
            var methods = inputs.get(actor.getClass()); if (methods.emote == null || !((String)(Object)methods.emote.invokeExact((Object)actor)).isEmpty()) return false;
            var e = entries.get(manager); var entity = (LivingEntity)actor;
            Object state = methods.state.invokeExact((Object)actor);
            if (e != null && e.frame == AppearanceCache.frame() && e.actor == actor && e.model == model && e.processor == processor && e.time == time
                && e.tick == animation.getData(DataTickets.TICK) && e.partial == animation.getPartialTick() && e.swing == animation.getLimbSwing()
                && e.amount == animation.getLimbSwingAmount() && e.moving == animation.isMoving() && e.state == state
                && e.speed == entity.getAttributeValue(Attributes.MOVEMENT_SPEED) && e.baseSpeed == entity.getAttributeBaseValue(Attributes.MOVEMENT_SPEED)
                && Objects.equals(e.modelData, animation.getData(DataTickets.ENTITY_MODEL_DATA)) && e.controllersMatch(manager) && e.pose.matches(processor.getRegisteredBones())) {
                e.pose.restore(); e.touched = AppearanceCache.frame(); Diagnostics.INSTANCE.count(Diagnostics.Counter.FRAME_ANIMATION_HIT, "animation"); return true;
            }
        } catch (Throwable error) { Diagnostics.INSTANCE.reason("frame animation", error.getClass().getSimpleName()); }
        Diagnostics.INSTANCE.count(Diagnostics.Counter.FRAME_ANIMATION_MISS, "animation"); return false;
    }
    public static void capture(GeoAnimatable actor, GeoModel<?> model, AnimationProcessor<?> processor, AnimatableManager<?> manager, double time, AnimationState<?> animation) {
        if (!eligible(actor, model, manager, animation)) return;
        try {
            var methods = inputs.get(actor.getClass()); if (methods.emote == null || !((String)(Object)methods.emote.invokeExact((Object)actor)).isEmpty()) return;
            var e = entries.get(manager);
            if (e == null || e.actor != actor || e.model != model || e.processor != processor || !e.pose.matches(processor.getRegisteredBones()) || e.controllers.length != manager.getAnimationControllers().size()) {
                long estimate = 640L + processor.getRegisteredBones().size()*64L + manager.getAnimationControllers().size()*24L;
                if (entries.size() >= 128 || GpuDispatcher.bytes() + estimate > OptimizerConfig.MESH_MIB.get() * 1048576L) return;
                var fresh = new Entry(actor, model, processor, manager);
                if (entries.size() >= 128 || GpuDispatcher.bytes() + fresh.bytes() > OptimizerConfig.MESH_MIB.get() * 1048576L) return;
                entries.put(manager, e = fresh); recount();
            }
            e.pose.capture(); e.frame = e.touched = AppearanceCache.frame(); e.time = time; e.tick = animation.getData(DataTickets.TICK);
            e.partial = animation.getPartialTick(); e.swing = animation.getLimbSwing(); e.amount = animation.getLimbSwingAmount(); e.moving = animation.isMoving();
            e.modelData = animation.getData(DataTickets.ENTITY_MODEL_DATA); e.state = methods.state.invokeExact((Object)actor);
            var entity = (LivingEntity)actor; e.speed = entity.getAttributeValue(Attributes.MOVEMENT_SPEED); e.baseSpeed = entity.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
            for (int i = 0; i < e.controllers.length; i++) e.revisions[i] = ((ControllerRevision)e.controllers[i]).dsbr$revision();
        } catch (Throwable error) { entries.remove(manager); recount(); Diagnostics.INSTANCE.reason("frame animation", error.getClass().getSimpleName()); }
    }
    private static void recount() { bytes = 0; for (var e : entries.values()) bytes += e.bytes(); }
    public static long bytes() { return bytes; }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean resourceVerified(GeoAnimatable actor, GeoModel<?> model) {
        String digest = actor.getClass().getName().endsWith("MoEntity") ? "6072afbcae37bbdce18db5aa8ea86ae747915e9ec4231372e3d054a30f70c916" : "6280bf2452cf73efe7eceaa7c60d775c6f0d87aa42bad02168b2a3af1c3a50be";
        try (var stream = Minecraft.getInstance().getResourceManager().getResourceOrThrow(((GeoModel)model).getAnimationResource(actor)).open()) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())).equals(digest);
        } catch (Exception | LinkageError error) { return false; }
    }
    public static void maintenance() { if (entries.isEmpty()) return; entries.values().removeIf(e -> AppearanceCache.frame() - e.touched > 120); recount(); }
    public static void clear() { entries.clear(); auditedResources.clear(); bytes = 0; }
    private FrameAnimations() {}
}
