package top.wu949.dsbr.optimizer.diagnostics;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.LongAccumulator;

/** Bounded attribution; totals survive eviction of individual owners/texture keys. */
public final class Diagnostics {
    public enum Counter { REQUEST, STATE_SYNC, GENERATED, HIT, RELEASE, READBACK, COPY, GPU_PASS, GPU_VERTICES, GUI_CPU_PASS, CPU_FALLBACK, GPU_DRAW_CALLS, SOUL_RENDER, SOUL_GPU_PASS, SOUL_GPU_DRAW_CALLS, SOUL_CPU_FALLBACK, SOUL_TEXTURE_HIT,
        PLAYER_GPU_PASS, PLAYER_GPU_DRAW_CALLS, PLAYER_CPU_BONE, PLAYER_CPU_FALLBACK,
        NPC_GPU_PASS, NPC_GPU_DRAW_CALLS, NPC_CPU_BONE, NPC_CPU_FALLBACK, SOUL_CPU_BONE, POSE_UPLOAD, FRAME_ANIMATION_HIT, FRAME_ANIMATION_MISS }
    public void add(String counter, long amount) { totals.computeIfAbsent(counter, x -> new LongAdder()).add(amount); }
    public static final Diagnostics INSTANCE = new Diagnostics();
    private final Map<String, LongAdder> totals = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> timings = new ConcurrentHashMap<>();
    private final Map<String, LongAccumulator> peaks = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> attributed = new LinkedHashMap<>();
    private final Map<String, String> reasons = new LinkedHashMap<>();
    private final Deque<String> traces = new ArrayDeque<>();
    private final double[] frames = new double[100000];
    private long frameCount;
    private boolean detailed;
    private FrameWindow capture;
    private boolean captureAwaitFirst;
    public void detailed(boolean value) { detailed = value; }
    public boolean detailed() { return detailed; }
    public synchronized void beginCapture() { capture = new FrameWindow(2_000_000); captureAwaitFirst = true; }
    public synchronized void endCapture() { capture = null; }
    private boolean attribute(String key) { return detailed || key.equals("entity") || key.equals("shadow") || key.equals("glow/entity") || key.equals("glow/shadow") || key.equals("gui"); }
    public void count(Counter counter, String key) {
        totals.computeIfAbsent(counter.name(), x -> new LongAdder()).increment();
        if (!attribute(key)) return;
        synchronized (this) {
            if (!attributed.containsKey(key) && attributed.size() >= 512) attributed.remove(attributed.keySet().iterator().next());
            attributed.computeIfAbsent(key, x -> new TreeMap<>()).merge(counter.name(), 1L, Long::sum);
        }
    }
    public void nanos(String stage, long nanos) {
        var timer = timings.get(stage);
        if (timer == null) timer = timings.computeIfAbsent(stage, s -> totals.computeIfAbsent(s + "_NANOS", x -> new LongAdder()));
        timer.add(nanos);
    }
    public void nanos(String stage, long nanos, String key) {
        nanos(stage, nanos);
        if (!attribute(key)) return;
        synchronized (this) {
            if (!attributed.containsKey(key) && attributed.size() >= 512) attributed.remove(attributed.keySet().iterator().next());
            attributed.computeIfAbsent(key, x -> new TreeMap<>()).merge(stage + "_NANOS", nanos, Long::sum);
        }
    }
    public void vertices(int count) { totals.computeIfAbsent("GPU_VERTEX_COUNT", x -> new LongAdder()).add(count); }
    public void peak(String key, long value) { peaks.computeIfAbsent(key, x -> new LongAccumulator(Long::max, 0)).accumulate(value); }
    public synchronized void reason(String feature, String reason) { reasons.put(feature, reason); }
    public synchronized void trace(String text) { if (traces.size() >= 32) traces.removeFirst(); traces.addLast(text); }
    public synchronized void frame(double milliseconds) {
        if (capture != null) { if (captureAwaitFirst) captureAwaitFirst = false; else capture.add(milliseconds); }
        long index = frameCount++;
        if (index >= frames.length) index = java.util.concurrent.ThreadLocalRandom.current().nextLong(frameCount);
        if (index < frames.length) frames[(int)index] = milliseconds;
    }
    public synchronized Map<String, Object> snapshot() {
        var sums = new TreeMap<String, Long>(); totals.forEach((k, v) -> sums.put(k, v.sum()));
        var maximums = new TreeMap<String, Long>(); peaks.forEach((k, v) -> maximums.put(k, v.get()));
        var sorted = Arrays.copyOf(frames, (int)Math.min(frameCount, frames.length)); Arrays.sort(sorted);
        var owners = new LinkedHashMap<String, Map<String, Long>>(); attributed.forEach((k, v) -> owners.put(k, new TreeMap<>(v)));
        var out = new LinkedHashMap<String, Object>();
        out.put("totals", sums); out.put("peaks", maximums); out.put("fallbacks", new TreeMap<>(reasons));
        out.put("attribution", owners); out.put("dirtyTraces", List.copyOf(traces));
        out.put("frameSamples", frameCount); out.put("quantileSamples", sorted.length);
        if (sorted.length > 0) out.put("frameTimeMs", Map.of("p50", percentile(sorted, .50), "p95", percentile(sorted, .95), "p99", percentile(sorted, .99), "p999", percentile(sorted, .999)));
        if (capture != null) {
            var complete = capture.snapshot(); out.put("completeFrameCapture", complete);
            if (Boolean.TRUE.equals(complete.get("complete"))) { out.put("frameTimeMs", complete.get("frameTimeMs")); out.put("quantileSamples", complete.get("samples")); }
        }
        return out;
    }
    public static double percentile(double[] sorted, double q) { return sorted[Math.max(0, (int)Math.ceil(sorted.length * q) - 1)]; }
    public synchronized void reset() { totals.clear(); timings.clear(); peaks.clear(); attributed.clear(); traces.clear(); frameCount = 0; capture = null; }
    public void export(Path path) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(snapshot()));
    }
}
