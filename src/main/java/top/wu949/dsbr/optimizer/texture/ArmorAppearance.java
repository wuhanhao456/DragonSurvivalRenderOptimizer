package top.wu949.dsbr.optimizer.texture;

import by.dragonsurvivalteam.dragonsurvival.compat.car.CosmeticArmorReworkedHelper;
import by.dragonsurvivalteam.dragonsurvival.compat.curios.CurioAPIHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class ArmorAppearance {
    public static String extend(String dsKey, Player p) {
        var h = TextureCache.handler(p); var size = h.body().value().textureSize();
        var text = new StringBuilder(dsKey).append('|').append(h.speciesId()).append('|').append(h.getModel()).append('|').append(size);
        for (var slot : EquipmentSlot.values()) if (slot.isArmor()) {
            text.append('|').append(slot).append('=').append(item(CosmeticArmorReworkedHelper.getItemVisibleInSlot(p, slot), p));
            text.append('|').append(p.getItemBySlot(slot).get(DataComponents.DYED_COLOR));
        }
        var curios = CurioAPIHelper.getVisibleCurioItems(p);
        if (curios != null) for (var stack : curios) text.append("|curio=").append(item(stack, p));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private static String item(ItemStack original, Player p) {
        if (original.isEmpty()) return "empty";
        var copy = original.copyWithCount(1);
        copy.remove(DataComponents.DAMAGE); copy.remove(DataComponents.REPAIR_COST);
        return copy.save(p.registryAccess()).toString();
    }
}
