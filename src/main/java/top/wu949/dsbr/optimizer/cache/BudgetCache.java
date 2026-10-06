package top.wu949.dsbr.optimizer.cache;

import java.util.*;
import java.util.function.Consumer;

/** Render-thread LRU. Current-frame leases are retained until their batch is drawn. */
public final class BudgetCache<K, V> {
    public static final class Entry<V> {
        public final V value;
        public final long bytes;
        public long touched;
        public int leases;
        Entry(V value, long bytes, long now) { this.value = value; this.bytes = bytes; touched = now; }
    }
    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(16, .75f, true);
    private final Consumer<V> disposer;
    private long bytes;
    public BudgetCache(Consumer<V> disposer) { this.disposer = disposer; }
    public Entry<V> get(K key, long now) { var e = entries.get(key); if (e != null) e.touched = now; return e; }
    public void put(K key, V value, long size, long now) {
        var old = entries.get(key);
        if (old != null && old.leases != 0) throw new IllegalStateException("replace leased resource");
        remove(key); entries.put(key, new Entry<>(value, size, now)); bytes += size;
    }
    public void remove(K key) { var e = entries.remove(key); if (e != null) { bytes -= e.bytes; disposer.accept(e.value); } }
    public void prune(long now, long retention, long budget) {
        var i = entries.entrySet().iterator();
        while (i.hasNext()) {
            var e = i.next().getValue();
            // Never invalidate resources still referenced by this frame (including ordinary DS layers).
            if (e.leases > 0 || e.touched == now) continue;
            if (bytes <= budget && now - e.touched <= retention) continue;
            i.remove(); bytes -= e.bytes; disposer.accept(e.value);
        }
    }
    public void clear() { entries.values().forEach(e -> disposer.accept(e.value)); entries.clear(); bytes = 0; }
    public long bytes() { return bytes; }
    public int size() { return entries.size(); }
}
