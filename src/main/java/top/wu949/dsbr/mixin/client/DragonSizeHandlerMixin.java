package top.wu949.dsbr.mixin.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.event.entity.EntityEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.wu949.dsbr.client.DSBRRenderConfig;

@Pseudo
@Mixin(targets = "by.dragonsurvivalteam.dragonsurvival.common.handlers.DragonSizeHandler")
public abstract class DragonSizeHandlerMixin {
    @Inject(method = "getDragonSize", at = @At("HEAD"), cancellable = true, require = 0)
    private static void dsbr$skipLocalPlayerDragonSizeForYsm(
            final EntityEvent.Size event,
            final CallbackInfo callbackInfo
    ) {
        if (!DSBRRenderConfig.useYsmRendererForNormalDragonRender()) {
            return;
        }

        if (Minecraft.getInstance().player != null && event.getEntity() == Minecraft.getInstance().player) {
            callbackInfo.cancel();
        }
    }
}
