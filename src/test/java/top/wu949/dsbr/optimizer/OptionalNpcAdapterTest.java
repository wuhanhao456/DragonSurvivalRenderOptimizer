package top.wu949.dsbr.optimizer;

import org.junit.jupiter.api.Test;
import top.wu949.dsbr.optimizer.compat.OptionalNpcAdapter;
import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class OptionalNpcAdapterTest {
    @Test void absentOrMismatchedCoreNeverAttemptsClassDiscovery() {
        var calls = new AtomicInteger();
        OptionalNpcAdapter.discover(null, name -> { calls.incrementAndGet(); throw new AssertionError(); });
        assertFalse(OptionalNpcAdapter.compatible);
        OptionalNpcAdapter.discover("0.8.2", name -> { calls.incrementAndGet(); throw new AssertionError(); });
        assertFalse(OptionalNpcAdapter.compatible); assertEquals(0, calls.get());
    }
    @Test void missingOrAlteredResourcesDisableOnlyNpcAdapter() {
        OptionalNpcAdapter.discover("0.10.1", name -> null); assertFalse(OptionalNpcAdapter.compatible);
        OptionalNpcAdapter.discover("0.10.1", name -> new ByteArrayInputStream(new byte[]{1,2,3}));
        assertFalse(OptionalNpcAdapter.compatible); assertTrue(OptionalNpcAdapter.status.contains("unverified"));
    }
}
