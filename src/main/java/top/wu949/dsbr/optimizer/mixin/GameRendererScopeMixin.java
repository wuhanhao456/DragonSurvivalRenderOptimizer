package top.wu949.dsbr.optimizer.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import top.wu949.dsbr.optimizer.gpu.GpuDispatcher;

@Mixin(GameRenderer.class)
public abstract class GameRendererScopeMixin {
    @WrapMethod(method = "renderLevel")
    private void dsbr$worldScope(DeltaTracker delta, Operation<Void> original) {
        GpuDispatcher.enterWorld();
        try { original.call(delta); }
        finally { GpuDispatcher.exitWorld(); }
    }
}
