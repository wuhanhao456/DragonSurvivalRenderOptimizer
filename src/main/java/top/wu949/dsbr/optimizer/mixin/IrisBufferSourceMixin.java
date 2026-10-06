package top.wu949.dsbr.optimizer.mixin;

import top.wu949.dsbr.optimizer.gpu.GpuDispatcher;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

@Pseudo
@Mixin(targets = "net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource", remap = false)
public abstract class IrisBufferSourceMixin {
    @Shadow @Final private Map<RenderType, ?> typeToSegment;
    @Shadow private List<RenderType> renderOrder;
    @WrapOperation(method = "getBuffer", at = @At(value = "INVOKE", target = "Lnet/irisshaders/batchedentityrendering/impl/SegmentedBufferBuilder;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer beloong$buffer(@Coerce Object builder, RenderType wrapped, Operation<VertexConsumer> original) {
        var buffer = original.call(builder, wrapped); GpuDispatcher.remember(this, wrapped, buffer); return buffer;
    }
    @Inject(method = "readyUp", at = @At("TAIL"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void beloong$emptySegments(CallbackInfo ci) {
        // Preserve graph ordering even when a type contains GPU commands and no CPU MeshData.
        for (var type : renderOrder) if (GpuDispatcher.has(this, type)) ((Map)typeToSegment).putIfAbsent(type, Collections.emptyList());
    }
    @WrapOperation(method = {"endBatch()V", "endBatchWithType"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;clearRenderState()V"))
    private void beloong$draw(RenderType type, Operation<Void> original) {
        try { GpuDispatcher.draw(this, type); } finally { original.call(type); }
    }
}
