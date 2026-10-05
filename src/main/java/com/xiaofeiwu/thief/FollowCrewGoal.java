package com.xiaofeiwu.thief;

import net.minecraft.world.entity.ai.goal.Goal;

import java.util.Comparator;
import java.util.EnumSet;

/** The lookout stays within a dozen blocks of the robbers: near enough to take what they pass it, far enough to see anyone coming. */
final class FollowCrewGoal extends Goal {

    private static final double STAY_SQR = 12.0D * 12.0D;

    private final ThiefEntity thief;
    private ThiefEntity leader;

    FollowCrewGoal(ThiefEntity thief) {
        this.thief = thief;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(thief.isLookout() || thief.isMagician()) || thief.tiedUp() || thief.isHung()) {
            return false;
        }
        leader = thief.crewMates(64.0D).stream().filter(m -> !m.isLookout() && !m.isMagician() && m.isAlive()).min(Comparator.comparingDouble(thief::distanceToSqr)).orElse(null);
        return leader != null && thief.distanceToSqr(leader) > STAY_SQR;
    }

    @Override
    public boolean canContinueToUse() {
        return leader != null && leader.isAlive() && !thief.tiedUp() && thief.distanceToSqr(leader) > 8.0D * 8.0D;
    }

    @Override
    public void tick() {
        if (thief.tickCount % 10 == 0 || thief.getNavigation().isDone()) {
            thief.getNavigation().moveTo(leader, 1.0D);
        }
    }
}
