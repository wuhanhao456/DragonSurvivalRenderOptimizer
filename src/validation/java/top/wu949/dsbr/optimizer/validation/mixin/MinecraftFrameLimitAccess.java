package top.wu949.dsbr.optimizer.validation.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftFrameLimitAccess {
    @Invoker("getFramerateLimit") int dsbr$getEffectiveFramerateLimit();
}
