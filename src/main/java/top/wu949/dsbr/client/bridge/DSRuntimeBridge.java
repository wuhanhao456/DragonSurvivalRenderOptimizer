package top.wu949.dsbr.client.bridge;

import top.wu949.dsbr.DragonSurvivalBedrockRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

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
    private Method dragonEntityGetPlayerMethod;
    private Method dragonGetCurrentlyPlayingEmotesMethod;
    private Field clientDragonRendererRenderItemsInMouthField;
    private Field clientDragonRendererRenderHeldItemField;
    private Field dragonCurrentAbilityAnimationField;
    private Method handlerSpeciesMethod;
    private Method handlerStageKeyMethod;
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
    private Field customizationDefaultSkinField;
    private Field customizationLayerSettingsField;
    private Method lazyGetMethod;
    private Field layerSettingsIsGlowingField;
    private Method dragonSkinsGetGlowTextureMethod;
    private Method stageResourcesGetDefaultSkinMethod;
    private Method dragonModelDynamicTextureMethod;
    private Method renderingUtilsHasTextureMethod;
    private Field dragonBodyDefaultModelField;
    private Method betterCombatIsAttackingMethod;
    private Method dragonArmorRenderLayerInitArmorMasksMethod;
    private Method dragonArmorRenderLayerConstructTrimmedDragonArmorTextureMethod;
    private Method bodyBackpackOffsetsMethod;
    private Method backpackOffsetsPosOffsetMethod;
    private Method backpackOffsetsRotOffsetMethod;
    private Method backpackOffsetsScaleMethod;
    private Field dragonBackpackShouldRenderField;
    private Field dragonBackpackCuriosSlotField;
    private Class<?> sophisticatedBackpackItemClass;
    private Method curiosApiGetCuriosInventoryMethod;
    private Method curiosHandlerFindCuriosMethod;
    private Method slotResultSlotContextMethod;
    private Method slotContextVisibleMethod;
    private Method slotResultStackMethod;
    private Class<?> dragonEntityClass;
    private Class<?> fakeClientPlayerClass;
    private Field fakeClientPlayerHandlerField;
    private Field fakeClientPlayerAnimationSupplierField;
    private Object dragonArmorRenderLayerInstance;
    private boolean armorRenderingAvailable;
    private boolean glowRenderingAvailable;
    private boolean backpackRenderingAvailable;
    private final Map<UUID, GlowTextureCacheEntry> glowTextureCache = new HashMap<>();
    private final Map<UUID, ArmorVisibilityCacheEntry> armorVisibilityCache = new HashMap<>();
    private final Map<UUID, ArmorTextureCacheEntry> armorTextureCache = new HashMap<>();
    private final Map<UUID, BackpackRenderCacheEntry> backpackRenderCache = new HashMap<>();

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
            return buildPreparedDragonRender(player, dragon, handler, movement, partialTick, null, false);
        } catch (Throwable throwable) {
            DragonSurvivalBedrockRenderer.LOGGER.error("DSBR failed to read Dragon Survival runtime state, falling back to the original renderer for this frame.", throwable);
            return null;
        }
    }

    public PreparedDragonRender prepareExternalDragonRender(final Entity dragonEntity, final float partialTick) {
        ensureInitialized();
        if (!available || dragonEntity == null || dragonEntityClass == null || !dragonEntityClass.isInstance(dragonEntity)) {
            return null;
        }

        try {
            Object playerObject = dragonEntityGetPlayerMethod.invoke(dragonEntity);
            if (!(playerObject instanceof AbstractClientPlayer player)) {
                return null;
            }

            boolean fakePlayer = fakeClientPlayerClass != null && fakeClientPlayerClass.isInstance(player);
            Object handler;
            String forcedAnimationKey = null;

            if (fakePlayer) {
                handler = fakeClientPlayerHandlerField != null ? fakeClientPlayerHandlerField.get(player) : null;
                forcedAnimationKey = resolveForcedAnimationKey(player);
            } else {
                if (!(boolean) dragonStateProviderIsDragonMethod.invoke(null, player)) {
                    return null;
                }

                handler = dragonStateProviderGetDataMethod.invoke(null, player);

                float realtimeDeltaTick = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
                clientDragonRendererSetDragonMovementDataMethod.invoke(null, player, realtimeDeltaTick);
            }

            if (handler == null) {
                return null;
            }

            Object movement = movementDataGetDataMethod.invoke(null, player);
            if (movement == null) {
                return null;
            }

            if (!fakePlayer) {
                clientDragonRendererHandleFlightMovementMethod.invoke(null, player, dragonEntity, movement, partialTick);
            }

            return buildPreparedDragonRender(player, dragonEntity, handler, movement, partialTick, forcedAnimationKey, fakePlayer);
        } catch (Throwable throwable) {
            DragonSurvivalBedrockRenderer.LOGGER.error("DSBR failed to read Dragon Survival player render state, falling back to the original renderer for this frame.", throwable);
            return null;
        }
    }

    private PreparedDragonRender buildPreparedDragonRender(
            final AbstractClientPlayer player,
            final Object dragon,
            final Object handler,
            final Object movement,
            final float partialTick,
            final String forcedAnimationKey,
            final boolean fakePlayer
    ) throws Exception {
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
        boolean flying = !fakePlayer && (boolean) serverFlightIsFlyingMethod.invoke(null, player);
        boolean gliding = !fakePlayer && (boolean) serverFlightIsGlidingMethod.invoke(null, player);
        boolean spin = !fakePlayer && (boolean) serverFlightIsSpinMethod.invoke(null, player);
        double distanceFromGround = fakePlayer ? 0.0D : ((Double) serverFlightDistanceFromGroundMethod.invoke(null, player)).doubleValue();
        boolean consideredSwimming = !fakePlayer && (boolean) dragonEntityIsSwimmingMethod.invoke(null, player);
        boolean resting = !fakePlayer && (boolean) treasureRestIsRestingMethod.invoke(treasureRestGetDataMethod.invoke(null, player));
        boolean ridingSkyhook = !fakePlayer && (boolean) skyhookRidingMethod.invoke(null, player.getUUID());
        boolean canPoseFitStanding = fakePlayer || (boolean) dragonSizeCanPoseFitMethod.invoke(null, player, Pose.STANDING);
        boolean canPoseFitCrouching = fakePlayer || (boolean) dragonSizeCanPoseFitMethod.invoke(null, player, Pose.CROUCHING);
        boolean onMagicSource = handlerIsOnMagicSourceField.getBoolean(handler);
        boolean renderItemsInMouth = Boolean.TRUE.equals(clientDragonRendererRenderItemsInMouthField.get(null));
        boolean usingEdibleItem = !fakePlayer
                && player.isUsingItem()
                && (boolean) dragonFoodHandlerIsEdibleMethod.invoke(null, player, player.getItemInHand(player.getUsedItemHand()));
        PreparedAbilityAnimation abilityAnimation = fakePlayer ? null : resolveAbilityAnimation(dragon);
        List<PreparedEmoteSlot> activeEmoteSlots = fakePlayer ? List.of() : resolveActiveEmoteSlots(dragon);
        boolean jumpTriggered = !fakePlayer && isJumpTriggered(player.getId());

        RenderCustomization customization = resolveCustomization(handler);
        PreparedAnimationSpeedConfig animationSpeedConfig = resolveAnimationSpeedConfig();
        boolean movingHorizontally = desiredMoveVec.x * desiredMoveVec.x + desiredMoveVec.z * desiredMoveVec.z > 1.0E-12;

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
                movingHorizontally,
                !fakePlayer && (player.isUsingItem() || !activeEmoteSlots.isEmpty() || abilityAnimation != null || ridingSkyhook),
                animationSpeedConfig,
                customization.renderWings(),
                customization.bodyScaleMultiplier(),
                customization.bonesToHideForToggle(),
                distanceFromGround,
                forcedAnimationKey
        );
    }

    private String resolveForcedAnimationKey(final AbstractClientPlayer player) throws IllegalAccessException {
        if (fakeClientPlayerClass == null || fakeClientPlayerAnimationSupplierField == null || player == null || !fakeClientPlayerClass.isInstance(player)) {
            return null;
        }

        Object supplierObject = fakeClientPlayerAnimationSupplierField.get(player);
        if (!(supplierObject instanceof Supplier<?> supplier)) {
            return null;
        }

        Object value = supplier.get();
        if (!(value instanceof String animationKey) || animationKey.isBlank()) {
            return null;
        }

        return animationKey;
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

    public ResourceLocation resolveGlowTexture(final PreparedDragonRender prepared) {
        ensureInitialized();
        if (!glowRenderingAvailable || prepared == null) {
            return null;
        }

        try {
            ResourceLocation currentModel = (ResourceLocation) handlerGetModelMethod.invoke(prepared.handler());
            ResourceLocation defaultModel = dragonBodyDefaultModelField != null ? (ResourceLocation) dragonBodyDefaultModelField.get(null) : null;
            Object stageKey = handlerStageKeyMethod.invoke(prepared.handler());
            Object customization = handlerGetCurrentStageCustomizationMethod.invoke(prepared.handler());
            boolean defaultSkin = customization != null && customizationDefaultSkinField.getBoolean(customization);
            boolean glowingCustomLayer = hasGlowingCustomLayer(customization);
            Object species = defaultSkin ? handlerSpeciesMethod.invoke(prepared.handler()) : null;
            GlowTextureCacheKey cacheKey = new GlowTextureCacheKey(prepared.textureLocation(), currentModel, stageKey, species, defaultSkin, glowingCustomLayer);
            GlowTextureCacheEntry cachedEntry = glowTextureCache.get(prepared.player().getUUID());
            if (cachedEntry != null && cachedEntry.matches(cacheKey)) {
                return cachedEntry.texture();
            }

            ResourceLocation glowTexture = null;

            if (defaultModel != null && defaultModel.equals(currentModel)) {
                Object customGlow = dragonSkinsGetGlowTextureMethod.invoke(null, prepared.player(), stageKey);
                if (customGlow instanceof ResourceLocation resourceLocation && hasTexture(resourceLocation)) {
                    glowTexture = resourceLocation;
                }
            }

            if (glowTexture == null && defaultSkin) {
                Object defaultGlow = stageResourcesGetDefaultSkinMethod.invoke(null, species, stageKey, true);
                if (defaultGlow instanceof ResourceLocation resourceLocation && hasTexture(resourceLocation)) {
                    glowTexture = resourceLocation;
                }
            }

            if (glowTexture == null && glowingCustomLayer) {
                Object dynamicGlow = dragonModelDynamicTextureMethod.invoke(null, prepared.player(), prepared.handler(), true);
                if (dynamicGlow instanceof ResourceLocation resourceLocation && hasTexture(resourceLocation)) {
                    glowTexture = resourceLocation;
                }
            }

            putCacheEntry(glowTextureCache, prepared.player().getUUID(), new GlowTextureCacheEntry(cacheKey, glowTexture));
            return glowTexture;
        } catch (Throwable throwable) {
            DragonSurvivalBedrockRenderer.LOGGER.error("DSBR failed to read Dragon Survival glow-layer texture, skipping the glow layer for this frame.", throwable);
            return null;
        }
    }

    public boolean shouldRenderHeldItems() {
        ensureInitialized();
        if (clientDragonRendererRenderHeldItemField == null) {
            return false;
        }

        try {
            return clientDragonRendererRenderHeldItemField.getBoolean(null);
        } catch (IllegalAccessException exception) {
            return false;
        }
    }

    public boolean isBetterCombatAttacking(final Player player) {
        ensureInitialized();
        if (betterCombatIsAttackingMethod == null || player == null) {
            return false;
        }

        try {
            return (boolean) betterCombatIsAttackingMethod.invoke(null, player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public PreparedBackpackRender resolveBackpackRender(final PreparedDragonRender prepared) {
        ensureInitialized();
        if (!backpackRenderingAvailable || prepared == null) {
            return null;
        }

        try {
            if (!Boolean.TRUE.equals(dragonBackpackShouldRenderField.get(null))) {
                return null;
            }

            ItemStack backpack = resolveVisibleBackpack(prepared.player());
            if (backpack.isEmpty()) {
                return null;
            }

            Object bodyValue = null;
            Object bodyHolder = handlerBodyMethod.invoke(prepared.handler());
            if (bodyHolder instanceof Holder<?> holder) {
                bodyValue = holder.value();
            }

            BackpackRenderCacheKey cacheKey = new BackpackRenderCacheKey(bodyValue, hashItemStack(backpack));
            BackpackRenderCacheEntry cachedEntry = backpackRenderCache.get(prepared.player().getUUID());
            if (cachedEntry != null && cachedEntry.matches(cacheKey)) {
                return cachedEntry.render();
            }

            Vec3 posOffset = Vec3.ZERO;
            Vec3 rotOffset = Vec3.ZERO;
            Vec3 scale = new Vec3(1, 1, 1);

            if (bodyValue != null) {
                Object offsetsOptional = bodyBackpackOffsetsMethod.invoke(bodyValue);
                if (offsetsOptional instanceof java.util.Optional<?> optional) {
                    Object offsets = optional.orElse(null);
                    if (offsets != null) {
                        posOffset = (Vec3) backpackOffsetsPosOffsetMethod.invoke(offsets);
                        rotOffset = (Vec3) backpackOffsetsRotOffsetMethod.invoke(offsets);
                        scale = (Vec3) backpackOffsetsScaleMethod.invoke(offsets);
                    }
                }
            }

            PreparedBackpackRender render = new PreparedBackpackRender(backpack, posOffset, rotOffset, scale);
            putCacheEntry(backpackRenderCache, prepared.player().getUUID(), new BackpackRenderCacheEntry(cacheKey, render));
            return render;
        } catch (Throwable throwable) {
            DragonSurvivalBedrockRenderer.LOGGER.error("DSBR failed to read Dragon Survival backpack-layer state, skipping the backpack layer for this frame.", throwable);
            return null;
        }
    }

    public boolean canRenderArmorWithBedrock() {
        ensureInitialized();
        return armorRenderingAvailable;
    }

    public boolean hasVisibleArmor(final Player player) {
        if (player == null) {
            return false;
        }

        int armorSignature = computeArmorSignature(player);
        ArmorVisibilityCacheEntry cachedEntry = armorVisibilityCache.get(player.getUUID());
        if (cachedEntry != null && cachedEntry.signature() == armorSignature) {
            return cachedEntry.visible();
        }

        boolean visible = armorSignature != 0;
        putCacheEntry(armorVisibilityCache, player.getUUID(), new ArmorVisibilityCacheEntry(armorSignature, visible));
        return visible;
    }

    public ResourceLocation resolveArmorTexture(final PreparedDragonRender prepared) {
        ensureInitialized();
        if (!armorRenderingAvailable || prepared == null) {
            return null;
        }

        int armorSignature = computeArmorSignature(prepared.player());
        if (armorSignature == 0) {
            return null;
        }

        try {
            ResourceLocation armorModel = (ResourceLocation) handlerGetModelMethod.invoke(prepared.handler());
            if (armorModel == null) {
                return null;
            }

            ArmorTextureCacheKey cacheKey = new ArmorTextureCacheKey(armorModel, armorSignature);
            ArmorTextureCacheEntry cachedEntry = armorTextureCache.get(prepared.player().getUUID());
            if (cachedEntry != null && cachedEntry.matches(cacheKey)) {
                return cachedEntry.texture();
            }

            dragonArmorRenderLayerInitArmorMasksMethod.invoke(dragonArmorRenderLayerInstance, armorModel);
            Object result = dragonArmorRenderLayerConstructTrimmedDragonArmorTextureMethod.invoke(null, prepared.player());
            if (result instanceof java.util.Optional<?> optional) {
                Object value = optional.orElse(null);
                if (value instanceof ResourceLocation resourceLocation) {
                    putCacheEntry(armorTextureCache, prepared.player().getUUID(), new ArmorTextureCacheEntry(cacheKey, resourceLocation));
                    return resourceLocation;
                }
            }
            putCacheEntry(armorTextureCache, prepared.player().getUUID(), new ArmorTextureCacheEntry(cacheKey, null));
        } catch (Throwable throwable) {
            DragonSurvivalBedrockRenderer.LOGGER.error("DSBR failed to read Dragon Survival armor textures, continuing with only the Bedrock base body for this frame.", throwable);
        }

        return null;
    }

    private int computeArmorSignature(final Player player) {
        int signature = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmor()) {
                continue;
            }

            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            signature = 31 * signature + slot.ordinal() + 1;
            signature = 31 * signature + hashItemStack(stack);
        }
        return signature;
    }

    private int hashItemStack(final ItemStack stack) {
        return stack == null || stack.isEmpty() ? 0 : 31 * ItemStack.hashItemAndComponents(stack) + stack.getCount();
    }

    private <T> void putCacheEntry(final Map<UUID, T> cache, final UUID key, final T value) {
        if (cache.size() > 256 && !cache.containsKey(key)) {
            cache.clear();
        }
        cache.put(key, value);
    }

    private boolean hasTexture(final ResourceLocation resourceLocation) throws Exception {
        return resourceLocation != null && (boolean) renderingUtilsHasTextureMethod.invoke(null, resourceLocation);
    }

    private boolean hasGlowingCustomLayer(final Object customization) throws Exception {
        if (customization == null || customizationLayerSettingsField == null || lazyGetMethod == null || layerSettingsIsGlowingField == null) {
            return false;
        }

        Object layerSettingsObject = customizationLayerSettingsField.get(customization);
        if (!(layerSettingsObject instanceof Map<?, ?> layerSettings)) {
            return false;
        }

        for (Object lazyValue : layerSettings.values()) {
            Object settings = lazyGetMethod.invoke(lazyValue);
            if (settings != null && layerSettingsIsGlowingField.getBoolean(settings)) {
                return true;
            }
        }

        return false;
    }

    private ItemStack resolveVisibleBackpack(final Player player) throws Exception {
        ItemStack curiosBackpack = resolveCuriosBackpack(player);
        if (!curiosBackpack.isEmpty()) {
            return curiosBackpack;
        }

        ItemStack chestStack = player.getItemBySlot(EquipmentSlot.CHEST);
        if (sophisticatedBackpackItemClass != null && sophisticatedBackpackItemClass.isInstance(chestStack.getItem())) {
            return chestStack;
        }

        return ItemStack.EMPTY;
    }

    private ItemStack resolveCuriosBackpack(final Player player) throws Exception {
        if (curiosApiGetCuriosInventoryMethod == null || dragonBackpackCuriosSlotField == null) {
            return ItemStack.EMPTY;
        }

        Object curiosInventoryOptional = curiosApiGetCuriosInventoryMethod.invoke(null, player);
        if (!(curiosInventoryOptional instanceof java.util.Optional<?> optional) || optional.isEmpty()) {
            return ItemStack.EMPTY;
        }

        Object curiosInventory = optional.get();
        if (curiosHandlerFindCuriosMethod == null) {
            curiosHandlerFindCuriosMethod = curiosInventory.getClass().getMethod("findCurios", String.class);
        }

        Object results = curiosHandlerFindCuriosMethod.invoke(curiosInventory, (String) dragonBackpackCuriosSlotField.get(null));
        if (!(results instanceof Iterable<?> iterable)) {
            return ItemStack.EMPTY;
        }

        for (Object slotResult : iterable) {
            if (slotResult == null) {
                continue;
            }

            if (slotResultSlotContextMethod == null) {
                slotResultSlotContextMethod = slotResult.getClass().getMethod("slotContext");
            }
            if (slotResultStackMethod == null) {
                slotResultStackMethod = slotResult.getClass().getMethod("stack");
            }

            Object slotContext = slotResultSlotContextMethod.invoke(slotResult);
            if (slotContext != null) {
                if (slotContextVisibleMethod == null) {
                    slotContextVisibleMethod = slotContext.getClass().getMethod("visible");
                }

                if (!(boolean) slotContextVisibleMethod.invoke(slotContext)) {
                    continue;
                }
            }

            Object stackObject = slotResultStackMethod.invoke(slotResult);
            if (stackObject instanceof ItemStack stack && sophisticatedBackpackItemClass != null && sophisticatedBackpackItemClass.isInstance(stack.getItem())) {
                return stack;
            }

            return ItemStack.EMPTY;
        }

        return ItemStack.EMPTY;
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
            glowRenderingAvailable = false;
            backpackRenderingAvailable = false;
            return;
        }

        try {
            Class<?> dragonStateProviderClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateProvider");
            dragonEntityClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity");
            Class<?> clientDragonRendererClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.client.render.ClientDragonRenderer");
            Class<?> movementDataClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.registry.attachments.MovementData");
            Class<?> dragonModelClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.client.models.DragonModel");
            Class<?> dragonSurvivalClientClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.client.DragonSurvivalClient");
            Class<?> dragonStateHandlerClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateHandler");
            Class<?> dragonStageCustomizationClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.client.skin_editor_system.objects.DragonStageCustomization");
            Class<?> layerSettingsClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.client.skin_editor_system.objects.LayerSettings");
            Class<?> dragonArmorRenderLayerClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.client.render.entity.dragon.DragonArmorRenderLayer");
            Class<?> serverFlightHandlerClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.server.handlers.ServerFlightHandler");
            Class<?> dragonSizeHandlerClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.common.handlers.DragonSizeHandler");
            Class<?> dragonFoodHandlerClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.common.handlers.DragonFoodHandler");
            Class<?> clientConfigClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.config.ClientConfig");
            Class<?> dragonSkinsClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.client.skins.DragonSkins");
            Class<?> stageResourcesClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.common.codecs.StageResources");
            Class<?> renderingUtilsClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.client.util.RenderingUtils");
            Class<?> treasureRestDataClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.registry.attachments.TreasureRestData");
            Class<?> dragonBodyClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.registry.dragon.body.DragonBody");
            Class<?> backpackOffsetsClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.registry.dragon.body.DragonBody$BackpackOffsets");
            Class<?> dragonEmoteClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.registry.dragon.body.emotes.DragonEmote");
            Class<?> pairClass = Class.forName("com.mojang.datafixers.util.Pair");
            Class<?> abilityAnimationClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.common.codecs.ability.animation.AbilityAnimation");
            Class<?> geoEntityRendererClass = Class.forName("software.bernie.geckolib.renderer.GeoEntityRenderer");
            Class<?> lazyClass = Class.forName("net.neoforged.neoforge.common.util.Lazy");
            Class<?> resourceKeyClass = Class.forName("net.minecraft.resources.ResourceKey");
            Class<?> betterCombatClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.compat.bettercombat.BetterCombat");
            simpleAbilityAnimationClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.common.codecs.ability.animation.SimpleAbilityAnimation");
            compoundAbilityAnimationClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.common.codecs.ability.animation.CompoundAbilityAnimation");
            Class<?> skyhookHelperClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.compat.create.SkyhookRendererHelper");
            fakeClientPlayerClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.client.util.FakeClientPlayer");

            dragonStateProviderIsDragonMethod = dragonStateProviderClass.getMethod("isDragon", Entity.class);
            dragonStateProviderGetDataMethod = dragonStateProviderClass.getMethod("getData", Player.class);

            clientDragonRendererGetOrCreateDragonMethod = clientDragonRendererClass.getMethod("getOrCreateDragon", Player.class);
            clientDragonRendererSetDragonMovementDataMethod = clientDragonRendererClass.getMethod("setDragonMovementData", Player.class, float.class);
            clientDragonRendererHandleFlightMovementMethod = clientDragonRendererClass.getDeclaredMethod("handleFlightMovement", Player.class, dragonEntityClass, movementDataClass, float.class);
            clientDragonRendererHandleFlightMovementMethod.setAccessible(true);
            clientDragonRendererRenderItemsInMouthField = clientDragonRendererClass.getField("renderItemsInMouth");
            clientDragonRendererRenderHeldItemField = clientDragonRendererClass.getField("renderHeldItem");

            movementDataGetDataMethod = movementDataClass.getMethod("getData", Entity.class);

            dragonModelField = dragonSurvivalClientClass.getField("DRAGON_MODEL");
            Object dragonModel = dragonModelField.get(null);
            dragonModelGetModelResourceMethod = dragonModel.getClass().getMethod("getModelResource", dragonEntityClass);
            dragonModelGetTextureResourceMethod = dragonModel.getClass().getMethod("getTextureResource", dragonEntityClass);
            dragonModelGetAnimationResourceMethod = dragonModel.getClass().getMethod("getAnimationResource", dragonEntityClass);
            dragonModelDynamicTextureMethod = dragonModelClass.getMethod("dynamicTexture", Player.class, dragonStateHandlerClass, boolean.class);

            handlerGetVisualScaleMethod = dragonStateHandlerClass.getMethod("getVisualScale", Player.class, float.class);
            handlerGetModelMethod = dragonStateHandlerClass.getMethod("getModel");
            handlerBodyMethod = dragonStateHandlerClass.getMethod("body");
            handlerSpeciesMethod = dragonStateHandlerClass.getMethod("species");
            handlerStageKeyMethod = dragonStateHandlerClass.getMethod("stageKey");
            handlerGetCurrentStageCustomizationMethod = dragonStateHandlerClass.getMethod("getCurrentStageCustomization");
            handlerIsOnMagicSourceField = dragonStateHandlerClass.getField("isOnMagicSource");

            customizationDefaultSkinField = dragonStageCustomizationClass.getField("defaultSkin");
            customizationLayerSettingsField = dragonStageCustomizationClass.getField("layerSettings");
            lazyGetMethod = lazyClass.getMethod("get");
            layerSettingsIsGlowingField = layerSettingsClass.getField("isGlowing");

            dragonSkinsGetGlowTextureMethod = dragonSkinsClass.getMethod("getGlowTexture", Player.class, resourceKeyClass);
            stageResourcesGetDefaultSkinMethod = stageResourcesClass.getMethod("getDefaultSkin", Holder.class, resourceKeyClass, boolean.class);
            renderingUtilsHasTextureMethod = renderingUtilsClass.getMethod("hasTexture", ResourceLocation.class);
            dragonBodyDefaultModelField = dragonBodyClass.getField("DEFAULT_MODEL");
            betterCombatIsAttackingMethod = betterCombatClass.getMethod("isAttacking", Player.class);

            dragonArmorRenderLayerInitArmorMasksMethod = dragonArmorRenderLayerClass.getDeclaredMethod("initArmorMasks", ResourceLocation.class);
            dragonArmorRenderLayerInitArmorMasksMethod.setAccessible(true);
            dragonArmorRenderLayerConstructTrimmedDragonArmorTextureMethod = dragonArmorRenderLayerClass.getDeclaredMethod("constructTrimmedDragonArmorTexture", Player.class);
            dragonArmorRenderLayerConstructTrimmedDragonArmorTextureMethod.setAccessible(true);
            dragonArmorRenderLayerInstance = dragonArmorRenderLayerClass.getConstructor(geoEntityRendererClass).newInstance(new Object[] { null });

            bodyBackpackOffsetsMethod = dragonBodyClass.getMethod("backpackOffsets");
            backpackOffsetsPosOffsetMethod = backpackOffsetsClass.getMethod("posOffset");
            backpackOffsetsRotOffsetMethod = backpackOffsetsClass.getMethod("rotOffset");
            backpackOffsetsScaleMethod = backpackOffsetsClass.getMethod("scale");

            serverFlightIsFlyingMethod = serverFlightHandlerClass.getMethod("isFlying", Player.class);
            serverFlightIsGlidingMethod = serverFlightHandlerClass.getMethod("isGliding", Player.class);
            serverFlightIsSpinMethod = serverFlightHandlerClass.getMethod("isSpin", Player.class);
            serverFlightDistanceFromGroundMethod = serverFlightHandlerClass.getMethod("distanceFromGround", Player.class);

            dragonEntityIsSwimmingMethod = dragonEntityClass.getMethod("isConsideredSwimmingForAnimation", Player.class);
            dragonEntityGetPlayerMethod = dragonEntityClass.getMethod("getPlayer");
            dragonSizeCanPoseFitMethod = dragonSizeHandlerClass.getMethod("canPoseFit", Player.class, Pose.class);
            dragonFoodHandlerIsEdibleMethod = dragonFoodHandlerClass.getMethod("isEdible", Player.class, net.minecraft.world.item.ItemStack.class);
            treasureRestGetDataMethod = treasureRestDataClass.getMethod("getData", Player.class);
            treasureRestIsRestingMethod = treasureRestDataClass.getMethod("isResting");
            skyhookRidingMethod = skyhookHelperClass.getMethod("isPlayerRidingSkyhook", java.util.UUID.class);
            fakeClientPlayerHandlerField = fakeClientPlayerClass.getField("handler");
            fakeClientPlayerAnimationSupplierField = fakeClientPlayerClass.getField("animationSupplier");

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

            try {
                Class<?> dragonBackpackRenderLayerClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.compat.sophisticatedBackpacks.DragonBackpackRenderLayer");
                dragonBackpackShouldRenderField = dragonBackpackRenderLayerClass.getField("SHOULD_RENDER");
                dragonBackpackCuriosSlotField = dragonBackpackRenderLayerClass.getField("CURIOS_SLOT");
                sophisticatedBackpackItemClass = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem");

                try {
                    Class<?> curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                    for (Method method : curiosApiClass.getMethods()) {
                        if (method.getName().equals("getCuriosInventory") && method.getParameterCount() == 1) {
                            curiosApiGetCuriosInventoryMethod = method;
                            break;
                        }
                    }
                } catch (Throwable ignored) {
                    curiosApiGetCuriosInventoryMethod = null;
                }
            } catch (Throwable ignored) {
                dragonBackpackShouldRenderField = null;
                dragonBackpackCuriosSlotField = null;
                sophisticatedBackpackItemClass = null;
                curiosApiGetCuriosInventoryMethod = null;
            }

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
            glowRenderingAvailable = handlerStageKeyMethod != null
                    && handlerSpeciesMethod != null
                    && handlerGetCurrentStageCustomizationMethod != null
                    && customizationDefaultSkinField != null
                    && customizationLayerSettingsField != null
                    && lazyGetMethod != null
                    && layerSettingsIsGlowingField != null
                    && dragonSkinsGetGlowTextureMethod != null
                    && stageResourcesGetDefaultSkinMethod != null
                    && dragonModelDynamicTextureMethod != null
                    && renderingUtilsHasTextureMethod != null
                    && dragonBodyDefaultModelField != null;
            backpackRenderingAvailable = bodyBackpackOffsetsMethod != null
                    && backpackOffsetsPosOffsetMethod != null
                    && backpackOffsetsRotOffsetMethod != null
                    && backpackOffsetsScaleMethod != null
                    && dragonBackpackShouldRenderField != null
                    && sophisticatedBackpackItemClass != null;
        } catch (Throwable throwable) {
            available = false;
            armorRenderingAvailable = false;
            glowRenderingAvailable = false;
            backpackRenderingAvailable = false;
            DragonSurvivalBedrockRenderer.LOGGER.error("DSBR failed to initialize Dragon Survival reflection bridges, disabling the compatibility backend.", throwable);
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
            double distanceFromGround,
            String forcedAnimationKey
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

    public record PreparedBackpackRender(
            ItemStack stack,
            Vec3 posOffset,
            Vec3 rotOffset,
            Vec3 scale
    ) {
    }

    private record RenderCustomization(boolean renderWings, double bodyScaleMultiplier, List<String> bonesToHideForToggle) {
        private static final RenderCustomization DEFAULT = new RenderCustomization(true, 1.0D, List.of());
    }

    private record GlowTextureCacheKey(
            ResourceLocation baseTexture,
            ResourceLocation currentModel,
            Object stageKey,
            Object species,
            boolean defaultSkin,
            boolean glowingCustomLayer
    ) {
    }

    private record GlowTextureCacheEntry(GlowTextureCacheKey key, ResourceLocation texture) {
        private boolean matches(final GlowTextureCacheKey other) {
            return Objects.equals(key, other);
        }
    }

    private record ArmorVisibilityCacheEntry(int signature, boolean visible) {
    }

    private record ArmorTextureCacheKey(ResourceLocation armorModel, int armorSignature) {
    }

    private record ArmorTextureCacheEntry(ArmorTextureCacheKey key, ResourceLocation texture) {
        private boolean matches(final ArmorTextureCacheKey other) {
            return Objects.equals(key, other);
        }
    }

    private record BackpackRenderCacheKey(Object bodyValue, int backpackSignature) {
    }

    private record BackpackRenderCacheEntry(BackpackRenderCacheKey key, PreparedBackpackRender render) {
        private boolean matches(final BackpackRenderCacheKey other) {
            return Objects.equals(key, other);
        }
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
