package top.wu949.dsbr.optimizer.texture;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import top.wu949.dsbr.optimizer.OptimizerConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Small render-thread pool; borrowed targets cannot be recycled by nested synthesis. DS sets the clear colour
 * and clears every target before drawing, so a pooled target needs no extra clear.
 */
public final class FramebufferPool {
    private static final Set<RenderTarget> owned = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Deque<TextureTarget> free = new ArrayDeque<>();
    /** Targets handed out and not yet released, in acquisition order. */
    private static final List<RenderTarget> outstanding = new ArrayList<>();
    private static long ownedBytes;

    public static TextureTarget acquire(int w, int h, boolean depth, boolean osx) {
        TextureTarget target = null;
        if (TextureCache.enabled()) {
            for (var i = free.iterator(); i.hasNext(); ) {
                var f = i.next();
                if (f.width == w && f.height == h && f.useDepth == depth) {
                    i.remove();
                    target = f;
                    break;
                }
            }
            if (target == null) {
                target = new TextureTarget(w, h, depth, osx);
                owned.add(target);
                ownedBytes += size(target);
            }
        } else {
            target = new TextureTarget(w, h, depth, osx);
        }
        outstanding.add(target);
        return target;
    }

    public static void release(RenderTarget target) {
        outstanding.remove(target);
        if (!owned.contains(target)) {
            target.destroyBuffers();
            return;
        }
        if (!TextureCache.enabled()) {
            destroyOwned(target);
            return;
        }
        free.addLast((TextureTarget) target);
        long cap = Math.min(8 * 1048576L, OptimizerConfig.textureMiB() * 1048576L / 8);
        while (ownedBytes > cap && !free.isEmpty()) destroyOwned(free.removeFirst());
    }

    /** Position to pass to {@link #abandon(int)} if the synthesis that follows throws. */
    public static int mark() { return outstanding.size(); }

    /** Destroys targets borrowed after {@code mark} that a failed synthesis never released. */
    public static void abandon(int mark) {
        while (outstanding.size() > mark) {
            var target = outstanding.removeLast();
            if (owned.contains(target)) destroyOwned(target);
            else target.destroyBuffers();
        }
    }

    public static long bytes() { return ownedBytes; }

    public static void clear() {
        while (!free.isEmpty()) destroyOwned(free.removeFirst());
    }

    private static void destroyOwned(RenderTarget target) {
        if (owned.remove(target)) ownedBytes -= size(target);
        target.destroyBuffers();
    }

    private static long size(RenderTarget t) { return (long) t.width * t.height * (t.useDepth ? 8 : 4); }
}
