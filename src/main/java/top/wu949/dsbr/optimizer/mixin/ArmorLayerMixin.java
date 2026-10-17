package top.wu949.dsbr.optimizer.mixin;

import by.dragonsurvivalteam.dragonsurvival.client.render.entity.dragon.DragonArmorRenderLayer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import top.wu949.dsbr.optimizer.lowend.LowEndSupport;
import top.wu949.dsbr.optimizer.texture.ArmorAppearance;
import top.wu949.dsbr.optimizer.texture.FramebufferPool;
import top.wu949.dsbr.optimizer.texture.SynthesisScope;
import top.wu949.dsbr.optimizer.texture.TextureCache;

import java.util.Optional;

@Mixin(value = DragonArmorRenderLayer.class, remap = false)
public abstract class ArmorLayerMixin {
    @Inject(method = "constructTrimmedDragonArmorTexture", at = @At("HEAD"))
    private static void beloong$demand(Player p, CallbackInfoReturnable<Optional<ResourceLocation>> ci) {
        if (!TextureCache.enabled(p)) return;
        try {
            ArmorRegistryAccessor.beloong$prepare(p);
        } catch (RuntimeException | LinkageError e) {
            TextureCache.problem(p, "armor prepare", e);
        }
    }

    @Inject(method = "constructTrimmedDragonArmorTexture", at = @At("RETURN"))
    private static void beloong$touch(Player p, CallbackInfoReturnable<Optional<ResourceLocation>> ci) {
        if (!TextureCache.enabled()) return;
        ci.getReturnValue().ifPresent(key -> {
            TextureCache.touch(key);
            Diagnostics.INSTANCE.count(Diagnostics.Counter.HIT, Diagnostics.INSTANCE.detailed() ? p.getUUID() + "/armor/" + key : "armor");
        });
    }

    @Inject(method = "buildUniqueArmorUUID", at = @At("RETURN"), cancellable = true)
    private static void beloong$content(Player p, CallbackInfoReturnable<String> ci) {
        if (!TextureCache.enabled(p)) return;
        try {
            var cached = ArmorAppearance.cached(p);
            if (!ci.getReturnValue().equals(cached)) ci.setReturnValue(ArmorAppearance.extend(ci.getReturnValue(), p));
        } catch (RuntimeException | LinkageError e) {
            // Keep DS's own key for this player instead of failing the render.
            TextureCache.problem(p, "armor key", e);
        }
    }

    @Inject(method = "buildUniqueArmorUUID", at = @At("HEAD"), cancellable = true)
    private static void beloong$cachedContent(Player p, CallbackInfoReturnable<String> ci) {
        if (!TextureCache.enabled(p)) return;
        try {
            var cached = ArmorAppearance.cached(p);
            if (cached != null) ci.setReturnValue(cached);
        } catch (RuntimeException | LinkageError e) {
            TextureCache.problem(p, "armor key", e);
        }
    }

    @WrapMethod(method = "generateArmorTexture")
    private static void beloong$generate(Player p, ResourceLocation key, Operation<Void> original) {
        long start = System.nanoTime();
        int borrowed = FramebufferPool.mark();
        SynthesisScope.enter(p.getUUID() + "/armor", TextureCache.enabled(p));
        try {
            original.call(p, key);
        } catch (RuntimeException | LinkageError e) {
            // Targets borrowed by the failed attempt are unreachable; free them before the original retry.
            FramebufferPool.abandon(borrowed);
            if (!TextureCache.enabled()) throw e;
            TextureCache.fail("armor synthesis", e);
            original.call(p, key);
        } finally {
            SynthesisScope.exit();
        }
        if (!TextureCache.valid(key)) return;
        try {
            if (TextureCache.enabled()) {
                var size = TextureCache.handler(p).body().value().textureSize();
                TextureCache.register(key, (long) size.width() * size.height() * 4);
            }
            Diagnostics.INSTANCE.count(Diagnostics.Counter.GENERATED, p.getUUID() + "/armor/" + key);
            Diagnostics.INSTANCE.nanos("ARMOR_GENERATE", System.nanoTime() - start, p.getUUID() + "/armor/" + key);
        } catch (RuntimeException | LinkageError e) {
            TextureCache.problem(p, "armor cache", e);
        }
    }

    @Inject(method = "purgeUnusedArmorTextures", at = @At("HEAD"), cancellable = true)
    private static void beloong$onDemand(RenderFrameEvent.Pre event, CallbackInfo ci) {
        if (TextureCache.enabled() || LowEndSupport.enabled()) ci.cancel();
    }

    @Redirect(method = "generateArmorTexture", at = @At(value = "NEW", target = "(IIZZ)Lcom/mojang/blaze3d/pipeline/TextureTarget;"))
    private static TextureTarget beloong$borrow(int w, int h, boolean depth, boolean osx) {
        return FramebufferPool.acquire(w, h, depth, osx);
    }

    @Redirect(method = "generateArmorTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;destroyBuffers()V"))
    private static void beloong$return(RenderTarget t) {
        FramebufferPool.release(t);
    }
}
