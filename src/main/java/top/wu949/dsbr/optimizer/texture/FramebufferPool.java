package top.wu949.dsbr.optimizer.texture;

import com.mojang.blaze3d.pipeline.*;
import java.util.*;

/** Small render-thread pool; borrowed targets cannot be recycled by nested synthesis. */
public final class FramebufferPool {
    private static final Set<RenderTarget> owned = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Deque<TextureTarget> free = new ArrayDeque<>();
    public static TextureTarget acquire(int w, int h, boolean depth, boolean osx) {
        if (!TextureCache.enabled()) return new TextureTarget(w, h, depth, osx);
        for (var i = free.iterator(); i.hasNext();) { var f = i.next(); if (f.width == w && f.height == h && f.useDepth == depth) { i.remove(); return f; } }
        var f = new TextureTarget(w, h, depth, osx); owned.add(f); return f;
    }
    public static void release(RenderTarget target) {
        if (!owned.contains(target)) { target.destroyBuffers(); return; }
        if (!TextureCache.enabled()) { owned.remove(target); target.destroyBuffers(); return; }
        free.addLast((TextureTarget)target);
        while (bytes() > Math.min(8 * 1048576L, top.wu949.dsbr.optimizer.OptimizerConfig.TEXTURE_MIB.get() * 1048576L / 8) && !free.isEmpty()) {
            var f = free.removeFirst(); owned.remove(f); f.destroyBuffers();
        }
    }
    public static long bytes() { return owned.stream().mapToLong(t -> (long)t.width * t.height * (t.useDepth ? 8 : 4)).sum(); }
    public static void clear() { free.forEach(RenderTarget::destroyBuffers); owned.removeAll(free); free.clear(); }
}
