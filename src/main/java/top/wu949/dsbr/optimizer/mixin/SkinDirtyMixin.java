package top.wu949.dsbr.optimizer.mixin;

import by.dragonsurvivalteam.dragonsurvival.common.capability.SkinData;
import top.wu949.dsbr.optimizer.OptimizerConfig;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Holder;
import by.dragonsurvivalteam.dragonsurvival.registry.dragon.body.DragonBody;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SkinData.class, remap = false)
public abstract class SkinDirtyMixin implements top.wu949.dsbr.optimizer.texture.SkinRevision {
    @org.spongepowered.asm.mixin.Unique private volatile long dsbr$skinRevision;
    public long dsbr$revision() { return dsbr$skinRevision; }
    @Inject(method = "deserializeNBT(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void beloong$sync(HolderLookup.Provider provider, CompoundTag nbt, CallbackInfo ci) {
        beloong$traceSync();
    }
    @Inject(method = "deserializeNBT(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/Holder;)V", at = @At("TAIL"))
    private void beloong$syncWithBody(HolderLookup.Provider provider, CompoundTag nbt, Holder<DragonBody> body, CallbackInfo ci) { beloong$traceSync(); }
    private void beloong$traceSync() {
        dsbr$skinRevision++;
        Diagnostics.INSTANCE.count(Diagnostics.Counter.STATE_SYNC, Diagnostics.INSTANCE.detailed() ? "SkinData@" + System.identityHashCode(this) + "/deserializeNBT" : "skinSync");
        if (OptimizerConfig.DIRTY_TRACES.get()) Diagnostics.INSTANCE.trace("SkinData@" + System.identityHashCode(this) + "/deserializeNBT: "
                + StackWalker.getInstance().walk(s -> s.skip(1).limit(8).map(Object::toString).toList()));
    }
    @Inject(method = "compileSkin", at = @At("HEAD"))
    private void beloong$dirty(ResourceKey<?> stage, CallbackInfo ci) {
        dsbr$skinRevision++;
        Diagnostics.INSTANCE.count(Diagnostics.Counter.REQUEST, Diagnostics.INSTANCE.detailed() ? "dirty/" + stage.location() : "skinDirty");
        if (OptimizerConfig.DIRTY_TRACES.get()) Diagnostics.INSTANCE.trace("SkinData@" + System.identityHashCode(this) + "/" + stage.location() + ": "
                + StackWalker.getInstance().walk(s -> s.skip(1).limit(8).map(Object::toString).toList()));
    }
}
