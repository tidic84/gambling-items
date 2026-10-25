package dev.gamblingitems.fabric.arcade;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A neon tube, set on any face like an end rod, glowing in one of the sixteen dye colours; a dye recolours it. */
public final class NeonBlock extends Block {
    //#if MC >= 1.20.3 && MC < 26.3
    public static final MapCodec<NeonBlock> CODEC = simpleCodec(NeonBlock::new);
    //#endif
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    private static final VoxelShape UP = Block.box(6.5, 0, 6.5, 9.5, 16, 9.5);
    private static final VoxelShape NORTH = Block.box(6.5, 6.5, 0, 9.5, 9.5, 16);
    private static final VoxelShape EAST = Block.box(0, 6.5, 6.5, 16, 9.5, 9.5);

    public NeonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(COLOR, DyeColor.PINK));
    }

    //#if MC >= 1.20.3 && MC < 26.3
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    //#endif

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, COLOR); }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING).getAxis()) {
            case Y -> UP;
            case Z -> NORTH;
            case X -> EAST;
        };
    }

    //#if MC >= 1.20.5
    //#if MC >= 1.21.2
    //$ @Override protected net.minecraft.world.InteractionResult useItemOn(
    //#else
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(
    //#endif
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dye) || state.getValue(COLOR) == dye.getDyeColor()) {
            //#if MC >= 1.21.2
            //$ return net.minecraft.world.InteractionResult.PASS;
            //#else
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            //#endif
        }
        recolour(level, pos, state, dye.getDyeColor(), player, stack);
        //#if MC >= 1.21.2
        //$ return dev.gamblingitems.fabric.Compat.handled(level);
        //#else
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        //#endif
    }
    //#else
    //$ @Override public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
    //$         InteractionHand hand, BlockHitResult hit) {
    //$     ItemStack stack = player.getItemInHand(hand);
    //$     if (!(stack.getItem() instanceof DyeItem dye) || state.getValue(COLOR) == dye.getDyeColor()) return net.minecraft.world.InteractionResult.PASS;
    //$     recolour(level, pos, state, dye.getDyeColor(), player, stack);
    //$     return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    //$ }
    //#endif

    private static void recolour(Level level, BlockPos pos, BlockState state, DyeColor colour, Player player, ItemStack stack) {
        if (level.isClientSide) return;
        level.setBlock(pos, state.setValue(COLOR, colour), 3);
        if (!player.getAbilities().instabuild) stack.shrink(1);
    }
}
