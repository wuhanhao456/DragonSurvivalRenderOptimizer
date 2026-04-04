package top.wu949.dsbr.client.bridge;

import top.wu949.dsbr.DragonSurvivalBedrockRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class DSRuntimeBridge {
    private static final String DRAGON_SURVIVAL_MOD_ID = "dragonsurvival";
    private static Field sharedMovementBiteField;

    private boolean initialized;
    private boolean available;

    private Method dragonStateProviderIsDragonMethod;
    private Method dragonStateProviderGetDataMethod;
    private Method clientDragonRendererGetOrCreateDragonMethod;
    private Method clientDragonRendererSetDragonMovementDataMethod;
    private Method clientDragonRendererHandleFlightMovementMethod;
    private Method movementDataGetDataMethod;
    private Method dragonModelGetModelResourceMethod;
    private Method dragonModelGetTextureResourceMethod;
    private Method dragonModelGetAnimationResourceMethod;
    private Field dragonModelField;
    private Method handlerGetVisualScaleMethod;
    private Method handlerGetModelMethod;
    private Method handlerBodyMethod;
    private Method handlerGetCurrentStageCustomizationMethod;
    private Field handlerIsOnMagicSourceField;
    private Method serverFlightIsFlyingMethod;
    private Method serverFlightIsGlidingMethod;
    private Method serverFlightIsSpinMethod;
    private Method serverFlightDistanceFromGroundMethod;
    private Method dragonEntityIsSwimmingMethod;
    private Method dragonSizeCanPoseFitMethod;
    private Method treasureRestGetDataMethod;
    private Method treasureRestIsRestingMethod;
    private Method skyhookRidingMethod;
    private Method dragonFoodHandlerIsEdibleMethod;
    private Method dragonGetCurrentlyPlayingEmotesMethod;
    private Field clientDragonRendererRenderItemsInMouthField;
    private Field dragonCurrentAbilityAnimationField;
    private Method pairGetFirstMethod;
    private Method pairGetSecondMethod;
    private Method abilityAnimationGetLayerMethod;
    private Method abilityAnimationGetNameMethod;
    private Method abilityAnimationLocksHeadMethod;
    private Method abilityAnimationLocksTailMethod;
    private Method simpleAbilityAnimationAnimationKeyMethod;
    private Method compoundAbilityAnimationStartingAnimationKeyMethod;
    private Method compoundAbilityAnimationLoopingAnimationKeyMethod;
    private Method dragonEmoteAnimationKeyMethod;
    private Method dragonEmoteSpeedMethod;
    private Method dragonEmoteLoopsMethod;
    private Method dragonEmoteBlendMethod;
    private Method dragonEmoteLocksHeadMethod;
    private Method dragonEmoteLocksTailMethod;
    private Class<?> simpleAbilityAnimationClass;
    private Class<?> compoundAbilityAnimationClass;
    private Field dragonsJumpingField;
    private Field dragonPrevXRotField;
    private Field dragonPrevZRotField;
    private Field movementBodyYawField;
    private Field movementHeadYawField;
    private Field movementHeadPitchField;
    private Field movementDeltaMovementField;
    private Field movementDesiredMoveVecField;
    private Field movementPrevXRotField;
    private Field movementDigField;
    private Field movementBiteField;
    private Method bodyCanHideWingsMethod;
    private Method bodyBonesToHideForToggleMethod;
    private Method bodyScalingProportionsMethod;
    private Method scalingProportionsScaleMultiplierMethod;
    private Field customizationWingsField;
    private Field clientConfigSmallSizeAnimationSpeedFactorField;
    private Field clientConfigLargeSizeAnimationSpeedFactorField;
    private Field clientConfigMovementAnimationSpeedFactorField;
    private Field clientConfigMaxAnimationSpeedFactorField;
    private Field clientConfigMaxAnimationSpeedField;
    private Field clientConfigMinAnimationSpeedField;
    private Method dragonArmorRenderLayerInitArmorMasksMethod;
    private Method dragonArmorRenderLayerConstructTrimmedDragonArmorTextureMethod;
    private Object dragonArmorRenderLayerInstance;
    private boolean armorRenderingAvailable;

    public PreparedDragonRender prepare(final AbstractClientPlayer player, final float partialTick) {
        ensureInitialized();
        if (!available) {
            return null;
        }

        try {
            if (!(boolean) dragonStateProviderIsDragonMethod.invoke(null, player)) {
                return null;
            }

            Object handler = dragonStateProviderGetDataMethod.invoke(null, player);
            Object dragon = clientDragonRendererGetOrCreateDragonMethod.invoke(null, player);

            float realtimeDeltaTick = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
            clientDragonRendererSetDragonMovementDataMethod.invoke(null, player, realtimeDeltaTick);

            Object movement = movementDataGetDataMethod.invoke(null, player);
            clientDragonRendererHandleFlightMovementMethod.invoke(null, player, dragon, movement, partialTick);

            Object dragonModel = dragonModelField.get(null);
            if (dragonModel == null) {
                return null;
            }

            ResourceLocation modelLocation = (ResourceLocation) dragonModelGetModelResourceMethod.invoke(dragonModel, dragon);
            ResourceLocation textureLocation = (ResourceLocation) dragonModelGetTextureResourceMethod.invoke(dragonModel, dragon);
            ResourceLocation animationLocation = (ResourceLocation) dragonModelGetAnimationResourceMethod.invoke(dragonModel, dragon);
            float visualScale = ((Double) handlerGetVisualScaleMethod.invoke(handler, player, partialTick)).floatValue();

            double bodyYaw = movementBodyYawField.getDouble(movement);
            double headYaw = movementHeadYawField.getDouble(movement);
            double headPitch = movementHeadPitchField.getDouble(movement);
            Vec3 deltaMovement = (Vec3) movementDeltaMovementField.get(movement);
            Vec3 desiredMoveVec = (Vec3) movementDesiredMoveVecField.get(movement);
            float movementPrevXRot = movementPrevXRotField.getFloat(movement);
            boolean dig = movementDigField.getBoolean(movement);
            boolean bite = movementBiteField.getBoolean(movement);
            boolean flying = (boolean) serverFlightIsFlyingMethod.invoke(null, player);
            boolean gliding = (boolean) serverFlightIsGlidingMethod.invoke(null, player);
            boolean spin = (boolean) serverFlightIsSpinMethod.invoke(null, player);
            double distanceFromGround = ((Double) serverFlightDistanceFromGroundMethod.invoke(null, player)).doubleValue();
            boolean consideredSwimming = (boolean) dragonEntityIsSwimmingMethod.invoke(null, player);
            boolean resting = (boolean) treasureRestIsRestingMethod.invoke(treasureRestGetDataMethod.invoke(null, player));
            boolean ridingSkyhook = (boolean) skyhookRidingMethod.invoke(null, player.getUUID());
            boolean canPoseFitStanding = (boolean) dragonSizeCanPoseFitMethod.invoke(null, player, Pose.STANDING);
            boolean canPoseFitCrouching = (boolean) dragonSizeCanPoseFitMethod.invoke(null, player, Pose.CROUCHING);
            boolean onMagicSource = handlerIsOnMagicSourceField.getBoolean(handler);
            boolean renderItemsInMouth = Boolean.TRUE.equals(clientDragonRendererRenderItemsInMouthField.get(null));
            boolean usingEdibleItem = player.isUsingItem() && (boolean) dragonFoodHandlerIsEdibleMethod.invoke(null, player, player.getItemInHand(player.getUsedItemHand()));
            PreparedAbilityAnimation abilityAnimation = resolveAbilityAnimation(dragon);
            List<PreparedEmoteSlot> activeEmoteSlots = resolveActiveEmoteSlots(dragon);
            boolean jumpTriggered = isJumpTriggered(player.getId());

            RenderCustomization customization = resolveCustomization(handler);
            PreparedAnimationSpeedConfig animationSpeedConfig = resolveAnimationSpeedConfig();

            return new PreparedDragonRender(
                    player,
                    dragon,
                    handler,
                    movement,
                    modelLocation,
                    textureLocation,
                    animationLocation,
                    partialTick,
                    visualScale,
                    bodyYaw,
                    headYaw,
                    headPitch,
                    deltaMovement,
                    desiredMoveVec,
                    movementPrevXRot,
                    dragonPrevXRotField.getFloat(dragon),
                    dragonPrevZRotField.getFloat(dragon),
                    flying,
                    gliding,
                    spin,
                    consideredSwimming,
                    resting,
                    onMagicSource,
                    canPoseFitStanding,
                    canPoseFitCrouching,
                    ridingSkyhook,
                    jumpTriggered,
                    dig,
                    bite,
                    usingEdibleItem,
                    renderItemsInMouth,
                    abilityAnimation,
                    activeEmoteSlots,
                    desiredMoveVec.x * desiredMoveVec.x + desiredMoveVec.z * desiredMoveVec.z > 1.0E-12,
                    player.isUsingItem() || !activeEmoteSlots.isEmpty() || abilityAnimation != null || ridingSkyhook,
                    animationSpeedConfig,
                    customization.renderWings(),
                    customization.bodyScaleMultiplier(),
                    customization.bonesToHideForToggle(),
                    distanceFromGround
            );
        } catch (Throwable throwable) {
            DragonSurvivalBedrockRenderer.LOGGER.error("DSBR 读取 Dragon Survival 运行时状态失败，本帧已回退到原版渲染", throwable);
            return null;
        }
    }

    private RenderCustomization resolveCustomization(final Object handler) throws Exception {
        Object bodyHolderObject = handlerBodyMethod.invoke(handler);
        if (!(bodyHolderObject instanceof Holder<?> holder)) {
            return RenderCustomization.DEFAULT;
        }

        Object body = holder.value();
        if (body == null) {
            return RenderCustomization.DEFAULT;
        }

        double bodyScaleMultiplier = 1.0D;
        Object scalingProportions = bodyScalingProportionsMethod.invoke(body);
        if (scalingProportions != null) {
            bodyScaleMultiplier = ((Double) scalingProportionsScaleMultiplierMethod.invoke(scalingProportions)).doubleValue();
        }

        boolean renderWings = true;
        if ((boolean) bodyCanHideWingsMethod.invoke(body)) {
            Object customization = handlerGetCurrentStageCustomizationMethod.invoke(handler);
            renderWings = customizationWingsField.getBoolean(customization);
        }

        @SuppressWarnings("unchecked")
        List<String> bonesToHide = (List<String>) bodyBonesToHideForToggleMethod.invoke(body);
        return new RenderCustomization(renderWings, bodyScaleMultiplier, bonesToHide == null ? List.of() : List.copyOf(bonesToHide));
    }

    private boolean isJumpTriggered(final int playerId) throws IllegalAccessException {
        @SuppressWarnings("unchecked")
        Map<Integer, Boolean> jumping = (Map<Integer, Boolean>) dragonsJumpingField.get(null);
        return jumping.getOrDefault(playerId, false);
    }

    private PreparedAnimationSpeedConfig resolveAnimationSpeedConfig() throws IllegalAccessException {
        PreparedAnimationSpeedConfig defaults = PreparedAnimationSpeedConfig.DEFAULT;
        return new PreparedAnimationSpeedConfig(
                getStaticDouble(clientConfigSmallSizeAnimationSpeedFactorField, defaults.smallSizeAnimationSpeedFactor()),
                getStaticDouble(clientConfigLargeSizeAnimationSpeedFactorField, defaults.largeSizeAnimationSpeedFactor()),
                getStaticDouble(clientConfigMovementAnimationSpeedFactorField, defaults.movementAnimationSpeedFactor()),
                getStaticDouble(clientConfigMaxAnimationSpeedFactorField, defaults.maxAnimationSpeedFactor()),
                getStaticDouble(clientConfigMaxAnimationSpeedField, defaults.maxAnimationSpeed()),
                getStaticDouble(clientConfigMinAnimationSpeedField, defaults.minAnimationSpeed())
        );
    }

    public boolean canRenderArmorWithBedrock() {
        ensureInitialized();
        return armorRenderingAvailable;
    }

    public boolean hasVisibleArmor(final Player player) {
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (!slot.isArmor()) {
                continue;
            }

            if (!player.getItemBySlot(slot).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    public ResourceLocation resolveArmorTexture(final PreparedDragonRender prepared) {
        ensureInitialized();
        if (!armorRenderingAvailable || prepared == null || !hasVisibleArmor(prepared.player())) {
            return null;
        }

        try {
            ResourceLocation armorModel = (ResourceLocation) handlerGetModelMethod.invoke(prepared.handler());
            if (armorModel == null) {
                return null;
            }

            dragonArmorRenderLayerInitArmorMasksMethod.invoke(dragonArmorRenderLayerInstance, armorModel);
            Object result = dragonArmorRenderLayerConstructTrimmedDragonArmorTextureMethod.invoke(null, prepared.player());
            if (result instanceof java.util.Optional<?> optional) {
                Object value = optional.orElse(null);
                if (value instanceof ResourceLocation resourceLocation) {
                    return resourceLocation;
                }
            }
        } catch (Throwable throwable) {
            DragonSurvivalBedrockRenderer.LOGGER.error("DSBR 读取 Dragon Survival 装备贴图失败，本帧将继续只渲染 Bedrock 本体", throwable);
        }

        return null;
    }

    private PreparedAbilityAnimation resolveAbilityAnimation(final Object dragon) throws Exception {
        Object abilityPair = dragonCurrentAbilityAnimationField.get(dragon);
        if (abilityPair == null) {
            return null;
        }

        Object abilityAnimation = pairGetFirstMethod.invoke(abilityPair);
        Object animationType = pairGetSecondMethod.invoke(abilityPair);
        if (abilityAnimation == null || animationType == null) {
            return null;
        }

        AbilityAnimationLayer layer = resolveAbilityLayer(abilityAnimationGetLayerMethod.invoke(abilityAnimation));
        if (layer == null) {
            return null;
        }

        boolean locksHead = (boolean) abilityAnimationLocksHeadMethod.invoke(abilityAnimation);
        boolean locksTail = (boolean) abilityAnimationLocksTailMethod.invoke(abilityAnimation);
        if (compoundAbilityAnimationClass.isInstance(abilityAnimation)) {
            return new PreparedAbilityAnimation(
                    layer,
                    SpecialAnimationPlayback.START_THEN_LOOP,
                    (String) compoundAbilityAnimationStartingAnimationKeyMethod.invoke(abilityAnimation),
                    (String) compoundAbilityAnimationLoopingAnimationKeyMethod.invoke(abilityAnimation),
                    locksHead,
                    locksTail
            );
        }

        String primaryAnimationKey = simpleAbilityAnimationClass.isInstance(abilityAnimation)
                ? (String) simpleAbilityAnimationAnimationKeyMethod.invoke(abilityAnimation)
                : (String) abilityAnimationGetNameMethod.invoke(abilityAnimation);
        SpecialAnimationPlayback playback = resolvePlayback(animationType);
        return playback == null ? null : new PreparedAbilityAnimation(layer, playback, primaryAnimationKey, null, locksHead, locksTail);
    }

    private List<PreparedEmoteSlot> resolveActiveEmoteSlots(final Object dragon) throws Exception {
        Object emoteArrayObject = dragonGetCurrentlyPlayingEmotesMethod.invoke(dragon);
        if (!(emoteArrayObject instanceof Object[] emoteArray) || emoteArray.length == 0) {
            return List.of();
        }

        List<PreparedEmoteSlot> activeEmoteSlots = new ArrayList<>();
        for (int slot = 0; slot < emoteArray.length; slot++) {
            Object emote = emoteArray[slot];
            if (emote == null) {
                continue;
            }

            activeEmoteSlots.add(new PreparedEmoteSlot(slot, new PreparedEmote(
                    (String) dragonEmoteAnimationKeyMethod.invoke(emote),
                    ((Double) dragonEmoteSpeedMethod.invoke(emote)).doubleValue(),
                    (boolean) dragonEmoteLoopsMethod.invoke(emote),
                    (boolean) dragonEmoteBlendMethod.invoke(emote),
                    (boolean) dragonEmoteLocksHeadMethod.invoke(emote),
                    (boolean) dragonEmoteLocksTailMethod.invoke(emote)
            )));
        }

        return activeEmoteSlots.isEmpty() ? List.of() : List.copyOf(activeEmoteSlots);
    }

    private AbilityAnimationLayer resolveAbilityLayer(final Object layerValue) {
        if (!(layerValue instanceof Enum<?> enumValue)) {
            return null;
        }

        return switch (enumValue.name()) {
            case "BASE" -> AbilityAnimationLayer.BASE;
            case "BITE" -> AbilityAnimationLayer.BITE;
            case "BREATH" -> AbilityAnimationLayer.BREATH;
            default -> null;
        };
    }

    private SpecialAnimationPlayback resolvePlayback(final Object playbackValue) {
        if (!(playbackValue instanceof Enum<?> enumValue)) {
            return null;
        }

        return switch (enumValue.name()) {
            case "PLAY_ONCE" -> SpecialAnimationPlayback.PLAY_ONCE;
            case "LOOPING" -> SpecialAnimationPlayback.LOOPING;
            case "PLAY_AND_HOLD" -> SpecialAnimationPlayback.PLAY_AND_HOLD;
            default -> null;
        };
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }

        initialized = true;
        if (!ModList.get().isLoaded(DRAGON_SURVIVAL_MOD_ID)) {
            available = false;
            armorRenderingAvailable = false;
            return;
        }

        try {
            Class<?> dragonStateProviderClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateProvider");
            Class<?> dragonEntityClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity");
            Class<?> clientDragonRendererClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.client.render.ClientDragonRenderer");
            Class<?> movementDataClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.registry.attachments.MovementData");
            Class<?> dragonSurvivalClientClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.client.DragonSurvivalClient");
            Class<?> dragonStateHandlerClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateHandler");
            Class<?> dragonArmorRenderLayerClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.client.render.entity.dragon.DragonArmorRenderLayer");
            Class<?> serverFlightHandlerClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.server.handlers.ServerFlightHandler");
            Class<?> dragonSizeHandlerClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.common.handlers.DragonSizeHandler");
            Class<?> dragonFoodHandlerClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.common.handlers.DragonFoodHandler");
            Class<?> clientConfigClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.config.ClientConfig");
            Class<?> treasureRestDataClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.registry.attachments.TreasureRestData");
            Class<?> dragonEmoteClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.registry.dragon.body.emotes.DragonEmote");
            Class<?> pairClass = Class.forName("com.mojang.datafixers.util.Pair");
            Class<?> abilityAnimationClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.common.codecs.ability.animation.AbilityAnimation");
            Class<?> geoEntityRendererClass = Class.forName("software.bernie.geckolib.renderer.GeoEntityRenderer");
            simpleAbilityAnimationClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.common.codecs.ability.animation.SimpleAbilityAnimation");
            compoundAbilityAnimationClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.common.codecs.ability.animation.CompoundAbilityAnimation");
            Class<?> skyhookHelperClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.compat.create.SkyhookRendererHelper");

            dragonStateProviderIsDragonMethod = dragonStateProviderClass.getMethod("isDragon", Entity.class);
            dragonStateProviderGetDataMethod = dragonStateProviderClass.getMethod("getData", Player.class);

            clientDragonRendererGetOrCreateDragonMethod = clientDragonRendererClass.getMethod("getOrCreateDragon", Player.class);
            clientDragonRendererSetDragonMovementDataMethod = clientDragonRendererClass.getMethod("setDragonMovementData", Player.class, float.class);
            clientDragonRendererHandleFlightMovementMethod = clientDragonRendererClass.getDeclaredMethod("handleFlightMovement", Player.class, dragonEntityClass, movementDataClass, float.class);
            clientDragonRendererHandleFlightMovementMethod.setAccessible(true);
            clientDragonRendererRenderItemsInMouthField = clientDragonRendererClass.getField("renderItemsInMouth");

            movementDataGetDataMethod = movementDataClass.getMethod("getData", Entity.class);

            dragonModelField = dragonSurvivalClientClass.getField("DRAGON_MODEL");
            Object dragonModel = dragonModelField.get(null);
            dragonModelGetModelResourceMethod = dragonModel.getClass().getMethod("getModelResource", dragonEntityClass);
            dragonModelGetTextureResourceMethod = dragonModel.getClass().getMethod("getTextureResource", dragonEntityClass);
            dragonModelGetAnimationResourceMethod = dragonModel.getClass().getMethod("getAnimationResource", dragonEntityClass);

            handlerGetVisualScaleMethod = dragonStateHandlerClass.getMethod("getVisualScale", Player.class, float.class);
            handlerGetModelMethod = dragonStateHandlerClass.getMethod("getModel");
            handlerBodyMethod = dragonStateHandlerClass.getMethod("body");
            handlerGetCurrentStageCustomizationMethod = dragonStateHandlerClass.getMethod("getCurrentStageCustomization");
            handlerIsOnMagicSourceField = dragonStateHandlerClass.getField("isOnMagicSource");

            dragonArmorRenderLayerInitArmorMasksMethod = dragonArmorRenderLayerClass.getDeclaredMethod("initArmorMasks", ResourceLocation.class);
            dragonArmorRenderLayerInitArmorMasksMethod.setAccessible(true);
            dragonArmorRenderLayerConstructTrimmedDragonArmorTextureMethod = dragonArmorRenderLayerClass.getDeclaredMethod("constructTrimmedDragonArmorTexture", Player.class);
            dragonArmorRenderLayerConstructTrimmedDragonArmorTextureMethod.setAccessible(true);
            dragonArmorRenderLayerInstance = dragonArmorRenderLayerClass.getConstructor(geoEntityRendererClass).newInstance(new Object[] { null });

            serverFlightIsFlyingMethod = serverFlightHandlerClass.getMethod("isFlying", Player.class);
            serverFlightIsGlidingMethod = serverFlightHandlerClass.getMethod("isGliding", Player.class);
            serverFlightIsSpinMethod = serverFlightHandlerClass.getMethod("isSpin", Player.class);
            serverFlightDistanceFromGroundMethod = serverFlightHandlerClass.getMethod("distanceFromGround", Player.class);

            dragonEntityIsSwimmingMethod = dragonEntityClass.getMethod("isConsideredSwimmingForAnimation", Player.class);
            dragonSizeCanPoseFitMethod = dragonSizeHandlerClass.getMethod("canPoseFit", Player.class, Pose.class);
            dragonFoodHandlerIsEdibleMethod = dragonFoodHandlerClass.getMethod("isEdible", Player.class, net.minecraft.world.item.ItemStack.class);
            treasureRestGetDataMethod = treasureRestDataClass.getMethod("getData", Player.class);
            treasureRestIsRestingMethod = treasureRestDataClass.getMethod("isResting");
            skyhookRidingMethod = skyhookHelperClass.getMethod("isPlayerRidingSkyhook", java.util.UUID.class);

            dragonGetCurrentlyPlayingEmotesMethod = dragonEntityClass.getMethod("getCurrentlyPlayingEmotes");
            dragonCurrentAbilityAnimationField = dragonEntityClass.getDeclaredField("currentAbilityAnimation");
            dragonCurrentAbilityAnimationField.setAccessible(true);
            pairGetFirstMethod = pairClass.getMethod("getFirst");
            pairGetSecondMethod = pairClass.getMethod("getSecond");
            abilityAnimationGetLayerMethod = abilityAnimationClass.getMethod("getLayer");
            abilityAnimationGetNameMethod = abilityAnimationClass.getMethod("getName");
            abilityAnimationLocksHeadMethod = abilityAnimationClass.getMethod("locksHead");
            abilityAnimationLocksTailMethod = abilityAnimationClass.getMethod("locksTail");
            simpleAbilityAnimationAnimationKeyMethod = simpleAbilityAnimationClass.getMethod("animationKey");
            compoundAbilityAnimationStartingAnimationKeyMethod = compoundAbilityAnimationClass.getMethod("startingAnimationKey");
            compoundAbilityAnimationLoopingAnimationKeyMethod = compoundAbilityAnimationClass.getMethod("loopingAnimationKey");
            dragonEmoteAnimationKeyMethod = dragonEmoteClass.getMethod("animationKey");
            dragonEmoteSpeedMethod = dragonEmoteClass.getMethod("speed");
            dragonEmoteLoopsMethod = dragonEmoteClass.getMethod("loops");
            dragonEmoteBlendMethod = dragonEmoteClass.getMethod("blend");
            dragonEmoteLocksHeadMethod = dragonEmoteClass.getMethod("locksHead");
            dragonEmoteLocksTailMethod = dragonEmoteClass.getMethod("locksTail");
            dragonsJumpingField = dragonEntityClass.getField("DRAGONS_JUMPING");
            dragonPrevXRotField = dragonEntityClass.getField("prevXRot");
            dragonPrevZRotField = dragonEntityClass.getField("prevZRot");

            movementBodyYawField = movementDataClass.getField("bodyYaw");
            movementHeadYawField = movementDataClass.getField("headYaw");
            movementHeadPitchField = movementDataClass.getField("headPitch");
            movementDeltaMovementField = movementDataClass.getField("deltaMovement");
            movementDesiredMoveVecField = movementDataClass.getField("desiredMoveVec");
            movementPrevXRotField = movementDataClass.getField("prevXRot");
            movementDigField = movementDataClass.getField("dig");
            movementBiteField = movementDataClass.getField("bite");
            sharedMovementBiteField = movementBiteField;
            clientConfigSmallSizeAnimationSpeedFactorField = getOptionalField(clientConfigClass, "smallSizeAnimationSpeedFactor");
            clientConfigLargeSizeAnimationSpeedFactorField = getOptionalField(clientConfigClass, "largeSizeAnimationSpeedFactor");
            clientConfigMovementAnimationSpeedFactorField = getOptionalField(clientConfigClass, "movementAnimationSpeedFactor");
            clientConfigMaxAnimationSpeedFactorField = getOptionalField(clientConfigClass, "maxAnimationSpeedFactor");
            clientConfigMaxAnimationSpeedField = getOptionalField(clientConfigClass, "maxAnimationSpeed");
            clientConfigMinAnimationSpeedField = getOptionalField(clientConfigClass, "minAnimationSpeed");

            Player localPlayer = Minecraft.getInstance().player;
            if (localPlayer != null) {
                Object localHandler = dragonStateProviderGetDataMethod.invoke(null, localPlayer);
                Object customization = handlerGetCurrentStageCustomizationMethod.invoke(localHandler);
                customizationWingsField = customization.getClass().getField("wings");

                Object bodyHolder = handlerBodyMethod.invoke(localHandler);
                if (bodyHolder instanceof Holder<?> holder && holder.value() != null) {
                    Object body = holder.value();
                    bodyCanHideWingsMethod = body.getClass().getMethod("canHideWings");
                    bodyBonesToHideForToggleMethod = body.getClass().getMethod("bonesToHideForToggle");
                    bodyScalingProportionsMethod = body.getClass().getMethod("scalingProportions");
                    Object scalingProportions = bodyScalingProportionsMethod.invoke(body);
                    scalingProportionsScaleMultiplierMethod = scalingProportions.getClass().getMethod("scaleMultiplier");
                }
            }

            available = bodyCanHideWingsMethod != null
                    && bodyBonesToHideForToggleMethod != null
                    && bodyScalingProportionsMethod != null
                    && scalingProportionsScaleMultiplierMethod != null
                    && customizationWingsField != null
                    && dragonFoodHandlerIsEdibleMethod != null
                    && dragonGetCurrentlyPlayingEmotesMethod != null
                    && clientDragonRendererRenderItemsInMouthField != null
                    && pairGetFirstMethod != null
                    && pairGetSecondMethod != null
                    && abilityAnimationGetLayerMethod != null
                    && abilityAnimationGetNameMethod != null
                    && abilityAnimationLocksHeadMethod != null
                    && abilityAnimationLocksTailMethod != null
                    && simpleAbilityAnimationAnimationKeyMethod != null
                    && compoundAbilityAnimationStartingAnimationKeyMethod != null
                    && compoundAbilityAnimationLoopingAnimationKeyMethod != null
                    && dragonEmoteAnimationKeyMethod != null
                    && dragonEmoteSpeedMethod != null
                    && dragonEmoteLoopsMethod != null
                    && dragonEmoteBlendMethod != null
                    && dragonEmoteLocksHeadMethod != null
                    && dragonEmoteLocksTailMethod != null;
            armorRenderingAvailable = handlerGetModelMethod != null
                    && dragonArmorRenderLayerInitArmorMasksMethod != null
                    && dragonArmorRenderLayerConstructTrimmedDragonArmorTextureMethod != null
                    && dragonArmorRenderLayerInstance != null;
        } catch (Throwable throwable) {
            available = false;
            armorRenderingAvailable = false;
            DragonSurvivalBedrockRenderer.LOGGER.error("DSBR 初始化 Dragon Survival 反射桥失败，模组将保持旁路状态", throwable);
        }
    }

    public record PreparedDragonRender(
            AbstractClientPlayer player,
            Object dragon,
            Object handler,
            Object movement,
            ResourceLocation modelLocation,
            ResourceLocation textureLocation,
            ResourceLocation animationLocation,
            float partialTick,
            float visualScale,
            double bodyYaw,
            double headYaw,
            double headPitch,
            Vec3 deltaMovement,
            Vec3 desiredMoveVec,
            float movementPrevXRot,
            float dragonPrevXRot,
            float dragonPrevZRot,
            boolean flying,
            boolean gliding,
            boolean spin,
            boolean consideredSwimming,
            boolean resting,
            boolean onMagicSource,
            boolean canPoseFitStanding,
            boolean canPoseFitCrouching,
            boolean ridingSkyhook,
            boolean jumpTriggered,
            boolean dig,
            boolean bite,
            boolean usingEdibleItem,
            boolean renderItemsInMouth,
            PreparedAbilityAnimation abilityAnimation,
            List<PreparedEmoteSlot> activeEmoteSlots,
            boolean movingHorizontally,
            boolean usesSpecialAnimation,
            PreparedAnimationSpeedConfig animationSpeedConfig,
            boolean renderWings,
            double bodyScaleMultiplier,
            List<String> bonesToHideForToggle,
            double distanceFromGround
    ) {
        public List<String> bonesToHideForToggle() {
            return bonesToHideForToggle == null ? Collections.emptyList() : bonesToHideForToggle;
        }

        public List<PreparedEmoteSlot> activeEmoteSlots() {
            return activeEmoteSlots == null ? Collections.emptyList() : activeEmoteSlots;
        }

        public PreparedAnimationSpeedConfig animationSpeedConfig() {
            return animationSpeedConfig == null ? PreparedAnimationSpeedConfig.DEFAULT : animationSpeedConfig;
        }

        public void consumeBiteFlag() {
            DSRuntimeBridge.setMovementBiteFlag(movement, false);
        }
    }

    public record PreparedAnimationSpeedConfig(
            double smallSizeAnimationSpeedFactor,
            double largeSizeAnimationSpeedFactor,
            double movementAnimationSpeedFactor,
            double maxAnimationSpeedFactor,
            double maxAnimationSpeed,
            double minAnimationSpeed
    ) {
        public static final PreparedAnimationSpeedConfig DEFAULT = new PreparedAnimationSpeedConfig(0.3D, 1.0D, 1.0D, 3.0D, 1.5D, 0.2D);
    }

    public enum AbilityAnimationLayer {
        BASE,
        BITE,
        BREATH
    }

    public enum SpecialAnimationPlayback {
        PLAY_ONCE,
        LOOPING,
        PLAY_AND_HOLD,
        START_THEN_LOOP
    }

    public record PreparedAbilityAnimation(
            AbilityAnimationLayer layer,
            SpecialAnimationPlayback playback,
            String primaryAnimationKey,
            String secondaryAnimationKey,
            boolean locksHead,
            boolean locksTail
    ) {
    }

    public record PreparedEmote(
            String animationKey,
            double speed,
            boolean loops,
            boolean blend,
            boolean locksHead,
            boolean locksTail
    ) {
    }

    public record PreparedEmoteSlot(int slot, PreparedEmote emote) {
    }

    private record RenderCustomization(boolean renderWings, double bodyScaleMultiplier, List<String> bonesToHideForToggle) {
        private static final RenderCustomization DEFAULT = new RenderCustomization(true, 1.0D, List.of());
    }

    private Field getOptionalField(final Class<?> owner, final String name) {
        try {
            return owner.getField(name);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private double getStaticDouble(final Field field, final double fallback) throws IllegalAccessException {
        if (field == null) {
            return fallback;
        }

        Object value = field.get(null);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static void setMovementBiteFlag(final Object movement, final boolean bite) {
        if (movement == null || sharedMovementBiteField == null) {
            return;
        }

        try {
            sharedMovementBiteField.setBoolean(movement, bite);
        } catch (IllegalAccessException ignored) {
        }
    }
}
