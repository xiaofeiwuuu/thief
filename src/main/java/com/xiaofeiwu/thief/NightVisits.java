package com.xiaofeiwu.thief;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Where thieves come from. Once a night, at dusk, each player has a chance that a thief appears some way off from something worth
 * robbing near them (a chest, a ripe field) and goes for it; now and then it is a crew of them. Nothing comes while the player is a
 * spectator, on peaceful, or when mobs may not spawn. Creative players are visited too.
 */
@Mod.EventBusSubscriber(modid = ThiefMod.MODID)
public final class NightVisits {

    private static final long DUSK = 13000L;
    private static long lastDay = -1;

    private NightVisits() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        long time = level.getDayTime();
        // only on the tick of dusk, and only once a day, so that stopping the clock cannot make it fire every tick
        if (time % 24000L != DUSK || time / 24000L == lastDay || !level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            return;
        }
        lastDay = time / 24000L;
        if (!ThiefConfig.ENABLED.get() || level.getDifficulty() == Difficulty.PEACEFUL || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && level.random.nextDouble() < ThiefConfig.NIGHTLY_CHANCE.get()) {
                visit(level, player);
            }
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        lastDay = -1;
    }

    /** Send a thief, or a crew, to rob what is near this player. @return false if there is nothing to rob, no place to appear, or enough thieves about already */
    public static boolean visit(ServerLevel level, ServerPlayer player) {
        int nearby = level.getEntitiesOfClass(ThiefEntity.class, player.getBoundingBox().inflate(128.0D)).size();
        if (nearby >= ThiefConfig.MAX_NEARBY.get()) {
            return false;
        }
        Random random = new Random(level.random.nextLong());
        int radius = Math.min(ThiefConfig.SEARCH_RADIUS.get(), 48);
        List<BlockPos> chests = ThiefConfig.STEAL_CHESTS.get() ? Targets.containersNear(level, player.blockPosition(), radius) : List.of();
        List<BlockPos> fields = ThiefConfig.STEAL_CROPS.get() ? Targets.ripeCropsNear(level, player.blockPosition(), 16, 50) : List.of();
        List<BlockPos> pool = chests.isEmpty() || (!fields.isEmpty() && random.nextBoolean()) ? fields : chests;
        BlockPos goal = Targets.pickNear(pool, random);
        if (goal == null) {
            return false;
        }
        boolean crew = nearby == 0 && random.nextDouble() < ThiefConfig.CREW_CHANCE.get();
        ThiefEntity first = spawnNear(level, goal, 16, 24, player, random);
        if (first == null) {
            return false;
        }
        if (crew) {
            formCrew(level, first, ThiefConfig.CREW_SIZE.get(), random);
        }
        return true;
    }

    /**
     * A thief on firm ground between {@code minDist} and {@code maxDist} blocks from a place, not in water, not on top of {@code avoid} (a
     * player, or null). It is added to the world.
     */
    static ThiefEntity spawnNear(ServerLevel level, BlockPos center, int minDist, int maxDist, ServerPlayer avoid, Random random) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = minDist + random.nextDouble() * Math.max(0, maxDist - minDist);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            ThiefEntity thief = spawnAt(level, x, z, center.getY(), avoid, random);
            if (thief != null) {
                return thief;
            }
        }
        return null;
    }

    /**
     * Firm ground at this spot with two free blocks above it, as near as can be found to the height {@code aroundY}: the height of what the thief
     * is after, so that it is not put on the roof or the hill above a cellar. Null if there is none within ten blocks up or down.
     */
    static BlockPos groundNear(ServerLevel level, int x, int z, int aroundY) {
        if (!level.hasChunkAt(new BlockPos(x, aroundY, z))) {
            return null;
        }
        for (int offset = 0; offset <= 10; offset++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                if (offset == 0 && sign == -1) {
                    continue;
                }
                BlockPos pos = new BlockPos(x, aroundY + offset * sign, z);
                if (!level.getBlockState(pos).blocksMotion() && !level.getBlockState(pos.above()).blocksMotion() && level.getFluidState(pos).isEmpty()
                        && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                    return pos;
                }
            }
        }
        return null;
    }

    private static ThiefEntity spawnAt(ServerLevel level, int x, int z, int aroundY, ServerPlayer avoid, Random random) {
        BlockPos pos = groundNear(level, x, z, aroundY);
        if (pos == null || (avoid != null && pos.distSqr(avoid.blockPosition()) < 12 * 12)) {
            return null;        // no firm ground at that height, or on top of the player
        }
        int y = pos.getY();
        ThiefEntity thief = ModEntities.THIEF.get().create(level);
        if (thief == null) {
            return null;
        }
        thief.moveTo(x + 0.5D, y, z + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(thief)) {
            return null;
        }
        thief.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        level.addFreshEntity(thief);
        return thief;
    }

    /**
     * Makes a crew of the thief and others put down close to it: the thief and all but the last are robbers, the last is the lookout.
     * @return how many are in the crew
     */
    static int formCrew(ServerLevel level, ThiefEntity first, int size, Random random) {
        UUID crew = UUID.randomUUID();
        List<ThiefEntity> members = new ArrayList<>();
        members.add(first);
        for (int i = 1; i < size; i++) {
            for (int attempt = 0; attempt < 12; attempt++) {
                ThiefEntity mate = spawnAt(level, first.getBlockX() + random.nextInt(9) - 4, first.getBlockZ() + random.nextInt(9) - 4, first.getBlockY(), null, random);
                if (mate != null) {
                    members.add(mate);
                    break;
                }
            }
        }
        // with three or more: the last is the lookout, and the first may be a magician (who does not steal): the rest rob
        boolean withMagician = members.size() >= 3 && random.nextDouble() < (ThiefEntity.magicianChanceOverride != null ? ThiefEntity.magicianChanceOverride : ThiefConfig.MAGICIAN_CHANCE.get());
        for (int i = 0; i < members.size(); i++) {
            members.get(i).joinCrew(crew, members.size() >= 3 && i == members.size() - 1);
            if (withMagician && i == 0) {
                members.get(i).becomeMagician();
            }
        }
        return members.size();
    }
}
