package top.wu949.dsbr.optimizer;

import org.junit.jupiter.api.Test;
import top.wu949.dsbr.optimizer.gpu.DeferredBatches;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DeferredBatchesTest {
    private record WrappedState(String state) {}
    @Test void equivalentIrisWrappersFlushEveryPoseInSubmissionOrder() {
        var batches = new DeferredBatches<Object, WrappedState, String>(); var source = new Object();
        var first = new WrappedState("armor"); var second = new WrappedState("armor"); assertNotSame(first, second);
        batches.add(source, first, "player 1 pose"); batches.add(source, second, "player 2 pose");
        assertTrue(batches.has(source, new WrappedState("armor")));
        assertEquals(List.of("player 1 pose", "player 2 pose"), batches.take(source, new WrappedState("armor")));
        assertTrue(batches.isEmpty()); assertNull(batches.take(source, first));
    }
    @Test void equalBufferOwnersKeepSeparateShaderStages() {
        var batches = new DeferredBatches<String, WrappedState, String>();
        var entitySource = new String("buffer"); var shadowSource = new String("buffer");
        var type = new WrappedState("body"); batches.add(entitySource, type, "entity"); batches.add(shadowSource, type, "shadow");
        assertEquals(List.of("entity"), batches.take(entitySource, type)); assertFalse(batches.isEmpty());
        assertEquals(List.of("shadow"), batches.take(shadowSource, type)); assertTrue(batches.isEmpty());
    }
}
