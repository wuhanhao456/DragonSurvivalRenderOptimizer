package top.wu949.dsbr.optimizer.mixin;

import top.wu949.dsbr.optimizer.gpu.GpuDispatcher;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = MultiBufferSource.BufferSource.class, remap = false)
public abstract class BufferSourceMixin {
    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void beloong$buffer(RenderType type, CallbackInfoReturnable<VertexConsumer> ci) { GpuDispatcher.remember(this, type, ci.getReturnValue()); }
    @Inject(method = "endBatch(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/BufferBuilder;)V", at = @At("TAIL"))
    private void beloong$batch(RenderType type, BufferBuilder builder, CallbackInfo ci) { GpuDispatcher.drawWithState(this, type); }
}
