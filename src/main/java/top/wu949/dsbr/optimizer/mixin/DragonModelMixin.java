package top.wu949.dsbr.optimizer.mixin;

import by.dragonsurvivalteam.dragonsurvival.client.models.DragonModel;
import by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateHandler;
import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.wu949.dsbr.optimizer.OptimizationStage;
import top.wu949.dsbr.optimizer.lowend.LowEndSupport;
import top.wu949.dsbr.optimizer.soul.SoulRenderContext;
import top.wu949.dsbr.optimizer.texture.AppearanceCache;
import top.wu949.dsbr.optimizer.texture.AppearanceKey;
import top.wu949.dsbr.optimizer.texture.TextureCache;

@Mixin(value = DragonModel.class, remap = false)
public abstract class DragonModelMixin {
    @Shadow private ResourceLocation overrideTexture;

    /** Content-addressed names: a texture still queued for drawing is never overwritten by a new appearance. */
    @Inject(method = "dynamicTexture", at = @At("RETURN"), cancellable = true)
    private static void beloong$immutableKey(Player player, DragonStateHandler h, boolean glow, CallbackInfoReturnable<ResourceLocation> ci) {
        if (h == null || !TextureCache.enabled(player)) return;
        try {
            if (h.body() == null) return;
            if (SoulRenderContext.sharedTexture(player)) {
                ci.setReturnValue(AppearanceCache.soulTexture(h, glow));
                return;
            }
            ci.setReturnValue(OptimizationStage.VALUE >= 2 ? AppearanceCache.texture(h, ci.getReturnValue(), glow)
                    : ci.getReturnValue().withSuffix("_bro_" + AppearanceKey.skin(h).digest()));
        } catch (RuntimeException | LinkageError e) {
            // Keep DS's own name; this player falls back to the original path.
            TextureCache.problem(player, "skin key", e);
        }
    }

    @Inject(method = "getTextureResource(Lby/dragonsurvivalteam/dragonsurvival/common/entity/DragonEntity;)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true)
    private void beloong$prepare(DragonEntity dragon, CallbackInfoReturnable<ResourceLocation> ci) {
        if (overrideTexture != null) return;
        if (LowEndSupport.enabled()) {
            var base = LowEndSupport.skin(dragon);
            if (base != null) {
                ci.setReturnValue(base);
                return;
            }
        }
        if (!TextureCache.enabled()) return;
        try {
            TextureCache.demand(dragon.overrideUUIDWithLocalPlayerForTextureFetch ? Minecraft.getInstance().player : dragon.getPlayer());
        } catch (LinkageError e) {
            // DragonEntity changed shape; demand() handles per-player problems itself.
            TextureCache.fail("dragon texture lookup", e);
        }
    }

    @Inject(method = "getTextureResource(Lby/dragonsurvivalteam/dragonsurvival/common/entity/DragonEntity;)Lnet/minecraft/resources/ResourceLocation;", at = @At("RETURN"))
    private void beloong$touch(DragonEntity dragon, CallbackInfoReturnable<ResourceLocation> ci) {
        if (TextureCache.enabled()) TextureCache.touch(ci.getReturnValue());
    }
}
