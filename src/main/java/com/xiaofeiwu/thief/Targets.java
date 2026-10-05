package com.xiaofeiwu.thief;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** What a thief can rob, and where to find it. Chests and barrels of the plain game and ripe crops: nothing that belongs to another mod. */
final class Targets {

    private Targets() {
    }

    /** A plain chest or barrel. Exactly those classes: a trapped chest is a subclass and stays out, as does any other mod's storage. */
    static boolean isRobbableContainer(BlockEntity be) {
        return be != null && !be.isRemoved() && (be.getClass() == ChestBlockEntity.class || be.getClass() == BarrelBlockEntity.class);
    }

    static boolean isRipeCrop(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }

    /** The container to take from: both halves of a double chest, or the barrel. Null if it is not robbable. */
    static Container containerAt(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!isRobbableContainer(be)) {
            return null;
        }
        if (be instanceof ChestBlockEntity) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ChestBlock chest) {
                return ChestBlock.getContainer(chest, state, level, pos, true);
            }
            return null;
        }
        return be instanceof Container c ? c : null;
    }

    static boolean isAllowed(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null || !ThiefConfig.PROTECTED_ITEMS.get().contains(key.toString());
    }

    static boolean hasLoot(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (isAllowed(container.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    /** Positions of robbable containers within the radius of a point, in the chunks that are loaded. */
    static List<BlockPos> containersNear(ServerLevel level, BlockPos center, int radius) {
        List<BlockPos> out = new ArrayList<>();
        ChunkPos from = new ChunkPos(center.offset(-radius, 0, -radius));
        ChunkPos to = new ChunkPos(center.offset(radius, 0, radius));
        long r2 = (long) radius * radius;
        for (int cx = from.x; cx <= to.x; cx++) {
            for (int cz = from.z; cz <= to.z; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                    BlockPos p = be.getBlockPos();
                    if (isRobbableContainer(be) && p.distSqr(center) <= r2 && Math.abs(p.getY() - center.getY()) <= 24) {
                        Container c = containerAt(level, p);
                        if (c != null && hasLoot(c)) {
                            out.add(p.immutable());
                        }
                    }
                }
            }
        }
        out.sort((a, b) -> Double.compare(a.distSqr(center), b.distSqr(center)));
        return out;
    }

    /** Ripe crops around a point, nearest first. A small area: this looks at every block in it. */
    static List<BlockPos> ripeCropsNear(ServerLevel level, BlockPos center, int radius, int max) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!level.hasChunkAt(p.set(center.getX() + dx, center.getY(), center.getZ() + dz))) {
                    continue;
                }
                for (int dy = -5; dy <= 5; dy++) {
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (isRipeCrop(level, p)) {
                        out.add(p.immutable());
                    }
                }
            }
        }
        out.sort((a, b) -> Double.compare(a.distSqr(center), b.distSqr(center)));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** How far a walk to a place really gets. A path that stops short, at a wall, is not a way there. */
    enum Reach {
        NO_PATH, STOPS_SHORT, REACHES
    }

    /** For a chest, a wall, anything solid to walk up to: a path that ends right beside it gets there. */
    static Reach reach(net.minecraft.world.entity.PathfinderMob mob, BlockPos pos) {
        return reach(mob, pos, true);
    }

    /**
     * @param solid whether the place is a solid block, which can only be reached from beside it. For a crop, or a spot someone stands on, it
     *              is not: a path that stops short at a gap (a ditch, a fence) is no way there, however near it ends.
     */
    static Reach reach(net.minecraft.world.entity.PathfinderMob mob, BlockPos pos, boolean solid) {
        var path = mob.getNavigation().createPath(pos, 1);
        if (path == null) {
            return Reach.NO_PATH;
        }
        if (path.canReach()) {
            return Reach.REACHES;
        }
        var end = path.getEndNode();
        return solid && end != null && end.asBlockPos().distSqr(pos) <= 4.0D ? Reach.REACHES : Reach.STOPS_SHORT;
    }

    /** {ripe, all} crops around a point, for finding out why nothing was taken. */
    static int[] cropCounts(ServerLevel level, BlockPos center, int radius) {
        int ripe = 0, all = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!level.hasChunkAt(p.set(center.getX() + dx, center.getY(), center.getZ() + dz))) {
                    continue;
                }
                for (int dy = -5; dy <= 5; dy++) {
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.getBlockState(p).getBlock() instanceof CropBlock) {
                        all++;
                        if (isRipeCrop(level, p)) {
                            ripe++;
                        }
                    }
                }
            }
        }
        return new int[]{ripe, all};
    }

    /** A bell close to what is being robbed, or null. */
    static BlockPos bellNear(Level level, BlockPos pos) {
        int r = ThiefConfig.BELL_RADIUS.get();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    p.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (level.getBlockState(p).getBlock() instanceof BellBlock) {
                        double d = p.distSqr(pos);
                        if (d < bestDist) {
                            bestDist = d;
                            best = p.immutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    /** A random one of the nearest few, so the thief does not always go for the same chest. */
    static BlockPos pickNear(List<BlockPos> sorted, Random random) {
        return sorted.isEmpty() ? null : sorted.get(random.nextInt(Math.min(3, sorted.size())));
    }
}
