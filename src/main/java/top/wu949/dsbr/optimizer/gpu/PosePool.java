package top.wu949.dsbr.optimizer.gpu;
import java.util.*;

/** Render-thread-only. Idle bytes are budgeted; leased snapshots cannot be acquired again. */
public final class PosePool {
    private record Idle(PoseSnapshot pose, long since) {}
    private final Map<Integer, ArrayDeque<Idle>> idle = new HashMap<>();
    private final Set<PoseSnapshot> leased = Collections.newSetFromMap(new IdentityHashMap<>());
    private long idleBytes;
    public long idleBytes() { return idleBytes; }
    public PoseSnapshot acquire(int bones) {
        var q = idle.get(bones); PoseSnapshot pose;
        if (q == null || q.isEmpty()) pose = new PoseSnapshot(bones);
        else { pose = q.removeFirst().pose; idleBytes -= pose.retainedBytes(); }
        pose.reset(); if (!leased.add(pose)) throw new IllegalStateException("pose leased twice"); return pose;
    }
    public void release(PoseSnapshot pose) {
        if (!leased.remove(pose)) throw new IllegalStateException("pose released twice");
        idle.computeIfAbsent(pose.visible.length, ignored -> new ArrayDeque<>()).addLast(new Idle(pose, System.nanoTime())); idleBytes += pose.retainedBytes();
    }
    public void prune(long now, long budget) {
        for (var q : idle.values()) {
            while (!q.isEmpty() && (idleBytes > Math.max(0, budget) || now - q.peekFirst().since > 30_000_000_000L)) {
                var p = q.removeFirst().pose; idleBytes -= p.retainedBytes(); p.close();
            }
        }
        idle.values().removeIf(ArrayDeque::isEmpty);
    }
    public void clear() {
        if (!leased.isEmpty()) throw new IllegalStateException("clear with leased poses");
        for (var q : idle.values()) for (var p : q) p.pose.close(); idle.clear(); idleBytes = 0;
    }
}
