package top.wu949.dsbr.optimizer;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class OptimizerConfig {
    public enum Mode implements net.neoforged.neoforge.common.TranslatableEnum {
        VANILLA, TEXTURES, GPU;
        @Override public net.minecraft.network.chat.Component getTranslatedName() { return net.minecraft.network.chat.Component.translatable("dsbr.configuration.mode." + name()); }
    }
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<Mode> MODE;
    public static final ModConfigSpec.IntValue TEXTURE_MIB, MESH_MIB, RETENTION_SECONDS;
    public static final ModConfigSpec.BooleanValue DIRTY_TRACES, DETAILED_DIAGNOSTICS, NPC_GPU, CPU_SCRATCH, LOW_END;
    static {
        var b = new ModConfigSpec.Builder();
        MODE = b.translation("dsbr.configuration.mode").comment("GPU: textures and GPU geometry, including dragon souls. TEXTURES: texture cache only. VANILLA: original DS rendering. Unsupported paths fall back automatically.").defineEnum("mode", Mode.GPU);
        TEXTURE_MIB = b.translation("dsbr.configuration.textureBudgetMiB").comment("Texture cache soft budget in MiB; textures used by the current frame remain protected.").defineInRange("textureBudgetMiB", 64, 8, 1024);
        MESH_MIB = b.translation("dsbr.configuration.meshBudgetMiB").comment("GPU geometry, retained poses and working buffer budget in MiB.").defineInRange("meshBudgetMiB", 128, 8, 2048);
        RETENTION_SECONDS = b.translation("dsbr.configuration.unusedTextureSeconds").comment("Retain unused textures for this many seconds; budgets may evict them sooner.").defineInRange("unusedTextureSeconds", 30, 1, 600);
        DIRTY_TRACES = b.translation("dsbr.configuration.traceDirtyFlags").comment("Capture up to 32 skin invalidation stacks for troubleshooting.").define("traceDirtyFlags", false);
        DETAILED_DIAGNOSTICS = b.translation("dsbr.configuration.detailedDiagnostics").comment("Record per-player and per-texture attribution; adds diagnostic overhead.").define("detailedDiagnostics", false);
        NPC_GPU = b.translation("dsbr.configuration.beloongNpcGpu").comment("GPU mode: accelerate verified optional BeLoong NPC renderers. No Core dependency.").define("beloongNpcGpu", true);
        CPU_SCRATCH = b.translation("dsbr.configuration.reuseCpuVertexScratch").comment("GPU mode: reuse temporary vectors and matrices on audited CPU fallback paths, including inventory previews.").define("reuseCpuVertexScratch", true);
        LOW_END = b.translation("dsbr.configuration.lowEndGpuSupport").comment("Keep current models, hide soul models, use base skins, remove cosmetic layers and update nearby animations at 5 Hz. Works with CPU fallback. Changes are client-only and reversible.").define("lowEndGpuSupport", false);
        SPEC = b.build();
    }
    public static int textureMiB() { return LOW_END.get() ? Math.min(16, TEXTURE_MIB.get()) : TEXTURE_MIB.get(); }
    public static int meshMiB() { return LOW_END.get() ? Math.min(64, MESH_MIB.get()) : MESH_MIB.get(); }
    public static int retentionSeconds() { return LOW_END.get() ? Math.min(10, RETENTION_SECONDS.get()) : RETENTION_SECONDS.get(); }
    private OptimizerConfig() {}
}
