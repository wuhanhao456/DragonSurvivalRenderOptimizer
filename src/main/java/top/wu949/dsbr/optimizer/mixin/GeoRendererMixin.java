package top.wu949.dsbr.optimizer.mixin;

import top.wu949.dsbr.optimizer.gpu.GpuDispatcher;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import by.dragonsurvivalteam.dragonsurvival.client.render.entity.dragon.DragonRenderer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.renderer.GeoRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GeoRenderer.class, remap = false)
public interface GeoRendererMixin {
    @WrapMethod(method = "actuallyRender")
    default void beloong$pose(PoseStack pose, GeoAnimatable animatable, BakedGeoModel model, RenderType type, MultiBufferSource source, VertexConsumer buffer,
                             boolean rerender, float tick, int light, int overlay, int colour, Operation<Void> original) {
        var context = GpuDispatcher.begin(this, model, type, buffer); boolean completed = false;
        try { original.call(pose, animatable, model, type, source, buffer, rerender, tick, light, overlay, colour); completed = true; }
        finally { GpuDispatcher.end(context, completed); }
    }
    @WrapMethod(method = "renderCubesOfBone")
    default void beloong$cubes(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay, int colour, Operation<Void> original) {
        if (!((Object)this instanceof DragonRenderer)) { original.call(pose, bone, buffer, light, overlay, colour); return; }
        long start = System.nanoTime();
        if (GpuDispatcher.bone(pose, bone, buffer, light, overlay, colour)) Diagnostics.INSTANCE.nanos("GPU_BONE_SUBMIT", System.nanoTime() - start);
        else { original.call(pose, bone, buffer, light, overlay, colour); Diagnostics.INSTANCE.nanos("CPU_VERTEX_SUBMIT", System.nanoTime() - start); }
    }
}
