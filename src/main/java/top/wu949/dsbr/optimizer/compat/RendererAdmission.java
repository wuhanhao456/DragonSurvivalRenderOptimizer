package top.wu949.dsbr.optimizer.compat;

import by.dragonsurvivalteam.dragonsurvival.client.render.entity.dragon.DragonRenderer;
import com.mojang.blaze3d.vertex.*;
import org.joml.*;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.renderer.GeoRenderer;
import top.wu949.dsbr.optimizer.OptimizerConfig;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import top.wu949.dsbr.optimizer.soul.SoulRenderContext;
import java.util.*;

/** One admission policy shared by capture and CPU fallback hooks. */
public final class RendererAdmission {
    public enum Kind { OTHER, PLAYER, SOUL, NPC }
    private static final ClassValue<Boolean> geometry = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("renderCube", PoseStack.class, GeoCube.class, VertexConsumer.class, int.class, int.class, int.class).getDeclaringClass() == GeoRenderer.class
                    && type.getMethod("createVerticesOfQuad", GeoQuad.class, Matrix4f.class, Vector3f.class, VertexConsumer.class, int.class, int.class, int.class).getDeclaringClass() == GeoRenderer.class;
            } catch (ReflectiveOperationException e) { return false; }
        }
    };
    private static final ClassValue<Boolean> npc = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) { return OptionalNpcAdapter.renderer(type); }
    };
    private static final Set<Class<?>> quarantined = Collections.newSetFromMap(new IdentityHashMap<>());
    public static Kind kind(Object renderer) {
        if (renderer instanceof DragonRenderer) return SoulRenderContext.active() ? Kind.SOUL : Kind.PLAYER;
        return npc.get(renderer.getClass()) ? Kind.NPC : Kind.OTHER;
    }
    public static boolean originalGeometry(Object renderer) { return geometry.get(renderer.getClass()); }
    public static boolean gpu(Object renderer, Kind kind) {
        return kind != Kind.OTHER && (kind != Kind.NPC || OptimizerConfig.NPC_GPU.get() && !quarantined.contains(renderer.getClass()));
    }
    public static void quarantine(Object renderer, Throwable error) {
        quarantined.add(renderer.getClass());
        Diagnostics.INSTANCE.reason("NPC/" + renderer.getClass().getSimpleName(), error.getClass().getSimpleName());
    }
    private RendererAdmission() {}
}
