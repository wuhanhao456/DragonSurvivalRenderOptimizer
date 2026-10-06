package top.wu949.dsbr.optimizer.mixin;

import by.dragonsurvivalteam.dragonsurvival.client.render.entity.dragon.DragonArmorRenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;
import java.util.Set;

@Mixin(value = DragonArmorRenderLayer.class, remap = false)
public interface ArmorRegistryAccessor {
    @Accessor("generatedArmorTextures") static Set<ResourceLocation> beloong$generated() { throw new AssertionError(); }
    @Accessor("usedArmorTextures") static Set<ResourceLocation> beloong$used() { throw new AssertionError(); }
    @Invoker("prepareArmorTexture") static void beloong$prepare(Player player) { throw new AssertionError(); }
}
