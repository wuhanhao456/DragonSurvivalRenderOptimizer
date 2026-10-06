package top.wu949.dsbr.client;

import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

public final class DSBRRenderConfig {
    public enum NormalRenderMode implements TranslatableEnum {
        ORIGINAL("original"),
        BEDROCK("bedrock"),
        YSM("ysm");

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

    public enum ArmorRenderMode implements TranslatableEnum {
        ORIGINAL("original"),
        BEDROCK("bedrock");

        private final String translationSuffix;

        ArmorRenderMode(final String translationSuffix) {
            this.translationSuffix = translationSuffix;
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable("dsbr.config.general.armor_render_mode." + translationSuffix);
        }
    }

    public enum HeldItemRenderMode implements TranslatableEnum {
        ORIGINAL("original"),
        ALWAYS_MOUTH("always_mouth"),
        ALWAYS_HAND("always_hand");

        private final String translationSuffix;

        HeldItemRenderMode(final String translationSuffix) {
            this.translationSuffix = translationSuffix;
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable("dsbr.config.general.held_item_render_mode." + translationSuffix);
        }
    }

    public enum AnimationComputeInterval implements TranslatableEnum {
        ORIGINAL("original", 0),
        EVERY_1_TICK("every_1_tick", 1),
        EVERY_2_TICKS("every_2_ticks", 2),
        EVERY_3_TICKS("every_3_ticks", 3),
        EVERY_4_TICKS("every_4_ticks", 4);

        private final String translationSuffix;
        private final int tickInterval;

        AnimationComputeInterval(final String translationSuffix, final int tickInterval) {
            this.translationSuffix = translationSuffix;
            this.tickInterval = tickInterval;
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable("dsbr.config.general.animation_compute_interval." + translationSuffix);
        }

        public int tickInterval() {
            return tickInterval;
        }
    }

    public enum TakeoverScope implements TranslatableEnum {
        PLAYER_ONLY("player_only"),
        PLAYER_AND_DRAGON_SOUL("player_and_dragon_soul"),
        GLOBAL("global");

        private final String translationSuffix;

        TakeoverScope(final String translationSuffix) {
            this.translationSuffix = translationSuffix;
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable("dsbr.config.general.takeover_scope." + translationSuffix);
        }
    }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue LEGACY_BACKEND_ENABLED;
    public static final ModConfigSpec.EnumValue<NormalRenderMode> NORMAL_RENDER_MODE;
    public static final ModConfigSpec.EnumValue<TakeoverScope> TAKEOVER_SCOPE;
    public static final ModConfigSpec.EnumValue<SpecialAnimationMode> SPECIAL_ANIMATION_MODE;
    public static final ModConfigSpec.EnumValue<ArmorRenderMode> ARMOR_RENDER_MODE;
    public static final ModConfigSpec.EnumValue<HeldItemRenderMode> HELD_ITEM_RENDER_MODE;
    public static final ModConfigSpec.BooleanValue RENDER_GLOW_LAYER;
    public static final ModConfigSpec.BooleanValue RENDER_ARMOR_LAYER;
    public static final ModConfigSpec.BooleanValue RENDER_HELD_ITEM_LAYER;
    public static final ModConfigSpec.BooleanValue RENDER_BACKPACK_LAYER;
    public static final ModConfigSpec.BooleanValue RENDER_HEAD_IN_FIRST_PERSON;
    public static final ModConfigSpec.DoubleValue FIRST_PERSON_MODEL_OFFSET_X;
    public static final ModConfigSpec.DoubleValue FIRST_PERSON_MODEL_OFFSET_Y;
    public static final ModConfigSpec.DoubleValue FIRST_PERSON_MODEL_OFFSET_Z;
    public static final ModConfigSpec.DoubleValue ANIMATION_SPEED_MULTIPLIER;
    public static final ModConfigSpec.EnumValue<AnimationComputeInterval> ANIMATION_COMPUTE_INTERVAL;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.translation("dsbr.config.general")
                .comment("Settings for switching the normal dragon renderer.")
                .push("general");

        LEGACY_BACKEND_ENABLED = builder.comment("Explicit opt-in to the old DS 2.0.67 Bedrock/YSM backend. Disabled on DS 2.0.71 and in VANILLA/GPU modes.")
                .define("legacy_backend_enabled", false);

        NORMAL_RENDER_MODE = builder
                .translation("dsbr.config.general.normal_render_mode")
                .comment("Controls how the normal dragon form is rendered.", "ORIGINAL uses Dragon Survival's original renderer.", "BEDROCK uses this mod's Bedrock renderer.", "YSM keeps the local player on the player render path so Yes Steve Model can render it, while other players and external dragon renders still use the Bedrock backend.")
                .defineEnum("normal_render_mode", NormalRenderMode.BEDROCK);

        TAKEOVER_SCOPE = builder
                .translation("dsbr.config.general.takeover_scope")
                .comment("Controls how widely DSBR replaces Dragon Survival's DragonEntity renderer.", "PLAYER_ONLY only affects actual player dragon rendering.", "PLAYER_AND_DRAGON_SOUL additionally affects dragon soul statues.", "GLOBAL affects all DragonEntity renders such as soul statues and preview screens.")
                .defineEnum("takeover_scope", TakeoverScope.PLAYER_ONLY);

        SPECIAL_ANIMATION_MODE = builder
                .translation("dsbr.config.general.special_animation_mode")
                .comment("Controls how abilities, emotes and item-use animations are rendered.", "ORIGINAL_GECKO uses Dragon Survival's original GeckoLib renderer.", "BEDROCK uses this mod's Bedrock animation engine.")
                .defineEnum("special_animation_mode", SpecialAnimationMode.ORIGINAL_GECKO);

        ARMOR_RENDER_MODE = builder
                .translation("dsbr.config.general.armor_render_mode")
                .comment("Controls how dragon armor appearance changes are rendered.", "ORIGINAL falls back to Dragon Survival's original renderer while armor is visible.", "BEDROCK keeps the Bedrock renderer active and draws Dragon Survival's armor overlay on top.")
                .defineEnum("armor_render_mode", ArmorRenderMode.BEDROCK);

        HELD_ITEM_RENDER_MODE = builder
                .translation("dsbr.config.general.held_item_render_mode")
                .comment("Controls where held items attach on the dragon model.", "ORIGINAL follows Dragon Survival's renderItemsInMouth setting.", "ALWAYS_MOUTH keeps held items attached to the jaw bones.", "ALWAYS_HAND keeps held items attached to the side hand bones.")
                .defineEnum("held_item_render_mode", HeldItemRenderMode.ORIGINAL);

        RENDER_GLOW_LAYER = builder
                .translation("dsbr.config.general.render_glow_layer")
                .comment("Controls whether the dragon glow overlay is rendered at all.", "Disabling this also skips Bedrock-side glow texture lookup work.")
                .define("render_glow_layer", true);

        RENDER_ARMOR_LAYER = builder
                .translation("dsbr.config.general.render_armor_layer")
                .comment("Controls whether dragon armor appearance changes are rendered at all.", "Disabling this keeps Bedrock body rendering active and skips armor state and texture work.")
                .define("render_armor_layer", true);

        RENDER_HELD_ITEM_LAYER = builder
                .translation("dsbr.config.general.render_held_item_layer")
                .comment("Controls whether held items are rendered on the dragon model at all.", "Disabling this also skips held-item layer checks and Better Combat attack checks.")
                .define("render_held_item_layer", true);

        RENDER_BACKPACK_LAYER = builder
                .translation("dsbr.config.general.render_backpack_layer")
                .comment("Controls whether backpack layers are rendered on the dragon model at all.", "Disabling this also skips backpack lookup and offset calculations.")
                .define("render_backpack_layer", true);

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

        ANIMATION_COMPUTE_INTERVAL = builder
                .translation("dsbr.config.general.animation_compute_interval")
                .comment("Controls how often the Bedrock animation engine recomputes dragon animation poses.", "ORIGINAL keeps per-frame animation calculation.", "EVERY_X_TICKS reuses the last sampled pose between recalculation ticks to reduce CPU cost.")
                .defineEnum("animation_compute_interval", AnimationComputeInterval.ORIGINAL);

        builder.pop();
        SPEC = builder.build();
    }

    public static boolean useBedrockRendererForNormalDragonRender() {
        return legacyActive() && NORMAL_RENDER_MODE.get() == NormalRenderMode.BEDROCK;
    }

    public static boolean useYsmRendererForNormalDragonRender() {
        return legacyActive() && NORMAL_RENDER_MODE.get() == NormalRenderMode.YSM && isYsmInstalled();
    }

    public static boolean legacyActive() {
        return top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin.legacyCompatible && LEGACY_BACKEND_ENABLED.get()
                && top.wu949.dsbr.optimizer.OptimizerConfig.MODE.get() == top.wu949.dsbr.optimizer.OptimizerConfig.Mode.TEXTURES;
    }

    public static boolean useBedrockRendererForNonLocalDragonRenders() {
        return useBedrockRendererForNormalDragonRender() || useYsmRendererForNormalDragonRender();
    }

    public static boolean isYsmInstalled() {
        return ModList.get().isLoaded("yes_steve_model");
    }

    public static TakeoverScope takeoverScope() {
        return TAKEOVER_SCOPE.get();
    }

    public static boolean useBedrockRendererForSpecialAnimations() {
        return SPECIAL_ANIMATION_MODE.get() == SpecialAnimationMode.BEDROCK;
    }

    public static boolean useBedrockRendererForArmor() {
        return ARMOR_RENDER_MODE.get() == ArmorRenderMode.BEDROCK;
    }

    public static HeldItemRenderMode heldItemRenderMode() {
        return HELD_ITEM_RENDER_MODE.get();
    }

    public static boolean renderGlowLayer() {
        return RENDER_GLOW_LAYER.get();
    }

    public static boolean renderArmorLayer() {
        return RENDER_ARMOR_LAYER.get();
    }

    public static boolean renderHeldItemLayer() {
        return RENDER_HELD_ITEM_LAYER.get();
    }

    public static boolean renderBackpackLayer() {
        return RENDER_BACKPACK_LAYER.get();
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

    public static AnimationComputeInterval animationComputeInterval() {
        return ANIMATION_COMPUTE_INTERVAL.get();
    }

    private DSBRRenderConfig() {
    }
}
