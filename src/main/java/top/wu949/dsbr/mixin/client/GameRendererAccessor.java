package top.wu949.dsbr.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GameRenderer.class, remap = false)
public interface GameRendererAccessor {
    @Accessor("zoom")
    void dsbr$setZoom(float zoom);
}
