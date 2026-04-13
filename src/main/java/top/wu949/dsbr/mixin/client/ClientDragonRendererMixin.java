package top.wu949.dsbr.mixin.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.wu949.dsbr.client.DSBRRenderConfig;

@Pseudo
@Mixin(targets = "by.dragonsurvivalteam.dragonsurvival.client.render.ClientDragonRenderer")
public abstract class ClientDragonRendererMixin {
    @Inject(method = "renderDragon", at = @At("HEAD"), cancellable = true, require = 0)
    private static void dsbr$skipDragonEntityRenderForYsm(final RenderPlayerEvent.Pre event, final CallbackInfo callbackInfo) {
        if (DSBRRenderConfig.useYsmRendererForNormalDragonRender()
                && Minecraft.getInstance().player == event.getEntity()) {
            callbackInfo.cancel();
        }
    }
}
