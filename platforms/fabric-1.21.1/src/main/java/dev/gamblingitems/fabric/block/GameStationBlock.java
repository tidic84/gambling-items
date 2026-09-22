package dev.gamblingitems.fabric.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gamblingitems.core.GameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One placed machine per game. Its screen shows the running draw to nearby players. */
public final class GameStationBlock extends BaseEntityBlock implements GameSurface {
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty PART = net.minecraft.world.level.block.state.properties.IntegerProperty.create("part", 0, 5);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<GameStationBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("mode").forGetter(block -> block.mode().id()),
            propertiesCodec()).apply(instance, (mode, properties) ->
                    new GameStationBlock(GameMode.fromId(mode), properties)));

    private final GameMode mode;

    public GameStationBlock(GameMode mode, Properties properties) {
        super(properties);
        this.mode = mode;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 1));
    }

    @Override public GameMode mode() { return mode; }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        for (int part = 0; part < 6; part++) {
            BlockPos target = partPos(context.getClickedPos(), facing, part);
            if (context.getLevel().isOutsideBuildHeight(target) || !context.getLevel().getWorldBorder().isWithinBounds(target)
                    || !context.getLevel().getBlockState(target).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return state.getValue(PART) == 1 ? new GameStationEntity(pos, state) : null; }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> Block.box(0, 0, 0, 16, 16, 4);
            case SOUTH -> Block.box(0, 0, 12, 16, 16, 16);
            case WEST -> Block.box(0, 0, 0, 4, 16, 16);
            default -> Block.box(12, 0, 0, 16, 16, 16);
        };
    }

    public static BlockPos partPos(BlockPos anchor, Direction facing, int part) {
        return anchor.relative(facing.getCounterClockWise(), part % 3 - 1).above(part / 3);
    }

    public static BlockPos anchor(BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        return pos.relative(state.getValue(FACING).getCounterClockWise(), 1 - part % 3).below(part / 3);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) return;
        for (int part = 0; part < 6; part++) if (part != 1)
            level.setBlock(partPos(pos, state.getValue(FACING), part), state.setValue(PART, part), 3);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        if (state.is(next.getBlock()) || level.isClientSide) return;
        BlockPos root = anchor(pos, state);
        for (int part = 0; part < 6; part++) {
            BlockPos target = partPos(root, state.getValue(FACING), part);
            BlockState other = level.getBlockState(target);
            if (!target.equals(pos) && other.is(this) && other.getValue(PART) == part
                    && other.getValue(FACING) == state.getValue(FACING)) level.removeBlock(target, false);
        }
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        interact(state, level, pos, player, hit);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand == InteractionHand.MAIN_HAND) interact(state, level, pos, player, hit);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private void interact(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(anchor(pos, state)) instanceof GameStationEntity station) {
            StationInteractions.click(serverPlayer, station, StationPanel.button(mode, state.getValue(FACING), hit, anchor(pos, state)));
        }
    }
}
