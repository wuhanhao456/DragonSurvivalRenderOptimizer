package top.wu949.dsbr.optimizer.mixin;

import by.dragonsurvivalteam.dragonsurvival.client.render.blocks.DragonSoulRenderer;
import by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateHandler;
import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;
import by.dragonsurvivalteam.dragonsurvival.server.tileentity.DragonSoulBlockEntity;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import top.wu949.dsbr.optimizer.soul.SoulRenderContext;
import top.wu949.dsbr.optimizer.texture.TextureCache;

@Mixin(value = DragonSoulRenderer.class, remap = false)
public abstract class SoulRendererMixin {
    @WrapMethod(method = "render(Lby/dragonsurvivalteam/dragonsurvival/server/tileentity/DragonSoulBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V")
    private void dsbr$soul(DragonSoulBlockEntity soul, float tick, PoseStack pose, MultiBufferSource source, int light, int overlay, Operation<Void> original) {
        if (!TextureCache.enabled()) { original.call(soul, tick, pose, source, light, overlay); return; }
        SoulRenderContext.enter(soul); long start = System.nanoTime();
        try { original.call(soul, tick, pose, source, light, overlay); }
        finally { SoulRenderContext.exit(); Diagnostics.INSTANCE.count(Diagnostics.Counter.SOUL_RENDER, "soul"); Diagnostics.INSTANCE.nanos("SOUL_RENDER_CPU", System.nanoTime() - start); }
    }
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lby/dragonsurvivalteam/dragonsurvival/client/util/FakeClientPlayerUtils;getFakeDragon(ILby/dragonsurvivalteam/dragonsurvival/common/capability/DragonStateHandler;)Lby/dragonsurvivalteam/dragonsurvival/common/entity/DragonEntity;"))
    private DragonEntity dsbr$register(int index, DragonStateHandler handler, Operation<DragonEntity> original) {
        // Preserve DS's per-frame active-player registration and handler rebinding.
        var dragon = original.call(index, handler);
        if (SoulRenderContext.active()) SoulRenderContext.register(dragon);
        return dragon;
    }
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lby/dragonsurvivalteam/dragonsurvival/util/AnimationUtils;doesAnimationExist(Lsoftware/bernie/geckolib/model/GeoModel;Lsoftware/bernie/geckolib/animatable/GeoAnimatable;Ljava/lang/String;)Z"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean dsbr$animation(GeoModel model, GeoAnimatable dragon, String name, Operation<Boolean> original) {
        if (!SoulRenderContext.active()) return original.call(model, dragon, name);
        var resource = model.getAnimationResource(dragon);
        var cached = SoulRenderContext.cachedAnimation(resource, name);
        if (cached != null) return cached;
        boolean exists = original.call(model, dragon, name);
        SoulRenderContext.animation(resource, name, exists); return exists;
    }
}
