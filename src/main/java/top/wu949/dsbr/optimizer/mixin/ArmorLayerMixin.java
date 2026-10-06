package top.wu949.dsbr.optimizer.mixin;

import by.dragonsurvivalteam.dragonsurvival.client.render.entity.dragon.DragonArmorRenderLayer;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import top.wu949.dsbr.optimizer.texture.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.Optional;

@Mixin(value = DragonArmorRenderLayer.class, remap = false)
public abstract class ArmorLayerMixin {
    @Inject(method = "constructTrimmedDragonArmorTexture", at = @At("HEAD"))
    private static void beloong$demand(Player p, CallbackInfoReturnable<Optional<ResourceLocation>> ci) { if (TextureCache.enabled()) ArmorRegistryAccessor.beloong$prepare(p); }
    @Inject(method = "constructTrimmedDragonArmorTexture", at = @At("RETURN"))
    private static void beloong$touch(Player p, CallbackInfoReturnable<Optional<ResourceLocation>> ci) {
        if (TextureCache.enabled()) ci.getReturnValue().ifPresent(key -> { TextureCache.touch(key); Diagnostics.INSTANCE.count(Diagnostics.Counter.HIT, p.getUUID() + "/armor/" + key); });
    }
    @Inject(method = "buildUniqueArmorUUID", at = @At("RETURN"), cancellable = true)
    private static void beloong$content(Player p, CallbackInfoReturnable<String> ci) {
        if (TextureCache.enabled()) ci.setReturnValue(ArmorAppearance.extend(ci.getReturnValue(), p));
    }
    @WrapMethod(method = "generateArmorTexture")
    private static void beloong$generate(Player p, ResourceLocation key, Operation<Void> original) {
        long start = System.nanoTime(); SynthesisScope.enter(p.getUUID() + "/armor");
        try {
            original.call(p, key);
            if (TextureCache.valid(key)) {
                if (TextureCache.enabled()) { var size = TextureCache.handler(p).body().value().textureSize(); TextureCache.register(key, (long)size.width() * size.height() * 4); }
                Diagnostics.INSTANCE.count(Diagnostics.Counter.GENERATED, p.getUUID() + "/armor/" + key);
                Diagnostics.INSTANCE.nanos("ARMOR_GENERATE", System.nanoTime() - start, p.getUUID() + "/armor/" + key);
            }
        } catch (RuntimeException | LinkageError e) {
            if (!TextureCache.enabled()) throw e;
            TextureCache.fail("armor synthesis", e); original.call(p, key);
        } finally { SynthesisScope.exit(); }
    }
    @Inject(method = "purgeUnusedArmorTextures", at = @At("HEAD"), cancellable = true)
    private static void beloong$onDemand(RenderFrameEvent.Pre event, CallbackInfo ci) { if (TextureCache.enabled()) ci.cancel(); }
    @Redirect(method = "generateArmorTexture", at = @At(value = "NEW", target = "(IIZZ)Lcom/mojang/blaze3d/pipeline/TextureTarget;"))
    private static TextureTarget beloong$borrow(int w, int h, boolean depth, boolean osx) { return FramebufferPool.acquire(w, h, depth, osx); }
    @Redirect(method = "generateArmorTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;destroyBuffers()V"))
    private static void beloong$return(RenderTarget t) { FramebufferPool.release(t); }
}
