package top.wu949.dsbr.optimizer.soul;

import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;
import by.dragonsurvivalteam.dragonsurvival.server.tileentity.DragonSoulBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import top.wu949.dsbr.optimizer.texture.TextureCache;
import java.util.*;

/** Render-thread-only scope. Cache values never retain their weak block-entity keys. */
public final class SoulRenderContext {
    private static final Map<DragonSoulBlockEntity, AnimationChoice> choices = new WeakHashMap<>();
    private static final Map<Player, Boolean> players = new WeakHashMap<>();
    private static final Deque<AnimationChoice> scopes = new ArrayDeque<>();
    private static final class AnimationChoice {
        ResourceLocation resource;
        String name;
        boolean exists;
    }
    private SoulRenderContext() {}
    public static void enter(DragonSoulBlockEntity soul) {
        scopes.push(choices.computeIfAbsent(soul, ignored -> new AnimationChoice()));
    }
    public static void exit() { scopes.pop(); }
    public static boolean active() { return !scopes.isEmpty(); }
    public static void register(DragonEntity dragon) {
        if (dragon.getPlayer() != null) players.put(dragon.getPlayer(), Boolean.TRUE);
    }
    public static boolean sharedTexture(Player player) { return TextureCache.enabled() && players.containsKey(player); }
    public static Boolean cachedAnimation(ResourceLocation resource, String name) {
        var choice = scopes.peek();
        return choice != null && Objects.equals(resource, choice.resource) && Objects.equals(name, choice.name) ? choice.exists : null;
    }
    public static void animation(ResourceLocation resource, String name, boolean exists) {
        var choice = scopes.peek();
        if (choice != null) { choice.resource = resource; choice.name = name; choice.exists = exists; }
    }
    public static void clear() { choices.clear(); players.clear(); }
}
