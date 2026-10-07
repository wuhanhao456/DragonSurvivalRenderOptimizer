package top.wu949.dsbr.optimizer.lowend;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;
public interface SoulIndicator {
    void dsro$indicator(BlockState state, PoseStack pose, MultiBufferSource buffer, int light, int overlay);
}
