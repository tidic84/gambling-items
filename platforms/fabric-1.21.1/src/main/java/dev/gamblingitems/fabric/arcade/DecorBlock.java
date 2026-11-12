package dev.gamblingitems.fabric.arcade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.Compat;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.vault.ArcadeLedger;
import java.math.BigDecimal;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
 * The arcade's decor that the renderer draws: the leaderboard on its wall, with the week's best
 * wins, and the croupier standing behind a table. Both keep a block entity only to be drawn.
 *
 * <p>Clicking the leaderboard writes the player's own last plays in the chat.
 */
public final class DecorBlock extends BaseEntityBlock {
    public enum Kind { LEADERBOARD, CROUPIER }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    //#if MC >= 1.20.3 && MC < 26.3
    public static final MapCodec<DecorBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("kind").forGetter(block -> block.kind.name()),
            propertiesCodec()).apply(instance, (kind, properties) -> new DecorBlock(Kind.valueOf(kind), properties)));
    //#endif
    /** The leaderboard hangs flat against the wall behind it; the croupier stands a little narrower than a block. */
    private static final VoxelShape[] BOARD = {Block.box(0, 0, 0, 16, 16, 2), Block.box(14, 0, 0, 16, 16, 16),
            Block.box(0, 0, 14, 16, 16, 16), Block.box(0, 0, 0, 2, 16, 16)};
    private static final VoxelShape CROUPIER = Block.box(3, 0, 3, 13, 16, 13);
    private static final VoxelShape CROUPIER_COLLISION = Block.box(3, 0, 3, 13, 24, 13);
    /** How often a leaderboard reads the week's wins again, in ticks. */
    private static final int REFRESH = 40;

    private final Kind kind;

    public DecorBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Kind kind() { return kind; }

    //#if MC >= 1.20.3 && MC < 26.3
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    //#endif

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // The plate is against the wall behind the board: facing north (2), the wall is to the south.
        return kind == Kind.LEADERBOARD ? BOARD[state.getValue(FACING).get2DDataValue()] : CROUPIER;
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return kind == Kind.LEADERBOARD ? getShape(state, level, pos, context) : CROUPIER_COLLISION;
    }

    /** The croupier is drawn whole by the renderer; the leaderboard's frame is a block model. */
    @Override protected RenderShape getRenderShape(BlockState state) {
        return kind == Kind.CROUPIER ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GameStationEntity(pos, state); }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || kind != Kind.LEADERBOARD || type != ModContent.STATION_ENTITY) return null;
        return (tickLevel, pos, tickState, entity) -> {
            if (tickLevel.getGameTime() % REFRESH == 0 && entity instanceof GameStationEntity board) refresh(board);
        };
    }

    /** Writes the week's best wins on the board: "name|mode|tickets|multiplier", one per line. */
    static void refresh(GameStationEntity board) {
        if (!(board.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        List<ArcadeLedger.Win> wins = dev.gamblingitems.fabric.vault.PlayerVaults.get(level.getServer()).arcade()
                .leaderboard(level.getGameTime());
        StringBuilder text = new StringBuilder();
        for (ArcadeLedger.Win win : wins) {
            if (text.length() > 0) text.append('\n');
            text.append(win.name()).append('|').append(win.mode()).append('|').append(win.tickets()).append('|').append(win.multiplier());
        }
        board.preview("", text.toString());
    }

    //#if MC >= 1.20.5
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server) use(server, pos);
        return Compat.handled(level);
    }
    //#else
    //$ @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
    //$         net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
    //$     if (player instanceof ServerPlayer server) use(server, pos);
    //$     return InteractionResult.sidedSuccess(level.isClientSide);
    //$ }
    //#endif

    /**
     * The board shows the player their last plays. The croupier changes chips and hands out prizes like the
     * cashier; asked while crouching, he explains the rules of the nearest game instead.
     */
    private void use(ServerPlayer player, BlockPos pos) {
        if (kind == Kind.LEADERBOARD) history(player);
        else if (player.isShiftKeyDown()) explain(player, pos);
        else CashierBlock.open(player, pos, Component.translatable("block.gamblingitems.croupier"));
    }

    /** The rules of the nearest table, station or cabinet within five blocks, as the croupier tells them. */
    private static void explain(ServerPlayer player, BlockPos croupier) {
        Level level = player.level();
        GameMode nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(croupier.offset(-5, -2, -5), croupier.offset(5, 2, 5))) {
            if (level.getBlockState(pos).getBlock() instanceof dev.gamblingitems.fabric.block.GameSurface surface
                    && pos.distSqr(croupier) < best) {
                best = pos.distSqr(croupier);
                nearest = surface.mode();
            }
        }
        if (nearest == null) {
            Compat.message(player, Component.translatable("gui.gamblingitems.croupier_nothing").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        Component game = Component.translatable("gui.gamblingitems.mode." + nearest.id());
        Compat.message(player, Component.translatable("gui.gamblingitems.croupier_explains", game).withStyle(ChatFormatting.GOLD), false);
        Compat.message(player, Component.translatable("gui.gamblingitems.rules." + nearest.id()), false);
    }

    /** The player's last plays, newest first, and their ticket balance. */
    private static void history(ServerPlayer player) {
        ArcadeLedger ledger = Arcade.ledger(player);
        long now = player.level().getGameTime();
        Compat.message(player, Component.translatable("gui.gamblingitems.history_title",
                ledger.balance(player.getUUID(), now)).withStyle(ChatFormatting.GOLD), false);
        List<ArcadeLedger.Play> plays = ledger.history(player.getUUID(), now);
        if (plays.isEmpty()) Compat.message(player, Component.translatable("gui.gamblingitems.history_empty").withStyle(ChatFormatting.GRAY), false);
        for (int index = 0; index < Math.min(10, plays.size()); index++) {
            ArcadeLedger.Play play = plays.get(index);
            Compat.message(player, Component.translatable("gui.gamblingitems.history_line",
                    Component.translatable("gui.gamblingitems.mode." + GameMode.fromId(play.mode()).id()),
                    dev.gamblingitems.fabric.block.CabinetPlay.amount(play.stake()),
                    BigDecimal.valueOf(play.multiplier(), 2).stripTrailingZeros().toPlainString() + "x",
                    play.tickets()).withStyle(play.tickets() > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        }
    }
}
