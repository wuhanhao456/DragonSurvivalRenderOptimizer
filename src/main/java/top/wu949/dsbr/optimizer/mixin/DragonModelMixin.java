package top.wu949.dsbr.optimizer.mixin;

import by.dragonsurvivalteam.dragonsurvival.client.models.DragonModel;
import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;
import top.wu949.dsbr.optimizer.texture.TextureCache;
import top.wu949.dsbr.optimizer.texture.AppearanceKey;
import by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DragonModel.class, remap = false)
public abstract class DragonModelMixin {
    @Shadow private ResourceLocation overrideTexture;
    @Inject(method = "dynamicTexture", at = @At("RETURN"), cancellable = true)
    private static void beloong$immutableKey(Player player, DragonStateHandler h, boolean glow, CallbackInfoReturnable<ResourceLocation> ci) {
        if (TextureCache.enabled() && h.body() != null) ci.setReturnValue(top.wu949.dsbr.optimizer.OptimizationStage.VALUE >= 2 ? top.wu949.dsbr.optimizer.texture.AppearanceCache.texture(h, ci.getReturnValue(), glow) : ci.getReturnValue().withSuffix("_bro_" + AppearanceKey.skin(h).digest()));
    }
    @Inject(method = "getTextureResource(Lby/dragonsurvivalteam/dragonsurvival/common/entity/DragonEntity;)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"))
    private void beloong$prepare(DragonEntity dragon, CallbackInfoReturnable<ResourceLocation> ci) {
        if (overrideTexture == null) TextureCache.demand(dragon.overrideUUIDWithLocalPlayerForTextureFetch ? Minecraft.getInstance().player : dragon.getPlayer());
    }
    @Inject(method = "getTextureResource(Lby/dragonsurvivalteam/dragonsurvival/common/entity/DragonEntity;)Lnet/minecraft/resources/ResourceLocation;", at = @At("RETURN"))
    private void beloong$touch(DragonEntity dragon, CallbackInfoReturnable<ResourceLocation> ci) { if (TextureCache.enabled()) TextureCache.touch(ci.getReturnValue()); }
}
