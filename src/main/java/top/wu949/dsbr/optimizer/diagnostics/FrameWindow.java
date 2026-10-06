package top.wu949.dsbr.optimizer.diagnostics;

import java.util.*;

/** Complete, bounded capture. Overflow invalidates Low metrics instead of sampling the tail. */
public final class FrameWindow {
    private final double[] frames;
    private int size;
    private boolean overflow;
    public FrameWindow(int capacity) { frames = new double[capacity]; }
    public void add(double ms) {
        if (!Double.isFinite(ms) || ms <= 0) { overflow = true; return; }
        if (size == frames.length) overflow = true;
        else frames[size++] = ms;
    }
    public Map<String, Object> snapshot() {
        var out = new LinkedHashMap<String, Object>();
        out.put("complete", !overflow && size > 0); out.put("samples", size);
        if (overflow || size == 0) return out;
        var sorted = Arrays.copyOf(frames, size); Arrays.sort(sorted);
        double sum = 0; for (double ms : sorted) sum += ms;
        out.put("capturedSeconds", sum / 1000);
        out.put("averageFps", 1000 * size / sum);
        out.put("frameTimeMs", Map.of("p50", Diagnostics.percentile(sorted, .5), "p95", Diagnostics.percentile(sorted, .95), "p99", Diagnostics.percentile(sorted, .99), "p999", Diagnostics.percentile(sorted, .999)));
        out.put("low1Fps", low(sorted, .01)); out.put("low01Fps", low(sorted, .001));
        out.put("p999Ms", Diagnostics.percentile(sorted, .999));
        var longFrames = new LinkedHashMap<String, Long>();
        for (double threshold : new double[]{16.7, 33.3, 50}) {
            long count = 0; for (double ms : sorted) if (ms > threshold) count++;
            longFrames.put(Double.toString(threshold), count);
        }
        out.put("longFramesAboveMs", longFrames);
        out.put("definition", "1000 / mean slowest ceil(samples * fraction) frame times in ms");
        return out;
    }
    private static double low(double[] sorted, double fraction) {
        int count = Math.max(1, (int)Math.ceil(sorted.length * fraction)); double sum = 0;
        for (int i = sorted.length - count; i < sorted.length; i++) sum += sorted[i];
        return 1000 * count / sum;
    }
}
