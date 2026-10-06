package top.wu949.dsbr.optimizer.diagnostics;

import top.wu949.dsbr.optimizer.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import java.nio.file.*;
import java.util.*;

/** Runs the specified 60s warmup + 300s sample in each mode in the user's fixed scene. */
public final class ClientBenchmark {
    private static final OptimizerConfig.Mode[] MODES = OptimizerConfig.Mode.values();
    private static boolean active, sampling;
    private static int players, repetition, modeIndex;
    private static long deadline;
    private static Path directory;
    private static OptimizerConfig.Mode originalMode;
    private static Object level;
    private static final List<Map<String, Object>> results = new ArrayList<>();
    public static String start(int count, int repeat) {
        var mc = Minecraft.getInstance();
        if (active) return "Benchmark already running; use /beloongrender benchmark stop";
        if (top.wu949.dsbr.client.DSBRRenderConfig.legacyActive()) return "Select /dsbr textures before comparing the three modern modes";
        if (!top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin.gpuCompatible) return "Benchmark requires all three modes to be compatible: " + top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin.status;
        if (mc.level == null) return "Enter a fixed test scene before starting the benchmark";
        if (count != 1 && count != 4 && count != 12) return "Dragon player count must be 1, 4 or 12";
        if (repeat < 1 || repeat > 3) return "Repetition must be 1, 2 or 3";
        players = count; repetition = repeat; modeIndex = 0; originalMode = OptimizerConfig.MODE.get(); level = mc.level;
        directory = mc.gameDirectory.toPath().resolve("logs/dsbr-render-benchmark/" + System.currentTimeMillis() + "-p" + count + "-r" + repeat);
        results.clear(); active = true; beginMode();
        return "Benchmark started: 1 scene, 3 modes; each 60s warmup + 300s sample. Output: " + directory;
    }
    private static void beginMode() { OptimizerConfig.MODE.set(MODES[modeIndex]); sampling = false; deadline = System.nanoTime() + 60_000_000_000L; }
    public static void frame() {
        if (!active) return;
        var mc = Minecraft.getInstance();
        if (mc.level != level || mc.isPaused() || OptimizerConfig.MODE.get() != MODES[modeIndex]) { cancel("world, pause or mode changed"); return; }
        if (System.nanoTime() < deadline) return;
        if (!sampling) { sampling = true; Diagnostics.INSTANCE.reset(); deadline = System.nanoTime() + 300_000_000_000L; return; }
        try {
            var row = new LinkedHashMap<String, Object>(); row.put("mode", MODES[modeIndex].name()); row.put("sceneDragonPlayers", players); row.put("repetition", repetition);
            row.put("warmupSeconds", 60); row.put("samplingSeconds", 300); row.put("width", mc.getWindow().getWidth()); row.put("height", mc.getWindow().getHeight());
            row.put("renderDistance", mc.options.renderDistance().get()); row.put("fpsLimit", mc.options.framerateLimit().get()); row.put("gpu", GL11.glGetString(GL11.GL_RENDERER));
            row.put("vsync", mc.options.enableVsync().get()); row.put("textureBudgetMiB", OptimizerConfig.TEXTURE_MIB.get()); row.put("meshBudgetMiB", OptimizerConfig.MESH_MIB.get());
            row.put("camera", mc.options.getCameraType().name()); row.put("cameraPosition", mc.gameRenderer.getMainCamera().getPosition().toString());
            if (top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin.irisCompatible) {
                var iris = Class.forName("net.irisshaders.iris.Iris");
                row.put("shaderPack", iris.getMethod("getCurrentPackName").invoke(null)); row.put("shaderEnabled", iris.getMethod("isPackInUseQuick").invoke(null));
            }
            row.put("textureBytes", top.wu949.dsbr.optimizer.texture.TextureCache.bytes()); row.put("meshBytes", top.wu949.dsbr.optimizer.gpu.GpuDispatcher.bytes());
            row.put("compatibility", top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin.status); row.put("stats", Diagnostics.INSTANCE.snapshot());
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(MODES[modeIndex].name().toLowerCase(Locale.ROOT) + ".json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(row));
            results.add(row); modeIndex++;
            if (modeIndex < MODES.length) beginMode();
            else {
                Files.writeString(directory.resolve("comparison.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results));
                active = false; OptimizerConfig.MODE.set(originalMode); RenderOptimizer.LOGGER.info("Benchmark complete: {}", directory);
            }
        } catch (Exception e) { cancel(e.toString()); }
    }
    public static String status() { return active ? MODES[modeIndex] + ": " + (sampling ? "sampling" : "warming") + ", remaining=" + Math.max(0, (deadline - System.nanoTime()) / 1_000_000_000L) + "s" : "idle"; }
    public static void cancel(String reason) {
        if (!active) return;
        active = false; OptimizerConfig.MODE.set(originalMode); Diagnostics.INSTANCE.reason("benchmark cancelled", reason);
        RenderOptimizer.LOGGER.warn("Benchmark cancelled: {}", reason);
    }
}
