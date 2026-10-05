package com.xiaofeiwu.thief.client;

import com.xiaofeiwu.thief.ThiefEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** A player-shaped model that shows the hands tied: in front when the thief is led, stretched up over the head when it hangs. */
public class ThiefModel extends HumanoidModel<ThiefEntity> {

    public ThiefModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(ThiefEntity thief, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(thief, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (thief.isPosted()) {
            // stood against the post: legs together, arms pulled back and in against the body, head down
            rightArm.xRot = 0.3F;
            leftArm.xRot = 0.3F;
            rightArm.yRot = 0.0F;
            leftArm.yRot = 0.0F;
            rightArm.zRot = -0.1F;
            leftArm.zRot = 0.1F;
            rightLeg.xRot = 0.0F;
            leftLeg.xRot = 0.0F;
            rightLeg.yRot = 0.0F;
            leftLeg.yRot = 0.0F;
            rightLeg.zRot = 0.0F;
            leftLeg.zRot = 0.0F;
            head.xRot = 0.35F;
        } else if (thief.isRacked()) {
            // spread out on the frame: arms to the upper left and right, legs to the lower left and right, head up
            rightArm.xRot = 0.0F;
            leftArm.xRot = 0.0F;
            rightArm.yRot = 0.0F;
            leftArm.yRot = 0.0F;
            rightArm.zRot = 2.36F;
            leftArm.zRot = -2.36F;
            rightLeg.xRot = 0.0F;
            leftLeg.xRot = 0.0F;
            rightLeg.yRot = 0.0F;
            leftLeg.yRot = 0.0F;
            rightLeg.zRot = 0.8F;
            leftLeg.zRot = -0.8F;
            head.xRot = 0.1F;
        } else if (thief.isHung()) {
            // both arms straight up, bound together at the wrists; the body hangs from them, the head hangs forward, the legs swing a little
            rightArm.xRot = -(float) Math.PI;
            leftArm.xRot = -(float) Math.PI;
            rightArm.yRot = 0.0F;
            leftArm.yRot = 0.0F;
            rightArm.zRot = 0.0F;
            leftArm.zRot = 0.0F;
            head.xRot = 0.45F;
            rightLeg.xRot = 0.08F + (float) Math.sin(ageInTicks * 0.05F) * 0.05F;
            leftLeg.xRot = -0.04F - (float) Math.sin(ageInTicks * 0.05F) * 0.05F;
            rightLeg.yRot = 0.0F;
            leftLeg.yRot = 0.0F;
        } else if (thief.isRopeTied()) {
            // hands together in front, held a little forward and down
            rightArm.xRot = -1.0F;
            leftArm.xRot = -1.0F;
            rightArm.yRot = 0.0F;
            leftArm.yRot = 0.0F;
            rightArm.zRot = 0.0F;
            leftArm.zRot = 0.0F;
            head.xRot = Math.max(head.xRot, 0.2F);
        }
    }
}
