package top.wu949.dsbr.optimizer.mixin;

import by.dragonsurvivalteam.dragonsurvival.client.util.RenderingUtils;
import top.wu949.dsbr.optimizer.texture.*;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderingUtils.class, remap = false)
public abstract class RenderingUtilsMixin {
    @Inject(method = "copyTextureFromRenderTarget", at = @At("HEAD"), cancellable = true)
    private static void beloong$copy(RenderTarget source, ResourceLocation key, CallbackInfo ci) {
        if (!SynthesisScope.active()) {
            if (SynthesisScope.inDS()) Diagnostics.INSTANCE.count(Diagnostics.Counter.READBACK, SynthesisScope.owner() + "/" + key);
            return;
        }
        var manager = Minecraft.getInstance().getTextureManager();
        try {
            var existing = manager.getTexture(key, null);
            if (existing instanceof GpuComposedTexture gpu) gpu.copy(source);
            else {
                var gpu = new GpuComposedTexture(SynthesisScope.owner() + "/" + key);
                try { gpu.copy(source); manager.register(key, gpu); }
                catch (RuntimeException | LinkageError e) { gpu.close(); throw e; }
            }
            ci.cancel();
        } catch (RuntimeException | LinkageError e) { TextureCache.fail("GPU texture copy", e); }
    }
}
