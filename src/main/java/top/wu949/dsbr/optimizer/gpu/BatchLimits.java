package top.wu949.dsbr.optimizer.gpu;

/** Limits expanded geometry and packed poses before cancelling the original submission. */
public final class BatchLimits {
    private BatchLimits() {}
    public static int instances(int quads, int bones, int stride, long budget, long ssboLimit, long workGroups) {
        if (quads <= 0 || bones <= 0 || stride <= 0 || budget <= 0 || ssboLimit <= 0 || workGroups <= 0) return 0;
        long output = (long)quads * 4 * stride, pose = (long)bones * 144;
        long limit = Math.min(budget / (output + pose), Math.min(ssboLimit / output, ssboLimit / pose));
        limit = Math.min(limit, Math.min(Integer.MAX_VALUE / ((long)quads * 6), workGroups * 64 / quads));
        return (int)Math.min(limit, Integer.MAX_VALUE);
    }
}
