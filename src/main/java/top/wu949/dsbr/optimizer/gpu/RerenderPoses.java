package top.wu949.dsbr.optimizer.gpu;

import org.joml.*;
import org.lwjgl.system.MemoryUtil;
import software.bernie.geckolib.cache.object.GeoBone;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Render-thread only. DS glow and armor layers re-render a dragon with {@code isReRender=true} right after its base
 * pass; GeckoLib evaluates no animation and runs no per-bone layers then, so the traversal rebuilds the same bone
 * matrices. A re-render reuses the base pass's matrices only when the renderer, model, root matrices and every
 * bone's transform and visibility flags are bit-identical; anything else traverses as before.
 *
 * <p>Baked models and their bones (including GeckoLib's tracked model/local/world matrices) are shared by every
 * dragon with that model. A template is therefore only valid while no other animatable has rendered the model since
 * its base pass began: any such render, including DS's bone-only calculation pass, drops the model's templates.
 */
public final class RerenderPoses implements AutoCloseable {
    /** Why a re-render could not reuse; {@code null} means it did. */
    public enum Miss { NO_TEMPLATE, RENDERER, ROOT, BONES }
    static final int FLOATS = 12, MAX_TEMPLATES = 256, MAX_IDLE = 64;
    public static final class Template {
        Object renderer, model, animatable; StaticMesh mesh; long generation;
        final Matrix4f root = new Matrix4f(); final Matrix3f rootNormal = new Matrix3f();
        float[] state = new float[0]; byte[] flags = new byte[0];
        ByteBuffer data; boolean[] visible;
        void size(int bones) {
            if (visible != null && visible.length == bones) return;
            if (data != null) MemoryUtil.memFree(data);
            data = MemoryUtil.memAlloc(bones * PoseSnapshot.STRIDE); visible = new boolean[bones];
            state = new float[bones * FLOATS]; flags = new byte[bones];
        }
        long bytes() { return visible == null ? 0 : estimate(visible.length); }
        void close() { if (data != null) MemoryUtil.memFree(data); data = null; visible = null; }
    }
    private final IdentityHashMap<Object, Template> live = new IdentityHashMap<>();
    private final Set<Template> pending = Collections.newSetFromMap(new IdentityHashMap<>());
    private final IdentityHashMap<Object, Object> owners = new IdentityHashMap<>();
    /** Per model, how often a different animatable took it over; catches A -> B -> A nesting inside one base pass. */
    private final IdentityHashMap<Object, long[]> handovers = new IdentityHashMap<>();
    private final ArrayDeque<Template> idle = new ArrayDeque<>();
    private float[] state = new float[0];
    private byte[] flags = new byte[0];
    private long bytes;

    public static long estimate(int bones) { return 256L + bones * (PoseSnapshot.STRIDE + FLOATS * 4L + 2); }

    /** Reads every value GeckoLib's traversal derives a bone matrix or its visibility from. */
    static void state(List<GeoBone> bones, float[] out, byte[] flags) {
        for (int i = 0, at = 0; i < bones.size(); i++, at += FLOATS) {
            var b = bones.get(i);
            out[at] = b.getRotX(); out[at + 1] = b.getRotY(); out[at + 2] = b.getRotZ();
            out[at + 3] = b.getPosX(); out[at + 4] = b.getPosY(); out[at + 5] = b.getPosZ();
            out[at + 6] = b.getScaleX(); out[at + 7] = b.getScaleY(); out[at + 8] = b.getScaleZ();
            out[at + 9] = b.getPivotX(); out[at + 10] = b.getPivotY(); out[at + 11] = b.getPivotZ();
            flags[i] = (byte)((b.isHidden() ? 1 : 0) | (b.isHidingChildren() ? 2 : 0));
        }
    }
    private boolean unchanged(StaticMesh mesh, float[] expected, byte[] expectedFlags) {
        int bones = mesh.order.size();
        if (state.length < bones * FLOATS) { state = new float[bones * FLOATS]; flags = new byte[bones]; }
        state(mesh.order, state, flags);
        return Arrays.equals(state, 0, bones * FLOATS, expected, 0, bones * FLOATS) && Arrays.equals(flags, 0, bones, expectedFlags, 0, bones);
    }

    /** Every render of a shared model, captured or not, must be reported before it can change the model's bones. */
    public void touch(Object model, Object animatable) {
        var previous = owners.put(model, animatable);
        if (previous == null || previous == animatable) return;
        handovers.computeIfAbsent(model, k -> new long[1])[0]++;
        for (var it = live.entrySet().iterator(); it.hasNext();) {
            var e = it.next();
            if (e.getValue().model == model && e.getKey() != animatable) { recycle(e.getValue()); it.remove(); }
        }
    }

    /** Starts a base-pass template: the bone state the traversal will read, recorded before it runs. */
    public Template prepare(Object model, Object animatable, StaticMesh mesh) {
        if (live.size() + pending.size() >= MAX_TEMPLATES) return null;
        var t = idle.isEmpty() ? new Template() : idle.removeFirst();
        bytes -= t.bytes(); t.size(mesh.order.size()); bytes += t.bytes();
        t.model = model; t.animatable = animatable; t.mesh = mesh; t.generation = handovers(model);
        state(mesh.order, t.state, t.flags);
        pending.add(t); return t;
    }

    /**
     * Completes a template after a clean base pass. It is dropped when another animatable rendered the model meanwhile
     * (nested render) or the bones changed during the traversal.
     */
    public boolean commit(Template t, Object renderer, Matrix4fc root, Matrix3fc rootNormal, PoseSnapshot pose) {
        if (!pending.remove(t)) return false;
        if (owners.get(t.model) != t.animatable || handovers(t.model) != t.generation || !unchanged(t.mesh, t.state, t.flags) || pose.visible.length != t.visible.length) { recycle(t); return false; }
        t.renderer = renderer; t.root.set(root); t.rootNormal.set(rootNormal);
        MemoryUtil.memCopy(MemoryUtil.memAddress0(pose.data), MemoryUtil.memAddress0(t.data), t.data.capacity());
        System.arraycopy(pose.visible, 0, t.visible, 0, t.visible.length);
        var old = live.put(t.animatable, t);
        if (old != null && old != t) recycle(old);
        return true;
    }

    private long handovers(Object model) { var n = handovers.get(model); return n == null ? 0 : n[0]; }

    public void discard(Template t) { if (pending.remove(t)) recycle(t); }

    /** On a match fills {@code target} with the base pass's bones and this pass's colour/light/overlay. */
    public Miss reuse(Object renderer, Object animatable, Object model, StaticMesh mesh, Matrix4fc root, Matrix3fc rootNormal,
                      PoseSnapshot target, int colour, int light, int overlay) {
        var t = live.get(animatable);
        if (t == null) return Miss.NO_TEMPLATE;
        if (t.renderer != renderer || t.model != model || t.mesh != mesh || t.visible.length != target.visible.length) return Miss.RENDERER;
        if (!t.root.equals(root) || !t.rootNormal.equals(rootNormal)) return Miss.ROOT;
        if (!unchanged(mesh, t.state, t.flags)) return Miss.BONES;
        target.copyFrom(t.data, t.visible, colour, light, overlay);
        return null;
    }

    public int size() { return live.size(); }
    public long bytes() { return bytes; }

    private void recycle(Template t) {
        t.renderer = t.model = t.animatable = null; t.mesh = null;
        if (t.data != null && idle.size() < MAX_IDLE) idle.addLast(t); else { bytes -= t.bytes(); t.close(); }
    }

    /** Templates are valid within one world render only. Pending base passes keep their slot until they end. */
    public void clear() {
        live.values().forEach(this::recycle); live.clear(); owners.clear(); handovers.clear();
    }

    @Override public void close() {
        clear();
        for (var t : pending) { bytes -= t.bytes(); t.close(); }
        pending.clear();
        for (var t : idle) { bytes -= t.bytes(); t.close(); }
        idle.clear(); bytes = 0;
    }
}
