package top.wu949.dsbr.optimizer;
import top.wu949.dsbr.optimizer.gpu.PosePool;
import com.mojang.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PosePoolTest {
    @Test void commandsHaveIndependentLeasesAndHiddenBonesAreReset() {
        var pool = new PosePool(); var a = pool.acquire(2); var b = pool.acquire(2);
        assertNotSame(a, b); var stack = new PoseStack(); stack.translate(5, 0, 0);
        a.bone(0, stack.last(), 1, 2, 3); assertFalse(b.anyVisible());
        assertThrows(IllegalStateException.class, pool::clear);
        pool.release(a); var reused = pool.acquire(2); assertSame(a, reused);
        assertFalse(reused.anyVisible()); assertFalse(reused.visible[0]); assertEquals(0, reused.data.getInt(140));
        pool.release(reused); pool.release(b); assertEquals(a.retainedBytes() + b.retainedBytes(), pool.idleBytes());
        assertThrows(IllegalStateException.class, () -> pool.release(b));
        pool.prune(System.nanoTime(), 0); assertEquals(0, pool.idleBytes()); pool.clear();
    }
}
