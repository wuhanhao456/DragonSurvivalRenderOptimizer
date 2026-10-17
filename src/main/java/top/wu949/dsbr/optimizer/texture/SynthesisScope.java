package top.wu949.dsbr.optimizer.texture;

import java.util.ArrayDeque;
import java.util.Deque;

/** Only DS synthesis uses the copy interception; unrelated framebuffer readbacks are untouched. */
public final class SynthesisScope {
    private record Owner(String name, boolean intercept) {}

    private static final Deque<Owner> owners = new ArrayDeque<>();

    /** {@code intercept} is false for players that fell back to the original DS path. */
    public static void enter(String owner, boolean intercept) { owners.push(new Owner(owner, intercept)); }

    public static void exit() { owners.pop(); }

    public static boolean inDS() { return !owners.isEmpty(); }

    public static String owner() { return owners.isEmpty() ? "explicit" : owners.peek().name(); }

    public static boolean active() { return inDS() && owners.peek().intercept() && TextureCache.enabled(); }
}
