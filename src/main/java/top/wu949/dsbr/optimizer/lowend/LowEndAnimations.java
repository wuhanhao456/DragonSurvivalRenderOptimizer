package top.wu949.dsbr.optimizer.lowend;

import by.dragonsurvivalteam.dragonsurvival.client.models.DragonModel;
import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import top.wu949.dsbr.optimizer.OptimizerConfig;
import top.wu949.dsbr.optimizer.compat.OptionalNpcAdapter;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import top.wu949.dsbr.optimizer.gpu.GpuDispatcher;
import java.util.*;

/** Render-thread only. Values never retain the weak actor keys or animation managers. */
public final class LowEndAnimations {
    private static final Map<GeoAnimatable, Entry> entries = new WeakHashMap<>();
    private static final Deque<Scope> scopes = new ArrayDeque<>();
    private static long bytes;
    private static final class Entry {
        final GeoModel<?> model; final LocalBonePose pose;
        long tick = Long.MIN_VALUE, touched; boolean captured;
        Entry(GeoModel<?> model) { this.model = model; pose = new LocalBonePose(model.getAnimationProcessor().getRegisteredBones()); }
        long bytes() { return pose.bytes() + 96; }
    }
    public static final class Scope {
        final Entry entry; final boolean update; final long tick, start;
        Scope(Entry entry, boolean update, long tick) { this.entry=entry; this.update=update; this.tick=tick; start=System.nanoTime(); }
    }
    public static boolean eligible(GeoAnimatable actor, GeoModel<?> model) {
        if (!LowEndSupport.enabled() || LowEndSupport.editor()) return false;
        if (actor instanceof DragonEntity && model.getClass() == DragonModel.class) return true;
        if (!OptionalNpcAdapter.compatible) return false;
        String a=actor.getClass().getName(), m=model.getClass().getName();
        return a.equals("com.zonlong.beloong.entity.MoEntity") && m.equals("com.zonlong.beloong.client.model.MoModel")
            || a.equals("com.zonlong.beloong.entity.DihuangLoongEntity") && m.equals("com.zonlong.beloong.client.model.DihuangLoongModel");
    }
    public static Scope enter(GeoAnimatable actor, GeoModel<?> model) {
        Entry e=null; boolean update=true; long tick=0;
        if (eligible(actor, model)) {
            var mc=Minecraft.getInstance(); tick=mc.level==null?0:mc.level.getGameTime();
            e=entries.get(actor);
            if (e!=null && (e.model!=model || !e.pose.matches(model.getAnimationProcessor().getRegisteredBones()))) { entries.remove(actor); recount(); e=null; }
            if (e==null) {
                var fresh=new Entry(model);
                if (entries.size()<128 && bytes+fresh.bytes()<=4*1048576L && GpuDispatcher.bytes()+fresh.bytes()<=OptimizerConfig.meshMiB()*1048576L) { entries.put(actor,e=fresh); recount(); }
                else Diagnostics.INSTANCE.count(Diagnostics.Counter.LOW_END_ANIMATION_FALLBACK,"animation");
            }
            if(e!=null) {
                Entity position=actor instanceof DragonEntity d?d.getPlayer():actor instanceof Entity entity?entity:null;
                boolean near=actor instanceof DragonEntity d && d.isInInventory || position==mc.player || position!=null && position.position().distanceToSqr(mc.gameRenderer.getMainCamera().getPosition())<=32*32;
                update=AnimationCadence.due(tick,e.tick,near,e.captured); e.touched=tick;
                if(e.captured) { if(actor instanceof DragonEntity) e.pose.restoreKeepingVisibility("Neck"); else e.pose.restore(); }
            }
        }
        var scope=new Scope(e,update,tick);scopes.push(scope);return scope;
    }
    public static boolean holding() { var s=scopes.peek();return s!=null && s.entry!=null && !s.update; }
    public static void exit(Scope s, boolean completed) {
        if(scopes.pop()!=s) throw new IllegalStateException("Unbalanced low-end animation scope");
        if(s.entry==null || !completed) return;
        if(s.update) { s.entry.pose.capture();s.entry.captured=true;s.entry.tick=s.tick;Diagnostics.INSTANCE.count(Diagnostics.Counter.LOW_END_ANIMATION_UPDATE,"animation");Diagnostics.INSTANCE.nanos("LOW_END_ANIMATION_EVALUATE",System.nanoTime()-s.start); }
        else Diagnostics.INSTANCE.count(Diagnostics.Counter.LOW_END_ANIMATION_REUSED,"animation");
        Diagnostics.INSTANCE.peak("LOW_END_POSE_BYTES",bytes);
    }
    private static void recount() { bytes=entries.values().stream().mapToLong(Entry::bytes).sum(); }
    public static void maintenance() {
        if(entries.isEmpty()) { bytes=0;return; }
        var level=Minecraft.getInstance().level;
        if(level==null){clear();return;}
        long tick=level.getGameTime(); entries.values().removeIf(e->tick<e.touched || tick-e.touched>200);recount();
    }
    public static long bytes() { return bytes; }
    public static void clear() { entries.clear();bytes=0; }
    private LowEndAnimations() {}
}
