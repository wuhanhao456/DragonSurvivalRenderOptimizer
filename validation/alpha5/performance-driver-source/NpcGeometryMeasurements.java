package top.wu949.dsbr.optimizer.validation;

/** One timer per NPC pass, applied identically to both jars; no per-vertex instrumentation. */
public final class NpcGeometryMeasurements {
    public static long calls, nanos;
    private static final ClassValue<Boolean> npc = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            return type.getName().equals("com.zonlong.beloong.client.MoRenderer") || type.getName().equals("com.zonlong.beloong.client.DihuangLoongRenderer");
        }
    };
    public static long begin(Object renderer) { return npc.get(renderer.getClass()) ? System.nanoTime() : 0; }
    public static void end(long start) { if (start != 0) { calls++; nanos += System.nanoTime() - start; } }
    public static void reset() { calls=nanos=0; }
}
