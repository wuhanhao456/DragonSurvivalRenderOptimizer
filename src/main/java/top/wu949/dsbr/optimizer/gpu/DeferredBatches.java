package top.wu949.dsbr.optimizer.gpu;

import java.util.*;
import java.util.function.Consumer;

/** Buffer owners are distinct; equivalent wrapped render states share one ordered batch. */
public final class DeferredBatches<S, K, V> {
    private final Map<S, Map<K, List<V>>> sources = new IdentityHashMap<>();
    public void add(S source, K type, V value) {
        sources.computeIfAbsent(source, ignored -> new HashMap<>()).computeIfAbsent(type, ignored -> new ArrayList<>()).add(value);
    }
    public boolean has(S source, K type) { var types = sources.get(source); return types != null && types.containsKey(type); }
    public List<V> take(S source, K type) {
        var types = sources.get(source); if (types == null) return null;
        var values = types.remove(type); if (types.isEmpty()) sources.remove(source); return values;
    }
    public void forEach(Consumer<V> consumer) { sources.values().forEach(types -> types.values().forEach(values -> values.forEach(consumer))); }
    public boolean isEmpty() { return sources.isEmpty(); }
    public void clear() { sources.clear(); }
}
