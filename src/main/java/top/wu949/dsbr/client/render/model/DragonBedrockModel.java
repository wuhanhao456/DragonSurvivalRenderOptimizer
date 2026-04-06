package top.wu949.dsbr.client.render.model;

import top.wu949.dsbr.client.bedrock.AbstractBedrockEntityModel;
import top.wu949.dsbr.client.bedrock.model.BedrockPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class DragonBedrockModel extends AbstractBedrockEntityModel<Entity> {
    private final List<ResetEntry> resetEntries = new ArrayList<>();

    public DragonBedrockModel(final InputStream stream) {
        super(stream);
        captureBasePoses();
    }

    @Override
    public void setupAnim(final Entity entity, final float limbSwing, final float limbSwingAmount, final float ageInTicks, final float netHeadYaw, final float headPitch) {
        // DSBR 会在渲染前把动画结果直接写回骨骼，因此这里保持空实现。
    }

    public void resetPose() {
        for (ResetEntry resetEntry : resetEntries) {
            BedrockPart part = resetEntry.part();
            BasePose basePose = resetEntry.basePose();
            part.setPos(basePose.x(), basePose.y(), basePose.z());
            part.xRot = basePose.xRot();
            part.yRot = basePose.yRot();
            part.zRot = basePose.zRot();
            part.offsetX = 0.0F;
            part.offsetY = 0.0F;
            part.offsetZ = 0.0F;
            part.xScale = 1.0F;
            part.yScale = 1.0F;
            part.zScale = 1.0F;
            if (part.additionalQuaternion == null) {
                part.additionalQuaternion = new Quaternionf();
            } else {
                part.additionalQuaternion.identity();
            }
            part.visible = true;
        }
    }

    public void hideBones(final List<String> bones) {
        for (String bone : bones) {
            BedrockPart part = modelMap.get(bone);
            if (part != null) {
                part.visible = false;
            }
        }
    }

    public void addRotationDegrees(final String boneName, final Vector3f rotation) {
        BedrockPart part = modelMap.get(boneName);
        if (part == null) {
            return;
        }

        part.xRot += (float) Math.toRadians(rotation.x);
        part.yRot += (float) Math.toRadians(rotation.y);
        part.zRot += (float) Math.toRadians(rotation.z);
    }

    public void addPosition(final String boneName, final Vector3f position) {
        BedrockPart part = modelMap.get(boneName);
        if (part == null) {
            return;
        }

        part.x += position.x;
        // Bedrock animation positions use an upward-positive Y axis, while the
        // Java model pivot space we write into here is downward-positive.
        part.y -= position.y;
        part.z += position.z;
    }

    public void applyScale(final String boneName, final Vector3f scale) {
        BedrockPart part = modelMap.get(boneName);
        if (part == null) {
            return;
        }
        // DS 的动画里会用 0 缩放来隐藏翅膀/法术特效骨骼，这里必须原样保留。
        part.xScale = scale.x;
        part.yScale = scale.y;
        part.zScale = scale.z;
    }

    public void renderWithColor(final PoseStack poseStack, final VertexConsumer buffer, final int packedLight, final int packedOverlay, final float red, final float green, final float blue, final float alpha) {
        for (BedrockPart model : shouldRender) {
            model.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }

    public boolean applyBoneTransform(final String boneName, final PoseStack poseStack) {
        BedrockPart part = modelMap.get(boneName);
        if (part == null) {
            return false;
        }

        applyPartTransform(part, poseStack);
        return true;
    }

    private void applyPartTransform(final BedrockPart part, final PoseStack poseStack) {
        if (part.getParent() != null) {
            applyPartTransform(part.getParent(), poseStack);
        }

        part.translateAndRotateAndScale(poseStack);
    }

    private void captureBasePoses() {
        for (BedrockPart part : modelMap.values()) {
            resetEntries.add(new ResetEntry(part, new BasePose(part.x, part.y, part.z, part.getInitRotX(), part.getInitRotY(), part.getInitRotZ())));
        }
    }

    private record BasePose(float x, float y, float z, float xRot, float yRot, float zRot) {
    }

    private record ResetEntry(BedrockPart part, BasePose basePose) {
    }
}
