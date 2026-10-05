package com.xiaofeiwu.thief;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The thief that holds up a sign and offers to buy back a captive stays in sight of it: not closer than eight blocks and not further than
 * eighteen, watching the nearest player. It does nothing else while the offer is open (it does not run, steal, or drink).
 */
final class NegotiateGoal extends Goal {

    private final ThiefEntity thief;

    NegotiateGoal(ThiefEntity thief) {
        this.thief = thief;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return thief.isNegotiating();
    }

    @Override
    public boolean canContinueToUse() {
        return thief.isNegotiating();
    }

    @Override
    public void tick() {
        if (!(thief.level() instanceof ServerLevel server)) {
            return;
        }
        Entity captive = server.getEntity(thief.negotiatingFor());
        if (captive == null) {
            return;
        }
        Player watched = server.getNearestPlayer(thief, 32.0D);
        if (watched != null) {
            thief.getLookControl().setLookAt(watched, 30.0F, 30.0F);
        } else {
            thief.getLookControl().setLookAt(captive, 30.0F, 30.0F);
        }
        double d = thief.distanceTo(captive);
        if (d > 18.0D) {
            if (thief.tickCount % 10 == 0 || thief.getNavigation().isDone()) {
                thief.getNavigation().moveTo(captive, 1.0D);
            }
        } else if (d < 8.0D) {
            Vec3 away = DefaultRandomPos.getPosAway(thief, 8, 4, captive.position());
            if (away != null && (thief.tickCount % 10 == 0 || thief.getNavigation().isDone())) {
                thief.getNavigation().moveTo(away.x, away.y, away.z, 1.0D);
            }
        } else {
            thief.getNavigation().stop();
        }
    }
}
