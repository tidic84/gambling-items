package dev.gamblingitems.fabric.blackjack;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.blackjack.BlackjackRules.Outcome;
import dev.gamblingitems.core.cheat.CheatRules.Tool;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.cheat.CasinoSecurity;
import dev.gamblingitems.fabric.menu.GameMenus;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A marked card lens reads the hole card for the one player who used it, or, spotted, ends the hand
 * at once with the stake lost and the player thrown out of the casino.
 */
public class BlackjackCheatGameTests implements FabricGameTest {
    private static final long IRON = 1_000;

    private static BlackjackSetup setup() {
        return new BlackjackSetup(new ValueCatalog(List.of(
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("iron_ingot"), IRON),
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("diamond"), 10_000))),
                new BlackjackSettings(IRON, 1, 20));
    }

    /** A shoe left in order: the player is dealt A♥ and 3♥, the dealer 2♥ and 4♥, and 5♥ comes next. */
    private static BlackjackTable table(GameTestHelper helper, ServerPlayer player) {
        BlackjackTable table = new BlackjackTable(helper.getLevel().getServer(), player.getUUID(), setup(), bound -> bound - 1);
        Container vault = table.vault();
        for (int slot = 0; slot < BlackjackSettings.VAULT_SIZE; slot++) vault.setItem(slot, ItemStack.EMPTY);
        vault.setItem(BlackjackSettings.INPUT_SLOT, new ItemStack(Items.IRON_INGOT, 4));
        return table;
    }

    private static ServerPlayer cheater(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().add(new ItemStack(ModContent.POCKET_CASINO));
        player.getInventory().add(new ItemStack(ModContent.cheatTool(Tool.MARKED_LENS)));
        return player;
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void anUnnoticedLensReadsTheHoleCardAndTheNextOne(GameTestHelper helper) {
        CasinoSecurity.testing(bound -> bound - 1, null);
        try {
            ServerPlayer player = cheater(helper);
            BlackjackTable table = table(helper, player);
            helper.assertTrue(table.deal(), "The hand is dealt");
            BlackjackMenu menu = new BlackjackMenu(1, player.getInventory(), table.setup(), table.vault(), table,
                    ContainerLevelAccess.NULL);
            menu.broadcastChanges();
            helper.assertTrue(menu.cheatChance() > 0, "The window shows the risk of a carried lens");
            helper.assertTrue(menu.peekedHole() < 0, "Nothing is read before the lens is used");
            helper.assertTrue(menu.clickMenuButton(player, BlackjackMenu.CHEAT_BUTTON), "The lens is used");
            menu.broadcastChanges();
            helper.assertTrue(menu.peekedHole() == 3, "The hole card 4♥ is read");
            helper.assertTrue(menu.peekedNext() == 4, "And the next card of the shoe, 5♥");
            helper.assertTrue(table.dealerHidden(), "The dealer still keeps it hidden from everyone else");
            helper.assertTrue(CasinoSecurity.tool(player, Tool.MARKED_LENS).getDamageValue() == 1, "Each use wears the lens");
            helper.assertTrue(CasinoSecurity.heat(player.server, player.getUUID()) == Tool.MARKED_LENS.defaultHeat(),
                    "An unnoticed trick leaves suspicion behind");
            helper.assertFalse(menu.clickMenuButton(player, BlackjackMenu.CHEAT_BUTTON), "Once per hand");
            table.hit();
            helper.assertTrue(table.peekedNext() == 5, "The lens keeps reading the shoe during the hand");
            helper.assertFalse(CasinoSecurity.banned(player), "Nobody noticed");
        } finally {
            CasinoSecurity.testing(null, null);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aSpottedLensLosesTheHandAndBansThePlayer(GameTestHelper helper) {
        CasinoSecurity.testing(bound -> 0, null);
        try {
            ServerPlayer player = cheater(helper);
            BlackjackTable table = table(helper, player);
            helper.assertTrue(table.deal(), "The hand is dealt");
            BlackjackMenu menu = new BlackjackMenu(1, player.getInventory(), table.setup(), table.vault(), table,
                    ContainerLevelAccess.NULL);
            helper.assertTrue(menu.clickMenuButton(player, BlackjackMenu.CHEAT_BUTTON), "The trick is attempted");
            helper.assertTrue(table.phase() == BlackjackTable.Phase.DONE && table.outcome() == Outcome.LOSS,
                    "A spotted lens ends the hand as a loss");
            helper.assertTrue(table.paid() == 0 && table.winnings() == 0, "Nothing is paid");
            helper.assertTrue(table.vault().getItem(BlackjackSettings.ENGAGED_SLOT).isEmpty(), "The stake is seized");
            helper.assertTrue(table.peekedHole() < 0, "Nothing was read");
            helper.assertTrue(CasinoSecurity.tool(player, Tool.MARKED_LENS).isEmpty(), "The lens is confiscated");
            helper.assertTrue(CasinoSecurity.banned(player), "The cheater is banned");
            helper.assertFalse(GameMenus.open(player, GameMode.BLACKJACK, ContainerLevelAccess.NULL),
                    "A banned player cannot open a game");
            CasinoSecurity.pardon(player.server, player.getUUID());
            helper.assertFalse(CasinoSecurity.banned(player), "A pardon lifts the ban");
        } finally {
            CasinoSecurity.testing(null, null);
        }
        helper.succeed();
    }
}
