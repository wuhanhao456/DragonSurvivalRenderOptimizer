package top.wu949.dsbr.optimizer;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.cache.object.GeoBone;
import top.wu949.dsbr.optimizer.lowend.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class LowEndAnimationTest {
    @Test void holdsUntilFourGameTicksAndFarPosesDoNotAdvance() {
        assertTrue(AnimationCadence.due(10,0,false,false));
        for(int tick=10;tick<14;tick++)assertFalse(AnimationCadence.due(tick,10,true,true));
        assertTrue(AnimationCadence.due(14,10,true,true));
        assertFalse(AnimationCadence.due(1000,10,false,true));
        assertTrue(AnimationCadence.due(1000,10,true,true));
        assertTrue(AnimationCadence.due(0,10,false,true));
    }
    @Test void sharedBonesRestoreIndependentPosesFlagsAndPivots() {
        var bone=new GeoBone(null,"root",false,0d,false,false);var bones=List.of(bone);
        var a=new LocalBonePose(bones);var b=new LocalBonePose(bones);
        bone.updateRotation(1,2,3);bone.updatePosition(4,5,6);bone.updateScale(.5f,1,2);bone.updatePivot(7,8,9);bone.setHidden(true);bone.setChildrenHidden(true);a.capture();
        bone.updateRotation(-1,-2,-3);bone.updatePosition(-4,-5,-6);bone.updateScale(2,3,4);bone.updatePivot(-7,-8,-9);bone.setHidden(false);bone.setChildrenHidden(false);b.capture();
        a.restore();assertEquals(1,bone.getRotX());assertEquals(4,bone.getPosX());assertEquals(.5f,bone.getScaleX());assertEquals(7,bone.getPivotX());assertTrue(bone.isHidden());assertTrue(bone.isHidingChildren());
        b.restore();assertEquals(-1,bone.getRotX());assertEquals(-4,bone.getPosX());assertEquals(2,bone.getScaleX());assertEquals(-7,bone.getPivotX());assertFalse(bone.isHidden());assertFalse(bone.isHidingChildren());
        assertTrue(a.matches(bones));assertFalse(a.matches(List.of(new GeoBone(null,"root",false,0d,false,false))));
    }
    @Test void rendererNeckVisibilityOverridesSavedPose() {
        var neck=new GeoBone(null,"Neck",false,0d,false,false);
        var part=new GeoBone(null,"part",false,0d,false,false);
        var pose=new LocalBonePose(List.of(neck,part));
        neck.updateRotation(1,2,3);neck.setHidden(true);part.setHidden(true);pose.capture();
        neck.updateRotation(0,0,0);neck.setHidden(false);part.setHidden(false);
        pose.restoreKeepingVisibility("Neck");
        assertEquals(1,neck.getRotX());assertFalse(neck.isHidden());assertFalse(neck.isHidingChildren());assertTrue(part.isHidden());
        neck.setHidden(true);pose.restoreKeepingVisibility("Neck");assertTrue(neck.isHidden());assertTrue(neck.isHidingChildren());
    }
}
