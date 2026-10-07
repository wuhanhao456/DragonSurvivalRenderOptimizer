package top.wu949.dsbr.optimizer.validation.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.wu949.dsbr.optimizer.validation.Alpha5SceneProbe;

/** Test-only fixed projection after pack camera hooks; applied identically to both versions. */
@Mixin(value = Camera.class, priority = 100)
abstract class FixedCameraMixin {
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Inject(method = "setup", at = @At("RETURN"))
    private void dsbrValidation$camera(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partial, CallbackInfo ci) {
        if (!Alpha5SceneProbe.fixedCamera() || (Object)this != net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera()) return;
        setPosition(0, -52, 36); setRotation(180, 13);
    }
}
