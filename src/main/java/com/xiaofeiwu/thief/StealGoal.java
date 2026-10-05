package com.xiaofeiwu.thief;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * Looks for a chest or a ripe crop, walks up to it and takes what it can carry. A thief only steals when a player is within 128 blocks
 * (a world with nobody in it is left alone, even where chunks are kept loaded), and never when mob griefing is off.
 */
final class StealGoal extends Goal {

    private static final int GIVE_UP = 600;          // ticks to reach the target before it tries something else
    private static final double REACH_SQR = 2.6D * 2.6D;

    /** Off only in the automatic tests, where there is no player. */
    static boolean requirePlayerNearby = true;

    private final ThiefEntity thief;
    private BlockPos target;
    private boolean crop;
    private int giveUp;
    private int cropsTaken;
    private BlockPos firstTarget;
    private long nextScan;

    StealGoal(ThiefEntity thief) {
        this.thief = thief;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ThiefConfig.ENABLED.get() || thief.hasStolen() || thief.tiedUp() || thief.alertTicks() > 0 || thief.isIdleLookout() || thief.isNegotiating() || thief.isVengeful() || !(thief.level() instanceof ServerLevel level)) {
            return false;
        }
        if (level.getGameTime() < nextScan) {
            return false;
        }
        nextScan = level.getGameTime() + 80 + thief.getRandom().nextInt(80);
        if (!ForgeEventFactory.getMobGriefingEvent(level, thief) || (requirePlayerNearby && level.getNearestPlayer(thief, 128.0D) == null)) {
            return false;
        }
        return choose(level);
    }

    /** Pick a chest or a field near here that the thief can walk to. */
    private boolean choose(ServerLevel level) {
        Random random = new Random(thief.getRandom().nextLong());
        int radius = ThiefConfig.SEARCH_RADIUS.get();
        List<BlockPos> chests = ThiefConfig.STEAL_CHESTS.get() ? Targets.containersNear(level, thief.blockPosition(), radius) : List.of();
        List<BlockPos> fields = ThiefConfig.STEAL_CROPS.get() ? Targets.ripeCropsNear(level, thief.blockPosition(), Math.min(radius, 16), 50) : List.of();
        boolean cropFirst = !fields.isEmpty() && (chests.isEmpty() || random.nextBoolean());
        for (int round = 0; round < 2; round++) {
            boolean tryCrops = (round == 0) == cropFirst;
            List<BlockPos> list = new ArrayList<>(tryCrops ? fields : chests);
            // one of the nearest three first, for variety; then the rest, nearest first, so that a few that cannot be reached
            // (a walled-in field, a chest behind an iron door) do not stop it from trying the ones further off that can
            for (int i = 0; i < 12 && !list.isEmpty(); i++) {
                BlockPos pos = i == 0 ? Targets.pickNear(list, random) : list.get(0);
                list.remove(pos);
                if (reachable(pos, !tryCrops)) {
                    target = pos;
                    firstTarget = pos;
                    crop = tryCrops;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean reachable(BlockPos pos, boolean solid) {
        return Targets.reach(thief, pos, solid) == Targets.Reach.REACHES;
    }

    @Override
    public void start() {
        giveUp = GIVE_UP;
        cropsTaken = 0;
        moveToTarget();
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && giveUp > 0 && ThiefConfig.ENABLED.get() && !thief.tiedUp();
    }

    @Override
    public void stop() {
        target = null;
        thief.getNavigation().stop();
    }

    @Override
    public void tick() {
        giveUp--;
        if (target == null) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(target);
        thief.getLookControl().setLookAt(center.x, center.y, center.z);
        if (thief.distanceToSqr(center) <= REACH_SQR) {
            act();
        } else if (thief.tickCount % 10 == 0 || thief.getNavigation().isDone()) {
            moveToTarget();
        }
    }

    private void moveToTarget() {
        if (target != null) {
            thief.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 1.05D);
        }
    }

    private void act() {
        if (!(thief.level() instanceof ServerLevel level)) {
            return;
        }
        if (!ForgeEventFactory.getMobGriefingEvent(level, thief)) {
            target = null;
            return;
        }
        thief.reveal();        // a cow at the chest turns out to be a thief
        thief.swing(InteractionHand.MAIN_HAND);
        if (crop) {
            pickCrop(level);
        } else {
            robContainer(level);
        }
    }

    private void robContainer(ServerLevel level) {
        Container container = Targets.containerAt(level, target);
        if (container == null || !Targets.hasLoot(container)) {
            target = null;        // gone, or emptied by someone else
            return;
        }
        BlockPos bell = Targets.bellNear(level, target);
        int stacks = bell != null ? 1 : ThiefConfig.STACKS_PER_THEFT.get();
        List<StealPlan.Slot> slots = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            slots.add(new StealPlan.Slot(i, s.getCount(), Targets.isAllowed(s)));
        }
        for (StealPlan.Take take : StealPlan.plan(slots, stacks, ThiefConfig.MAX_ITEMS_PER_STACK.get(), new Random(thief.getRandom().nextLong()))) {
            ItemStack taken = container.removeItem(take.index(), take.count());
            thief.addLoot(taken);
        }
        container.setChanged();
        level.playSound(null, target, level.getBlockEntity(target) instanceof BarrelBlockEntity ? SoundEvents.BARREL_OPEN : SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.6F, 1.1F);
        raiseAlarm(level, bell);
        finish();
    }

    private void pickCrop(ServerLevel level) {
        BlockState state = level.getBlockState(target);
        if (!(state.getBlock() instanceof CropBlock cropBlock) || !cropBlock.isMaxAge(state)) {
            target = null;
            return;
        }
        for (ItemStack drop : Block.getDrops(state, level, target, null)) {
            thief.addLoot(drop);
        }
        level.levelEvent(2001, target, Block.getId(state));
        level.setBlock(target, cropBlock.getStateForAge(0), 3);          // the seedling is put back
        cropsTaken++;
        BlockPos bell = Targets.bellNear(level, target);
        int limit = bell != null ? 2 : ThiefConfig.CROPS_PER_THEFT.get();
        if (bell != null) {
            raiseAlarm(level, bell);
        }
        if (cropsTaken < limit) {
            List<BlockPos> more = Targets.ripeCropsNear(level, target, 6, 1);
            if (!more.isEmpty()) {
                target = more.get(0);
                giveUp = 200;
                moveToTarget();
                return;
            }
        }
        finish();
    }

    /** A bell near what was robbed rings, the thief shines for half a minute, and the players near enough are told. */
    private void raiseAlarm(ServerLevel level, BlockPos bell) {
        if (bell == null) {
            return;
        }
        level.playSound(null, bell, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0F, 1.0F);
        thief.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, false, false));
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(bell.getX(), bell.getY(), bell.getZ()) <= 48.0D * 48.0D) {
                p.displayClientMessage(Component.translatable("message.thief.bell"), true);
            }
        }
    }

    private void finish() {
        thief.finishedStealing(firstTarget == null ? thief.blockPosition() : firstTarget);
        target = null;
    }
}
