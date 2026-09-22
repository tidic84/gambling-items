package dev.gamblingitems.fabric.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.menu.GameMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A game played on a real table: six blocks, three wide and two deep, laid out in front of the
 * player who places it. The felt is drawn across the whole surface, so the layout of a roulette
 * or the mat of a blackjack has the room a real one needs.
 *
 * <p>The block a player stands at is the middle of the front row; it carries the game, and the
 * five others point back to it. Right-clicking any of them sits the player down at that game.
 */
public final class GameTableBlock extends BaseEntityBlock implements GameSurface {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Which of the six blocks this is: the front row is 0 to 2, the back row 3 to 5. */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 5);
    /** The game is played on the middle block of the front row. */
    public static final int ANCHOR_PART = 1;
    /** A table is waist high, so players can see the felt from where they stand. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 13, 16);
    public static final MapCodec<GameTableBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("mode").forGetter(block -> block.mode().id()),
            propertiesCodec()).apply(instance, (mode, properties) ->
                    new GameTableBlock(GameMode.fromId(mode), properties)));

    private final GameMode mode;

    public GameTableBlock(GameMode mode, Properties properties) {
        super(properties);
        this.mode = mode;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, ANCHOR_PART));
    }

    @Override public GameMode mode() { return mode; }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Where one block of the table sits, counted from the block the player stands at. */
    public static BlockPos partPos(BlockPos anchor, Direction facing, int part) {
        return anchor.relative(facing.getCounterClockWise(), part % 3 - 1)
                .relative(facing.getOpposite(), part / 3);
    }

    /** The block that carries the game, whichever part of the table was touched. */
    public static BlockPos anchor(BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        Direction facing = state.getValue(FACING);
        return pos.relative(facing.getCounterClockWise(), 1 - part % 3)
                .relative(facing, part / 3);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                                      LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) return;
        for (int part = 0; part < 6; part++) {
            if (part == ANCHOR_PART) continue;
            level.setBlock(partPos(pos, state.getValue(FACING), part), state.setValue(PART, part), 3);
        }
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        if (state.is(next.getBlock()) || level.isClientSide) return;
        BlockPos root = anchor(pos, state);
        for (int part = 0; part < 6; part++) {
            BlockPos target = partPos(root, state.getValue(FACING), part);
            BlockState other = level.getBlockState(target);
            if (!target.equals(pos) && other.is(this) && other.getValue(PART) == part
                    && other.getValue(FACING) == state.getValue(FACING)) {
                level.removeBlock(target, false);
            }
        }
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                            CollisionContext context) {
        return SHAPE;
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                     CollisionContext context) {
        return SHAPE;
    }

    /** Only the block the player stands at carries the game; the others are just table. */
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == ANCHOR_PART ? new GameStationEntity(pos, state) : null;
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        sitDown(state, level, pos, player, hit);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                        BlockPos pos, Player player, InteractionHand hand,
                                                        BlockHitResult hit) {
        if (hand == InteractionHand.MAIN_HAND) sitDown(state, level, pos, player, hit);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * A table is played on where it is touched: the controls printed along the near edge answer
     * like the panel of a station, and on a roulette every printed area is its own bet. A click
     * that lands on none of them opens the full window, as it always did.
     */
    private void sitDown(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        BlockPos root = anchor(pos, state);
        Direction facing = state.getValue(FACING);
        if (level.getBlockEntity(root) instanceof GameStationEntity table) {
            int button = TableLayout.button(mode, facing, hit, root);
            if (button >= 0) {
                StationInteractions.click(serverPlayer, table, button);
                return;
            }
            float[] point = TableLayout.felt(facing, hit, root);
            if (mode == GameMode.ROULETTE && point != null) {
                var bet = TableLayout.betAt(point[0], point[1]);
                if (bet != null) {
                    StationInteractions.bet(serverPlayer, table, bet);
                    return;
                }
            }
        }
        GameMenus.open(serverPlayer, mode, ContainerLevelAccess.create(level, root));
    }
}
