package com.xiaofeiwu.thief;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.Comparator;
import java.util.EnumSet;

/**
 * A crew member with loot who is being chased (a player near, a call from the lookout, or a blow) hands it all to a mate who has nothing,
 * who runs off with it. The one who handed it over keeps shining for a while, which makes it the decoy.
 */
final class PassLootGoal extends Goal {

    private static final int GIVE_UP = 200;
    private static final double REACH_SQR = 2.2D * 2.2D;

    private final ThiefEntity thief;
    private ThiefEntity mate;
    private int ticks;

    PassLootGoal(ThiefEntity thief) {
        this.thief = thief;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!thief.hasLoot() || thief.tiedUp() || thief.crewId() == null || !(thief.level() instanceof ServerLevel level) || level.getGameTime() < thief.nextPass()) {
            return false;
        }
        boolean chased = thief.alertTicks() > 0 || thief.recentlyHurt() || level.getNearestPlayer(thief, 16.0D) != null;
        if (!chased) {
            return false;
        }
        mate = thief.crewMates(16.0D).stream().filter(m -> !m.hasLoot() && !m.tiedUp() && !m.isHung()).min(Comparator.comparingDouble(thief::distanceToSqr)).orElse(null);
        return mate != null;
    }

    @Override
    public void start() {
        ticks = GIVE_UP;
    }

    @Override
    public boolean canContinueToUse() {
        return ticks > 0 && mate != null && mate.isAlive() && !mate.tiedUp() && !mate.hasLoot() && thief.hasLoot();
    }

    @Override
    public void stop() {
        mate = null;
    }

    @Override
    public void tick() {
        ticks--;
        thief.getLookControl().setLookAt(mate, 30.0F, 30.0F);
        if (thief.distanceToSqr(mate) <= REACH_SQR) {
            thief.passLootTo(mate);
            mate = null;
        } else if (thief.tickCount % 5 == 0 || thief.getNavigation().isDone()) {
            thief.getNavigation().moveTo(mate, 1.2D);
        }
    }
}
