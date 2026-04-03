package top.wu949.dsbr.client;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

public final class DSBRRenderConfig {
    public enum NormalRenderMode implements TranslatableEnum {
        ORIGINAL("original"),
        BEDROCK("bedrock");

        private final String translationSuffix;

        NormalRenderMode(final String translationSuffix) {
            this.translationSuffix = translationSuffix;
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable("dsbr.config.general.normal_render_mode." + translationSuffix);
        }
    }

    public enum SpecialAnimationMode implements TranslatableEnum {
        ORIGINAL_GECKO("original_gecko"),
        BEDROCK("bedrock");

        private final String translationSuffix;

        SpecialAnimationMode(final String translationSuffix) {
            this.translationSuffix = translationSuffix;
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable("dsbr.config.general.special_animation_mode." + translationSuffix);
        }
    }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<NormalRenderMode> NORMAL_RENDER_MODE;
    public static final ModConfigSpec.EnumValue<SpecialAnimationMode> SPECIAL_ANIMATION_MODE;
    public static final ModConfigSpec.BooleanValue RENDER_HEAD_IN_FIRST_PERSON;
    public static final ModConfigSpec.DoubleValue FIRST_PERSON_MODEL_OFFSET_X;
    public static final ModConfigSpec.DoubleValue FIRST_PERSON_MODEL_OFFSET_Y;
    public static final ModConfigSpec.DoubleValue FIRST_PERSON_MODEL_OFFSET_Z;
    public static final ModConfigSpec.DoubleValue ANIMATION_SPEED_MULTIPLIER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.translation("dsbr.config.general")
                .comment("Settings for switching the normal dragon renderer.")
                .push("general");

        NORMAL_RENDER_MODE = builder
                .translation("dsbr.config.general.normal_render_mode")
                .comment("Controls how the normal dragon form is rendered.", "ORIGINAL uses Dragon Survival's original renderer.", "BEDROCK uses this mod's Bedrock renderer.")
                .defineEnum("normal_render_mode", NormalRenderMode.BEDROCK);

        SPECIAL_ANIMATION_MODE = builder
                .translation("dsbr.config.general.special_animation_mode")
                .comment("Controls how abilities, emotes and item-use animations are rendered.", "ORIGINAL_GECKO uses Dragon Survival's original GeckoLib renderer.", "BEDROCK uses this mod's Bedrock animation engine.")
                .defineEnum("special_animation_mode", SpecialAnimationMode.ORIGINAL_GECKO);

        RENDER_HEAD_IN_FIRST_PERSON = builder
                .translation("dsbr.config.general.render_head_in_first_person")
                .comment("Controls whether the dragon head stays visible in first-person view.")
                .define("render_head_in_first_person", false);

        FIRST_PERSON_MODEL_OFFSET_X = builder
                .translation("dsbr.config.general.first_person_model_offset_x")
                .comment("Additional X offset applied to first-person dragon rendering.")
                .defineInRange("first_person_model_offset_x", 0.0D, -8.0D, 8.0D);

        FIRST_PERSON_MODEL_OFFSET_Y = builder
                .translation("dsbr.config.general.first_person_model_offset_y")
                .comment("Additional Y offset applied to first-person dragon rendering.")
                .defineInRange("first_person_model_offset_y", 0.0D, -8.0D, 8.0D);

        FIRST_PERSON_MODEL_OFFSET_Z = builder
                .translation("dsbr.config.general.first_person_model_offset_z")
                .comment("Additional Z offset applied to first-person dragon rendering.")
                .defineInRange("first_person_model_offset_z", 0.0D, -8.0D, 8.0D);

        ANIMATION_SPEED_MULTIPLIER = builder
                .translation("dsbr.config.general.animation_speed_multiplier")
                .comment("Global multiplier applied after the Dragon Survival-compatible animation speed calculation.", "1.0 matches Dragon Survival's original speed.", "Values above 1.0 speed animations up, values below 1.0 slow them down.")
                .defineInRange("animation_speed_multiplier", 0.5D, 0.1D, 3.0D);

        builder.pop();
        SPEC = builder.build();
    }

    public static boolean useBedrockRendererForNormalDragonRender() {
        return NORMAL_RENDER_MODE.get() == NormalRenderMode.BEDROCK;
    }

    public static boolean useBedrockRendererForSpecialAnimations() {
        return SPECIAL_ANIMATION_MODE.get() == SpecialAnimationMode.BEDROCK;
    }

    public static boolean renderHeadInFirstPerson() {
        return RENDER_HEAD_IN_FIRST_PERSON.get();
    }

    public static double firstPersonModelOffsetX() {
        return FIRST_PERSON_MODEL_OFFSET_X.get();
    }

    public static double firstPersonModelOffsetY() {
        return FIRST_PERSON_MODEL_OFFSET_Y.get();
    }

    public static double firstPersonModelOffsetZ() {
        return FIRST_PERSON_MODEL_OFFSET_Z.get();
    }

    public static double animationSpeedMultiplier() {
        return ANIMATION_SPEED_MULTIPLIER.get();
    }

    private DSBRRenderConfig() {
    }
}
