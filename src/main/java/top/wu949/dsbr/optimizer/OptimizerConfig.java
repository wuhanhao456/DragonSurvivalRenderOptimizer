package top.wu949.dsbr.optimizer;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class OptimizerConfig {
    public enum Mode { VANILLA, TEXTURES, GPU }
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<Mode> MODE;
    public static final ModConfigSpec.IntValue TEXTURE_MIB, MESH_MIB, RETENTION_SECONDS;
    public static final ModConfigSpec.BooleanValue DIRTY_TRACES, DETAILED_DIAGNOSTICS;
    static {
        var b = new ModConfigSpec.Builder();
        MODE = b.comment("GPU is enabled by default; unsupported capabilities and paths use the original renderer.").defineEnum("mode", Mode.GPU);
        TEXTURE_MIB = b.defineInRange("textureBudgetMiB", 64, 8, 1024);
        MESH_MIB = b.defineInRange("meshBudgetMiB", 128, 8, 2048);
        RETENTION_SECONDS = b.defineInRange("unusedTextureSeconds", 30, 1, 600);
        DIRTY_TRACES = b.comment("Capture bounded stacks for skin dirty notifications.").define("traceDirtyFlags", false);
        DETAILED_DIAGNOSTICS = b.comment("Record per-player and per-texture attribution; adds diagnostic overhead.").define("detailedDiagnostics", false);
        SPEC = b.build();
    }
    private OptimizerConfig() {}
}
