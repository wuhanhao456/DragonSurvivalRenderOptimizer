package top.wu949.dsbr.optimizer.mixin;

import by.dragonsurvivalteam.dragonsurvival.client.render.entity.dragon.DragonGlowLayerRenderer;
import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;
import top.wu949.dsbr.optimizer.gpu.GpuDispatcher;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import org.spongepowered.asm.mixin.Mixin;

/** Pinned DS glow uses EYES + LIGHTNING_TRANSPARENCY: additive blending is order independent. */
@Mixin(value = DragonGlowLayerRenderer.class, remap = false)
public abstract class DragonGlowMixin {
    @WrapMethod(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lby/dragonsurvivalteam/dragonsurvival/common/entity/DragonEntity;Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;FII)V")
    private void beloong$additive(PoseStack stack, DragonEntity dragon, BakedGeoModel model, RenderType type, MultiBufferSource source,
                                  VertexConsumer buffer, float tick, int light, int overlay, Operation<Void> original) {
        GpuDispatcher.enterGlow();
        try { original.call(stack, dragon, model, type, source, buffer, tick, light, overlay); }
        finally { GpuDispatcher.exitGlow(); }
    }
}
