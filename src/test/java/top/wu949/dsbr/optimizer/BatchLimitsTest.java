package top.wu949.dsbr.optimizer;

import org.junit.jupiter.api.Test;
import top.wu949.dsbr.optimizer.gpu.BatchLimits;
import static org.junit.jupiter.api.Assertions.*;

class BatchLimitsTest {
    @Test void memoryAndGlLimitsSplitExpandedInstances() {
        assertEquals(2, BatchLimits.instances(10, 2, 36, 4032, 100000, 100));
        assertEquals(1, BatchLimits.instances(10, 2, 36, 100000, 1440, 100));
        assertEquals(6, BatchLimits.instances(10, 2, 36, 100000, 100000, 1));
        assertEquals(0, BatchLimits.instances(65, 2, 36, 100000, 100000, 1));
        assertEquals(0, BatchLimits.instances(10, 2, 36, 2015, 100000, 100));
    }
    @Test void invalidOrOverflowingGeometryCannotBeDispatched() {
        assertEquals(0, BatchLimits.instances(0, 1, 54, 100000, 100000, 100));
        assertEquals(0, BatchLimits.instances(Integer.MAX_VALUE, 1, 54, Long.MAX_VALUE, Long.MAX_VALUE, 65535));
    }
}
