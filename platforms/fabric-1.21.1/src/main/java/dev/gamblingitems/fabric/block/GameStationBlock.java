package dev.gamblingitems.fabric.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.menu.GameMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
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

/** One placed machine per game. Its screen shows the running draw to nearby players. */
public final class GameStationBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<GameStationBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("mode").forGetter(block -> block.mode().id()),
            propertiesCodec()).apply(instance, (mode, properties) ->
                    new GameStationBlock(GameMode.fromId(mode), properties)));

    private final GameMode mode;

    public GameStationBlock(GameMode mode, Properties properties) {
        super(properties);
        this.mode = mode;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public GameMode mode() { return mode; }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GameStationEntity(pos, state); }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            GameMenus.open(serverPlayer, mode, ContainerLevelAccess.create(level, pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
