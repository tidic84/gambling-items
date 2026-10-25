package dev.gamblingitems.fabric.arcade;

import com.mojang.serialization.MapCodec;
import dev.gamblingitems.fabric.config.ModConfig;
import dev.gamblingitems.fabric.menu.Opening;
import dev.gamblingitems.fabric.platform.Platform;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The cashier's counter: chips for items and back, and the prize counter of the arcade tickets. */
public final class CashierBlock extends HorizontalDirectionalBlock {
    //#if MC >= 1.20.3 && MC < 26.3
    public static final MapCodec<CashierBlock> CODEC = simpleCodec(CashierBlock::new);
    //#endif
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 15, 16);

    public CashierBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    //#if MC >= 1.20.3 && MC < 26.3
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    //#endif

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    //#if MC >= 1.20.5
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server) open(server, pos);
        return dev.gamblingitems.fabric.Compat.handled(level);
    }
    //#else
    //$ @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
    //$         net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
    //$     if (player instanceof ServerPlayer server) open(server, pos);
    //$     return InteractionResult.sidedSuccess(level.isClientSide);
    //$ }
    //#endif

    /** Opens the cashier's window for that player, with their own tray and their last plays. */
    public static void open(ServerPlayer player, BlockPos pos) {
        if (player.isSpectator()) return;
        var ledger = Arcade.ledger(player);
        CashierSetup setup = new CashierSetup(ModConfig.values(), ModConfig.arcade(),
                ledger.history(player.getUUID(), player.level().getGameTime()));
        ContainerLevelAccess access = ContainerLevelAccess.create(player.level(), pos);
        Platform.openMenu(player, Component.translatable("block.gamblingitems.cashier"), new Opening<>(setup, false),
                Opening.codec(CashierSetup.CODEC),
                (id, inventory, ignored) -> new CashierMenu(id, inventory, setup,
                        PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.CASHIER), access));
    }
}
