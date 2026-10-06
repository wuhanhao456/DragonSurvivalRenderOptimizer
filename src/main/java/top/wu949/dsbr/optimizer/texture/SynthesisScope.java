package top.wu949.dsbr.optimizer.texture;

/** Only DS synthesis uses the copy interception; unrelated framebuffer readbacks are untouched. */
public final class SynthesisScope {
    private static final java.util.Deque<String> owners = new java.util.ArrayDeque<>();
    public static void enter(String owner) { owners.push(owner); }
    public static void exit() { owners.pop(); }
    public static boolean inDS() { return !owners.isEmpty(); }
    public static String owner() { return owners.isEmpty() ? "explicit" : owners.peek(); }
    public static boolean active() { return inDS() && TextureCache.enabled(); }
}
