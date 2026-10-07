package top.wu949.dsbr.optimizer;

import org.junit.jupiter.api.Test;
import software.bernie.geckolib.cache.object.GeoBone;
import top.wu949.dsbr.optimizer.animation.LocalBonePose;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LocalBonePoseTest {
    @Test void capturesBeforeCustomAnimationAndRestoresFlagsWithoutWorldMatrices() {
        var bone = new GeoBone(null, "wing", false, 0d, false, false);
        bone.updateRotation(.2f, .4f, .6f); bone.updatePosition(1,2,3); bone.updateScale(1,2,1);
        bone.resetStateChanges(); bone.markPositionAsChanged(); bone.setHidden(true); bone.setChildrenHidden(true);
        bone.getWorldSpaceMatrix().translation(12,13,14);
        var a = new LocalBonePose(List.of(bone)); a.capture();
        bone.updateRotation(1,2,3); bone.updateScale(3,4,5); bone.setHidden(false); bone.setChildrenHidden(false);
        var b = new LocalBonePose(List.of(bone)); b.capture();
        bone.getWorldSpaceMatrix().translation(20,21,22);
        a.restore(); assertEquals(.2f, bone.getRotX()); assertEquals(2, bone.getScaleY());
        assertTrue(bone.isHidden()); assertTrue(bone.isHidingChildren()); assertTrue(bone.hasPositionChanged());
        assertFalse(bone.hasRotationChanged()); assertFalse(bone.hasScaleChanged());
        assertEquals(20, bone.getWorldSpaceMatrix().m30());
        b.restore(); assertEquals(1, bone.getRotX()); assertFalse(bone.isHidden());
        assertFalse(a.matches(List.of(new GeoBone(null, "wing", false, 0d, false, false))));
    }
}
