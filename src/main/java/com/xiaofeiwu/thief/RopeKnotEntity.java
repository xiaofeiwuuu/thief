package com.xiaofeiwu.thief;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * The knot a rope is tied to. The game's own knot holds only on fences and is taken away a few seconds after being put anywhere
 * else; this one also holds on tree trunks, walls and any pillar, and falls off when the block under it is taken away.
 */
public class RopeKnotEntity extends LeashFenceKnotEntity {

    public RopeKnotEntity(EntityType<? extends RopeKnotEntity> type, Level level) {
        super(type, level);
    }

    /** Fences, tree trunks (logs, stems, bamboo blocks), walls, and anything that stands along an axis: pillars of stone, quartz, basalt, chains. */
    public static boolean canTieTo(BlockState state) {
        return state.is(BlockTags.FENCES) || state.is(BlockTags.LOGS) || state.is(BlockTags.WALLS)
                || state.hasProperty(BlockStateProperties.AXIS);
    }

    @Override
    public boolean survives() {
        BlockState state = level().getBlockState(getPos());
        return canTieTo(state) || state.isFaceSturdy(level(), getPos(), net.minecraft.core.Direction.DOWN);       // the second: a thief hung from the underside of any block
    }

    /** The knot at this place, put there if there is none yet. */
    public static LeashFenceKnotEntity getOrCreate(Level level, BlockPos pos) {
        for (LeashFenceKnotEntity existing : level.getEntitiesOfClass(LeashFenceKnotEntity.class, new AABB(pos).inflate(0.5D))) {
            if (existing.getPos().equals(pos)) {
                return existing;
            }
        }
        RopeKnotEntity knot = new RopeKnotEntity(ModEntities.ROPE_KNOT.get(), level);
        knot.setPos(pos.getX(), pos.getY(), pos.getZ());
        level.addFreshEntity(knot);
        return knot;
    }
}
