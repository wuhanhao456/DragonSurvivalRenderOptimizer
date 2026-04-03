package top.wu949.dsbr.client.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import top.wu949.dsbr.client.DSBRRenderConfig;
import top.wu949.dsbr.client.bridge.DSRuntimeBridge;
import top.wu949.dsbr.client.render.model.DragonBedrockModel;

public final class DragonAnimationEngine {
    private final Map<ResourceLocation, AnimationLibrary> libraries = new HashMap<>();
    private final Map<String, ExpressionNode> expressionCache = new HashMap<>();
    private final Map<UUID, PlayerAnimationState> playerStates = new HashMap<>();

    public void animate(final DragonBedrockModel model, final DSRuntimeBridge.PreparedDragonRender renderState) throws Exception {
        AnimationLibrary library = libraries.get(renderState.animationLocation());
        if (library == null) {
            library = loadLibrary(renderState.animationLocation());
            libraries.put(renderState.animationLocation(), library);
        }

        PlayerAnimationState animationState = playerStates.computeIfAbsent(renderState.player().getUUID(), ignored -> new PlayerAnimationState());
        AnimationPlan plan = animationState.advance(renderState, library);

        model.resetPose();
        if (!renderState.renderWings()) {
            model.hideBones(renderState.bonesToHideForToggle());
        }

        if (plan == null || plan.frames().isEmpty()) {
            return;
        }

        Map<String, Vector3f> rotations = new HashMap<>();
        Map<String, Vector3f> positions = new HashMap<>();
        Map<String, Vector3f> scales = new HashMap<>();
        QuerySnapshot querySnapshot = plan.querySnapshot();
        for (AnimationFrame frame : plan.frames()) {
            if (frame == null || frame.clip() == null) {
                continue;
            }

            for (Map.Entry<String, BoneAnimation> boneEntry : frame.clip().bones().entrySet()) {
                BoneAnimation boneAnimation = boneEntry.getValue();
                if (boneAnimation.rotation() != null) {
                    rotations.put(boneEntry.getKey(), boneAnimation.rotation().sample(frame.timeSeconds(), querySnapshot));
                }
                if (boneAnimation.position() != null) {
                    positions.put(boneEntry.getKey(), boneAnimation.position().sample(frame.timeSeconds(), querySnapshot));
                }
                if (boneAnimation.scale() != null) {
                    scales.put(boneEntry.getKey(), boneAnimation.scale().sample(frame.timeSeconds(), querySnapshot));
                }
            }
        }

        for (Map.Entry<String, Vector3f> entry : rotations.entrySet()) {
            model.addRotationDegrees(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Vector3f> entry : positions.entrySet()) {
            model.addPosition(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Vector3f> entry : scales.entrySet()) {
            model.applyScale(entry.getKey(), entry.getValue());
        }
    }

    private AnimationLibrary loadLibrary(final ResourceLocation resourceLocation) throws Exception {
        JsonObject root;
        try (InputStreamReader reader = new InputStreamReader(
                Minecraft.getInstance().getResourceManager().getResource(resourceLocation).orElseThrow().open(),
                StandardCharsets.UTF_8
        )) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }

        JsonObject animations = root.getAsJsonObject("animations");
        Map<String, AnimationClip> clips = new HashMap<>();
        for (Map.Entry<String, JsonElement> animationEntry : animations.entrySet()) {
            JsonObject animationObject = animationEntry.getValue().getAsJsonObject();
            boolean loop = parseLoop(animationObject.get("loop"));
            double animationLength = animationObject.has("animation_length") ? animationObject.get("animation_length").getAsDouble() : 0.0D;
            Map<String, BoneAnimation> bones = new HashMap<>();
            JsonObject bonesObject = animationObject.has("bones") ? animationObject.getAsJsonObject("bones") : new JsonObject();

            for (Map.Entry<String, JsonElement> boneEntry : bonesObject.entrySet()) {
                JsonObject transforms = boneEntry.getValue().getAsJsonObject();
                bones.put(boneEntry.getKey(), new BoneAnimation(
                        parseChannel(transforms.get("rotation")),
                        parseChannel(transforms.get("position")),
                        parseChannel(transforms.get("scale"))
                ));
            }

            clips.put(animationEntry.getKey(), new AnimationClip(animationEntry.getKey(), loop, animationLength, bones));
        }

        return new AnimationLibrary(clips);
    }

    private boolean parseLoop(final JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return false;
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return !"false".equalsIgnoreCase(element.getAsString());
        }

        return false;
    }

    private AnimationChannel parseChannel(final JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        if (element.isJsonArray() || element.isJsonPrimitive()) {
            return AnimationChannel.constant(parseVectorValue(element));
        }

        JsonObject object = element.getAsJsonObject();
        if (object.has("vector") || object.has("pre") || object.has("post")) {
            return AnimationChannel.constant(parseVectorValue(object));
        }

        Map<Double, VectorValue> keyframes = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if ("lerp_mode".equals(entry.getKey())) {
                continue;
            }

            try {
                keyframes.put(Double.parseDouble(entry.getKey()), parseVectorValue(entry.getValue()));
            } catch (NumberFormatException ignored) {
                // DSBR 当前只处理数字键帧，其他元数据字段直接跳过。
            }
        }

        return keyframes.isEmpty() ? null : AnimationChannel.keyframes(keyframes);
    }

    private VectorValue parseVectorValue(final JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("vector")) {
                return parseVectorValue(object.get("vector"));
            }
            if (object.has("post")) {
                return parseVectorValue(object.get("post"));
            }
            if (object.has("pre")) {
                return parseVectorValue(object.get("pre"));
            }
        }

        if (element.isJsonPrimitive()) {
            ExpressionNode node = parseNode(element);
            return new VectorValue(node, node, node);
        }

        JsonArray array = element.getAsJsonArray();
        if (array.size() == 1) {
            ExpressionNode node = parseNode(array.get(0));
            return new VectorValue(node, node, node);
        }

        return new VectorValue(
                array.size() > 0 ? parseNode(array.get(0)) : ConstantNode.ZERO,
                array.size() > 1 ? parseNode(array.get(1)) : ConstantNode.ZERO,
                array.size() > 2 ? parseNode(array.get(2)) : ConstantNode.ZERO
        );
    }

    private ExpressionNode parseNode(final JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return new ConstantNode(element.getAsDouble());
        }

        String expression = element.getAsString();
        return expressionCache.computeIfAbsent(expression, key -> new ExpressionParser(key).parse());
    }

    private static final class PlayerAnimationState {
        private static final int CONTINUOUS_SLOT_COUNT = 4;
        private static final double BASE_SCALE = 1.0D;
        private static final double DEFAULT_WALK_SPEED = 0.10D;
        private static final double DEFAULT_SPRINT_SPEED = 0.165D;
        private static final double DEFAULT_SNEAK_SPEED = 0.03D;
        private static final double DEFAULT_SWIM_SPEED = 0.051D;
        private static final double DEFAULT_FAST_SWIM_SPEED = 0.13D;
        private static final double DEFAULT_CLIMB_SPEED = 0.0001D;

        private String currentBaseAnimation = "idle";
        private double currentBaseTimeSeconds;
        private OneShotAnimation activeOneShot;
        private boolean lastJumpTriggered;
        private final QueryState queryState = new QueryState();
        private final AbilityTrackState baseAbilityState = new AbilityTrackState();
        private final AbilityTrackState biteAbilityState = new AbilityTrackState();
        private final AbilityTrackState breathAbilityState = new AbilityTrackState();
        private final EmoteTrackState[] emoteStates = new EmoteTrackState[] {
                new EmoteTrackState(),
                new EmoteTrackState(),
                new EmoteTrackState(),
                new EmoteTrackState()
        };
        private final double[] continuousTimes = new double[CONTINUOUS_SLOT_COUNT];
        private final TransientClipState biteLayerState = new TransientClipState();
        private String edibleClip;
        private double edibleTimeSeconds;

        private AnimationPlan advance(final DSRuntimeBridge.PreparedDragonRender renderState, final AnimationLibrary library) {
            double deltaSeconds = getDeltaSeconds();
            double animationSpeedMultiplier = DSBRRenderConfig.animationSpeedMultiplier();
            double overlayDeltaSeconds = deltaSeconds * animationSpeedMultiplier;
            String selectedBaseAnimation = selectBaseAnimation(renderState, library);
            queryState.update(renderState, selectedBaseAnimation);

            AnimationFrame normalBaseFrame = advanceNormalBase(renderState, library, selectedBaseAnimation, deltaSeconds, animationSpeedMultiplier);
            List<DSRuntimeBridge.PreparedEmoteSlot> activeEmoteSlots = renderState.activeEmoteSlots();
            boolean hasBlockingEmote = activeEmoteSlots.stream().anyMatch(slot -> !slot.emote().blend());
            boolean neckLocked = activeEmoteSlots.stream().anyMatch(slot -> slot.emote().locksHead());
            boolean tailLocked = activeEmoteSlots.stream().anyMatch(slot -> slot.emote().locksTail());

            DSRuntimeBridge.PreparedAbilityAnimation abilityAnimation = renderState.abilityAnimation();
            if (abilityAnimation != null) {
                neckLocked = abilityAnimation.locksHead();
                tailLocked = abilityAnimation.locksTail();
            }

            QuerySnapshot querySnapshot = queryState.snapshot(renderState, neckLocked, tailLocked);
            List<AnimationFrame> frames = new ArrayList<>();

            AnimationFrame baseLayerFrame = selectBaseLayerFrame(renderState, library, overlayDeltaSeconds, abilityAnimation, hasBlockingEmote, normalBaseFrame);
            if (baseLayerFrame != null) {
                frames.add(baseLayerFrame);
            }

            addContinuousFrames(frames, library, overlayDeltaSeconds);
            addEmoteFrames(frames, activeEmoteSlots, library, overlayDeltaSeconds);
            addSpecialOverlayFrames(frames, renderState, library, overlayDeltaSeconds, abilityAnimation);

            return new AnimationPlan(frames, querySnapshot);
        }

        private AnimationFrame selectBaseLayerFrame(
                final DSRuntimeBridge.PreparedDragonRender renderState,
                final AnimationLibrary library,
                final double deltaSeconds,
                final DSRuntimeBridge.PreparedAbilityAnimation abilityAnimation,
                final boolean hasBlockingEmote,
                final AnimationFrame normalBaseFrame
        ) {
            if (hasBlockingEmote) {
                baseAbilityState.clear();
                return null;
            }

            if (abilityAnimation != null && abilityAnimation.layer() == DSRuntimeBridge.AbilityAnimationLayer.BASE) {
                return baseAbilityState.frameFor(abilityAnimation, library, deltaSeconds);
            }

            baseAbilityState.clear();
            return normalBaseFrame;
        }

        private void addContinuousFrames(final List<AnimationFrame> frames, final AnimationLibrary library, final double deltaSeconds) {
            for (int slot = 0; slot < CONTINUOUS_SLOT_COUNT; slot++) {
                AnimationClip clip = library.get("continuous_" + slot);
                if (clip == null) {
                    continue;
                }

                double sampleTime = clip.length() > 0.0D ? continuousTimes[slot] % clip.length() : 0.0D;
                frames.add(new AnimationFrame(clip, sampleTime));
                continuousTimes[slot] += deltaSeconds;
            }
        }

        private void addEmoteFrames(
                final List<AnimationFrame> frames,
                final List<DSRuntimeBridge.PreparedEmoteSlot> activeEmoteSlots,
                final AnimationLibrary library,
                final double deltaSeconds
        ) {
            boolean[] usedSlots = new boolean[emoteStates.length];
            for (DSRuntimeBridge.PreparedEmoteSlot slot : activeEmoteSlots) {
                if (slot.slot() < 0 || slot.slot() >= emoteStates.length) {
                    continue;
                }

                usedSlots[slot.slot()] = true;
                AnimationFrame frame = emoteStates[slot.slot()].frameFor(slot.emote(), library, deltaSeconds);
                if (frame != null) {
                    frames.add(frame);
                }
            }

            for (int slot = 0; slot < emoteStates.length; slot++) {
                if (!usedSlots[slot]) {
                    emoteStates[slot].clear();
                }
            }
        }

        private void addSpecialOverlayFrames(
                final List<AnimationFrame> frames,
                final DSRuntimeBridge.PreparedDragonRender renderState,
                final AnimationLibrary library,
                final double deltaSeconds,
                final DSRuntimeBridge.PreparedAbilityAnimation abilityAnimation
        ) {
            AnimationFrame biteAbilityFrame = null;
            AnimationFrame breathAbilityFrame = null;

            if (abilityAnimation != null) {
                switch (abilityAnimation.layer()) {
                    case BASE -> {
                        biteAbilityState.clear();
                        breathAbilityState.clear();
                    }
                    case BITE -> {
                        biteAbilityFrame = biteAbilityState.frameFor(abilityAnimation, library, deltaSeconds);
                        breathAbilityState.clear();
                    }
                    case BREATH -> {
                        breathAbilityFrame = breathAbilityState.frameFor(abilityAnimation, library, deltaSeconds);
                        biteAbilityState.clear();
                    }
                }
            } else {
                biteAbilityState.clear();
                breathAbilityState.clear();
            }

            if (biteAbilityFrame != null) {
                frames.add(biteAbilityFrame);
                clearEdibleClip();
                biteLayerState.clear();
            } else {
                AnimationFrame biteLayerFrame = advanceBiteLayerFrame(renderState, library, deltaSeconds);
                if (biteLayerFrame != null) {
                    frames.add(biteLayerFrame);
                }
            }

            if (breathAbilityFrame != null) {
                frames.add(breathAbilityFrame);
            }
        }

        private AnimationFrame advanceBiteLayerFrame(
                final DSRuntimeBridge.PreparedDragonRender renderState,
                final AnimationLibrary library,
                final double deltaSeconds
        ) {
            if (!renderState.renderItemsInMouth()) {
                if (renderState.usingEdibleItem()) {
                    biteLayerState.clear();
                    AnimationFrame edibleFrame = advanceEdibleClip(renderState.player().getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? "eat_item_right" : "eat_item_left", library, deltaSeconds);
                    if (edibleFrame != null) {
                        renderState.consumeBiteFlag();
                    }
                    return edibleFrame;
                }

                clearEdibleClip();

                if (renderState.player().isUsingItem()) {
                    if (renderState.player().getTicksUsingItem() == 1) {
                        biteLayerState.trigger(renderState.player().getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? "use_item_right" : "use_item_left");
                    }
                } else if (!renderState.player().getMainHandItem().isEmpty() && renderState.bite()) {
                    biteLayerState.trigger("use_item_right");
                }
            } else {
                clearEdibleClip();
            }

            if (!biteLayerState.isActive() && renderState.bite() && !renderState.dig()) {
                biteLayerState.trigger("bite");
            }

            AnimationFrame biteLayerFrame = biteLayerState.sample(library, deltaSeconds);
            if (biteLayerFrame != null) {
                renderState.consumeBiteFlag();
            }
            return biteLayerFrame;
        }

        private AnimationFrame advanceEdibleClip(final String clipName, final AnimationLibrary library, final double deltaSeconds) {
            AnimationClip clip = library.get(clipName);
            if (clip == null) {
                clearEdibleClip();
                return null;
            }

            if (!Objects.equals(edibleClip, clipName)) {
                edibleClip = clipName;
                edibleTimeSeconds = 0.0D;
            }

            double sampleTime = clip.length() > 0.0D ? edibleTimeSeconds % clip.length() : 0.0D;
            edibleTimeSeconds += deltaSeconds;
            return new AnimationFrame(clip, sampleTime);
        }

        private void clearEdibleClip() {
            edibleClip = null;
            edibleTimeSeconds = 0.0D;
        }

        private AnimationFrame advanceNormalBase(
                final DSRuntimeBridge.PreparedDragonRender renderState,
                final AnimationLibrary library,
                final String selectedBaseAnimation,
                final double deltaSeconds,
                final double animationSpeedMultiplier
        ) {
            boolean jumpJustTriggered = renderState.jumpTriggered() && !lastJumpTriggered;
            lastJumpTriggered = renderState.jumpTriggered();

            if (jumpJustTriggered && library.get("jump") != null) {
                activeOneShot = new OneShotAnimation("jump", 0.0D);
            }

            if (!Objects.equals(currentBaseAnimation, selectedBaseAnimation)) {
                if ("fly_land".equals(currentBaseAnimation) && library.get("fly_land_end") != null && !"fly_land".equals(selectedBaseAnimation)) {
                    activeOneShot = new OneShotAnimation("fly_land_end", 0.0D);
                }

                currentBaseAnimation = selectedBaseAnimation;
                currentBaseTimeSeconds = 0.0D;
            } else {
                currentBaseTimeSeconds += deltaSeconds * getAnimationSpeed(currentBaseAnimation, renderState, animationSpeedMultiplier);
            }

            if (activeOneShot != null) {
                AnimationClip clip = library.get(activeOneShot.name());
                if (clip == null) {
                    activeOneShot = null;
                } else {
                    activeOneShot = activeOneShot.advance(deltaSeconds * animationSpeedMultiplier);
                    if (activeOneShot.elapsedSeconds() >= clip.length()) {
                        AnimationFrame frame = new AnimationFrame(clip, clip.length());
                        activeOneShot = null;
                        return frame;
                    }
                    return new AnimationFrame(clip, activeOneShot.elapsedSeconds());
                }
            }

            AnimationClip baseClip = library.get(currentBaseAnimation);
            if (baseClip == null) {
                baseClip = library.get("idle");
            }
            if (baseClip == null) {
                return null;
            }

            double sampleTime = baseClip.loop() && baseClip.length() > 0 ? currentBaseTimeSeconds % baseClip.length() : Math.min(currentBaseTimeSeconds, baseClip.length());
            return new AnimationFrame(baseClip, sampleTime);
        }

        private String selectBaseAnimation(final DSRuntimeBridge.PreparedDragonRender state, final AnimationLibrary library) {
            if (!state.movingHorizontally() && state.onMagicSource()) {
                return "sit_on_magic_source";
            }

            if (state.player().isSleeping() || state.resting()) {
                return "sleep_left";
            }

            if (state.ridingSkyhook() && library.get("create_skyhook_riding") != null) {
                return "create_skyhook_riding";
            }

            if (state.player().isPassenger()) {
                return "sit";
            }

            if (state.flying()) {
                if (state.gliding()) {
                    if (state.spin()) {
                        return "fly_spin";
                    } else if (state.deltaMovement().y < -1.0D) {
                        return "fly_dive_alt";
                    } else if (state.deltaMovement().y < -0.25D) {
                        return "fly_dive";
                    } else if (state.deltaMovement().y > 0.5D) {
                        return "fly";
                    } else {
                        return "fly_soaring";
                    }
                }

                if (state.desiredMoveVec().y < 0 && state.deltaMovement().y < 0 && state.distanceFromGround() < 10 && state.deltaMovement().length() < 4) {
                    return "fly_land";
                }

                if (state.spin()) {
                    return "fly_spin";
                }

                return "fly";
            }

            if (state.player().getPose() == Pose.SWIMMING) {
                return state.spin() ? "fly_spin" : "swim_fast";
            }

            if (state.consideredSwimming()) {
                return state.spin() ? "fly_spin" : "swim";
            }

            if (state.player().onClimbable()) {
                return state.deltaMovement().y < 0 ? "climbing_down" : "climbing_up";
            }

            if (!state.player().onGround()) {
                return "fall_loop";
            }

            if (state.player().isShiftKeyDown() || (!state.canPoseFitStanding() && state.canPoseFitCrouching())) {
                if (state.movingHorizontally()) {
                    return "sneak_walk";
                }
                if (state.dig()) {
                    return "dig_sneak";
                }
                return "sneak";
            }

            if (state.player().isSprinting()) {
                return "run";
            }

            if (state.movingHorizontally()) {
                return "walk";
            }

            if (state.dig()) {
                return "dig";
            }

            return "idle";
        }

        private double getAnimationSpeed(
                final String animation,
                final DSRuntimeBridge.PreparedDragonRender state,
                final double animationSpeedMultiplier
        ) {
            double baseAnimationSpeed = 1.0D;
            Double movementBaseSpeed = null;

            switch (animation) {
                case "fly_spin" -> baseAnimationSpeed = 2.0D;
                case "fly" -> {
                    if (state.gliding() && state.deltaMovement().y > 0.5D) {
                        baseAnimationSpeed = 1.5D;
                    } else if (!state.gliding() && state.desiredMoveVec().y > 0.0D) {
                        baseAnimationSpeed = 2.0D;
                    }
                }
                case "walk" -> movementBaseSpeed = DEFAULT_WALK_SPEED;
                case "run" -> movementBaseSpeed = DEFAULT_SPRINT_SPEED;
                case "sneak_walk" -> movementBaseSpeed = DEFAULT_SNEAK_SPEED;
                case "swim" -> movementBaseSpeed = DEFAULT_SWIM_SPEED;
                case "swim_fast" -> movementBaseSpeed = DEFAULT_FAST_SWIM_SPEED;
                case "climbing_up", "climbing_down" -> movementBaseSpeed = DEFAULT_CLIMB_SPEED;
                default -> {
                }
            }

            if (movementBaseSpeed != null) {
                baseAnimationSpeed = resolveDynamicAnimationSpeed(state, baseAnimationSpeed, movementBaseSpeed);
            }

            return Math.max(0.0D, baseAnimationSpeed * animationSpeedMultiplier);
        }

        private double resolveDynamicAnimationSpeed(
                final DSRuntimeBridge.PreparedDragonRender state,
                final double animationSpeed,
                final double baseSpeed
        ) {
            if (baseSpeed <= 0.0D) {
                return animationSpeed;
            }

            DSRuntimeBridge.PreparedAnimationSpeedConfig config = state.animationSpeedConfig();
            double speedComponent = Math.min(
                    config.maxAnimationSpeedFactor(),
                    (state.deltaMovement().horizontalDistance() - baseSpeed) / baseSpeed * config.movementAnimationSpeedFactor()
            );
            double sizeDistance = state.visualScale() - BASE_SCALE;
            double sizeFactor = sizeDistance >= 0.0D ? config.largeSizeAnimationSpeedFactor() : config.smallSizeAnimationSpeedFactor();
            double sizeDenominator = BASE_SCALE + sizeDistance * sizeFactor;
            double sizeComponent = Math.abs(sizeDenominator) < 1.0E-8D ? 1.0D : BASE_SCALE / sizeDenominator;
            double minAnimationSpeed = Math.min(config.minAnimationSpeed(), config.maxAnimationSpeed());
            double maxAnimationSpeed = Math.max(config.minAnimationSpeed(), config.maxAnimationSpeed());
            return Mth.clamp((animationSpeed + speedComponent) * sizeComponent, minAnimationSpeed, maxAnimationSpeed);
        }

        private double getDeltaSeconds() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return minecraft.getTimer().getRealtimeDeltaTicks() / 20.0D;
            }
            return minecraft.getTimer().getRealtimeDeltaTicks() * minecraft.level.tickRateManager().millisecondsPerTick() / 1000.0D;
        }
    }

    private static final class QueryState {
        private static final double DELTA_YAW_PITCH_FACTOR = 0.2D;
        private static final double DELTA_MOVEMENT_FACTOR = 10.0D;

        private final ArrayDeque<Double> bodyYawHistory = new ArrayDeque<>();
        private final ArrayDeque<Double> headYawHistory = new ArrayDeque<>();
        private final ArrayDeque<Double> headPitchHistory = new ArrayDeque<>();
        private final ArrayDeque<Double> verticalVelocityHistory = new ArrayDeque<>();

        private double currentBodyYawChange;
        private double currentHeadYawChange;
        private double currentHeadPitchChange;
        private double currentTailMotionUp;
        private String previousAnimation = "idle";

        private void update(final DSRuntimeBridge.PreparedDragonRender renderState, final String selectedAnimation) {
            Minecraft minecraft = Minecraft.getInstance();
            float deltaTick = Math.max(0.001F, minecraft.getTimer().getRealtimeDeltaTicks());
            float partialDeltaTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);

            boolean clearVerticalVelocity = (Objects.equals(selectedAnimation, "swim") || Objects.equals(selectedAnimation, "swim_fast"))
                    && !Objects.equals(previousAnimation, "swim")
                    && !Objects.equals(previousAnimation, "swim_fast");
            previousAnimation = selectedAnimation;

            double bodyYawChange = Mth.wrapDegrees(getLastBodyYaw(renderState) - renderState.bodyYaw()) / deltaTick * DELTA_YAW_PITCH_FACTOR;
            double headYawChange = Mth.wrapDegrees(getLastHeadYaw(renderState) - renderState.headYaw()) / deltaTick * DELTA_YAW_PITCH_FACTOR;
            double headPitchChange = Mth.wrapDegrees(getLastHeadPitch(renderState) - renderState.headPitch()) / deltaTick * DELTA_YAW_PITCH_FACTOR;
            double verticalVelocity = Mth.lerp(partialDeltaTick, getLastDeltaMovement(renderState).y, renderState.deltaMovement().y) * DELTA_MOVEMENT_FACTOR;
            verticalVelocity *= 1 - Mth.abs(Mth.clampedMap(renderState.movementPrevXRot(), -90, 90, -1, 1));

            int removeSize = Math.max(1, (int) (10 / getDeltaTickFor60Fps()));
            trim(bodyYawHistory, removeSize);
            trim(headYawHistory, removeSize);
            trim(headPitchHistory, removeSize);

            if (clearVerticalVelocity) {
                verticalVelocityHistory.clear();
                while (verticalVelocityHistory.size() < removeSize) {
                    verticalVelocityHistory.add(0.0D);
                }
            } else {
                trim(verticalVelocityHistory, removeSize);
            }

            bodyYawHistory.add(bodyYawChange);
            headYawHistory.add(headYawChange);
            headPitchHistory.add(headPitchChange);
            verticalVelocityHistory.add(verticalVelocity);

            double lerpRate = Math.min(1, deltaTick);
            currentBodyYawChange = Mth.lerp(lerpRate, currentBodyYawChange, average(bodyYawHistory));
            currentHeadYawChange = Mth.lerp(lerpRate, currentHeadYawChange, average(headYawHistory));
            currentHeadPitchChange = Mth.lerp(lerpRate, currentHeadPitchChange, average(headPitchHistory));

            if (clearVerticalVelocity) {
                currentTailMotionUp = 0;
            } else {
                currentTailMotionUp = Mth.lerp(lerpRate, currentTailMotionUp, -average(verticalVelocityHistory));
            }
        }

        private QuerySnapshot snapshot(final DSRuntimeBridge.PreparedDragonRender renderState, final boolean neckLocked, final boolean tailLocked) {
            return new QuerySnapshot(
                    renderState.headYaw(),
                    renderState.headPitch(),
                    currentBodyYawChange,
                    currentHeadYawChange,
                    currentHeadPitchChange,
                    currentTailMotionUp,
                    neckLocked,
                    tailLocked
            );
        }

        private double getLastBodyYaw(final DSRuntimeBridge.PreparedDragonRender renderState) {
            return getMovementFieldAsDouble(renderState.movement(), "bodyYawLastFrame", renderState.bodyYaw());
        }

        private double getLastHeadYaw(final DSRuntimeBridge.PreparedDragonRender renderState) {
            return getMovementFieldAsDouble(renderState.movement(), "headYawLastFrame", renderState.headYaw());
        }

        private double getLastHeadPitch(final DSRuntimeBridge.PreparedDragonRender renderState) {
            return getMovementFieldAsDouble(renderState.movement(), "headPitchLastFrame", renderState.headPitch());
        }

        private net.minecraft.world.phys.Vec3 getLastDeltaMovement(final DSRuntimeBridge.PreparedDragonRender renderState) {
            try {
                return (net.minecraft.world.phys.Vec3) renderState.movement().getClass().getField("deltaMovementLastFrame").get(renderState.movement());
            } catch (ReflectiveOperationException ignored) {
                return renderState.deltaMovement();
            }
        }

        private double getMovementFieldAsDouble(final Object movement, final String fieldName, final double fallback) {
            try {
                return movement.getClass().getField(fieldName).getDouble(movement);
            } catch (ReflectiveOperationException ignored) {
                return fallback;
            }
        }

        private float getDeltaTickFor60Fps() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return 1.0F;
            }
            float deltaTick = minecraft.getTimer().getRealtimeDeltaTicks();
            float millisecondsPerTick = minecraft.level.tickRateManager().millisecondsPerTick();
            return deltaTick / ((1.0F / 60.0F * 1000.0F) / millisecondsPerTick);
        }

        private void trim(final ArrayDeque<Double> values, final int maxSize) {
            while (values.size() > maxSize) {
                values.removeFirst();
            }
        }

        private double average(final ArrayDeque<Double> values) {
            if (values.isEmpty()) {
                return 0.0D;
            }
            double sum = 0.0D;
            for (Double value : values) {
                sum += value;
            }
            double average = sum / values.size();
            return Double.isFinite(average) ? average : 0.0D;
        }
    }

    private record QuerySnapshot(
            double headYaw,
            double headPitch,
            double bodyYawChange,
            double headYawChange,
            double headPitchChange,
            double tailMotionUp,
            boolean neckLocked,
            boolean tailLocked
    ) {
        private double query(final String variable) {
            return switch (variable) {
                case "query.head_yaw" -> neckLocked ? 0.0D : headYaw;
                case "query.head_pitch" -> neckLocked ? 0.0D : headPitch;
                case "query.body_yaw_change" -> tailLocked ? 0.0D : bodyYawChange;
                case "query.head_yaw_change" -> headYawChange;
                case "query.head_pitch_change" -> headPitchChange;
                case "query.tail_motion_up" -> tailLocked ? 0.0D : tailMotionUp;
                default -> 0.0D;
            };
        }
    }

    private record AnimationPlan(List<AnimationFrame> frames, QuerySnapshot querySnapshot) {
    }

    private record AnimationFrame(AnimationClip clip, double timeSeconds) {
    }

    private record OneShotAnimation(String name, double elapsedSeconds) {
        private OneShotAnimation advance(final double deltaSeconds) {
            return new OneShotAnimation(name, elapsedSeconds + deltaSeconds);
        }
    }

    private static final class AbilityTrackState {
        private DSRuntimeBridge.PreparedAbilityAnimation descriptor;
        private double elapsedSeconds;
        private boolean playOnceFinished;

        private AnimationFrame frameFor(
                final DSRuntimeBridge.PreparedAbilityAnimation nextDescriptor,
                final AnimationLibrary library,
                final double deltaSeconds
        ) {
            if (nextDescriptor == null) {
                clear();
                return null;
            }

            if (!Objects.equals(descriptor, nextDescriptor)) {
                descriptor = nextDescriptor;
                elapsedSeconds = 0.0D;
                playOnceFinished = false;
            }

            AnimationFrame frame = playOnceFinished ? null : sample(library);
            advance(library, deltaSeconds);
            return frame;
        }

        private void clear() {
            descriptor = null;
            elapsedSeconds = 0.0D;
            playOnceFinished = false;
        }

        private AnimationFrame sample(final AnimationLibrary library) {
            if (descriptor == null) {
                return null;
            }

            return switch (descriptor.playback()) {
                case PLAY_ONCE -> sampleSingleClip(library.get(descriptor.primaryAnimationKey()), false);
                case LOOPING -> sampleSingleClip(library.get(descriptor.primaryAnimationKey()), true);
                case PLAY_AND_HOLD -> sampleSingleClip(library.get(descriptor.primaryAnimationKey()), false);
                case START_THEN_LOOP -> sampleCompoundClip(library);
            };
        }

        private AnimationFrame sampleSingleClip(final AnimationClip clip, final boolean looping) {
            if (clip == null) {
                return null;
            }

            double sampleTime = looping && clip.length() > 0.0D ? elapsedSeconds % clip.length() : Math.min(elapsedSeconds, clip.length());
            return new AnimationFrame(clip, sampleTime);
        }

        private AnimationFrame sampleCompoundClip(final AnimationLibrary library) {
            AnimationClip startingClip = library.get(descriptor.primaryAnimationKey());
            AnimationClip loopingClip = descriptor.secondaryAnimationKey() == null ? null : library.get(descriptor.secondaryAnimationKey());

            if (startingClip != null && elapsedSeconds < startingClip.length()) {
                return new AnimationFrame(startingClip, Math.min(elapsedSeconds, startingClip.length()));
            }

            if (loopingClip != null) {
                double loopElapsed = Math.max(0.0D, elapsedSeconds - (startingClip == null ? 0.0D : startingClip.length()));
                double sampleTime = loopingClip.length() > 0.0D ? loopElapsed % loopingClip.length() : 0.0D;
                return new AnimationFrame(loopingClip, sampleTime);
            }

            if (startingClip != null) {
                return new AnimationFrame(startingClip, Math.min(elapsedSeconds, startingClip.length()));
            }

            return null;
        }

        private void advance(final AnimationLibrary library, final double deltaSeconds) {
            if (descriptor == null) {
                return;
            }

            elapsedSeconds += deltaSeconds;
            if (descriptor.playback() == DSRuntimeBridge.SpecialAnimationPlayback.PLAY_ONCE) {
                AnimationClip clip = library.get(descriptor.primaryAnimationKey());
                if (clip != null && clip.length() > 0.0D && elapsedSeconds >= clip.length()) {
                    playOnceFinished = true;
                }
            }
        }
    }

    private static final class EmoteTrackState {
        private DSRuntimeBridge.PreparedEmote descriptor;
        private double elapsedSeconds;

        private AnimationFrame frameFor(
                final DSRuntimeBridge.PreparedEmote nextDescriptor,
                final AnimationLibrary library,
                final double deltaSeconds
        ) {
            if (nextDescriptor == null) {
                clear();
                return null;
            }

            if (!Objects.equals(descriptor, nextDescriptor)) {
                descriptor = nextDescriptor;
                elapsedSeconds = 0.0D;
            }

            AnimationClip clip = library.get(descriptor.animationKey());
            AnimationFrame frame = null;
            if (clip != null) {
                double sampleTime = descriptor.loops() && clip.length() > 0.0D ? elapsedSeconds % clip.length() : Math.min(elapsedSeconds, clip.length());
                frame = new AnimationFrame(clip, sampleTime);
            }

            elapsedSeconds += deltaSeconds * Math.max(0.0D, descriptor.speed());
            return frame;
        }

        private void clear() {
            descriptor = null;
            elapsedSeconds = 0.0D;
        }
    }

    private static final class TransientClipState {
        private String clipName;
        private double elapsedSeconds;

        private void trigger(final String nextClipName) {
            if (!Objects.equals(clipName, nextClipName)) {
                clipName = nextClipName;
                elapsedSeconds = 0.0D;
            }
        }

        private boolean isActive() {
            return clipName != null;
        }

        private void clear() {
            clipName = null;
            elapsedSeconds = 0.0D;
        }

        private AnimationFrame sample(final AnimationLibrary library, final double deltaSeconds) {
            if (clipName == null) {
                return null;
            }

            AnimationClip clip = library.get(clipName);
            if (clip == null) {
                clear();
                return null;
            }

            double sampleTime = Math.min(elapsedSeconds, clip.length());
            AnimationFrame frame = new AnimationFrame(clip, sampleTime);
            elapsedSeconds += deltaSeconds;

            if (clip.length() > 0.0D && elapsedSeconds >= clip.length()) {
                clear();
            }

            return frame;
        }
    }

    private record AnimationLibrary(Map<String, AnimationClip> clips) {
        private AnimationClip get(final String name) {
            return clips.get(name);
        }
    }

    private record AnimationClip(String name, boolean loop, double length, Map<String, BoneAnimation> bones) {
    }

    private record BoneAnimation(AnimationChannel rotation, AnimationChannel position, AnimationChannel scale) {
    }

    private static final class AnimationChannel {
        private final VectorValue constantValue;
        private final List<Keyframe> keyframes;

        private AnimationChannel(final VectorValue constantValue, final List<Keyframe> keyframes) {
            this.constantValue = constantValue;
            this.keyframes = keyframes;
        }

        private static AnimationChannel constant(final VectorValue value) {
            return new AnimationChannel(value, List.of());
        }

        private static AnimationChannel keyframes(final Map<Double, VectorValue> keyframes) {
            List<Keyframe> sorted = keyframes.entrySet().stream()
                    .map(entry -> new Keyframe(entry.getKey(), entry.getValue()))
                    .sorted((left, right) -> Double.compare(left.time(), right.time()))
                    .toList();
            return new AnimationChannel(null, sorted);
        }

        private Vector3f sample(final double time, final QuerySnapshot querySnapshot) {
            if (constantValue != null) {
                return constantValue.evaluate(querySnapshot);
            }
            if (keyframes.isEmpty()) {
                return new Vector3f();
            }
            if (time <= keyframes.get(0).time()) {
                return keyframes.get(0).value().evaluate(querySnapshot);
            }
            if (time >= keyframes.get(keyframes.size() - 1).time()) {
                return keyframes.get(keyframes.size() - 1).value().evaluate(querySnapshot);
            }

            Keyframe previous = keyframes.get(0);
            for (int index = 1; index < keyframes.size(); index++) {
                Keyframe next = keyframes.get(index);
                if (time <= next.time()) {
                    float progress = (float) ((time - previous.time()) / (next.time() - previous.time()));
                    Vector3f start = previous.value().evaluate(querySnapshot);
                    Vector3f end = next.value().evaluate(querySnapshot);
                    return start.lerp(end, progress, new Vector3f());
                }
                previous = next;
            }

            return previous.value().evaluate(querySnapshot);
        }
    }

    private record Keyframe(double time, VectorValue value) {
    }

    private record VectorValue(ExpressionNode x, ExpressionNode y, ExpressionNode z) {
        private Vector3f evaluate(final QuerySnapshot querySnapshot) {
            return new Vector3f((float) x.evaluate(querySnapshot), (float) y.evaluate(querySnapshot), (float) z.evaluate(querySnapshot));
        }
    }

    private interface ExpressionNode {
        double evaluate(QuerySnapshot querySnapshot);
    }

    private record ConstantNode(double value) implements ExpressionNode {
        private static final ConstantNode ZERO = new ConstantNode(0.0D);

        @Override
        public double evaluate(final QuerySnapshot querySnapshot) {
            return value;
        }
    }

    private record VariableNode(String variable) implements ExpressionNode {
        @Override
        public double evaluate(final QuerySnapshot querySnapshot) {
            return querySnapshot.query(variable);
        }
    }

    private record UnaryNode(char operator, ExpressionNode child) implements ExpressionNode {
        @Override
        public double evaluate(final QuerySnapshot querySnapshot) {
            double value = child.evaluate(querySnapshot);
            return operator == '-' ? -value : value;
        }
    }

    private record BinaryNode(char operator, ExpressionNode left, ExpressionNode right) implements ExpressionNode {
        @Override
        public double evaluate(final QuerySnapshot querySnapshot) {
            double leftValue = left.evaluate(querySnapshot);
            double rightValue = right.evaluate(querySnapshot);
            return switch (operator) {
                case '+' -> leftValue + rightValue;
                case '-' -> leftValue - rightValue;
                case '*' -> leftValue * rightValue;
                case '/' -> rightValue == 0 ? 0.0D : leftValue / rightValue;
                default -> 0.0D;
            };
        }
    }

    private record FunctionNode(String function, List<ExpressionNode> arguments) implements ExpressionNode {
        @Override
        public double evaluate(final QuerySnapshot querySnapshot) {
            return switch (function) {
                case "math.abs" -> Math.abs(argument(0, querySnapshot));
                case "math.clamp" -> Mth.clamp(argument(0, querySnapshot), argument(1, querySnapshot), argument(2, querySnapshot));
                case "math.trunc" -> Math.floor(argument(0, querySnapshot));
                default -> 0.0D;
            };
        }

        private double argument(final int index, final QuerySnapshot querySnapshot) {
            return index < arguments.size() ? arguments.get(index).evaluate(querySnapshot) : 0.0D;
        }
    }

    private static final class ExpressionParser {
        private final String input;
        private int index;

        private ExpressionParser(final String input) {
            this.input = input.replace(" ", "");
        }

        private ExpressionNode parse() {
            ExpressionNode node = parseExpression();
            return node == null ? ConstantNode.ZERO : node;
        }

        private ExpressionNode parseExpression() {
            ExpressionNode node = parseTerm();
            while (match('+') || match('-')) {
                char operator = input.charAt(index - 1);
                node = new BinaryNode(operator, node, parseTerm());
            }
            return node;
        }

        private ExpressionNode parseTerm() {
            ExpressionNode node = parseUnary();
            while (match('*') || match('/')) {
                char operator = input.charAt(index - 1);
                node = new BinaryNode(operator, node, parseUnary());
            }
            return node;
        }

        private ExpressionNode parseUnary() {
            if (match('+')) {
                return parseUnary();
            }
            if (match('-')) {
                return new UnaryNode('-', parseUnary());
            }
            return parsePrimary();
        }

        private ExpressionNode parsePrimary() {
            if (match('(')) {
                ExpressionNode node = parseExpression();
                match(')');
                return node;
            }

            if (isNumberStart(peek())) {
                return parseNumber();
            }

            String identifier = parseIdentifier();
            if (identifier.isEmpty()) {
                return ConstantNode.ZERO;
            }

            if (match('(')) {
                List<ExpressionNode> arguments = new ArrayList<>();
                if (!match(')')) {
                    do {
                        arguments.add(parseExpression());
                    } while (match(','));
                    match(')');
                }
                return new FunctionNode(identifier, arguments);
            }

            return new VariableNode(identifier);
        }

        private ExpressionNode parseNumber() {
            int start = index;
            while (index < input.length() && (Character.isDigit(input.charAt(index)) || input.charAt(index) == '.')) {
                index++;
            }
            return new ConstantNode(Double.parseDouble(input.substring(start, index)));
        }

        private String parseIdentifier() {
            int start = index;
            while (index < input.length()) {
                char current = input.charAt(index);
                if (Character.isLetterOrDigit(current) || current == '_' || current == '.') {
                    index++;
                    continue;
                }
                break;
            }
            return input.substring(start, index);
        }

        private boolean match(final char expected) {
            if (peek() == expected) {
                index++;
                return true;
            }
            return false;
        }

        private char peek() {
            return index < input.length() ? input.charAt(index) : '\0';
        }

        private boolean isNumberStart(final char current) {
            return Character.isDigit(current) || current == '.';
        }
    }
}
