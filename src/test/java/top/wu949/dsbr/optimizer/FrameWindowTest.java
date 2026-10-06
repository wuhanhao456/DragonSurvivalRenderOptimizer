package top.wu949.dsbr.optimizer;
import top.wu949.dsbr.optimizer.diagnostics.FrameWindow;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FrameWindowTest {
    @Test void lowUsesTailMeanRatherThanPercentile() {
        var w = new FrameWindow(1000); for (int i = 0; i < 998; i++) w.add(1);
        w.add(50); w.add(150); var s = w.snapshot();
        assertEquals(1000d / 20.8, (double)s.get("low1Fps"), 1e-8);
        assertEquals(1000d / 150, (double)s.get("low01Fps"), 1e-8);
    }
    @Test void overflowNeverReportsLow() {
        var w = new FrameWindow(1); w.add(2); w.add(3);
        assertEquals(false, w.snapshot().get("complete")); assertFalse(w.snapshot().containsKey("low1Fps"));
    }
    @Test void captureExcludesIntervalBeforeItsFirstBoundary() {
        var d = Diagnostics.INSTANCE; d.reset(); d.beginCapture();
        try {
            d.frame(999); d.frame(2);
            var captured = (java.util.Map<?, ?>)d.snapshot().get("completeFrameCapture");
            assertEquals(1, captured.get("samples")); assertEquals(500d, captured.get("low1Fps"));
        } finally { d.endCapture(); d.reset(); }
    }
}
