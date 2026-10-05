package com.xiaofeiwu.thief;

import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Runs from any player that comes close, in creative mode too (the game's own "avoid" behaviour ignores creative players, which is
 * why this is written out). Closer than 7 blocks while it has nothing, 14 while it carries loot.
 */
final class FleePlayerGoal extends Goal {

    private final ThiefEntity thief;
    private Player threat;
    private Path path;

    FleePlayerGoal(ThiefEntity thief) {
        this.thief = thief;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private double range() {
        return thief.hasLoot() || thief.alertTicks() > 0 ? 14.0D : 7.0D;
    }

    @Override
    public boolean canUse() {
        if (thief.tiedUp() || thief.isDisguised() || thief.isVengeful() || thief.isNegotiating()) {
            return false;        // while it looks like a cow it behaves like one and does not run
        }
        threat = thief.level().getNearestPlayer(thief.getX(), thief.getY(), thief.getZ(), range(), EntitySelector.NO_SPECTATORS);
        if (threat == null) {
            return false;
        }
        // a few tries at a place away from the player that it can really walk to: one behind a wall is no use
        for (int i = 0; i < 6; i++) {
            Vec3 away = DefaultRandomPos.getPosAway(thief, 16, 7, threat.position());
            if (away == null || threat.distanceToSqr(away.x, away.y, away.z) < threat.distanceToSqr(thief)) {
                continue;
            }
            Path p = thief.getNavigation().createPath(away.x, away.y, away.z, 0);
            if (p != null && p.canReach()) {
                path = p;
                return true;
            }
        }
        return false;
    }

    @Override
    public void start() {
        thief.getNavigation().moveTo(path, 1.0D);
    }

    @Override
    public boolean canContinueToUse() {
        return !thief.getNavigation().isDone() && !thief.tiedUp() && !thief.isDisguised();
    }

    @Override
    public void stop() {
        threat = null;
    }

    @Override
    public void tick() {
        if (threat != null) {
            thief.getNavigation().setSpeedModifier(thief.distanceToSqr(threat) < 49.0D ? 1.15D : 1.0D);
        }
    }
}
