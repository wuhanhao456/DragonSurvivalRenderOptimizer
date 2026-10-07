package top.wu949.dsbr.optimizer.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.model.GeoModel;
import top.wu949.dsbr.optimizer.lowend.LowEndAnimations;
@Mixin(value=GeoModel.class,remap=false)
abstract class LowEndAnimationMixin {
    @WrapMethod(method="handleAnimations")
    private void dsro$scope(GeoAnimatable actor,long id,AnimationState<GeoAnimatable> state,float partial,Operation<Void> original) {
        if(!top.wu949.dsbr.optimizer.lowend.LowEndSupport.enabled()) { original.call(actor,id,state,partial);return; }
        var scope=LowEndAnimations.enter(actor,(GeoModel<?>)(Object)this);boolean completed=false;
        try { original.call(actor,id,state,partial);completed=true; } finally { LowEndAnimations.exit(scope,completed); }
    }
    @WrapOperation(method="handleAnimations",at=@At(value="INVOKE",target="Lsoftware/bernie/geckolib/animation/AnimationProcessor;preAnimationSetup(Lsoftware/bernie/geckolib/animation/AnimationState;D)V"))
    private void dsro$queries(AnimationProcessor<?> processor,AnimationState<?> state,double time,Operation<Void> original) {
        if(!LowEndAnimations.holding())original.call(processor,state,time);
    }
    @WrapOperation(method="handleAnimations",at=@At(value="INVOKE",target="Lsoftware/bernie/geckolib/animation/AnimationProcessor;tickAnimation(Lsoftware/bernie/geckolib/animatable/GeoAnimatable;Lsoftware/bernie/geckolib/model/GeoModel;Lsoftware/bernie/geckolib/animation/AnimatableManager;DLsoftware/bernie/geckolib/animation/AnimationState;Z)V"))
    private void dsro$evaluate(AnimationProcessor<?> processor,GeoAnimatable actor,GeoModel<?> model,AnimatableManager<?> manager,double time,AnimationState<?> state,boolean crash,Operation<Void> original) {
        if(!LowEndAnimations.holding())original.call(processor,actor,model,manager,time,state,crash);
    }
    @WrapOperation(method="handleAnimations",at=@At(value="INVOKE",target="Lsoftware/bernie/geckolib/model/GeoModel;setCustomAnimations(Lsoftware/bernie/geckolib/animatable/GeoAnimatable;JLsoftware/bernie/geckolib/animation/AnimationState;)V"))
    private void dsro$custom(GeoModel<?> model,GeoAnimatable actor,long id,AnimationState<?> state,Operation<Void> original) {
        if(!LowEndAnimations.holding())original.call(model,actor,id,state);
    }
}
