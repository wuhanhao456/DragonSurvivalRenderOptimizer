package top.wu949.dsbr.optimizer.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import software.bernie.geckolib.animation.AnimationController;
import top.wu949.dsbr.optimizer.animation.ControllerRevision;

@Mixin(value = AnimationController.class, remap = false)
abstract class AnimationControllerMixin implements ControllerRevision {
    @Shadow protected AnimationController.SoundKeyframeHandler<?> soundKeyframeHandler;
    @Shadow protected AnimationController.ParticleKeyframeHandler<?> particleKeyframeHandler;
    @Shadow protected AnimationController.CustomKeyframeHandler<?> customKeyframeHandler;
    @Unique private long dsbr$revision;
    @Override public long dsbr$revision() { return dsbr$revision; }
    @Override public boolean dsbr$eventful() { return soundKeyframeHandler != null || particleKeyframeHandler != null || customKeyframeHandler != null; }
    @Inject(method = {"forceAnimationReset", "stop", "setAnimation"}, at = @At("HEAD"))
    private void dsbr$mutate(CallbackInfo ci) { dsbr$revision++; }
    @Inject(method = {"tryTriggerAnimation", "stopTriggeredAnimation", "setSoundKeyframeHandler", "setParticleKeyframeHandler", "setCustomInstructionKeyframeHandler", "setAnimationSpeedHandler", "setAnimationSpeed", "setOverrideEasingType", "setOverrideEasingTypeFunction", "triggerableAnim", "receiveTriggeredAnimations", "transitionLength"}, at = @At("HEAD"))
    private void dsbr$mutateReturning(CallbackInfoReturnable<?> ci) { dsbr$revision++; }
}
