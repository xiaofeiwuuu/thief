package com.xiaofeiwu.thief;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * A thief out for revenge goes at its target and strikes. Written out rather than using the game's melee goal, which gives up on a player in
 * creative mode (who cannot be hurt, but who should still see the thief come for them).
 */
final class RevengeGoal extends Goal {

    private final ThiefEntity thief;
    private int cooldown;

    RevengeGoal(ThiefEntity thief) {
        this.thief = thief;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = thief.getTarget();
        return thief.isVengeful() && target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        LivingEntity target = thief.getTarget();
        if (target == null) {
            return;
        }
        thief.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (thief.tickCount % 5 == 0 || thief.getNavigation().isDone()) {
            thief.getNavigation().moveTo(target, 1.15D);
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (thief.distanceToSqr(target) <= 2.5D * 2.5D && cooldown <= 0) {
            thief.swing(InteractionHand.MAIN_HAND);
            thief.doHurtTarget(target);
            cooldown = 20;
        }
    }
}
