package dev.gamblingitems.fabric.roulette;

import dev.gamblingitems.core.roulette.RouletteWheel;
import dev.gamblingitems.core.roulette.RouletteWheel.Bet;
import dev.gamblingitems.core.roulette.RouletteWheel.BetType;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class RouletteGameTests implements FabricGameTest {
    private static final int BETTING_TICKS = 40, SPIN_TICKS = 20, RESULT_TICKS = 20;
    private static final long IRON = 1_000;
    /** Draws are uniform over the pockets: these land the ball on a chosen number. */
    private static final long DRAW_ZERO = drawFor(0);
    private static final long DRAW_RED_ONE = drawFor(1);
    private static final long DRAW_BLACK_TWO = drawFor(2);

    private static long drawFor(int number) {
        return (long) RouletteWheel.pocketOf(number) * RouletteGame.DRAW_BOUND / RouletteWheel.POCKETS + 5;
    }

    private static ValueCatalog catalog() {
        return new ValueCatalog(List.of(
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("copper_ingot"), 100),
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("iron_ingot"), IRON),
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("diamond"), 10_000)));
    }

    private static RouletteSetup setup() {
        return new RouletteSetup(catalog(),
                new RouletteSettings(IRON, BETTING_TICKS, SPIN_TICKS, RESULT_TICKS));
    }

    private static RouletteGame game(GameTestHelper helper, long draw) {
        return new RouletteGame(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), setup(), () -> draw);
    }

    private static Container chips(RouletteGame game, UUID player, ItemStack stack) {
        Container vault = game.vault(player);
        for (int slot = 0; slot < RouletteSettings.VAULT_SIZE; slot++) vault.setItem(slot, ItemStack.EMPTY);
        vault.setItem(RouletteSettings.INPUT_SLOT, stack);
        return vault;
    }

    private static void prepare(RouletteGame game, UUID player, ItemStack stack) {
        game.vault(player).setItem(RouletteSettings.INPUT_SLOT, stack);
    }

    private static long winnings(RouletteGame game, UUID player) {
        return game.winnings(game.vault(player));
    }

    private static void tick(RouletteGame game, int times) {
        for (int index = 0; index < times; index++) game.tick();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void anyPricedItemsCanBePlacedOnAnAreaOfTheTable(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        RouletteGame game = game(helper, DRAW_RED_ONE);
        UUID uuid = player.getUUID();
        Container vault = chips(game, uuid, new ItemStack(Items.IRON_INGOT, 3));
        vault.setItem(RouletteSettings.INPUT_SLOT + 1, new ItemStack(Items.COPPER_INGOT, 5));
        helper.assertTrue(game.stagedValue(uuid) == 3 * IRON + 500, "Chips are counted by value");
        helper.assertTrue(game.place(uuid, new Bet(BetType.RED, 0), game.stagedValue(uuid)), "The bet is accepted");
        helper.assertTrue(game.seatOf(uuid).total() == 3_500, "The whole prepared value is on the felt");
        helper.assertTrue(game.stagedValue(uuid) == 0, "The chips left the preparation row");
        helper.assertFalse(vault.getItem(RouletteSettings.ENGAGED_SLOT).isEmpty(), "They wait on the table");
        helper.assertTrue(game.pot() == 3_500 && game.participants() == 1, "The table is public");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void severalAreasCanBeCoveredAndEachIsPaidOnItsOwn(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        RouletteGame game = game(helper, DRAW_RED_ONE);
        UUID uuid = player.getUUID();
        chips(game, uuid, new ItemStack(Items.IRON_INGOT, 2));
        helper.assertTrue(game.place(uuid, new Bet(BetType.RED, 0), 2 * IRON), "Red covered");
        prepare(game, uuid, new ItemStack(Items.IRON_INGOT, 1));
        helper.assertTrue(game.place(uuid, new Bet(BetType.STRAIGHT, 1), IRON), "The number one covered too");
        prepare(game, uuid, new ItemStack(Items.IRON_INGOT, 1));
        helper.assertTrue(game.place(uuid, new Bet(BetType.BLACK, 0), IRON), "And black, which will lose");
        helper.assertTrue(game.seatOf(uuid).total() == 4 * IRON, "Everything is on the felt");
        tick(game, BETTING_TICKS + SPIN_TICKS);
        helper.assertTrue(game.lastResultNumber() == 1, "The ball landed on one");
        // Red pays twice two ingots, the number pays thirty six times one, black pays nothing.
        helper.assertTrue(game.seatOf(uuid).paid() == 2 * 2 * IRON + 36 * IRON,
                "Each area is settled on its own");
        helper.assertTrue(winnings(game, uuid) == 40 * IRON, "The winnings hold what was paid");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void zeroTakesEveryOutsideBet(GameTestHelper helper) {
        ServerPlayer outside = helper.makeMockServerPlayerInLevel();
        ServerPlayer onZero = helper.makeMockServerPlayerInLevel();
        RouletteGame game = game(helper, DRAW_ZERO);
        UUID loser = outside.getUUID(), winner = onZero.getUUID();
        chips(game, loser, new ItemStack(Items.IRON_INGOT, 2));
        chips(game, winner, new ItemStack(Items.IRON_INGOT, 1));
        helper.assertTrue(game.place(loser, new Bet(BetType.EVEN, 0), 2 * IRON), "An outside bet");
        helper.assertTrue(game.place(winner, new Bet(BetType.STRAIGHT, 0), IRON), "A bet on zero");
        tick(game, BETTING_TICKS + SPIN_TICKS);
        helper.assertTrue(game.lastResultNumber() == 0, "The ball landed on zero");
        helper.assertTrue(game.seatOf(loser).paid() == 0, "Even loses on zero");
        helper.assertTrue(winnings(game, loser) == 0, "Nothing is paid to it");
        helper.assertTrue(game.seatOf(winner).paid() == 36 * IRON, "Zero pays like any number");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void betsAreRefusedOutsideTheWindowAndUnderTheMinimum(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        RouletteGame game = game(helper, DRAW_BLACK_TWO);
        UUID uuid = player.getUUID();
        Container vault = chips(game, uuid, new ItemStack(Items.COPPER_INGOT, 5));
        helper.assertFalse(game.place(uuid, new Bet(BetType.RED, 0), 500), "Below the smallest bet");
        helper.assertFalse(game.place(uuid, null, 500), "A bet needs an area");
        vault.setItem(RouletteSettings.INPUT_SLOT, new ItemStack(Items.DIRT, 5));
        helper.assertTrue(game.stagedValue(uuid) == 0, "An unpriced item is worth nothing");
        helper.assertFalse(game.place(uuid, new Bet(BetType.RED, 0), 0), "So it cannot be played");
        vault.setItem(RouletteSettings.INPUT_SLOT, new ItemStack(Items.IRON_INGOT, 2));
        helper.assertTrue(game.place(uuid, new Bet(BetType.RED, 0), 2 * IRON), "A valid bet is accepted");
        tick(game, BETTING_TICKS);
        helper.assertTrue(game.phase() == RouletteGame.Phase.SPINNING, "The wheel is spinning");
        ServerPlayer late = helper.makeMockServerPlayerInLevel();
        chips(game, late.getUUID(), new ItemStack(Items.IRON_INGOT, 2));
        helper.assertFalse(game.place(late.getUUID(), new Bet(BetType.RED, 0), 2 * IRON),
                "A locked wheel accepts no new bet");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aSeatIsRefusedWhatItCouldNotBePaid(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        RouletteGame game = game(helper, DRAW_RED_ONE);
        UUID uuid = player.getUUID();
        Container vault = chips(game, uuid, new ItemStack(Items.IRON_INGOT, 2));
        for (int slot = RouletteSettings.FIRST_PAYOUT_SLOT; slot < RouletteSettings.VAULT_SIZE; slot++) {
            vault.setItem(slot, new ItemStack(Items.DIAMOND, 64));
        }
        helper.assertFalse(game.place(uuid, new Bet(BetType.STRAIGHT, 1), 2 * IRON),
                "A win that could not be handed over is refused before the bet");
        helper.assertTrue(game.stagedValue(uuid) == 2 * IRON, "Nothing is engaged");
        for (int slot = RouletteSettings.FIRST_PAYOUT_SLOT; slot < RouletteSettings.VAULT_SIZE; slot++) {
            vault.setItem(slot, ItemStack.EMPTY);
        }
        helper.assertTrue(game.place(uuid, new Bet(BetType.STRAIGHT, 1), 2 * IRON), "With room, it is accepted");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void anInterruptedRoundGivesEveryChipBackOnce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        RouletteGame game = game(helper, DRAW_RED_ONE);
        UUID uuid = player.getUUID();
        Container vault = chips(game, uuid, new ItemStack(Items.IRON_INGOT, 4));
        helper.assertTrue(game.place(uuid, new Bet(BetType.RED, 0), 4 * IRON), "Bet accepted");
        tick(game, 10);
        game.cancel();
        helper.assertTrue(game.stagedValue(uuid) == 4 * IRON, "The chips come back where they were prepared");
        helper.assertTrue(vault.getItem(RouletteSettings.ENGAGED_SLOT).isEmpty(), "Nothing stays on the table");
        game.cancel();
        helper.assertTrue(game.stagedValue(uuid) == 4 * IRON, "Cancelling twice refunds once");
        helper.assertTrue(game.phase() == RouletteGame.Phase.WAITING, "The table is free again");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theRoundOutlivesItsStation(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModContent.ROULETTE_STATION);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        RouletteGame game = game(helper, DRAW_RED_ONE);
        UUID uuid = player.getUUID();
        chips(game, uuid, new ItemStack(Items.IRON_INGOT, 3));
        helper.assertTrue(game.place(uuid, new Bet(BetType.RED, 0), 3 * IRON), "Bet accepted at the station");
        tick(game, BETTING_TICKS);
        helper.setBlock(relative, Blocks.AIR);
        // Breaking the station releases nothing: the spin belongs to the server.
        tick(game, SPIN_TICKS);
        helper.assertTrue(game.phase() == RouletteGame.Phase.RESULT, "The round settled anyway");
        helper.assertTrue(winnings(game, uuid) == 6 * IRON, "Red paid twice the stake");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aTerminalOnlyJoinsAWheelAnnouncedNearby(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModContent.ROULETTE_STATION);
        BlockPos pos = helper.absolutePos(relative);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        try {
            helper.assertTrue(RouletteGames.nearest(player) == null, "A terminal never opens a wheel on its own");
            RouletteGame hosted = RouletteGames.host(helper.getLevel(), pos);
            helper.assertTrue(RouletteGames.host(helper.getLevel(), pos) == hosted, "One wheel per station");
            helper.assertTrue(RouletteGames.nearest(player) == hosted, "A nearby wheel is joinable");
            player.setPos(pos.getX() + RouletteGames.REACH + 8, pos.getY() + 1, pos.getZ() + 0.5);
            helper.assertTrue(RouletteGames.nearest(player) == null, "A distant wheel is not");
        } finally {
            RouletteGames.clear();
        }
        helper.succeed();
    }
}
