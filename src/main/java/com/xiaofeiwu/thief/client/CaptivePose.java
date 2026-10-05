package com.xiaofeiwu.thief.client;

import com.xiaofeiwu.thief.Captives;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Mob;

import java.util.NoSuchElementException;

/**
 * Puts the limbs of a tied-up creature where they are tied: this is called after the game has worked out the pose of the model (and before it
 * draws it, and before the armour and what it holds, which follow the pose of the body), so it has the last word.
 * Humanoid models (zombies, skeletons, piglins) get the same poses as a thief. Illagers have arms of their own only when not crossed; a villager's
 * arms are one piece, crossed, and stay as they are.
 */
final class CaptivePose {

    private CaptivePose() {
    }

    static void apply(Mob mob, EntityModel<?> model) {
        Captives.State state = Captives.state(mob);
        if (state.mode() == Captives.Mode.NONE) {
            return;
        }
        try {
            if (model instanceof HumanoidModel<?> humanoid) {
                humanoid(humanoid, state.mode());
            } else if (model instanceof IllagerModel<?> illager) {
                illager(illager.root(), state.mode());
            } else if (model instanceof VillagerModel<?> villager) {
                villager(villager.root(), state.mode());
            }
        } catch (NoSuchElementException | ClassCastException ignored) {
            // a model of another shape than expected (another mod's): left as the game posed it
        }
    }

    private static void humanoid(HumanoidModel<?> m, Captives.Mode mode) {
        switch (mode) {
            case POST -> {                 // arms pulled back against the body, head down
                arms(m.rightArm, m.leftArm, 0.3F, 0.0F, -0.1F);
                legs(m.rightLeg, m.leftLeg, 0.0F);
                m.head.xRot = 0.35F;
            }
            case RACK -> {                 // arms to the upper left and right, legs to the lower left and right
                arms(m.rightArm, m.leftArm, 0.0F, 0.0F, 2.36F);
                legs(m.rightLeg, m.leftLeg, 0.8F);
                m.head.xRot = 0.1F;
            }
            case HUNG -> {                 // arms straight up, the body hanging from them
                arms(m.rightArm, m.leftArm, -(float) Math.PI, 0.0F, 0.0F);
                legs(m.rightLeg, m.leftLeg, 0.04F);
                m.head.xRot = 0.45F;
            }
            case LED -> {                  // hands bound in front
                arms(m.rightArm, m.leftArm, -1.0F, 0.0F, 0.0F);
                m.head.xRot = Math.max(m.head.xRot, 0.2F);
            }
            default -> {
                return;
            }
        }
        // what the game copies from the main parts in its own pass, and that we have changed since
        m.hat.copyFrom(m.head);
        if (m instanceof PlayerModel<?> p) {
            p.leftSleeve.copyFrom(p.leftArm);
            p.rightSleeve.copyFrom(p.rightArm);
            p.leftPants.copyFrom(p.leftLeg);
            p.rightPants.copyFrom(p.rightLeg);
            p.jacket.copyFrom(p.body);
        }
    }

    /** The right arm swings out by +z and the left by -z (that is away from the body for both). */
    private static void arms(ModelPart right, ModelPart left, float x, float y, float z) {
        right.xRot = x;
        left.xRot = x;
        right.yRot = y;
        left.yRot = y;
        right.zRot = z;
        left.zRot = -z;
    }

    private static void legs(ModelPart right, ModelPart left, float spread) {
        right.xRot = 0.0F;
        left.xRot = 0.0F;
        right.yRot = 0.0F;
        left.yRot = 0.0F;
        right.zRot = spread;
        left.zRot = -spread;
    }

    private static void illager(ModelPart root, Captives.Mode mode) {
        ModelPart arms = root.getChild("arms");
        ModelPart right = root.getChild("right_arm");
        ModelPart left = root.getChild("left_arm");
        ModelPart rightLeg = root.getChild("right_leg");
        ModelPart leftLeg = root.getChild("left_leg");
        switch (mode) {
            case RACK -> {
                arms.visible = false;
                right.visible = true;
                left.visible = true;
                arms(right, left, 0.0F, 0.0F, 2.36F);
                legs(rightLeg, leftLeg, 0.8F);
            }
            case HUNG -> {
                arms.visible = false;
                right.visible = true;
                left.visible = true;
                arms(right, left, -(float) Math.PI, 0.0F, 0.0F);
                legs(rightLeg, leftLeg, 0.04F);
            }
            default -> {
                // standing against a post, or led: the crossed arms are already hands tied in front
                arms.visible = true;
                right.visible = false;
                left.visible = false;
                legs(rightLeg, leftLeg, 0.0F);
            }
        }
    }

    private static void villager(ModelPart root, Captives.Mode mode) {
        ModelPart head = root.getChild("head");
        if (mode == Captives.Mode.RACK) {
            legs(root.getChild("right_leg"), root.getChild("left_leg"), 0.8F);
        } else if (mode == Captives.Mode.HUNG) {
            legs(root.getChild("right_leg"), root.getChild("left_leg"), 0.04F);
        } else if (mode == Captives.Mode.POST || mode == Captives.Mode.LED) {
            legs(root.getChild("right_leg"), root.getChild("left_leg"), 0.0F);
        }
        head.xRot = Math.max(head.xRot, mode == Captives.Mode.RACK ? 0.1F : 0.35F);
    }
}
