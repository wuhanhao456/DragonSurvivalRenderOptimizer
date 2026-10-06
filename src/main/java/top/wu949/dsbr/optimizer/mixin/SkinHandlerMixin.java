package top.wu949.dsbr.optimizer.mixin;

import by.dragonsurvivalteam.dragonsurvival.client.skin_editor_system.DragonEditorHandler;
import by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateHandler;
import top.wu949.dsbr.optimizer.texture.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.*;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DragonEditorHandler.class, remap = false)
public abstract class SkinHandlerMixin {
    @WrapMethod(method = "generateSkinTextures")
    private static void beloong$generate(Player p, DragonStateHandler h, Operation<Void> original) {
        if (TextureCache.reuse(p, h)) return;
        long start = System.nanoTime(); SynthesisScope.enter(p == null || h == null ? "skin" : p.getUUID() + "/" + h.stageId());
        try { original.call(p, h); TextureCache.compiled(p, h, System.nanoTime() - start); }
        catch (RuntimeException | LinkageError e) {
            if (!TextureCache.enabled()) throw e;
            TextureCache.fail("skin synthesis", e); original.call(p, h);
        } finally { SynthesisScope.exit(); }
    }
    @Inject(method = "purgeUnusedSkinTextures", at = @At("HEAD"), cancellable = true)
    private static void beloong$onDemand(RenderFrameEvent.Pre event, CallbackInfo ci) { if (TextureCache.enabled()) ci.cancel(); }
    @Redirect(method = "generateSkinTextures", at = @At(value = "NEW", target = "(IIZZ)Lcom/mojang/blaze3d/pipeline/TextureTarget;"))
    private static TextureTarget beloong$borrow(int w, int h, boolean depth, boolean osx) { return FramebufferPool.acquire(w, h, depth, osx); }
    @Redirect(method = "generateSkinTextures", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;destroyBuffers()V"))
    private static void beloong$return(RenderTarget t) { FramebufferPool.release(t); }
}
