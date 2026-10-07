package top.wu949.dsbr.optimizer.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import by.dragonsurvivalteam.dragonsurvival.client.render.blocks.DragonSoulRenderer;
import top.wu949.dsbr.optimizer.lowend.SoulIndicator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;
@Mixin(value = DragonSoulRenderer.class, remap = false)
public interface SoulIndicatorMixin extends SoulIndicator {
    @Invoker("renderBlock") void dsro$indicator(BlockState state, PoseStack pose, MultiBufferSource buffer, int light, int overlay);
}
