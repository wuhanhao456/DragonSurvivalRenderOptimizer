package top.wu949.dsbr.client.render;

public final class InventoryEntityRenderContext {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private InventoryEntityRenderContext() {
    }

    public static void push() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void pop() {
        int depth = DEPTH.get();
        if (depth <= 1) {
            DEPTH.remove();
            return;
        }

        DEPTH.set(depth - 1);
    }

    public static boolean isRenderingInventoryEntity() {
        return DEPTH.get() > 0;
    }
}
