package top.wu949.dsbr.optimizer.validation.mixin;

import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import org.spongepowered.asm.mixin.Mixin;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import top.wu949.dsbr.optimizer.validation.AnimationSamples;
import top.wu949.dsbr.optimizer.validation.NpcGeometryMeasurements;

@Mixin(value = GeoRenderer.class, remap = false)
public interface AnimationSampleMixin {
    @WrapMethod(method = "actuallyRender")
    default void dsbr$sample(PoseStack pose, GeoAnimatable animatable, BakedGeoModel model, RenderType type, MultiBufferSource source,
                             VertexConsumer buffer, boolean rerender, float tick, int light, int overlay, int colour, Operation<Void> original) {
        long start = NpcGeometryMeasurements.begin(this);
        try { original.call(pose, animatable, model, type, source, buffer, rerender, tick, light, overlay, colour); }
        finally { NpcGeometryMeasurements.end(start); }
        if (animatable instanceof DragonEntity dragon && !rerender) AnimationSamples.capture((GeoRenderer<?>)(Object)this, dragon, model);
    }
}
