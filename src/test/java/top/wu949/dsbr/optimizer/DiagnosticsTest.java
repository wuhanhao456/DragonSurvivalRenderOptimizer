package top.wu949.dsbr.optimizer;

import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsTest {
    @Test void snapshotRetainsOwnerValuesAcrossLaterMutation() {
        var d = new Diagnostics(); d.detailed(true); d.count(Diagnostics.Counter.REQUEST, "player/skin");
        d.nanos("SKIN_GENERATE", 23, "player/skin"); var snapshot = d.snapshot();
        d.count(Diagnostics.Counter.REQUEST, "player/skin");
        var owners = (Map<?, ?>)snapshot.get("attribution"); var player = (Map<?, ?>)owners.get("player/skin");
        assertEquals(1L, player.get("REQUEST")); assertEquals(23L, player.get("SKIN_GENERATE_NANOS"));
    }
    @Test void boundedQuantilesKeepTotalFrameDenominator() {
        var d = new Diagnostics(); for (int i = 0; i < 150000; i++) d.frame(4);
        var s = d.snapshot(); assertEquals(150000L, s.get("frameSamples")); assertEquals(100000, s.get("quantileSamples"));
        assertEquals(4d, ((Map<?, ?>)s.get("frameTimeMs")).get("p95"));
        d.reset(); assertEquals(0L, d.snapshot().get("frameSamples"));
        d.peak("GPU_RESOURCE_BYTES", 12); d.peak("GPU_RESOURCE_BYTES", 9);
        assertEquals(12L, ((Map<?, ?>)d.snapshot().get("peaks")).get("GPU_RESOURCE_BYTES"));
    }
}
