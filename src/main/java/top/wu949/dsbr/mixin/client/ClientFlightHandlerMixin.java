package top.wu949.dsbr.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.wu949.dsbr.client.DSBRRenderConfig;

@Pseudo
@Mixin(remap = false, targets = "by.dragonsurvivalteam.dragonsurvival.client.handlers.ClientFlightHandler")
public abstract class ClientFlightHandlerMixin {
    private static boolean dsbr$shouldForceVanillaCamera() {
        if (!DSBRRenderConfig.useYsmRendererForNormalDragonRender()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        return player != null && player.isAddedToLevel();
    }

    @Inject(method = "flightCamera(Lnet/neoforged/neoforge/client/event/CalculateDetachedCameraDistanceEvent;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void dsbr$forceVanillaDetachedCameraDistance(
            final CalculateDetachedCameraDistanceEvent event,
            final CallbackInfo callbackInfo
    ) {
        if (!dsbr$shouldForceVanillaCamera()) {
            return;
        }

        float scalingFactor = Math.max(event.getEntityScalingFactor(), 1.0E-4F);
        event.setDistance(event.getDistance() / scalingFactor);
        callbackInfo.cancel();
    }

    @Inject(method = "flightCamera(Lnet/neoforged/neoforge/client/event/ViewportEvent$ComputeCameraAngles;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void dsbr$forceVanillaCameraAngles(
            final ViewportEvent.ComputeCameraAngles event,
            final CallbackInfo callbackInfo
    ) {
        if (!dsbr$shouldForceVanillaCamera()) {
            return;
        }

        ((GameRendererAccessor) Minecraft.getInstance().gameRenderer).dsbr$setZoom(1.0F);
        callbackInfo.cancel();
    }
}
