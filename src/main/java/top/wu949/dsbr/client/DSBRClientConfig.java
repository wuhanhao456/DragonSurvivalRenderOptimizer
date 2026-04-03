package top.wu949.dsbr.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class DSBRClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue FALLBACK_TO_GECKO_FOR_SPECIAL_ANIMATIONS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");
        ENABLED = builder
                .comment("是否启用 Dragon Survival Bedrock 渲染后端")
                .define("enabled", true);
        FALLBACK_TO_GECKO_FOR_SPECIAL_ANIMATIONS = builder
                .comment("遇到技能、表情或使用物品等复杂动画时是否回退到 DS 原版 GeckoLib 渲染")
                .define("fallback_to_gecko_for_special_animations", true);
        builder.pop();

        SPEC = builder.build();
    }

    private DSBRClientConfig() {
    }
}
