package com.xiaofeiwu.thief;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A frame to tie someone to, arms out to the upper left and right and legs to the lower left and right: three blocks wide and three
 * high, nine blocks that stand and fall together. The middle column is where they stand; the posts are in the outer columns, and a rope
 * runs from each post to a wrist or an ankle. The thief stands in front of the frame ({@code FACING} is the side it faces), see
 * {@link ThiefEntity#bindToRack}.
 */
public class RackBlock extends Block {

    /** The row: bottom has the ankle ropes, middle the wrist ropes, top the beam. */
    public enum Part implements StringRepresentable {
        BOTTOM("bottom"), MIDDLE("middle"), TOP("top");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** The column, as seen by someone looking at the front of the frame. */
    public enum Side implements StringRepresentable {
        LEFT("left"), CENTER("center"), RIGHT("right");

        private final String name;

        Side(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** How far the person tied to the frame is lifted off the ground: so that the wrists meet the ropes at the top corners and the ankles those at the bottom. */
    public static final double LIFT = 0.4D;

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final EnumProperty<Side> SIDE = EnumProperty.create("side", Side.class);

    /** The board the frame is made of, at the back of each block (the thief stands in front of it). */
    private static final VoxelShape SOUTH = Block.box(0, 0, 2, 16, 16, 5);
    private static final VoxelShape NORTH = Block.box(0, 0, 11, 16, 16, 14);
    private static final VoxelShape WEST = Block.box(11, 0, 0, 14, 16, 16);
    private static final VoxelShape EAST = Block.box(2, 0, 0, 5, 16, 16);

    public RackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.BOTTOM).setValue(SIDE, Side.CENTER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, SIDE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> SOUTH;
        };
    }

    /** The side of the frame at the right hand of someone who looks at its front, and at the left. */
    static Direction rightOf(Direction facing) {
        return facing.getCounterClockWise();
    }

    static Direction leftOf(Direction facing) {
        return facing.getClockWise();
    }

    /** Where the block of this row and column is, given the middle of the bottom row. */
    static BlockPos cell(BlockPos centerBottom, Direction facing, Part part, Side side) {
        BlockPos p = centerBottom.above(part.ordinal());
        return side == Side.LEFT ? p.relative(leftOf(facing)) : side == Side.RIGHT ? p.relative(rightOf(facing)) : p;
    }

    /** Faces the player who puts it down. Needs the nine places free, and firm ground under the three at the bottom. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos at = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (at.getY() >= level.getMaxBuildHeight() - 3) {
            return null;
        }
        for (Part part : Part.values()) {
            for (Side side : Side.values()) {
                BlockPos cell = cell(at, facing, part, side);
                if (!(part == Part.BOTTOM && side == Side.CENTER) && !level.getBlockState(cell).canBeReplaced(context)) {
                    return null;
                }
                if (part == Part.BOTTOM && !level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)) {
                    return null;
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        build(level, pos, state.getValue(FACING));
    }

    /** Puts up the whole frame, the middle of its bottom row at {@code centerBottom}. */
    public static void build(Level level, BlockPos centerBottom, Direction facing) {
        BlockState base = ModBlocks.RACK.get().defaultBlockState().setValue(FACING, facing);
        for (Part part : Part.values()) {
            for (Side side : Side.values()) {
                level.setBlock(cell(centerBottom, facing, part, side), base.setValue(PART, part).setValue(SIDE, side), 3);
            }
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(PART) != Part.BOTTOM) {
            return true;
        }
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    private boolean is(BlockState state, Part part, Side side) {
        return state.is(this) && state.getValue(PART) == part && state.getValue(SIDE) == side;
    }

    /**
     * Every block needs its neighbours in the frame: the one above and the one below in its column, the middle column needs both outer
     * ones and each outer one needs the middle; the bottom row needs ground. When one goes, the change runs through all of them.
     */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Part part = state.getValue(PART);
        Side side = state.getValue(SIDE);
        Direction facing = state.getValue(FACING);
        boolean missing = false;
        if (direction == Direction.UP) {
            Part needed = part == Part.BOTTOM ? Part.MIDDLE : part == Part.MIDDLE ? Part.TOP : null;
            missing = needed != null && !is(neighbor, needed, side);
        } else if (direction == Direction.DOWN) {
            Part needed = part == Part.TOP ? Part.MIDDLE : part == Part.MIDDLE ? Part.BOTTOM : null;
            missing = needed != null ? !is(neighbor, needed, side) : !state.canSurvive(level, pos);
        } else if (direction == rightOf(facing)) {
            Side needed = side == Side.LEFT ? Side.CENTER : side == Side.CENTER ? Side.RIGHT : null;
            missing = needed != null && !is(neighbor, part, needed);
        } else if (direction == leftOf(facing)) {
            Side needed = side == Side.RIGHT ? Side.CENTER : side == Side.CENTER ? Side.LEFT : null;
            missing = needed != null && !is(neighbor, part, needed);
        }
        return missing ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /** In creative mode breaking any block of it does not also drop the rack: that is what the middle of the bottom row does. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && !isAnchor(state)) {
            BlockPos anchor = anchorPos(state, pos);
            BlockState anchorState = level.getBlockState(anchor);
            if (isAnchor(anchorState)) {
                level.setBlock(anchor, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, anchor, Block.getId(anchorState));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /** The middle of the bottom row: the block that drops the rack, and the one the thief is tied to. */
    public static boolean isAnchor(BlockState state) {
        return state.getBlock() instanceof RackBlock && state.getValue(PART) == Part.BOTTOM && state.getValue(SIDE) == Side.CENTER;
    }

    public static BlockPos anchorPos(BlockState state, BlockPos pos) {
        BlockPos p = pos.below(state.getValue(PART).ordinal());
        Direction facing = state.getValue(FACING);
        return switch (state.getValue(SIDE)) {
            case LEFT -> p.relative(rightOf(facing));
            case RIGHT -> p.relative(leftOf(facing));
            default -> p;
        };
    }
}
