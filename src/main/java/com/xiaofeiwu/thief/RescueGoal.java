package com.xiaofeiwu.thief;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.Comparator;
import java.util.EnumSet;

/**
 * Goes to a thief that is tied up and cuts it loose, which takes three seconds of standing beside it. Runs from a player that comes
 * near (the flee goal is stronger than this one), so a captive that is guarded is not rescued.
 */
final class RescueGoal extends Goal {

    private static final int CUT_TICKS = 60;
    private static final double REACH_SQR = 2.6D * 2.6D;
    /** How far a thief looks for a captive. Lowered in the automatic tests, where the areas are close together. */
    static double searchRange = 48.0D;

    private final ThiefEntity thief;
    private ThiefEntity captive;
    private int progress;
    private int sinceStart;
    private long nextScan;

    RescueGoal(ThiefEntity thief) {
        this.thief = thief;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ThiefConfig.RESCUE_ENABLED.get() || thief.tiedUp() || thief.isHung() || thief.isDecoy() || thief.isNegotiating() || thief.isVengeful() || !(thief.level() instanceof ServerLevel level) || level.getGameTime() < nextScan) {
            return false;
        }
        nextScan = level.getGameTime() + 40;
        if (StealGoal.requirePlayerNearby && level.getNearestPlayer(thief, 128.0D) == null) {
            return false;
        }
        java.util.List<ThiefEntity> tied = level.getEntitiesOfClass(ThiefEntity.class, thief.getBoundingBox().inflate(searchRange), c -> c != thief && c.isAlive() && c.tiedUp() && !c.ransomActive() && !c.rescueProofForTest);
        StringBuilder log = new StringBuilder("scan at " + level.getGameTime() + " from " + thief.blockPosition().toShortString() + ": " + tied.size() + " tied");
        captive = null;
        double best = Double.MAX_VALUE;
        for (ThiefEntity c : tied) {
            net.minecraft.core.BlockPos spot = approachSpot(c);
            Targets.Reach reach = spot == null ? null : Targets.reach(thief, spot, false);
            log.append(" [captive ").append(c.blockPosition().toShortString()).append(" spot ").append(spot == null ? "none" : spot.toShortString()).append(" ").append(reach).append("]");
            if (reach == Targets.Reach.REACHES && thief.distanceToSqr(c) < best) {
                best = thief.distanceToSqr(c);
                captive = c;
            }
        }
        note(log.toString());
        return captive != null;
    }

    private static boolean canStandAt(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos p) {
        net.minecraft.core.BlockPos below = p.below();
        return !level.getBlockState(p).blocksMotion() && !level.getBlockState(p.above()).blocksMotion() && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    /**
     * Where to walk to in order to cut a captive loose: where it stands, or, for one hung up in the air, the ground below it, or, if that place is
     * taken by a block (a rack's board is in the block the captive on it stands in), a free place beside it, the nearest to the rescuer. Null if
     * there is none. (A place in the air or in a block can not be walked to, so using the captive's own position made some look out of reach.)
     */
    private net.minecraft.core.BlockPos approachSpot(ThiefEntity captive) {
        net.minecraft.world.level.Level level = captive.level();
        net.minecraft.core.BlockPos p = captive.blockPosition();
        for (int i = 0; i < 8 && !level.getBlockState(p.below()).blocksMotion() && level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty(); i++) {
            p = p.below();
        }
        if (canStandAt(level, p)) {
            return p;
        }
        net.minecraft.core.BlockPos best = null;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            net.minecraft.core.BlockPos q = p.relative(d);
            if (canStandAt(level, q) && (best == null || q.distSqr(thief.blockPosition()) < best.distSqr(thief.blockPosition()))) {
                best = q;
            }
        }
        return best;
    }

    /** Close enough to cut the rope: within two blocks across, and the captive not more than three above (a hung one's wrists are overhead). */
    private boolean closeEnough() {
        double dx = captive.getX() - thief.getX(), dz = captive.getZ() - thief.getZ();
        return dx * dx + dz * dz <= REACH_SQR && captive.getY() - thief.getY() <= 3.2D && thief.getY() - captive.getY() <= 2.5D;
    }

    @Override
    public void start() {
        progress = 0;
        sinceStart = 0;
        note("START at " + thief.blockPosition().toShortString());
    }

    /** Written down on the thief, for {@code /thief debug}: the last few things this goal did. */
    private void note(String what) {
        String all = thief.rescueLog + " | " + what;
        thief.rescueLog = all.length() > 3000 ? all.substring(all.length() - 3000) : all;
    }

    @Override
    public boolean canContinueToUse() {
        // one that cannot get there in half a minute leaves it, and does not try again at once
        if (progress == 0 && sinceStart > 600) {
            nextScan = thief.level().getGameTime() + 400;
            return false;
        }
        return captive != null && captive.isAlive() && captive.tiedUp() && !thief.tiedUp();
    }

    @Override
    public void stop() {
        note("STOP progress " + progress + ", " + sinceStart + " ticks, at " + thief.blockPosition().toShortString());
        captive = null;
        progress = 0;
    }

    @Override
    public void tick() {
        sinceStart++;
        if (sinceStart % 25 == 0) {
            note("TICK " + sinceStart + " at " + thief.position() + " progress " + progress + " navDone " + thief.getNavigation().isDone() + " spot " + approachSpot(captive)
                    + " | inWater " + thief.isInWater() + " inLava " + thief.isInLava() + " noGravity " + thief.isNoGravity() + " onGround " + thief.onGround() + " delta " + thief.getDeltaMovement()
                    + " feet " + thief.level().getBlockState(thief.blockPosition()).getBlock() + " below " + thief.level().getBlockState(thief.blockPosition().below()).getBlock()
                    + " fluid " + thief.level().getFluidState(thief.blockPosition()) + " effects " + thief.getActiveEffects().stream().map(e -> e.getEffect().getDescriptionId()).toList()
                    + " vehicle " + thief.getVehicle() + " passengers " + thief.getPassengers().size());
        }
        thief.getLookControl().setLookAt(captive, 30.0F, 30.0F);
        if (!closeEnough()) {
            if (progress > 0) {
                note("LOST closeness at " + thief.blockPosition().toShortString() + " captive " + captive.position());
            }
            progress = 0;
            if (thief.tickCount % 10 == 0 || thief.getNavigation().isDone()) {
                net.minecraft.core.BlockPos spot = approachSpot(captive);
                if (spot != null) {
                    if (thief.getNavigation().isDone() && sinceStart > 20) {
                        // the walk counts as done a block short of the place, which may be too far to reach the rope: the last step is straight
                        thief.getMoveControl().setWantedPosition(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 1.0D);
                    } else {
                        thief.getNavigation().moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 1.1D);
                    }
                }
            }
            return;
        }
        thief.getNavigation().stop();
        if (progress == 0) {
            thief.reveal();        // a sheep that is cutting a rope is a thief
            note("CLOSE at " + thief.blockPosition().toShortString());
        }
        progress++;
        if (progress % 10 == 0) {
            thief.swing(InteractionHand.MAIN_HAND);
            thief.level().playSound(null, thief.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.HOSTILE, 0.8F, 1.3F);
        }
        if (progress >= CUT_TICKS) {
            captive.freedBy(thief);
            captive = null;
        }
    }
}
