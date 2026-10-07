package top.wu949.dsbr.optimizer;

import org.junit.jupiter.api.Test;
import top.wu949.dsbr.optimizer.gpu.CpuVertexScratch;
import static org.junit.jupiter.api.Assertions.*;

class CpuVertexScratchTest {
    @Test void reentrantConsumersCannotOverwriteCallerAndExceptionReleases() {
        var pool = new CpuVertexScratch(); var outer = pool.acquire(); outer.vertex.set(1, 2, 3, 1);
        var inner = pool.acquire(); inner.vertex.set(8, 9, 10, 1);
        assertNotSame(outer, inner); assertEquals(1, outer.vertex.x());
        assertThrows(IllegalStateException.class, () -> pool.release(outer));
        pool.release(inner); pool.release(outer);
        assertSame(outer, pool.acquire()); pool.release(outer);
        assertEquals(512, pool.bytes()); pool.clear(); assertEquals(0, pool.bytes());
    }
    @Test void deepRecursionIsTrimmedOnlyAfterAllOwnersReturn() {
        var pool = new CpuVertexScratch(); var borrowed = new CpuVertexScratch.Slot[40];
        for (int i = 0; i < 40; i++) borrowed[i] = pool.acquire();
        assertThrows(IllegalStateException.class, pool::clear);
        for (int i = 39; i >= 0; i--) pool.release(borrowed[i]);
        assertEquals(16 * 256, pool.bytes());
    }
}
