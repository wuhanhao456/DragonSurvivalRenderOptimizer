package top.wu949.dsbr.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

final class DSBRConfigScreen extends Screen {
    private static final int WIDGET_WIDTH = 310;
    private static final int WIDGET_HEIGHT = 20;
    private static final int ROW_SPACING = 24;
    private static final int FOOTER_BUTTON_WIDTH = 150;

    private final Screen lastScreen;
    private final Map<AbstractWidget, Component> baseTooltips = new HashMap<>();
    private final Map<AbstractWidget, Component> inactiveReasons = new HashMap<>();
    private final DSBRRenderConfig.NormalRenderMode initialNormalRenderMode;
    private final DSBRRenderConfig.TakeoverScope initialTakeoverScope;
    private final DSBRRenderConfig.SpecialAnimationMode initialSpecialAnimationMode;
    private final DSBRRenderConfig.ArmorRenderMode initialArmorRenderMode;
    private final DSBRRenderConfig.HeldItemRenderMode initialHeldItemRenderMode;
    private final boolean initialRenderGlowLayer;
    private final boolean initialRenderArmorLayer;
    private final boolean initialRenderHeldItemLayer;
    private final boolean initialRenderBackpackLayer;
    private final boolean initialRenderHeadInFirstPerson;
    private final double initialFirstPersonModelOffsetX;
    private final double initialFirstPersonModelOffsetY;
    private final double initialFirstPersonModelOffsetZ;
    private final double initialAnimationSpeedMultiplier;
    private boolean closingScreen;

    private DSBRRenderConfig.NormalRenderMode normalRenderMode = DSBRRenderConfig.NORMAL_RENDER_MODE.get();
    private DSBRRenderConfig.TakeoverScope takeoverScope = DSBRRenderConfig.TAKEOVER_SCOPE.get();
    private DSBRRenderConfig.SpecialAnimationMode specialAnimationMode = DSBRRenderConfig.SPECIAL_ANIMATION_MODE.get();
    private DSBRRenderConfig.ArmorRenderMode armorRenderMode = DSBRRenderConfig.ARMOR_RENDER_MODE.get();
    private DSBRRenderConfig.HeldItemRenderMode heldItemRenderMode = DSBRRenderConfig.HELD_ITEM_RENDER_MODE.get();
    private boolean renderGlowLayer = DSBRRenderConfig.RENDER_GLOW_LAYER.get();
    private boolean renderArmorLayer = DSBRRenderConfig.RENDER_ARMOR_LAYER.get();
    private boolean renderHeldItemLayer = DSBRRenderConfig.RENDER_HELD_ITEM_LAYER.get();
    private boolean renderBackpackLayer = DSBRRenderConfig.RENDER_BACKPACK_LAYER.get();
    private boolean renderHeadInFirstPerson = DSBRRenderConfig.RENDER_HEAD_IN_FIRST_PERSON.get();
    private double firstPersonModelOffsetX = DSBRRenderConfig.FIRST_PERSON_MODEL_OFFSET_X.get();
    private double firstPersonModelOffsetY = DSBRRenderConfig.FIRST_PERSON_MODEL_OFFSET_Y.get();
    private double firstPersonModelOffsetZ = DSBRRenderConfig.FIRST_PERSON_MODEL_OFFSET_Z.get();
    private double animationSpeedMultiplier = DSBRRenderConfig.ANIMATION_SPEED_MULTIPLIER.get();

    private CycleButton<DSBRRenderConfig.NormalRenderMode> normalRenderModeButton;
    private CycleButton<DSBRRenderConfig.TakeoverScope> takeoverScopeButton;
    private CycleButton<DSBRRenderConfig.SpecialAnimationMode> specialAnimationModeButton;
    private CycleButton<DSBRRenderConfig.ArmorRenderMode> armorRenderModeButton;
    private CycleButton<DSBRRenderConfig.HeldItemRenderMode> heldItemRenderModeButton;
    private CycleButton<Boolean> renderGlowLayerButton;
    private CycleButton<Boolean> renderArmorLayerButton;
    private CycleButton<Boolean> renderHeldItemLayerButton;
    private CycleButton<Boolean> renderBackpackLayerButton;
    private CycleButton<Boolean> renderHeadInFirstPersonButton;
    private DoubleSlider firstPersonModelOffsetXSlider;
    private DoubleSlider firstPersonModelOffsetYSlider;
    private DoubleSlider firstPersonModelOffsetZSlider;
    private DoubleSlider animationSpeedMultiplierSlider;

    DSBRConfigScreen(final Screen lastScreen) {
        super(Component.translatable("dsbr.config.screen.title"));
        this.lastScreen = lastScreen;
        this.initialNormalRenderMode = normalRenderMode;
        this.initialTakeoverScope = takeoverScope;
        this.initialSpecialAnimationMode = specialAnimationMode;
        this.initialArmorRenderMode = armorRenderMode;
        this.initialHeldItemRenderMode = heldItemRenderMode;
        this.initialRenderGlowLayer = renderGlowLayer;
        this.initialRenderArmorLayer = renderArmorLayer;
        this.initialRenderHeldItemLayer = renderHeldItemLayer;
        this.initialRenderBackpackLayer = renderBackpackLayer;
        this.initialRenderHeadInFirstPerson = renderHeadInFirstPerson;
        this.initialFirstPersonModelOffsetX = firstPersonModelOffsetX;
        this.initialFirstPersonModelOffsetY = firstPersonModelOffsetY;
        this.initialFirstPersonModelOffsetZ = firstPersonModelOffsetZ;
        this.initialAnimationSpeedMultiplier = animationSpeedMultiplier;
    }

    @Override
    protected void init() {
        clearWidgets();
        baseTooltips.clear();
        inactiveReasons.clear();

        int x = this.width / 2 - WIDGET_WIDTH / 2;
        int y = 32;

        normalRenderModeButton = addConfigWidget(
                createEnumButton(
                        x,
                        y,
                        Component.translatable("dsbr.config.general.normal_render_mode"),
                        normalRenderMode,
                        value -> {
                            normalRenderMode = value;
                            applyCurrentSettings();
                            refreshDependencyStates();
                        }
                ),
                Component.translatable("dsbr.config.general.normal_render_mode.tooltip")
        );
        y += ROW_SPACING;

        takeoverScopeButton = addConfigWidget(
                createEnumButton(
                        x,
                        y,
                        Component.translatable("dsbr.config.general.takeover_scope"),
                        takeoverScope,
                        value -> {
                            takeoverScope = value;
                            applyCurrentSettings();
                        }
                ),
                Component.translatable("dsbr.config.general.takeover_scope.tooltip")
        );
        y += ROW_SPACING;

        specialAnimationModeButton = addConfigWidget(
                createEnumButton(
                        x,
                        y,
                        Component.translatable("dsbr.config.general.special_animation_mode"),
                        specialAnimationMode,
                        value -> {
                            specialAnimationMode = value;
                            applyCurrentSettings();
                        }
                ),
                Component.translatable("dsbr.config.general.special_animation_mode.tooltip")
        );
        y += ROW_SPACING;

        armorRenderModeButton = addConfigWidget(
                createEnumButton(
                        x,
                        y,
                        Component.translatable("dsbr.config.general.armor_render_mode"),
                        armorRenderMode,
                        value -> {
                            armorRenderMode = value;
                            applyCurrentSettings();
                        }
                ),
                Component.translatable("dsbr.config.general.armor_render_mode.tooltip")
        );
        y += ROW_SPACING;

        heldItemRenderModeButton = addConfigWidget(
                createEnumButton(
                        x,
                        y,
                        Component.translatable("dsbr.config.general.held_item_render_mode"),
                        heldItemRenderMode,
                        value -> {
                            heldItemRenderMode = value;
                            applyCurrentSettings();
                        }
                ),
                Component.translatable("dsbr.config.general.held_item_render_mode.tooltip")
        );
        y += ROW_SPACING;

        renderGlowLayerButton = addConfigWidget(
                CycleButton.onOffBuilder(renderGlowLayer)
                        .create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Component.translatable("dsbr.config.general.render_glow_layer"), (button, value) -> {
                            renderGlowLayer = value;
                            applyCurrentSettings();
                        }),
                Component.translatable("dsbr.config.general.render_glow_layer.tooltip")
        );
        y += ROW_SPACING;

        renderArmorLayerButton = addConfigWidget(
                CycleButton.onOffBuilder(renderArmorLayer)
                        .create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Component.translatable("dsbr.config.general.render_armor_layer"), (button, value) -> {
                            renderArmorLayer = value;
                            applyCurrentSettings();
                            refreshDependencyStates();
                        }),
                Component.translatable("dsbr.config.general.render_armor_layer.tooltip")
        );
        y += ROW_SPACING;

        renderHeldItemLayerButton = addConfigWidget(
                CycleButton.onOffBuilder(renderHeldItemLayer)
                        .create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Component.translatable("dsbr.config.general.render_held_item_layer"), (button, value) -> {
                            renderHeldItemLayer = value;
                            applyCurrentSettings();
                            refreshDependencyStates();
                        }),
                Component.translatable("dsbr.config.general.render_held_item_layer.tooltip")
        );
        y += ROW_SPACING;

        renderBackpackLayerButton = addConfigWidget(
                CycleButton.onOffBuilder(renderBackpackLayer)
                        .create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Component.translatable("dsbr.config.general.render_backpack_layer"), (button, value) -> {
                            renderBackpackLayer = value;
                            applyCurrentSettings();
                        }),
                Component.translatable("dsbr.config.general.render_backpack_layer.tooltip")
        );
        y += ROW_SPACING;

        renderHeadInFirstPersonButton = addConfigWidget(
                CycleButton.onOffBuilder(renderHeadInFirstPerson)
                        .create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Component.translatable("dsbr.config.general.render_head_in_first_person"), (button, value) -> {
                            renderHeadInFirstPerson = value;
                            applyCurrentSettings();
                        }),
                Component.translatable("dsbr.config.general.render_head_in_first_person.tooltip")
        );
        y += ROW_SPACING;

        firstPersonModelOffsetXSlider = addConfigWidget(
                new DoubleSlider(
                        x,
                        y,
                        WIDGET_WIDTH,
                        Component.translatable("dsbr.config.general.first_person_model_offset_x"),
                        -8.0D,
                        8.0D,
                        firstPersonModelOffsetX,
                        value -> {
                            firstPersonModelOffsetX = value;
                            applyCurrentSettings();
                        }
                ),
                Component.translatable("dsbr.config.general.first_person_model_offset_x.tooltip")
        );
        y += ROW_SPACING;

        firstPersonModelOffsetYSlider = addConfigWidget(
                new DoubleSlider(
                        x,
                        y,
                        WIDGET_WIDTH,
                        Component.translatable("dsbr.config.general.first_person_model_offset_y"),
                        -8.0D,
                        8.0D,
                        firstPersonModelOffsetY,
                        value -> {
                            firstPersonModelOffsetY = value;
                            applyCurrentSettings();
                        }
                ),
                Component.translatable("dsbr.config.general.first_person_model_offset_y.tooltip")
        );
        y += ROW_SPACING;

        firstPersonModelOffsetZSlider = addConfigWidget(
                new DoubleSlider(
                        x,
                        y,
                        WIDGET_WIDTH,
                        Component.translatable("dsbr.config.general.first_person_model_offset_z"),
                        -8.0D,
                        8.0D,
                        firstPersonModelOffsetZ,
                        value -> {
                            firstPersonModelOffsetZ = value;
                            applyCurrentSettings();
                        }
                ),
                Component.translatable("dsbr.config.general.first_person_model_offset_z.tooltip")
        );
        y += ROW_SPACING;

        animationSpeedMultiplierSlider = addConfigWidget(
                new DoubleSlider(
                        x,
                        y,
                        WIDGET_WIDTH,
                        Component.translatable("dsbr.config.general.animation_speed_multiplier"),
                        0.1D,
                        3.0D,
                        animationSpeedMultiplier,
                        value -> {
                            animationSpeedMultiplier = value;
                            applyCurrentSettings();
                        }
                ),
                Component.translatable("dsbr.config.general.animation_speed_multiplier.tooltip")
        );

        int footerY = this.height - 28;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> persistAndClose())
                .bounds(this.width / 2 - FOOTER_BUTTON_WIDTH - 4, footerY, FOOTER_BUTTON_WIDTH, WIDGET_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> cancelAndClose())
                .bounds(this.width / 2 + 4, footerY, FOOTER_BUTTON_WIDTH, WIDGET_HEIGHT)
                .build());

        refreshDependencyStates();
        setInitialFocus(normalRenderModeButton);
    }

    @Override
    public void render(final GuiGraphics guiGraphics, final int mouseX, final int mouseY, final float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        renderInactiveReason(guiGraphics);
    }

    @Override
    public void onClose() {
        if (closingScreen) {
            return;
        }
        cancelAndClose();
    }

    private void closeScreen() {
        closingScreen = true;
        this.minecraft.setScreen(lastScreen);
    }

    private void applyCurrentSettings() {
        DSBRRenderConfig.NORMAL_RENDER_MODE.set(normalRenderMode);
        DSBRRenderConfig.TAKEOVER_SCOPE.set(takeoverScope);
        DSBRRenderConfig.SPECIAL_ANIMATION_MODE.set(specialAnimationMode);
        DSBRRenderConfig.ARMOR_RENDER_MODE.set(armorRenderMode);
        DSBRRenderConfig.HELD_ITEM_RENDER_MODE.set(heldItemRenderMode);
        DSBRRenderConfig.RENDER_GLOW_LAYER.set(renderGlowLayer);
        DSBRRenderConfig.RENDER_ARMOR_LAYER.set(renderArmorLayer);
        DSBRRenderConfig.RENDER_HELD_ITEM_LAYER.set(renderHeldItemLayer);
        DSBRRenderConfig.RENDER_BACKPACK_LAYER.set(renderBackpackLayer);
        DSBRRenderConfig.RENDER_HEAD_IN_FIRST_PERSON.set(renderHeadInFirstPerson);
        DSBRRenderConfig.FIRST_PERSON_MODEL_OFFSET_X.set(firstPersonModelOffsetX);
        DSBRRenderConfig.FIRST_PERSON_MODEL_OFFSET_Y.set(firstPersonModelOffsetY);
        DSBRRenderConfig.FIRST_PERSON_MODEL_OFFSET_Z.set(firstPersonModelOffsetZ);
        DSBRRenderConfig.ANIMATION_SPEED_MULTIPLIER.set(animationSpeedMultiplier);
    }

    private void persistAndClose() {
        applyCurrentSettings();
        DSBRRenderConfig.SPEC.save();
        closeScreen();
    }

    private void cancelAndClose() {
        restoreInitialSettings();
        DSBRRenderConfig.SPEC.save();
        closeScreen();
    }

    private void restoreInitialSettings() {
        normalRenderMode = initialNormalRenderMode;
        takeoverScope = initialTakeoverScope;
        specialAnimationMode = initialSpecialAnimationMode;
        armorRenderMode = initialArmorRenderMode;
        heldItemRenderMode = initialHeldItemRenderMode;
        renderGlowLayer = initialRenderGlowLayer;
        renderArmorLayer = initialRenderArmorLayer;
        renderHeldItemLayer = initialRenderHeldItemLayer;
        renderBackpackLayer = initialRenderBackpackLayer;
        renderHeadInFirstPerson = initialRenderHeadInFirstPerson;
        firstPersonModelOffsetX = initialFirstPersonModelOffsetX;
        firstPersonModelOffsetY = initialFirstPersonModelOffsetY;
        firstPersonModelOffsetZ = initialFirstPersonModelOffsetZ;
        animationSpeedMultiplier = initialAnimationSpeedMultiplier;
        applyCurrentSettings();
    }

    private void refreshDependencyStates() {
        Component bedrockNormalReason = normalRenderMode == DSBRRenderConfig.NormalRenderMode.BEDROCK
                ? null
                : Component.translatable("dsbr.config.requirement.normal_render_mode_bedrock");
        Component armorLayerDisabledReason = renderArmorLayer
                ? null
                : Component.translatable("dsbr.config.requirement.armor_layer_enabled");
        Component heldItemLayerDisabledReason = renderHeldItemLayer
                ? null
                : Component.translatable("dsbr.config.requirement.held_item_layer_enabled");

        applyDependencyState(takeoverScopeButton, bedrockNormalReason);
        applyDependencyState(specialAnimationModeButton, bedrockNormalReason);
        applyDependencyState(armorRenderModeButton, firstNonNull(bedrockNormalReason, armorLayerDisabledReason));
        applyDependencyState(heldItemRenderModeButton, firstNonNull(bedrockNormalReason, heldItemLayerDisabledReason));
        applyDependencyState(renderGlowLayerButton, bedrockNormalReason);
        applyDependencyState(renderArmorLayerButton, bedrockNormalReason);
        applyDependencyState(renderHeldItemLayerButton, bedrockNormalReason);
        applyDependencyState(renderBackpackLayerButton, bedrockNormalReason);
        applyDependencyState(renderHeadInFirstPersonButton, bedrockNormalReason);
        applyDependencyState(firstPersonModelOffsetXSlider, bedrockNormalReason);
        applyDependencyState(firstPersonModelOffsetYSlider, bedrockNormalReason);
        applyDependencyState(firstPersonModelOffsetZSlider, bedrockNormalReason);
        applyDependencyState(animationSpeedMultiplierSlider, bedrockNormalReason);
    }

    private void applyDependencyState(final AbstractWidget widget, final Component reason) {
        widget.active = reason == null;
        if (reason == null) {
            inactiveReasons.remove(widget);
            widget.setTooltip(Tooltip.create(baseTooltips.get(widget)));
            return;
        }

        inactiveReasons.put(widget, reason);
        widget.setTooltip(Tooltip.create(joinTooltip(baseTooltips.get(widget), reason)));
    }

    private Component joinTooltip(final Component baseTooltip, final Component reason) {
        return Component.empty()
                .append(baseTooltip)
                .append(Component.literal("\n"))
                .append(reason);
    }

    private Component firstNonNull(final Component first, final Component second) {
        return first != null ? first : second;
    }

    private void renderInactiveReason(final GuiGraphics guiGraphics) {
        Component reason = null;
        for (Map.Entry<AbstractWidget, Component> entry : inactiveReasons.entrySet()) {
            if (entry.getKey().isHoveredOrFocused()) {
                reason = entry.getValue();
                break;
            }
        }

        if (reason == null) {
            return;
        }

        List<FormattedCharSequence> lines = this.font.split(reason, this.width - 40);
        int y = this.height - 40 - lines.size() * (this.font.lineHeight + 1);
        for (FormattedCharSequence line : lines) {
            guiGraphics.drawString(this.font, line, 20, y, 0xA0A0A0);
            y += this.font.lineHeight + 1;
        }
    }

    private <T extends Enum<T> & net.neoforged.neoforge.common.TranslatableEnum> CycleButton<T> createEnumButton(
            final int x,
            final int y,
            final Component label,
            final T currentValue,
            final Consumer<T> onValueChanged
    ) {
        return CycleButton.<T>builder(value -> value.getTranslatedName())
                .withValues(currentValue.getDeclaringClass().getEnumConstants())
                .withInitialValue(currentValue)
                .create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, label, (button, value) -> onValueChanged.accept(value));
    }

    private <T extends AbstractWidget> T addConfigWidget(final T widget, final Component baseTooltip) {
        baseTooltips.put(widget, baseTooltip);
        widget.setTooltip(Tooltip.create(baseTooltip));
        return addRenderableWidget(widget);
    }

    private static final class DoubleSlider extends AbstractSliderButton {
        private final Component label;
        private final double minValue;
        private final double maxValue;
        private final DoubleConsumer onValueChanged;

        private DoubleSlider(
                final int x,
                final int y,
                final int width,
                final Component label,
                final double minValue,
                final double maxValue,
                final double currentValue,
                final DoubleConsumer onValueChanged
        ) {
            super(x, y, width, WIDGET_HEIGHT, CommonComponents.EMPTY, normalize(currentValue, minValue, maxValue));
            this.label = label;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.onValueChanged = onValueChanged;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(CommonComponents.optionNameValue(label, Component.literal(formatValue(actualValue()))));
        }

        @Override
        protected void applyValue() {
            onValueChanged.accept(actualValue());
        }

        private double actualValue() {
            double value = minValue + this.value * (maxValue - minValue);
            return Math.round(value * 100.0D) / 100.0D;
        }

        private static double normalize(final double value, final double minValue, final double maxValue) {
            if (maxValue <= minValue) {
                return 0.0D;
            }
            return (value - minValue) / (maxValue - minValue);
        }

        private static String formatValue(final double value) {
            return String.format(Locale.ROOT, "%.2f", value);
        }
    }
}
