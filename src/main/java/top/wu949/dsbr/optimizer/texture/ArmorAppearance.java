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
import java.util.*;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;

public final class ArmorAppearance {
    private static final EquipmentSlot[] SLOTS = Arrays.stream(EquipmentSlot.values()).filter(EquipmentSlot::isArmor).toArray(EquipmentSlot[]::new);
    private static final Map<Player, Entry> entries = new IdentityHashMap<>();
    private static final class Entry {
        long frame = -1, touched, revision;
        Object handler;
        String source, digest;
        List<ItemStack> items = List.of();
        List<Object> dyes = List.of();
    }
    public static String cached(Player p) {
        if (top.wu949.dsbr.optimizer.OptimizationStage.VALUE < 2) return null;
        var e = entries.get(p); var h = TextureCache.handler(p);
        return e != null && e.frame == AppearanceCache.frame() && e.handler == h && e.revision == AppearanceCache.revision(h) ? e.digest : null;
    }
    public static String extend(String dsKey, Player p) {
        long start = System.nanoTime();
        var h = TextureCache.handler(p); var size = h.body().value().textureSize();
        var e = entries.computeIfAbsent(p, ignored -> new Entry());
        String source = dsKey + '|' + h.speciesId() + '|' + h.getModel() + '|' + size;
        var items = new ArrayList<ItemStack>(); var dyes = new ArrayList<Object>();
        for (var slot : SLOTS) { items.add(CosmeticArmorReworkedHelper.getItemVisibleInSlot(p, slot)); dyes.add(p.getItemBySlot(slot).get(DataComponents.DYED_COLOR)); }
        var curios = CurioAPIHelper.getVisibleCurioItems(p);
        if (curios != null) items.addAll(curios);
        boolean same = top.wu949.dsbr.optimizer.OptimizationStage.VALUE >= 2 && source.equals(e.source) && dyes.equals(e.dyes) && items.size() == e.items.size();
        if (same) for (int i = 0; i < items.size(); i++) if (!sameVisibleComponents(e.items.get(i), items.get(i))) { same = false; break; }
        if (!same) {
            var text = new StringBuilder(source);
            for (int i = 0; i < SLOTS.length; i++) text.append('|').append(SLOTS[i]).append('=').append(item(items.get(i), p)).append('|').append(dyes.get(i));
            for (int i = SLOTS.length; i < items.size(); i++) text.append("|curio=").append(item(items.get(i), p));
            try { e.digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8))); }
            catch (java.security.NoSuchAlgorithmException ex) { throw new AssertionError(ex); }
            e.source = source; e.items = items.stream().map(ArmorAppearance::normalized).toList(); e.dyes = dyes;
        }
        e.handler = h; e.frame = AppearanceCache.frame(); e.revision = AppearanceCache.revision(h); e.touched = System.nanoTime();
        Diagnostics.INSTANCE.nanos("ARMOR_CHECK", System.nanoTime() - start);
        return e.digest;
    }
    public static boolean sameVisibleComponents(ItemStack saved, ItemStack current) {
        if (saved.isEmpty() || current.isEmpty()) return saved.isEmpty() == current.isEmpty();
        if (saved.getItem() != current.getItem()) return false;
        int seen = 0, expected = 0;
        for (var component : current.getComponents()) {
            if (component.type() == DataComponents.DAMAGE || component.type() == DataComponents.REPAIR_COST) continue;
            if (!Objects.equals(component.value(), saved.get(component.type()))) return false;
            seen++;
        }
        for (var component : saved.getComponents()) if (component.type() != DataComponents.DAMAGE && component.type() != DataComponents.REPAIR_COST) expected++;
        return seen == expected;
    }
    private static ItemStack normalized(ItemStack original) {
        if (original.isEmpty()) return ItemStack.EMPTY;
        var copy = original.copyWithCount(1); copy.remove(DataComponents.DAMAGE); copy.remove(DataComponents.REPAIR_COST); return copy;
    }
    public static void maintenance(long cutoff) { entries.values().removeIf(e -> e.touched < cutoff); }
    public static void clear() { entries.clear(); }
    private static String item(ItemStack original, Player p) {
        if (original.isEmpty()) return "empty";
        var copy = normalized(original);
        return copy.save(p.registryAccess()).toString();
    }
}
