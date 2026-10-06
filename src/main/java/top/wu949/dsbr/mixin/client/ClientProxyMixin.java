package top.wu949.dsbr.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.wu949.dsbr.client.DSBRRenderConfig;

@Pseudo
@Mixin(remap = false, targets = "by.dragonsurvivalteam.dragonsurvival.util.proxy.ClientProxy")
public abstract class ClientProxyMixin {
    @Inject(method = "dragonRenderingWasCancelled", at = @At("HEAD"), cancellable = true, require = 0)
    private void dsbr$forceDragonRenderingCancelledForLocalYsmPlayer(
            final Player player,
            final CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (!DSBRRenderConfig.useYsmRendererForNormalDragonRender()) {
            return;
        }

        if (Minecraft.getInstance().player == player) {
            callbackInfo.setReturnValue(true);
        }
    }
}
