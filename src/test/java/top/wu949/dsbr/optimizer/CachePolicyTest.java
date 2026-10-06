package top.wu949.dsbr.optimizer;

import top.wu949.dsbr.optimizer.cache.BudgetCache;
import top.wu949.dsbr.optimizer.texture.AppearanceKey;
import top.wu949.dsbr.optimizer.gpu.PoseSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CachePolicyTest {
    @Test void lruRespectsPendingBatchLeasesAndReleasesExactlyOnce() {
        var disposed = new ArrayList<String>(); var cache = new BudgetCache<String, String>(disposed::add);
        cache.put("a", "A", 40, 1); cache.put("b", "B", 40, 2); cache.put("c", "C", 40, 3);
        var a = cache.get("a", 4); a.leases++;
        cache.prune(5, 30, 50);
        assertEquals(List.of("B", "C"), disposed); assertEquals(40, cache.bytes()); assertNotNull(cache.get("a", 6));
        a.leases--; cache.prune(100, 30, 50); assertEquals(List.of("B", "C", "A"), disposed); assertEquals(0, cache.bytes());
        cache.clear(); assertEquals(3, disposed.size());
    }
    @Test void retentionPreservesUnseenTexturesAndExpiresAfterThirtySeconds() {
        var cache = new BudgetCache<String, String>(x -> {});
        cache.put("skin", "texture", 16, 1_000_000_000L);
        cache.prune(30_000_000_000L, 30_000_000_000L, 64); assertEquals(1, cache.size());
        cache.prune(32_000_000_000L, 30_000_000_000L, 64); assertEquals(0, cache.size());
    }
    private static AppearanceKey key(String model, int hue, boolean glow) {
        return new AppearanceKey("ds:cave", "ds:girl", model, "ds:adult", 128, 128, true, false, false, true,
                List.of(new AppearanceKey.Layer("base", "part", hue, 0, 0, true, glow)));
    }
    @Test void ContentKeysSurviveEquivalentSyncAndChangeForAppearance() {
        var original = key("ds:girl", 1, false); var synchronizedCopy = key("ds:girl", 1, false);
        assertEquals(original, synchronizedCopy); assertEquals(original.digest(), synchronizedCopy.digest());
        assertNotEquals(original.digest(), key("ds:other", 1, false).digest());
        assertNotEquals(original.digest(), key("ds:girl", 2, false).digest());
        assertNotEquals(original.digest(), key("ds:girl", 1, true).digest());
    }
    @Test void PoseSnapshotsDoNotChangeWhenNextPlayerMutatesSharedStack() {
        var stack = new PoseStack(); stack.translate(1, 2, 3);
        try (var snapshot = new PoseSnapshot(2)) {
            snapshot.bone(0, stack.last(), 0xff112233, 0xf000f0, 0);
            stack.translate(100, 200, 300);
            assertEquals(1, snapshot.positions[0].m30()); assertEquals(2, snapshot.positions[0].m31());
            assertEquals(3, snapshot.positions[0].m32()); assertNull(snapshot.positions[1]);
            assertEquals(0, snapshot.data.getInt(144 + 140));
        }
    }
}
