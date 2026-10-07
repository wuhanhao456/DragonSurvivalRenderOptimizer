package top.wu949.dsbr.optimizer.lowend;

import software.bernie.geckolib.cache.object.GeoBone;
import java.util.Collection;

/** Local animation baseline only; locator/world/render matrices are always recomputed. */
public final class LocalBonePose {
    private final GeoBone[] bones;
    private final float[] values;
    private final byte[] flags;
    public LocalBonePose(Collection<GeoBone> bones) {
        this.bones = bones.toArray(GeoBone[]::new); values = new float[this.bones.length * 12]; flags = new byte[this.bones.length];
    }
    public boolean matches(Collection<GeoBone> current) {
        if (current.size() != bones.length) return false;
        int i = 0; for (var bone : current) if (bone != bones[i++]) return false;
        return true;
    }
    public void capture() {
        for (int i = 0; i < bones.length; i++) {
            var b = bones[i]; int p = i * 12;
            values[p] = b.getRotX(); values[p+1] = b.getRotY(); values[p+2] = b.getRotZ();
            values[p+3] = b.getPosX(); values[p+4] = b.getPosY(); values[p+5] = b.getPosZ();
            values[p+6] = b.getScaleX(); values[p+7] = b.getScaleY(); values[p+8] = b.getScaleZ();
            values[p+9] = b.getPivotX(); values[p+10] = b.getPivotY(); values[p+11] = b.getPivotZ();
            flags[i] = (byte)((b.isHidden()?1:0) | (b.isHidingChildren()?2:0) | (b.hasPositionChanged()?4:0) | (b.hasRotationChanged()?8:0) | (b.hasScaleChanged()?16:0));
        }
    }
    public void restore() {
        for (int i = 0; i < bones.length; i++) {
            var b = bones[i]; int p = i * 12, f = flags[i];
            b.updateRotation(values[p], values[p+1], values[p+2]); b.updatePosition(values[p+3], values[p+4], values[p+5]);
            b.updateScale(values[p+6], values[p+7], values[p+8]); b.updatePivot(values[p+9], values[p+10], values[p+11]);
            b.setHidden((f&1)!=0); b.setChildrenHidden((f&2)!=0); b.resetStateChanges();
            if ((f&4)!=0) b.markPositionAsChanged(); if ((f&8)!=0) b.markRotationAsChanged(); if ((f&16)!=0) b.markScaleAsChanged();
        }
    }
    /** DS preRender owns temporary neck hiding; cached animation visibility must not override it. */
    public void restoreKeepingVisibility(String name) {
        GeoBone kept=null;for(var bone:bones)if(bone.getName().equals(name)){kept=bone;break;}
        boolean hidden=kept!=null&&kept.isHidden(),children=kept!=null&&kept.isHidingChildren();
        restore();if(kept!=null){kept.setHidden(hidden);kept.setChildrenHidden(children);}
    }
    public long bytes() { return 128L + bones.length * 64L; }
}
