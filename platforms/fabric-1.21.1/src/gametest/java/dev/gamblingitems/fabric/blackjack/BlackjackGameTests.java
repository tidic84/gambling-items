package dev.gamblingitems.fabric.blackjack;

import dev.gamblingitems.core.blackjack.BlackjackRules;
import dev.gamblingitems.core.blackjack.BlackjackRules.Outcome;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BlackjackGameTests implements FabricGameTest {
    private static final long IRON = 1_000;

    private static ValueCatalog catalog() {
        return new ValueCatalog(List.of(
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("copper_ingot"), 100),
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("iron_ingot"), IRON),
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("diamond"), 10_000)));
    }

    private static BlackjackSetup setup() {
        return new BlackjackSetup(catalog(), new BlackjackSettings(IRON, 1, 20));
    }

    /** A deck dealt from the top: the cards this test wants, in the order they will come out. */
    private static BlackjackTable table(GameTestHelper helper, UUID player, int... cards) {
        // The shuffle is driven so the deck ends up holding these cards first, in this order.
        int[] order = cards.clone();
        return new BlackjackTable(helper.getLevel().getServer(), player, setup(), new StackedDeck(order));
    }

    /** Turns a wanted sequence of cards into the draws a Fisher-Yates shuffle needs. */
    private static final class StackedDeck implements java.util.function.IntUnaryOperator {
        private final int[] wanted;
        private int step;

        private StackedDeck(int[] wanted) { this.wanted = wanted; }

        @Override public int applyAsInt(int bound) {
            // The shuffle walks the deck from the end; leaving every card in place keeps a known
            // deck, and the test then asks for the cards it needs by their index.
            int index = BlackjackRules.CARDS - 1 - step;
            step++;
            int position = step <= wanted.length ? wanted[step - 1] : index;
            return Math.min(Math.max(position, 0), bound - 1);
        }
    }

    private static Container chips(BlackjackTable table, ItemStack stack) {
        Container vault = table.vault();
        for (int slot = 0; slot < BlackjackSettings.VAULT_SIZE; slot++) vault.setItem(slot, ItemStack.EMPTY);
        vault.setItem(BlackjackSettings.INPUT_SLOT, stack);
        return vault;
    }

    /** Stands if the player still has to act, then lets the dealer finish the hand. */
    private static void playOut(BlackjackTable table) {
        if (table.phase() == BlackjackTable.Phase.PLAYER) table.stand();
        for (int tick = 0; tick < 200 && table.phase() == BlackjackTable.Phase.DEALER; tick++) {
            table.tick();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aHandEngagesItsChipsAndSettlesOnce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlackjackTable table = table(helper, player.getUUID());
        Container vault = chips(table, new ItemStack(Items.IRON_INGOT, 4));
        helper.assertTrue(table.canDeal(), "Four ingots are enough to be dealt a hand");
        helper.assertTrue(table.deal(), "The hand is dealt");
        helper.assertTrue(table.stake() == 4 * IRON, "The whole prepared value is at stake");
        helper.assertTrue(table.stagedValue() == 0, "The chips left the preparation row");
        helper.assertFalse(vault.getItem(BlackjackSettings.ENGAGED_SLOT).isEmpty(), "They wait on the table");
        helper.assertTrue(table.hand().size() == 2, "Two cards for the player");
        helper.assertFalse(table.deal(), "A hand in play cannot be dealt again");
        playOut(table);
        helper.assertTrue(table.phase() == BlackjackTable.Phase.DONE, "The hand settles on its own");
        helper.assertTrue(vault.getItem(BlackjackSettings.ENGAGED_SLOT).isEmpty(), "The chips left the table");
        long expected = BlackjackRules.payout(4 * IRON, table.outcome());
        helper.assertTrue(table.paid() == expected, "What was paid follows the rules");
        helper.assertTrue(table.winnings() == expected, "And it is waiting in the winnings");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void hittingCanBustAndThatEndsTheHand(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlackjackTable table = table(helper, player.getUUID());
        chips(table, new ItemStack(Items.IRON_INGOT, 2));
        helper.assertTrue(table.deal(), "The hand is dealt");
        int guard = 0;
        while (table.phase() == BlackjackTable.Phase.PLAYER && guard++ < 12) table.hit();
        helper.assertTrue(guard > 0, "Hitting is possible while the player acts");
        playOut(table);
        if (BlackjackRules.isBust(table.hand())) {
            helper.assertTrue(table.outcome() == Outcome.LOSS, "A busted hand always loses");
            helper.assertTrue(table.paid() == 0, "And is paid nothing");
        }
        helper.assertTrue(table.phase() == BlackjackTable.Phase.DONE, "The hand is over either way");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theHoleCardStaysHiddenWhileThePlayerActs(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlackjackTable table = table(helper, player.getUUID());
        chips(table, new ItemStack(Items.IRON_INGOT, 2));
        helper.assertTrue(table.deal(), "The hand is dealt");
        if (table.phase() == BlackjackTable.Phase.PLAYER) {
            helper.assertTrue(table.visibleDealer().size() == 1, "Only one dealer card is public");
            helper.assertTrue(table.dealerHidden(), "The hole card is hidden");
            table.stand();
        }
        playOut(table);
        helper.assertFalse(table.dealerHidden(), "Once the hand is over, everything is public");
        helper.assertTrue(table.visibleDealer().size() >= 2, "The dealer hand is shown in full");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void doublingTakesTheSameStakeAgainAndExactlyOneCard(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlackjackTable table = table(helper, player.getUUID());
        Container vault = chips(table, new ItemStack(Items.IRON_INGOT, 2));
        helper.assertTrue(table.deal(), "The hand is dealt");
        if (table.phase() != BlackjackTable.Phase.PLAYER) {
            helper.succeed();
            return;
        }
        vault.setItem(BlackjackSettings.INPUT_SLOT, new ItemStack(Items.IRON_INGOT, 2));
        helper.assertTrue(table.canDouble(), "The same stake can be put up again");
        int before = table.hand().size();
        helper.assertTrue(table.doubleDown(), "Doubling is accepted");
        helper.assertTrue(table.stake() == 4 * IRON, "The stake doubled");
        helper.assertTrue(table.hand().size() == before + 1, "Exactly one card was taken");
        helper.assertFalse(table.canDouble(), "A doubled hand cannot double again");
        helper.assertFalse(table.hit(), "And cannot ask for another card");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void anUnfinishedHandGivesItsChipsBackOnce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlackjackTable table = table(helper, player.getUUID());
        chips(table, new ItemStack(Items.IRON_INGOT, 3));
        helper.assertTrue(table.deal(), "The hand is dealt");
        table.cancel();
        helper.assertTrue(table.stagedValue() == 3 * IRON, "The chips came back where they were prepared");
        helper.assertTrue(table.phase() == BlackjackTable.Phase.IDLE, "The table is free again");
        table.cancel();
        helper.assertTrue(table.stagedValue() == 3 * IRON, "Cancelling twice refunds once");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aHandIsRefusedWhenItsWinCouldNotBeHandedOver(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlackjackTable table = table(helper, player.getUUID());
        Container vault = chips(table, new ItemStack(Items.IRON_INGOT, 2));
        for (int slot = BlackjackSettings.FIRST_PAYOUT_SLOT; slot < BlackjackSettings.VAULT_SIZE; slot++) {
            vault.setItem(slot, new ItemStack(Items.DIAMOND, 64));
        }
        helper.assertFalse(table.canDeal(), "Full winnings could not be paid");
        helper.assertFalse(table.deal(), "So no hand is dealt");
        helper.assertTrue(table.stagedValue() == 2 * IRON, "Nothing is engaged");
        for (int slot = BlackjackSettings.FIRST_PAYOUT_SLOT; slot < BlackjackSettings.VAULT_SIZE; slot++) {
            vault.setItem(slot, ItemStack.EMPTY);
        }
        helper.assertTrue(table.deal(), "With room, the hand is dealt");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void unpricedItemsAreNotChips(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlackjackTable table = table(helper, player.getUUID());
        chips(table, new ItemStack(Items.DIRT, 64));
        helper.assertTrue(table.stagedValue() == 0, "Dirt is worth nothing at this table");
        helper.assertFalse(table.canDeal(), "So no hand can be dealt");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aHandCanBePlayedOnTheBlockWithoutOpeningAnInventory(GameTestHelper helper) {
        net.minecraft.core.BlockPos relative = new net.minecraft.core.BlockPos(1, 1, 1);
        helper.setBlock(relative, dev.gamblingitems.fabric.ModContent.BLACKJACK_STATION);
        var station = (dev.gamblingitems.fabric.block.GameStationEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(station.getBlockPos().getCenter());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(Items.IRON_INGOT, 4));
        try {
            // Button one deposits the whole stack, button three deals the hand.
            dev.gamblingitems.fabric.block.StationInteractions.click(player, station, 1);
            helper.assertTrue(player.getMainHandItem().isEmpty(), "The chips were deposited");
            dev.gamblingitems.fabric.block.StationInteractions.click(player, station, 3);
            BlackjackTable table = BlackjackTables.existing(player.getUUID());
            helper.assertTrue(table != null && table.stake() > 0, "The hand was dealt from the block");
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "No inventory was opened");
            table.cancel();
        } finally {
            dev.gamblingitems.fabric.block.StationInteractions.close(player.getUUID());
            BlackjackTables.clear();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void winningsAreCollectedFromTheBlockToo(GameTestHelper helper) {
        net.minecraft.core.BlockPos relative = new net.minecraft.core.BlockPos(1, 1, 1);
        helper.setBlock(relative, dev.gamblingitems.fabric.ModContent.BLACKJACK_STATION);
        var station = (dev.gamblingitems.fabric.block.GameStationEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(station.getBlockPos().getCenter());
        BlackjackTable table = BlackjackTables.of(player.server, player.getUUID());
        Container vault = table.vault();
        for (int slot = 0; slot < BlackjackSettings.VAULT_SIZE; slot++) vault.setItem(slot, ItemStack.EMPTY);
        // A hand that has already been paid: the winnings are waiting in the vault.
        vault.setItem(BlackjackSettings.FIRST_PAYOUT_SLOT, new ItemStack(Items.DIAMOND, 3));
        try {
            helper.assertTrue(table.winnings() > 0, "There is something to collect");
            dev.gamblingitems.fabric.block.StationInteractions.click(player, station, 6);
            helper.assertTrue(vault.getItem(BlackjackSettings.FIRST_PAYOUT_SLOT).isEmpty(),
                    "The winnings left the vault");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 3,
                    "And landed in the inventory of the player");
        } finally {
            dev.gamblingitems.fabric.block.StationInteractions.close(player.getUUID());
            BlackjackTables.clear();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aBigWinIsShownAsAGainAndCanBeCollected(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlackjackTable table = table(helper, player.getUUID());
        Container vault = chips(table, ItemStack.EMPTY);
        // Five diamonds are fifty thousand in values: far beyond what one data slot can carry.
        vault.setItem(BlackjackSettings.FIRST_PAYOUT_SLOT, new ItemStack(Items.DIAMOND, 5));
        BlackjackMenu menu = new BlackjackMenu(1, player.getInventory(), setup(), vault, table,
                net.minecraft.world.inventory.ContainerLevelAccess.NULL);
        player.getInventory().setItem(0, new ItemStack(dev.gamblingitems.fabric.ModContent.TERMINAL));
        menu.broadcastChanges();
        helper.assertTrue(menu.winnings() == 50_000, "The screen shows a gain, not a negative number");
        helper.assertTrue(menu.clickMenuButton(player, BlackjackMenu.COLLECT_BUTTON), "Collecting is accepted");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 5, "The diamonds are handed over");
        menu.broadcastChanges();
        helper.assertTrue(menu.winnings() == 0, "And nothing is left to collect");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aTableIsReadyForAnotherHandOnceOneEnds(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlackjackTable table = table(helper, player.getUUID());
        chips(table, new ItemStack(Items.IRON_INGOT, 2));
        helper.assertTrue(table.deal(), "A first hand is dealt");
        playOut(table);
        helper.assertTrue(table.phase() == BlackjackTable.Phase.DONE, "It finishes");
        for (int tick = 0; tick < 40; tick++) table.tick();
        helper.assertTrue(table.phase() == BlackjackTable.Phase.IDLE, "The table clears itself");
        // The chips of the next hand are prepared exactly like the first ones.
        table.vault().setItem(BlackjackSettings.INPUT_SLOT, new ItemStack(Items.IRON_INGOT, 2));
        helper.assertTrue(table.canDeal(), "Another hand can be dealt");
        helper.assertTrue(table.deal(), "And is dealt");
        helper.assertTrue(table.hand().size() == 2, "With two fresh cards");
        table.cancel();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theTableOfAPlayerIsNotThrownAwayWhileTheyPlay(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlackjackTable first = BlackjackTables.of(player.server, player.getUUID());
        try {
            for (int tick = 0; tick < 200; tick++) BlackjackTables.tick(player.server);
            helper.assertTrue(BlackjackTables.of(player.server, player.getUUID()) == first,
                    "The same table answers, so an open screen keeps working");
        } finally {
            BlackjackTables.clear();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aSettledHandGivesBackTheVeryItemsThatWereStaked(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlackjackTable table = table(helper, player.getUUID());
        Container vault = chips(table, new ItemStack(Items.DIAMOND, 5));
        helper.assertTrue(table.deal(), "Five diamonds are staked");
        playOut(table);
        helper.assertTrue(table.phase() == BlackjackTable.Phase.DONE, "The hand is settled");
        int diamonds = 0;
        long other = 0;
        for (int slot = BlackjackSettings.FIRST_PAYOUT_SLOT; slot < BlackjackSettings.VAULT_SIZE; slot++) {
            ItemStack stack = vault.getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.is(Items.DIAMOND)) diamonds += stack.getCount();
            else other += setup().catalog().valueOf(stack);
        }
        switch (table.outcome()) {
            case PUSH -> {
                // A tie hands the same five diamonds back, never an item of the same value.
                helper.assertTrue(diamonds == 5, "A tie returns the diamonds that were staked");
                helper.assertTrue(other == 0, "And nothing else is invented");
            }
            case WIN, NATURAL -> {
                helper.assertTrue(diamonds >= 5, "A win returns the staked diamonds too");
                helper.assertTrue(diamonds * 10_000L + other == table.paid(),
                        "And the winnings on top add up to what was owed");
            }
            case LOSS -> helper.assertTrue(diamonds == 0 && other == 0, "A lost hand pays nothing");
            default -> helper.fail("A settled hand always has an outcome");
        }
        helper.succeed();
    }
}
