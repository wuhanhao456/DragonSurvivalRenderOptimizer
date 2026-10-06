package top.wu949.dsbr.optimizer.compat;

import com.mojang.blaze3d.vertex.*;
import top.wu949.dsbr.optimizer.mixin.BufferFormatAccessor;
import java.lang.reflect.*;

/** Optional Iris reflection is initialized only when metadata and batch contracts match. */
public final class IrisBridge {
    public record Attributes(VertexFormat format, int entity, int blockEntity, int item, boolean recalculateNormal, boolean shadow) {}
    private static Method entity, block, item, shadow;
    private static Field level;
    private static Object state, api;
    private static boolean initialized;
    public static Attributes capture(VertexConsumer consumer) throws ReflectiveOperationException {
        if (!OptimizerMixinPlugin.irisCompatible) return new Attributes(DefaultVertexFormat.NEW_ENTITY, 0, 0, 0, false, false);
        initialize();
        var f = ((BufferFormatAccessor)consumer).beloong$format();
        if (f.equals(DefaultVertexFormat.NEW_ENTITY)) return new Attributes(f, 0, 0, 0, false, (boolean)shadow.invoke(api));
        // Exact audited entity layout. A mod adding/removing fields triggers the CPU path.
        if (f.getVertexSize() != 54 || !f.getElementAttributeNames().equals(java.util.List.of("Position", "Color", "UV0", "UV1", "UV2", "Normal", "iris_Entity", "mc_midTexCoord", "at_tangent")))
            throw new IllegalStateException("unrecognized Iris entity vertex layout: " + f);
        int[] offsets = {0, 12, 16, 24, 28, 32, 36, 42, 50};
        for (int i = 0; i < offsets.length; i++) if (f.getOffset(f.getElements().get(i)) != offsets[i]) throw new IllegalStateException("Iris vertex offsets changed");
        return new Attributes(f, (int)entity.invoke(state), (int)block.invoke(state), (int)item.invoke(state), level.getBoolean(null), (boolean)shadow.invoke(api));
    }
    private static void initialize() throws ReflectiveOperationException {
        if (initialized) return;
        var capture = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState"); state = capture.getField("INSTANCE").get(null);
        entity = capture.getMethod("getCurrentRenderedEntity"); block = capture.getMethod("getCurrentRenderedBlockEntity"); item = capture.getMethod("getCurrentRenderedItem");
        level = Class.forName("net.irisshaders.iris.vertices.ImmediateState").getField("isRenderingLevel");
        var apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi"); api = apiClass.getMethod("getInstance").invoke(null); shadow = apiClass.getMethod("isRenderingShadowPass");
        initialized = true;
    }
}
