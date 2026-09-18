package dev.gamblingitems.fabric.roulette;

import dev.gamblingitems.core.roulette.RouletteRules.Colour;
import dev.gamblingitems.fabric.ModContent;
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
    /** The wheel holds fifteen slots: green first, then red and black alternating. */
    private static final long DRAW_GREEN = 0;
    private static final long DRAW_RED = drawForSlot(1);
    private static final long DRAW_BLACK = drawForSlot(2);

    private static long drawForSlot(int slot) {
        return slot * RouletteGame.DRAW_BOUND / 15 + 10;
    }

    private static RouletteSettings settings() {
        return new RouletteSettings(ResourceLocation.withDefaultNamespace("gold_ingot"), 1,
                7, 7, 1, 2, 14, BETTING_TICKS, SPIN_TICKS, RESULT_TICKS);
    }

    private static RouletteGame game(GameTestHelper helper, long draw) {
        return new RouletteGame(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                settings(), () -> draw);
    }

    private static Container staked(RouletteGame game, UUID player, int count) {
        Container vault = game.vault(player);
        for (int slot = 0; slot < RouletteSettings.VAULT_SIZE; slot++) vault.setItem(slot, ItemStack.EMPTY);
        vault.setItem(RouletteSettings.INPUT_SLOT, new ItemStack(Items.GOLD_INGOT, count));
        return vault;
    }

    private static int winnings(Container vault) {
        int total = 0;
        for (int slot = RouletteSettings.FIRST_PAYOUT_SLOT; slot < RouletteSettings.VAULT_SIZE; slot++) {
            total += vault.getItem(slot).getCount();
        }
        return total;
    }

    private static void tick(RouletteGame game, int times) {
        for (int index = 0; index < times; index++) game.tick();
    }

    private static void playRound(RouletteGame game) {
        tick(game, BETTING_TICKS + SPIN_TICKS);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aWinningColourPaysTheAnnouncedMultiplier(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        RouletteGame game = game(helper, DRAW_RED);
        UUID uuid = player.getUUID();
        Container vault = staked(game, uuid, 9);
        helper.assertTrue(game.place(uuid, Colour.RED, 6), "A bet inside the limits is accepted");
        helper.assertTrue(vault.getItem(RouletteSettings.INPUT_SLOT).getCount() == 3, "Only the stake is engaged");
        helper.assertTrue(vault.getItem(RouletteSettings.ENGAGED_SLOT).getCount() == 6, "The stake waits in the vault");
        helper.assertFalse(game.place(uuid, Colour.BLACK, 3), "One bet per player and per round");
        tick(game, BETTING_TICKS);
        helper.assertTrue(game.phase() == RouletteGame.Phase.SPINNING, "Bets close on their own");
        helper.assertTrue(settings().rules().colourAt(game.resultSlot()) == Colour.RED, "This draw lands on red");
        helper.assertTrue(winnings(vault) == 0, "Nothing is paid before the wheel stops");
        tick(game, SPIN_TICKS);
        helper.assertTrue(game.phase() == RouletteGame.Phase.RESULT, "The round settles itself");
        helper.assertTrue(winnings(vault) == 12, "Red pays twice the stake, stake included");
        helper.assertTrue(vault.getItem(RouletteSettings.ENGAGED_SLOT).isEmpty(), "The engaged stake is consumed");
        tick(game, RESULT_TICKS);
        helper.assertTrue(game.phase() == RouletteGame.Phase.WAITING, "The table reopens");
        helper.assertTrue(settings().rules().colourAt(game.lastResultSlot()) == Colour.RED,
                "The result stays public afterwards");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void greenPaysMoreAndTheOtherColoursLose(GameTestHelper helper) {
        ServerPlayer lucky = helper.makeMockServerPlayerInLevel();
        ServerPlayer other = helper.makeMockServerPlayerInLevel();
        RouletteGame game = game(helper, DRAW_GREEN);
        UUID green = lucky.getUUID(), red = other.getUUID();
        Container greenVault = staked(game, green, 2);
        Container redVault = staked(game, red, 2);
        helper.assertTrue(game.place(green, Colour.GREEN, 2) && game.place(red, Colour.RED, 2), "Both bets accepted");
        helper.assertTrue(game.participants() == 2 && game.pot() == 4, "The table is public");
        playRound(game);
        helper.assertTrue(settings().rules().colourAt(game.resultSlot()) == Colour.GREEN, "This draw lands on green");
        helper.assertTrue(winnings(greenVault) == 28, "Green pays fourteen times the stake");
        helper.assertTrue(winnings(redVault) == 0, "The losing colour is paid nothing");
        helper.assertTrue(game.betOf(red).settled() && game.betOf(red).paid() == 0, "The losing bet is settled");
        helper.assertTrue(redVault.getItem(RouletteSettings.ENGAGED_SLOT).isEmpty(), "Its stake is gone");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void betsAreLockedOnceTheWheelSpins(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerPlayer late = helper.makeMockServerPlayerInLevel();
        RouletteGame game = game(helper, DRAW_BLACK);
        UUID uuid = player.getUUID(), lateUuid = late.getUUID();
        staked(game, uuid, 4);
        Container lateVault = staked(game, lateUuid, 4);
        helper.assertTrue(game.place(uuid, Colour.BLACK, 4), "A bet before the lock is accepted");
        tick(game, BETTING_TICKS);
        helper.assertFalse(game.place(lateUuid, Colour.BLACK, 4), "A locked wheel accepts no new bet");
        helper.assertTrue(lateVault.getItem(RouletteSettings.INPUT_SLOT).getCount() == 4, "Nothing is engaged");
        tick(game, SPIN_TICKS);
        helper.assertFalse(game.place(lateUuid, Colour.RED, 4), "The result phase accepts no bet either");
        helper.assertTrue(settings().rules().colourAt(game.resultSlot()) == Colour.BLACK, "This draw lands on black");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void betsAreRefusedOutsideTheLimitsAndInTheWrongMaterial(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        RouletteGame game = game(helper, DRAW_RED);
        UUID uuid = player.getUUID();
        Container vault = staked(game, uuid, 4);
        helper.assertTrue(game.largestStake(uuid) == 64, "Empty winnings allow a full stack");
        helper.assertFalse(game.place(uuid, Colour.RED, 0), "Below the minimum stake");
        helper.assertFalse(game.place(uuid, Colour.RED, 5), "More than the vault holds");
        helper.assertFalse(game.place(uuid, null, 4), "A bet needs a colour");
        vault.setItem(RouletteSettings.INPUT_SLOT, new ItemStack(Items.DIAMOND, 8));
        helper.assertFalse(game.place(uuid, Colour.RED, 4), "Another material is not this table");
        vault.setItem(RouletteSettings.INPUT_SLOT, new ItemStack(Items.GOLD_INGOT, 4));
        for (int slot = RouletteSettings.FIRST_PAYOUT_SLOT; slot < RouletteSettings.VAULT_SIZE; slot++) {
            vault.setItem(slot, new ItemStack(Items.GOLD_INGOT, 64));
        }
        helper.assertTrue(game.largestStake(uuid) == 0, "Full winnings could not be paid");
        helper.assertFalse(game.place(uuid, Colour.RED, 4), "So the bet is refused before anything is engaged");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void anInterruptedRoundGivesEveryEngagedStakeBackOnce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        RouletteGame game = game(helper, DRAW_RED);
        UUID uuid = player.getUUID();
        Container vault = staked(game, uuid, 5);
        helper.assertTrue(game.place(uuid, Colour.RED, 5), "Bet accepted");
        tick(game, 10);
        game.cancel();
        helper.assertTrue(winnings(vault) == 5, "The engaged stake comes back once");
        helper.assertTrue(vault.getItem(RouletteSettings.ENGAGED_SLOT).isEmpty(), "Nothing stays engaged");
        game.cancel();
        helper.assertTrue(winnings(vault) == 5, "Cancelling twice refunds once");
        helper.assertTrue(game.phase() == RouletteGame.Phase.WAITING, "The table is free again");
        // A settled round owes nothing more, whatever happens to the server afterwards.
        staked(game, uuid, 5);
        helper.assertTrue(game.place(uuid, Colour.RED, 5), "A new round starts clean");
        playRound(game);
        int paid = winnings(vault);
        game.cancel();
        helper.assertTrue(winnings(vault) == paid, "A settled bet is never repaid");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theRoundOutlivesItsStation(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModContent.ROULETTE_STATION);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        RouletteGame game = game(helper, DRAW_RED);
        UUID uuid = player.getUUID();
        Container vault = staked(game, uuid, 3);
        helper.assertTrue(game.place(uuid, Colour.RED, 3), "Bet accepted at the station");
        tick(game, BETTING_TICKS);
        helper.setBlock(relative, Blocks.AIR);
        // Breaking the station releases nothing: the spin belongs to the server.
        tick(game, SPIN_TICKS);
        helper.assertTrue(game.phase() == RouletteGame.Phase.RESULT, "The round settled anyway");
        helper.assertTrue(winnings(vault) == 6, "The player is paid from the round, not from the block");
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
