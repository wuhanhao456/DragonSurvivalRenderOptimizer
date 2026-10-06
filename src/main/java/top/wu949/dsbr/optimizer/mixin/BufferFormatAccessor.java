package top.wu949.dsbr.optimizer.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BufferBuilder.class, remap = false)
public interface BufferFormatAccessor {
    @Accessor("format") VertexFormat beloong$format();
}
