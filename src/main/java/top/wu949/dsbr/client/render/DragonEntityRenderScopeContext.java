package top.wu949.dsbr.client.render;

public final class DragonEntityRenderScopeContext {
    private static final ThreadLocal<Integer> DRAGON_SOUL_DEPTH = ThreadLocal.withInitial(() -> 0);

    private DragonEntityRenderScopeContext() {
    }

    public static void pushDragonSoul() {
        DRAGON_SOUL_DEPTH.set(DRAGON_SOUL_DEPTH.get() + 1);
    }

    public static void popDragonSoul() {
        int depth = DRAGON_SOUL_DEPTH.get();
        if (depth <= 1) {
            DRAGON_SOUL_DEPTH.remove();
            return;
        }

        DRAGON_SOUL_DEPTH.set(depth - 1);
    }

    public static boolean isRenderingDragonSoul() {
        return DRAGON_SOUL_DEPTH.get() > 0;
    }
}
