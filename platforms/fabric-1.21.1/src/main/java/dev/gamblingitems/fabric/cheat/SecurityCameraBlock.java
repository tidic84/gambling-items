package dev.gamblingitems.fabric.cheat;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A camera watching the tables. Every camera within sight of a player makes each of their tricks
 * more likely to be spotted; it watches all around, whichever way it is turned.
 */
public final class SecurityCameraBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    //#if MC >= 1.20.3 && MC < 26.3
    public static final MapCodec<SecurityCameraBlock> CODEC = simpleCodec(SecurityCameraBlock::new);
    //#endif
    private static final VoxelShape SHAPE = Block.box(4, 4, 4, 12, 12, 12);

    public SecurityCameraBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    //#if MC >= 1.20.3 && MC < 26.3
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    //#endif

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The lens looks at whoever placed it, towards the room.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
