package com.xiaofeiwu.thief;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * The bounty board: a poster two blocks wide and two high, four blocks that stand and fall together. Lead a thief that is tied up on a rope to it
 * and use it: the thief is taken, and the player gets emeralds (more for a member of a crew, most for a magician), experience, the rope back, and
 * whatever the thief still carried. Used with a magician's badge in hand, it pays for the badge. Used with nothing to hand in, it says how it works.
 */
public class BountyBoardBlock extends Block {

    public enum Column implements StringRepresentable {
        LEFT("left"), RIGHT("right");

        private final String name;

        Column(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public enum Row implements StringRepresentable {
        BOTTOM("bottom"), TOP("top");

        private final String name;

        Row(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Column> COLUMN = EnumProperty.create("column", Column.class);
    public static final EnumProperty<Row> ROW = EnumProperty.create("row", Row.class);

    private static final VoxelShape SOUTH = Block.box(0, 0, 8, 16, 16, 10);
    private static final VoxelShape NORTH = Block.box(0, 0, 6, 16, 16, 8);
    private static final VoxelShape WEST = Block.box(6, 0, 0, 8, 16, 16);
    private static final VoxelShape EAST = Block.box(8, 0, 0, 10, 16, 16);

    public BountyBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLUMN, Column.LEFT).setValue(ROW, Row.BOTTOM));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLUMN, ROW);
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

    /** The column to the right of someone who looks at the front of the board. */
    private static Direction rightOf(Direction facing) {
        return facing.getCounterClockWise();
    }

    static BlockPos cell(BlockPos bottomLeft, Direction facing, Column column, Row row) {
        BlockPos p = row == Row.TOP ? bottomLeft.above() : bottomLeft;
        return column == Column.RIGHT ? p.relative(rightOf(facing)) : p;
    }

    /** Faces the player who puts it down; needs the four places free and firm ground under the two at the bottom. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos at = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        for (Row row : Row.values()) {
            for (Column column : Column.values()) {
                BlockPos cell = cell(at, facing, column, row);
                if (!(row == Row.BOTTOM && column == Column.LEFT) && !level.getBlockState(cell).canBeReplaced(context)) {
                    return null;
                }
                if (row == Row.BOTTOM && !level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)) {
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

    /** Puts up the whole board, the bottom left block (as seen from the front) at {@code bottomLeft}. */
    public static void build(Level level, BlockPos bottomLeft, Direction facing) {
        BlockState base = ModBlocks.BOUNTY_BOARD.get().defaultBlockState().setValue(FACING, facing);
        for (Row row : Row.values()) {
            for (Column column : Column.values()) {
                level.setBlock(cell(bottomLeft, facing, column, row), base.setValue(COLUMN, column).setValue(ROW, row), 3);
            }
        }
    }

    private boolean is(BlockState state, Column column, Row row) {
        return state.is(this) && state.getValue(COLUMN) == column && state.getValue(ROW) == row;
    }

    /** Each block needs its neighbours in the board; the bottom two need ground. When one goes, the change runs through all of them. */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Column column = state.getValue(COLUMN);
        Row row = state.getValue(ROW);
        Direction facing = state.getValue(FACING);
        boolean missing = false;
        if (direction == Direction.UP) {
            missing = row == Row.BOTTOM && !is(neighbor, column, Row.TOP);
        } else if (direction == Direction.DOWN) {
            if (row == Row.TOP) {
                missing = !is(neighbor, column, Row.BOTTOM);
            } else {
                missing = !neighbor.isFaceSturdy(level, neighborPos, Direction.UP);
            }
        } else if (direction == rightOf(facing)) {
            missing = column == Column.LEFT && !is(neighbor, Column.RIGHT, row);
        } else if (direction == rightOf(facing).getOpposite()) {
            missing = column == Column.RIGHT && !is(neighbor, Column.LEFT, row);
        }
        return missing ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /** The block that drops the board, and that the others are placed from. */
    public static boolean isAnchor(BlockState state) {
        return state.getBlock() instanceof BountyBoardBlock && state.getValue(COLUMN) == Column.LEFT && state.getValue(ROW) == Row.BOTTOM;
    }

    static BlockPos anchorPos(BlockState state, BlockPos pos) {
        BlockPos p = state.getValue(ROW) == Row.TOP ? pos.below() : pos;
        return state.getValue(COLUMN) == Column.RIGHT ? p.relative(rightOf(state.getValue(FACING)).getOpposite()) : p;
    }

    /** In creative mode breaking any block of it does not also drop the board. */
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

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ModItems.MAGICIAN_TOKEN.get())) {
            int emeralds = ThiefConfig.TOKEN_EMERALDS.get();
            held.shrink(1);        // paid for, and used up: in creative mode too, or the one badge would be emeralds without end
            ThiefEntity.give(player, new ItemStack(Items.EMERALD, emeralds));
            player.displayClientMessage(Component.translatable("message.thief.bounty_token", emeralds), false);
            return InteractionResult.CONSUME;
        }
        List<ThiefEntity> led = level.getEntitiesOfClass(ThiefEntity.class, new AABB(pos).inflate(7.0D), t -> t.tiedUp() && t.getLeashHolder() == player);
        if (led.isEmpty()) {
            // nothing to hand in: say how it works, with this world's amounts
            player.displayClientMessage(Component.translatable("message.thief.board_help_title"), false);
            player.displayClientMessage(Component.translatable("message.thief.board_help_how"), false);
            player.displayClientMessage(Component.translatable("message.thief.board_help_pay", ThiefConfig.BOUNTY_EMERALDS.get(), ThiefConfig.BOUNTY_EMERALDS.get() + ThiefConfig.BOUNTY_CREW_BONUS.get(),
                    ThiefConfig.BOUNTY_MAGICIAN.get()), false);
            player.displayClientMessage(Component.translatable("message.thief.board_help_more", ThiefConfig.TOKEN_EMERALDS.get()), false);
            player.displayClientMessage(Component.translatable("message.thief.board_help_tips"), false);
            return InteractionResult.CONSUME;
        }
        ThiefEntity thief = led.get(0);
        int emeralds = thief.isMagician() ? ThiefConfig.BOUNTY_MAGICIAN.get() : ThiefConfig.BOUNTY_EMERALDS.get() + (thief.crewId() != null ? ThiefConfig.BOUNTY_CREW_BONUS.get() : 0);
        boolean badge = thief.isMagician();
        thief.handIn(player, emeralds);
        player.displayClientMessage(Component.translatable(badge ? "message.thief.bounty_magician" : "message.thief.bounty", emeralds), false);
        return InteractionResult.CONSUME;
    }
}
