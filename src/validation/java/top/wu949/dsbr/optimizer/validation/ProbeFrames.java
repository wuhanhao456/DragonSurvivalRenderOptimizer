package top.wu949.dsbr.optimizer.validation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.nio.file.*;
import java.io.*;

/** Actual Minecraft display-loop boundaries; identical recorder with alpha.2/alpha.3. */
@EventBusSubscriber(modid = "dsbr_validation", value = Dist.CLIENT)
public final class ProbeFrames {
    private static long[] intervals;
    private static int size;
    private static long previous;
    private static boolean overflow;
    private static java.time.Instant origin;
    private static final boolean FULL_PACK = Boolean.getBoolean("beloongrender.fullpack");
    private static boolean pumping;
    public static void begin() { intervals = new long[2_000_000]; size = 0; previous = 0; overflow = false; }
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event) {
        // KubeJS may queue a client resource reload during synchronous world load.
        // Minecraft's nested runTick(false) does not drain its task queue. Pump
        // only while joining our isolated world, never in a sampled scene.
        if (FULL_PACK && intervals == null && !pumping) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null && mc.getSingleplayerServer() != null && mc.getOverlay() != null) {
                pumping = true; try { while (mc.pollTask()) { } } finally { pumping = false; }
            }
        }
    }
    public static void boundary() {
        if (intervals == null) return;
        long now = System.nanoTime();
        if (previous == 0) origin = java.time.Instant.now();
        if (previous != 0) { if (size == intervals.length) overflow = true; else intervals[size++] = now - previous; }
        previous = now;
    }
    public static String origin() { return origin.toString(); }
    public static void save(Path path) throws IOException {
        if (overflow || size == 0) throw new IllegalStateException("Incomplete frame capture");
        var captured = intervals; int count = size; intervals = null;
        try (var writer = Files.newBufferedWriter(path)) {
            writer.write("frame,elapsed_ns,duration_ns\n"); long elapsed = 0;
            for (int i = 0; i < count; i++) { elapsed += captured[i]; writer.write(i + "," + elapsed + "," + captured[i] + "\n"); }
        }
    }
}
