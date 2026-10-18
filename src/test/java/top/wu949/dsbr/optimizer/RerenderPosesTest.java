package top.wu949.dsbr.optimizer;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.cache.object.GeoBone;
import top.wu949.dsbr.optimizer.gpu.*;
import static org.junit.jupiter.api.Assertions.*;

class RerenderPosesTest {
    private final Object renderer = new Object(), dragon = new Object(), other = new Object(), model = new Object();

    private static StaticMesh mesh() {
        var mesh = new StaticMesh(); var body = new GeoBone(null, "body", false, 0d, false, false); var wing = new GeoBone(body, "wing", false, 0d, false, false);
        for (var bone : new GeoBone[]{body, wing}) { mesh.bones.put(bone, mesh.order.size()); mesh.order.add(bone); }
        return mesh;
    }
    private static PoseSnapshot capture(PoseStack root) {
        var pose = new PoseSnapshot(2); root.pushPose(); root.translate(1, 2, 3);
        pose.bone(0, root.last(), 0xffffffff, 0xf000f0, 0); root.popPose();
        return pose;
    }
    /** One base pass as GpuDispatcher drives it: touch, prepare before the traversal, commit after it. */
    private boolean base(RerenderPoses cache, Object animatable, StaticMesh mesh, PoseStack root, PoseSnapshot pose) {
        cache.touch(model, animatable);
        var t = cache.prepare(model, animatable, mesh);
        return cache.commit(t, renderer, root.last().pose(), root.last().normal(), pose);
    }
    private RerenderPoses.Miss rerender(RerenderPoses cache, StaticMesh mesh, PoseStack root, PoseSnapshot target) {
        cache.touch(model, dragon);
        return cache.reuse(renderer, dragon, model, mesh, root.last().pose(), root.last().normal(), target, 0x80ff0000, 0xf00000, 10);
    }

    @Test void identicalReRenderCopiesBonesAndAppliesItsOwnColourLightOverlay() {
        var mesh = mesh(); var root = new PoseStack(); root.translate(4, 5, 6);
        try (var cache = new RerenderPoses(); var pose = capture(root); var target = new PoseSnapshot(2)) {
            assertTrue(base(cache, dragon, mesh, root, pose));
            assertNull(rerender(cache, mesh, root, target));
            assertEquals(pose.position(0, new Matrix4f()), target.position(0, new Matrix4f()));
            assertTrue(target.visible[0]); assertFalse(target.visible[1]); assertTrue(target.anyVisible());
            assertEquals(0x80ff0000, target.data.getInt(128)); assertEquals(0xf00000, target.data.getInt(132)); assertEquals(10, target.data.getInt(136));
            assertEquals(1, target.data.getInt(140)); assertEquals(0, target.data.getInt(PoseSnapshot.STRIDE + 140));
            assertEquals(0x80ff0000, target.colours[0]); assertEquals(0xffffffff, pose.colours[0]);
        }
    }

    @Test void anyChangedInputTraversesAgain() {
        var mesh = mesh(); var root = new PoseStack();
        try (var cache = new RerenderPoses(); var pose = capture(root); var target = new PoseSnapshot(2)) {
            assertTrue(base(cache, dragon, mesh, root, pose));
            assertEquals(RerenderPoses.Miss.NO_TEMPLATE, cache.reuse(renderer, new Object(), model, mesh, root.last().pose(), root.last().normal(), target, 0, 0, 0));
            assertEquals(RerenderPoses.Miss.RENDERER, cache.reuse(new Object(), dragon, model, mesh, root.last().pose(), root.last().normal(), target, 0, 0, 0));
            assertEquals(RerenderPoses.Miss.RENDERER, cache.reuse(renderer, dragon, new Object(), mesh, root.last().pose(), root.last().normal(), target, 0, 0, 0));
            var moved = new Matrix4f(root.last().pose()).translate(0, 0, 1e-6f);
            assertEquals(RerenderPoses.Miss.ROOT, cache.reuse(renderer, dragon, model, mesh, moved, root.last().normal(), target, 0, 0, 0));
            mesh.order.get(1).setRotX(0.1f);
            assertEquals(RerenderPoses.Miss.BONES, rerender(cache, mesh, root, target));
            mesh.order.get(1).setRotX(0); mesh.order.get(1).setHidden(true);
            assertEquals(RerenderPoses.Miss.BONES, rerender(cache, mesh, root, target));
            mesh.order.get(1).setHidden(false); mesh.order.get(0).setChildrenHidden(true);
            assertEquals(RerenderPoses.Miss.BONES, rerender(cache, mesh, root, target));
            mesh.order.get(0).setChildrenHidden(false);
            assertNull(rerender(cache, mesh, root, target));
        }
    }

    @Test void anotherDragonRenderingTheSharedModelDropsTheTemplate() {
        var mesh = mesh(); var root = new PoseStack();
        try (var cache = new RerenderPoses(); var pose = capture(root); var target = new PoseSnapshot(2)) {
            // A -> B -> A: B's render may rewrite the shared bones' tracked matrices, so A must traverse again.
            assertTrue(base(cache, dragon, mesh, root, pose));
            cache.touch(model, other);
            assertEquals(RerenderPoses.Miss.NO_TEMPLATE, rerender(cache, mesh, root, target));
            // A different model does not affect the template.
            assertTrue(base(cache, dragon, mesh, root, pose));
            cache.touch(new Object(), other);
            assertNull(rerender(cache, mesh, root, target));
        }
    }

    @Test void nestedRenderOrBoneChangeDuringTheBasePassIsNotRecorded() {
        var mesh = mesh(); var root = new PoseStack();
        try (var cache = new RerenderPoses(); var pose = capture(root); var target = new PoseSnapshot(2)) {
            cache.touch(model, dragon);
            var nested = cache.prepare(model, dragon, mesh);
            cache.touch(model, other); cache.touch(model, dragon);   // a per-bone layer rendered another dragon
            assertFalse(cache.commit(nested, renderer, root.last().pose(), root.last().normal(), pose));
            var changed = cache.prepare(model, dragon, mesh);
            mesh.order.get(0).setPosY(3);                                // bones moved after the traversal read them
            assertFalse(cache.commit(changed, renderer, root.last().pose(), root.last().normal(), pose));
            assertEquals(0, cache.size());
            assertEquals(RerenderPoses.Miss.NO_TEMPLATE, rerender(cache, mesh, root, target));
        }
    }

    @Test void templatesLastOneWorldRenderAndAreBudgeted() {
        var mesh = mesh(); var root = new PoseStack();
        try (var cache = new RerenderPoses(); var pose = capture(root); var target = new PoseSnapshot(2)) {
            assertTrue(base(cache, dragon, mesh, root, pose));
            assertEquals(1, cache.size()); long bytes = cache.bytes(); assertEquals(RerenderPoses.estimate(2), bytes);
            cache.clear();
            assertEquals(0, cache.size()); assertEquals(bytes, cache.bytes(), "idle template stays allocated and counted");
            assertEquals(RerenderPoses.Miss.NO_TEMPLATE, rerender(cache, mesh, root, target));
            assertTrue(base(cache, dragon, mesh, root, pose));
            assertEquals(bytes, cache.bytes(), "idle template reused");
            var abandoned = cache.prepare(model, dragon, mesh); cache.discard(abandoned);
            assertEquals(1, cache.size());
            cache.close(); assertEquals(0, cache.bytes());
            assertFalse(cache.commit(abandoned, renderer, root.last().pose(), root.last().normal(), pose), "closed templates are never committed");
        }
    }

    @Test void copyRejectsADifferentMesh() {
        try (var a = new PoseSnapshot(2); var b = new PoseSnapshot(3)) {
            assertThrows(IllegalArgumentException.class, () -> b.copyFrom(a.data, a.visible, 0, 0, 0));
        }
    }
}
