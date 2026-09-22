package dev.gamblingitems.fabric.bingo;

import dev.gamblingitems.core.bingo.BingoRules;
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

public class BingoGameTests implements FabricGameTest {
    private static final int BETTING_TICKS = 40, DRAW_TICKS = 5, RESULT_TICKS = 20;
    private static final long IRON = 1_000;

    private static BingoSetup setup() {
        ValueCatalog catalog = new ValueCatalog(List.of(
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("copper_ingot"), 100),
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("iron_ingot"), IRON),
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("diamond"), 10_000)));
        return new BingoSetup(catalog, new BingoSettings(IRON, 9_000, BETTING_TICKS, DRAW_TICKS, RESULT_TICKS));
    }

    /** Always taking the first of whatever is left makes both the cards and the drum predictable. */
    private static BingoGame game(GameTestHelper helper) {
        return new BingoGame(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), setup(), size -> 0);
    }

    private static Container prepared(BingoGame game, UUID player, int ingots) {
        Container vault = game.vault(player);
        for (int slot = 0; slot < BingoSettings.VAULT_SIZE; slot++) vault.setItem(slot, ItemStack.EMPTY);
        vault.setItem(BingoSettings.INPUT_SLOT, new ItemStack(Items.IRON_INGOT, ingots));
        return vault;
    }

    private static void tick(BingoGame game, int times) {
        for (int index = 0; index < times; index++) game.tick();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aTerminalOpensARoundWhereItStands(GameTestHelper helper) {
        BingoGames.clear();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().add(new ItemStack(ModContent.TERMINAL));
        helper.assertTrue(BingoGames.nearest(player) == null, "Nothing is running before the terminal");
        BingoMenus.open(player, net.minecraft.world.inventory.ContainerLevelAccess.NULL);
        BingoGame game = BingoGames.nearest(player);
        helper.assertTrue(game != null, "A terminal with no table opens a round where it stands");
        helper.assertTrue(game.phase() == BingoGame.Phase.WAITING, "And that round is waiting for cards");
        BingoGames.clear();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aCardIsPaidForOnceAndIsWellFormed(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BingoGame game = game(helper);
        UUID uuid = player.getUUID();
        Container vault = prepared(game, uuid, 1);
        helper.assertTrue(game.buy(uuid, "first"), "A prepared ingot buys a card");
        helper.assertTrue(BingoRules.isCard(game.cardOf(uuid).squares()), "The card is well formed");
        helper.assertTrue(game.stagedValue(uuid) == 0, "What paid for it left the preparation row");
        helper.assertFalse(vault.getItem(BingoSettings.ENGAGED_SLOT).isEmpty(), "It waits with the round");
        helper.assertFalse(game.buy(uuid, "first"), "One card per player and per round");
        helper.assertTrue(game.pot() == IRON && game.players() == 1, "The pot is public");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aCardIsRefusedWithoutThePrice(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BingoGame game = game(helper);
        UUID uuid = player.getUUID();
        Container vault = prepared(game, uuid, 0);
        vault.setItem(BingoSettings.INPUT_SLOT, new ItemStack(Items.COPPER_INGOT, 5));
        helper.assertFalse(game.buy(uuid, "poor"), "Half a price buys nothing");
        vault.setItem(BingoSettings.INPUT_SLOT, new ItemStack(Items.DIRT, 64));
        helper.assertTrue(game.stagedValue(uuid) == 0, "An unpriced item is worth nothing");
        helper.assertFalse(game.buy(uuid, "dirt"), "So it buys nothing either");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theDrumMarksEveryCardAndPaysTheFirstLine(GameTestHelper helper) {
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        BingoGame game = game(helper);
        UUID one = first.getUUID(), two = second.getUUID();
        Container firstVault = prepared(game, one, 1);
        Container secondVault = prepared(game, two, 1);
        helper.assertTrue(game.buy(one, "one") && game.buy(two, "two"), "Two cards are sold");
        tick(game, BETTING_TICKS);
        helper.assertTrue(game.phase() == BingoGame.Phase.DRAWING, "The drum starts on its own");
        helper.assertFalse(game.buy(helper.makeMockServerPlayerInLevel().getUUID(), "late"),
                "A turning drum sells no card");
        tick(game, DRAW_TICKS * BingoRules.ROWS + 2);
        helper.assertTrue(game.phase() == BingoGame.Phase.RESULT, "A line ends the round");
        helper.assertTrue(game.cardOf(one).winner() && game.cardOf(two).winner(),
                "Both cards filled the same line on the same number");
        // Nine tenths of a pot of two ingots, split between two winners.
        long share = BingoRules.share(2 * IRON, 2, setup().settings().returnRate());
        helper.assertTrue(game.cardOf(one).paid() == share, "Each winner takes its share");
        helper.assertTrue(game.winnings(firstVault) >= share, "And holds it in the winnings");
        helper.assertTrue(game.winnings(secondVault) >= share, "The other one too");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void anInterruptedRoundGivesEveryCardBackOnce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BingoGame game = game(helper);
        UUID uuid = player.getUUID();
        prepared(game, uuid, 1);
        helper.assertTrue(game.buy(uuid, "one"), "A card is sold");
        tick(game, 10);
        game.cancel();
        helper.assertTrue(game.stagedValue(uuid) == IRON, "What paid for it comes back once");
        game.cancel();
        helper.assertTrue(game.stagedValue(uuid) == IRON, "Cancelling twice refunds once");
        helper.assertTrue(game.phase() == BingoGame.Phase.WAITING, "The table is free again");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theRoundOutlivesItsBlock(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModContent.BINGO_STATION);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BingoGame game = game(helper);
        UUID uuid = player.getUUID();
        Container vault = prepared(game, uuid, 1);
        helper.assertTrue(game.buy(uuid, "one"), "A card is sold at the board");
        tick(game, BETTING_TICKS);
        helper.setBlock(relative, Blocks.AIR);
        tick(game, DRAW_TICKS * BingoRules.ROWS + 2);
        helper.assertTrue(game.phase() == BingoGame.Phase.RESULT, "Breaking the board stops nothing");
        helper.assertTrue(game.winnings(vault) > 0, "The winner is still paid");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aTerminalOnlyJoinsARoundAnnouncedNearby(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModContent.BINGO_TABLE);
        BlockPos pos = helper.absolutePos(relative);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        try {
            helper.assertTrue(BingoGames.nearest(player) == null, "A terminal never opens a round on its own");
            BingoGame hosted = BingoGames.host(helper.getLevel(), pos);
            helper.assertTrue(BingoGames.host(helper.getLevel(), pos) == hosted, "One round per table");
            helper.assertTrue(BingoGames.nearest(player) == hosted, "A nearby round is joinable");
            player.setPos(pos.getX() + BingoGames.REACH + 8, pos.getY() + 1, pos.getZ() + 0.5);
            helper.assertTrue(BingoGames.nearest(player) == null, "A distant one is not");
        } finally {
            BingoGames.clear();
        }
        helper.succeed();
    }
}
