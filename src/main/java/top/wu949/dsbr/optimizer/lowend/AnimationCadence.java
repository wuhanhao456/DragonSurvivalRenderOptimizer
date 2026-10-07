package top.wu949.dsbr.optimizer.lowend;
/** Game ticks, not rendered frames. First/invalidated poses always get one evaluation. */
public final class AnimationCadence {
    public static boolean due(long tick, long previous, boolean near, boolean hasPose) {
        return !hasPose || tick < previous || near && tick - previous >= 4;
    }
    private AnimationCadence() {}
}
