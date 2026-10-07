package top.wu949.dsbr.optimizer.mixin;

import top.wu949.dsbr.optimizer.gpu.GpuDispatcher;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import top.wu949.dsbr.optimizer.compat.RendererAdmission;
import top.wu949.dsbr.optimizer.gpu.CpuGeometry;
import top.wu949.dsbr.optimizer.OptimizerConfig;
import org.joml.*;
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
        var kind = RendererAdmission.kind(this);
        if (kind == RendererAdmission.Kind.OTHER) { original.call(pose, bone, buffer, light, overlay, colour); return; }
        long start = System.nanoTime();
        if (GpuDispatcher.bone(pose, bone, buffer, light, overlay, colour)) Diagnostics.INSTANCE.nanos("GPU_BONE_SUBMIT", System.nanoTime() - start);
        else {
            original.call(pose, bone, buffer, light, overlay, colour);
            long elapsed = System.nanoTime() - start;
            Diagnostics.INSTANCE.nanos("CPU_VERTEX_SUBMIT", elapsed);
            Diagnostics.INSTANCE.nanos(kind == RendererAdmission.Kind.NPC ? "NPC_CPU_VERTEX_SUBMIT" : kind == RendererAdmission.Kind.SOUL ? "SOUL_CPU_VERTEX_SUBMIT" : "PLAYER_CPU_VERTEX_SUBMIT", elapsed);
            Diagnostics.INSTANCE.count(kind == RendererAdmission.Kind.NPC ? Diagnostics.Counter.NPC_CPU_BONE : kind == RendererAdmission.Kind.SOUL ? Diagnostics.Counter.SOUL_CPU_BONE : Diagnostics.Counter.PLAYER_CPU_BONE, "cpu");
        }
    }
    default boolean dsbr$scratchEligible() {
        return (OptimizerConfig.MODE.get() == OptimizerConfig.Mode.GPU || top.wu949.dsbr.optimizer.lowend.LowEndSupport.enabled()) && OptimizerConfig.CPU_SCRATCH.get()
            && RendererAdmission.kind(this) != RendererAdmission.Kind.OTHER && RendererAdmission.originalGeometry(this);
    }
    @WrapMethod(method = "renderCube")
    default void dsbr$cubeScratch(PoseStack pose, GeoCube cube, VertexConsumer buffer, int light, int overlay, int colour, Operation<Void> original) {
        if (!dsbr$scratchEligible()) { original.call(pose, cube, buffer, light, overlay, colour); return; }
        CpuGeometry.cube((GeoRenderer<?>)this, pose, cube, buffer, light, overlay, colour);
    }
    @WrapMethod(method = "createVerticesOfQuad")
    default void dsbr$vertexScratch(GeoQuad quad, Matrix4f matrix, Vector3f normal, VertexConsumer buffer, int light, int overlay, int colour, Operation<Void> original) {
        if (!dsbr$scratchEligible()) { original.call(quad, matrix, normal, buffer, light, overlay, colour); return; }
        CpuGeometry.quad(quad, matrix, normal, buffer, light, overlay, colour);
    }
}
