package top.wu949.dsbr.optimizer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.model.GeoModel;
import top.wu949.dsbr.optimizer.animation.FrameAnimations;
import top.wu949.dsbr.optimizer.OptimizerConfig;

@Mixin(value = GeoModel.class, remap = false)
abstract class GeoAnimationMixin {
    // preAnimationSetup and setCustomAnimations remain outside this interception.
    @WrapOperation(method = "handleAnimations", at = @At(value = "INVOKE", target = "Lsoftware/bernie/geckolib/animation/AnimationProcessor;tickAnimation(Lsoftware/bernie/geckolib/animatable/GeoAnimatable;Lsoftware/bernie/geckolib/model/GeoModel;Lsoftware/bernie/geckolib/animation/AnimatableManager;DLsoftware/bernie/geckolib/animation/AnimationState;Z)V"))
    private void dsbr$baseline(AnimationProcessor<GeoAnimatable> processor, GeoAnimatable actor, GeoModel<GeoAnimatable> model, AnimatableManager<GeoAnimatable> manager,
                               double time, AnimationState<GeoAnimatable> animation, boolean crash, Operation<Void> original) {
        if (!OptimizerConfig.FRAME_ANIMATIONS.get()) { original.call(processor, actor, model, manager, time, animation, crash); return; }
        if (FrameAnimations.restore(actor, model, processor, manager, time, animation)) return;
        original.call(processor, actor, model, manager, time, animation, crash);
        FrameAnimations.capture(actor, model, processor, manager, time, animation);
    }
}
