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
 * A slot machine cabinet: one block wide and two blocks and a half high, standing on the floor.
 * The reels are drawn on its front, at the height of the eyes of whoever stands at it, and a pull
 * is played out there for everyone to see.
 *
 * <p>The bottom block carries the game, the middle one holds the reels and the top one is the
 * arched roof, half a block high. Right-clicking any of them sits the player down at the machine.
 */
public final class SlotMachineBlock extends BaseEntityBlock implements GameSurface {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Which of the three blocks this is: 0 the cabinet, 1 the reels, 2 the roof above them. */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);
    /** How many blocks a cabinet occupies; the last of them is only half full. */
    public static final int PARTS = 3;
    /** The game is played on the block a player stands at. */
    public static final int ANCHOR_PART = 0;
    /** The cabinet fills its blocks: its rails and its roof stand a little past them. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    /** The roof is half a block high, so a cabinet stands two blocks and a half. */
    private static final VoxelShape ROOF = Block.box(0, 0, 0, 16, 8, 16);
    public static final MapCodec<SlotMachineBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("mode").forGetter(block -> block.mode().id()),
            propertiesCodec()).apply(instance, (mode, properties) ->
                    new SlotMachineBlock(GameMode.fromId(mode), properties)));

    private final GameMode mode;

    public SlotMachineBlock(GameMode mode, Properties properties) {
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
        for (int part = 1; part < PARTS; part++) {
            BlockPos above = context.getClickedPos().above(part);
            if (context.getLevel().isOutsideBuildHeight(above)
                    || !context.getLevel().getBlockState(above).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Where one block of the cabinet sits, counted from the block the player stands at. */
    public static BlockPos partPos(BlockPos anchor, int part) { return anchor.above(part); }

    /** The block that carries the game, whichever half of the cabinet was touched. */
    public static BlockPos anchor(BlockPos pos, BlockState state) {
        return pos.below(state.getValue(PART));
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                                      LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) return;
        for (int part = 1; part < PARTS; part++) {
            level.setBlock(partPos(pos, part), state.setValue(PART, part), 3);
        }
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        if (state.is(next.getBlock()) || level.isClientSide) return;
        BlockPos root = anchor(pos, state);
        for (int part = 0; part < PARTS; part++) {
            BlockPos target = partPos(root, part);
            BlockState other = level.getBlockState(target);
            if (!target.equals(pos) && other.is(this) && other.getValue(PART) == part
                    && other.getValue(FACING) == state.getValue(FACING)) {
                level.removeBlock(target, false);
            }
        }
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                            CollisionContext context) {
        return state.getValue(PART) == PARTS - 1 ? ROOF : SHAPE;
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                     CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    /** Only the block the player stands at carries the game; the marquee is just cabinet. */
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == ANCHOR_PART ? new GameStationEntity(pos, state) : null;
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        play(state, level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                        BlockPos pos, Player player, InteractionHand hand,
                                                        BlockHitResult hit) {
        if (hand == InteractionHand.MAIN_HAND) play(state, level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private void play(BlockState state, Level level, BlockPos pos, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        BlockPos root = anchor(pos, state);
        GameMenus.open(serverPlayer, mode, ContainerLevelAccess.create(level, root));
    }
}
