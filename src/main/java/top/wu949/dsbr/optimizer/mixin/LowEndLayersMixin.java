package top.wu949.dsbr.optimizer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import top.wu949.dsbr.optimizer.lowend.LowEndLayers;
import java.util.List;

/** Filter execution only. getRenderLayers remains the original mutable registration API. */
@Mixin(value=GeoRenderer.class,remap=false)
public interface LowEndLayersMixin {
    @WrapOperation(method={"applyRenderLayers","applyRenderLayersForBone","preApplyRenderLayers"},
        at=@At(value="INVOKE",target="Lsoftware/bernie/geckolib/renderer/GeoRenderer;getRenderLayers()Ljava/util/List;"))
    default List<GeoRenderLayer<?>> dsro$executionLayers(GeoRenderer<?> renderer,Operation<List<GeoRenderLayer<?>>> original) {
        return LowEndLayers.filter(renderer,original.call(renderer));
    }
}
