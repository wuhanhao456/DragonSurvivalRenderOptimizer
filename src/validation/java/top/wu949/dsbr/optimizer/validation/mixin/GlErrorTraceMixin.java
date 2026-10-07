package top.wu949.dsbr.optimizer.validation.mixin;

import com.mojang.blaze3d.platform.GlDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.wu949.dsbr.optimizer.RenderOptimizer;

/** Diagnostic-only synchronous GL call site, never enabled in paired performance samples. */
@Mixin(GlDebug.class)
public abstract class GlErrorTraceMixin {
    private static int dsbr$errors;
    @Inject(method="printDebugLog",at=@At("HEAD"))
    private static void dsbr$trace(int source,int type,int id,int severity,int length,long message,long user,CallbackInfo ci) {
        if(type==0x824c && dsbr$errors++<8 && (Boolean.getBoolean("beloongrender.npcSmoke")||Boolean.getBoolean("beloongrender.singleRound")||Boolean.getBoolean("beloongrender.reloadDiagnostic")))
            RenderOptimizer.LOGGER.warn("Isolated validation GL error call site id={}",id,new Throwable("Synchronous GL diagnostic"));
    }
}
