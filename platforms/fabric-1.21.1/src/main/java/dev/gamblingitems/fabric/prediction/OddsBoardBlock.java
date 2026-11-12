package dev.gamblingitems.fabric.prediction;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//#if MC >= 1.20.5 && MC < 1.21.2
import net.minecraft.world.ItemInteractionResult;
//#endif
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The odds board: a panel hung on a wall that shows the busiest questions of the prediction market
 * and their prices to everyone in the room. Using it opens the market.
 */
public final class OddsBoardBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    //#if MC >= 1.20.3 && MC < 26.3
    public static final MapCodec<OddsBoardBlock> CODEC = simpleCodec(OddsBoardBlock::new);
    //#endif

    public OddsBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    //#if MC >= 1.20.3 && MC < 26.3
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    //#endif

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new OddsBoardEntity(pos, state); }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    /** A thin panel against the wall behind it, its face towards whoever placed it. */
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> Block.box(0, 0, 14, 16, 16, 16);
            case SOUTH -> Block.box(0, 0, 0, 16, 16, 2);
            case WEST -> Block.box(14, 0, 0, 16, 16, 16);
            default -> Block.box(0, 0, 0, 2, 16, 16);
        };
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, dev.gamblingitems.fabric.ModContent.ODDS_BOARD_ENTITY,
                (tickLevel, pos, tickState, board) -> board.serverTick());
    }

    //#if MC >= 1.20.5
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        interact(level, pos, player);
        return dev.gamblingitems.fabric.Compat.handled(level);
    }

    //#if MC >= 1.21.2
    //$ @Override protected InteractionResult useItemOn(
    //#else
    @Override protected ItemInteractionResult useItemOn(
    //#endif
            ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand == InteractionHand.MAIN_HAND) interact(level, pos, player);
        //#if MC >= 1.21.2
        //$ return dev.gamblingitems.fabric.Compat.handled(level);
        //#else
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
        //#endif
    }
    //#else
    //$ @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
    //$         InteractionHand hand, BlockHitResult hit) {
    //$     if (hand == InteractionHand.MAIN_HAND) interact(level, pos, player);
    //$     return InteractionResult.sidedSuccess(level.isClientSide);
    //$ }
    //#endif

    private static void interact(Level level, BlockPos pos, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            PredictionMenus.open(serverPlayer, ContainerLevelAccess.create(level, pos));
        }
    }
}
