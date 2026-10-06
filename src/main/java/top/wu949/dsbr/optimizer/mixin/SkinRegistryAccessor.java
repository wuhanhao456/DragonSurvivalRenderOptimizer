package top.wu949.dsbr.optimizer.mixin;

import by.dragonsurvivalteam.dragonsurvival.client.skin_editor_system.DragonEditorHandler;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Set;

@Mixin(value = DragonEditorHandler.class, remap = false)
public interface SkinRegistryAccessor {
    @Accessor("generatedSkinTextures") static Set<ResourceLocation> beloong$generated() { throw new AssertionError(); }
    @Accessor("usedSkinTextures") static Set<ResourceLocation> beloong$used() { throw new AssertionError(); }
}
