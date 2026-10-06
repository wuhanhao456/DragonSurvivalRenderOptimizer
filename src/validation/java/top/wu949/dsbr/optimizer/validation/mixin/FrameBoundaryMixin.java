package top.wu949.dsbr.optimizer.validation.mixin;
import top.wu949.dsbr.optimizer.validation.ProbeFrames;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only: includes swap, frame limiting and frames with cached GUI content. */
@Mixin(Minecraft.class)
abstract class FrameBoundaryMixin {
    @Inject(method = "runTick(Z)V", at = @At("HEAD"))
    private void dsbrValidation$displayBoundary(boolean tick, CallbackInfo ci) { ProbeFrames.boundary(); }
}
