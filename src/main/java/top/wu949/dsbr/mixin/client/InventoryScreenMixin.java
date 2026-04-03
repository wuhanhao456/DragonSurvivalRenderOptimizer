package top.wu949.dsbr.mixin.client;

import top.wu949.dsbr.client.render.InventoryEntityRenderContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @Inject(method = "renderEntityInInventory", at = @At("HEAD"))
    private static void dsbr$pushInventoryRenderContext(final GuiGraphics graphics, final float x, final float y, final float scale, final Vector3f translate,
                                                        final Quaternionf pose, final Quaternionf cameraOrientation, final LivingEntity entity, final CallbackInfo callbackInfo) {
        InventoryEntityRenderContext.push();
    }

    @Inject(method = "renderEntityInInventory", at = @At("RETURN"))
    private static void dsbr$popInventoryRenderContext(final GuiGraphics graphics, final float x, final float y, final float scale, final Vector3f translate,
                                                       final Quaternionf pose, final Quaternionf cameraOrientation, final LivingEntity entity, final CallbackInfo callbackInfo) {
        InventoryEntityRenderContext.pop();
    }
}
