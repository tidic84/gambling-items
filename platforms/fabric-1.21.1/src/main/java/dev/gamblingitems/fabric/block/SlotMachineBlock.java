package dev.gamblingitems.fabric.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.machines.CabinetControls;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//#if MC >= 1.20.5 && MC < 1.21.2
import net.minecraft.world.ItemInteractionResult;
//#endif
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
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
 * arched roof, half a block high. A cabinet has no window: it is played at its lever, crank or
 * buttons, there and then (see {@link CabinetPlay}); anywhere else a click tells how, and a
 * sneaking click reads its rules out.
 *
 * <p>The giant machines stand two blocks wide: their right column is a second column of parts,
 * marked {@link #RIGHT}, the game still carried by the bottom left block.
 */
public final class SlotMachineBlock extends BaseEntityBlock implements GameSurface {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Which of the three blocks this is: 0 the cabinet, 1 the reels, 2 the roof above them. */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);
    /** True for the right column of a cabinet two blocks wide, seen from its front. */
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    /** How many blocks a cabinet occupies; the last of them is only half full. */
    public static final int PARTS = 3;
    /** The game is played on the block a player stands at. */
    public static final int ANCHOR_PART = 0;
    /** The cabinet fills its blocks: its rails and its roof stand a little past them. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    /** The roof is half a block high, so a cabinet stands two blocks and a half. */
    private static final VoxelShape ROOF = Block.box(0, 0, 0, 16, 8, 16);
    // Blocks declare a codec from 1.20.3 to 26.2.
    //#if MC >= 1.20.3 && MC < 26.3
    public static final MapCodec<SlotMachineBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("mode").forGetter(block -> block.mode().id()),
            propertiesCodec()).apply(instance, (mode, properties) ->
                    new SlotMachineBlock(GameMode.fromId(mode), properties)));
    //#endif

    private final GameMode mode;

    public SlotMachineBlock(GameMode mode, Properties properties) {
        super(properties);
        this.mode = mode;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, ANCHOR_PART)
                .setValue(RIGHT, false));
    }

    /** How many blocks wide this cabinet stands. */
    public int width() { return CabinetControls.width(mode); }

    @Override public GameMode mode() { return mode; }

    //#if MC >= 1.20.3 && MC < 26.3
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    //#endif

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, RIGHT);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        for (int column = 0; column < width(); column++) {
            for (int part = 0; part < PARTS; part++) {
                if (column == 0 && part == 0) continue;
                BlockPos target = partPos(context.getClickedPos(), facing, part, column == 1);
                if (context.getLevel().isOutsideBuildHeight(target)
                        || !context.getLevel().getBlockState(target).canBeReplaced(context)) {
                    return null;
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    /** Where one block of the cabinet sits, counted from the block the player stands at. */
    public static BlockPos partPos(BlockPos anchor, int part) { return anchor.above(part); }

    /** Where one block of a cabinet facing that way sits; the right column is on the player's right. */
    public static BlockPos partPos(BlockPos anchor, Direction facing, int part, boolean right) {
        return right ? anchor.above(part).relative(facing.getCounterClockWise()) : anchor.above(part);
    }

    /** The block that carries the game, whichever block of the cabinet was touched. */
    public static BlockPos anchor(BlockPos pos, BlockState state) {
        BlockPos below = pos.below(state.getValue(PART));
        return state.getValue(RIGHT) ? below.relative(state.getValue(FACING).getClockWise()) : below;
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                                      LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) return;
        Direction facing = state.getValue(FACING);
        for (int column = 0; column < width(); column++) {
            for (int part = 0; part < PARTS; part++) {
                if (column == 0 && part == 0) continue;
                level.setBlock(partPos(pos, facing, part, column == 1), state.setValue(PART, part).setValue(RIGHT, column == 1), 3);
            }
        }
        // A prize machine shows its prizes from the start, before anyone has played it.
        if (mode != GameMode.SLOT_MACHINE) dev.gamblingitems.fabric.machines.MachineMenu.publishTable(
                ContainerLevelAccess.create(level, pos), dev.gamblingitems.fabric.config.ModConfig.machine(mode).settings().table());
    }

    // Breaking any part of the furniture removes the others.
    //#if MC >= 1.21.5
    //$ @Override protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean moving) {
    //$     super.affectNeighborsAfterRemoval(state, level, pos, moving);
    //#else
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        if (state.is(next.getBlock()) || level.isClientSide) return;
    //#endif
        BlockPos root = anchor(pos, state);
        for (int column = 0; column < width(); column++) {
            for (int part = 0; part < PARTS; part++) {
                BlockPos target = partPos(root, state.getValue(FACING), part, column == 1);
                BlockState other = level.getBlockState(target);
                if (!target.equals(pos) && other.is(this) && other.getValue(PART) == part
                        && other.getValue(RIGHT) == (column == 1) && other.getValue(FACING) == state.getValue(FACING)) {
                    level.removeBlock(target, false);
                }
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
        return state.getValue(PART) == ANCHOR_PART && !state.getValue(RIGHT) ? new GameStationEntity(pos, state) : null;
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    //#if MC >= 1.20.5
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        play(state, level, pos, player, hit);
        return dev.gamblingitems.fabric.Compat.handled(level);
    }

    //#if MC >= 1.21.2
    //$ @Override protected InteractionResult useItemOn(
    //#else
    @Override protected ItemInteractionResult useItemOn(
    //#endif
            ItemStack stack, BlockState state, Level level,
                                                        BlockPos pos, Player player, InteractionHand hand,
                                                        BlockHitResult hit) {
        if (hand == InteractionHand.MAIN_HAND) play(state, level, pos, player, hit);
        //#if MC >= 1.21.2
        //$ return dev.gamblingitems.fabric.Compat.handled(level);
        //#else
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
        //#endif
    }
    //#else
    //$ // Before 1.20.5 one method answered a click, with or without an item in hand.
    //$ @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
    //$         InteractionHand hand, BlockHitResult hit) {
    //$     if (hand == InteractionHand.MAIN_HAND) play(state, level, pos, player, hit);
    //$     return InteractionResult.sidedSuccess(level.isClientSide);
    //$ }
    //#endif

    /** A click on the lever, crank or buttons plays the machine; anywhere else it tells how, or its rules. */
    private void play(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        BlockPos root = anchor(pos, state);
        if (CabinetPlay.use(serverPlayer, mode, root, CabinetPlay.control(mode, state, root, hit.getLocation()))) return;
        CabinetPlay.explain(serverPlayer, mode, root, player.isShiftKeyDown());
    }
}
