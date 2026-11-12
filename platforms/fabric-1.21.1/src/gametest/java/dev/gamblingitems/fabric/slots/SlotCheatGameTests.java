package dev.gamblingitems.fabric.slots;

import dev.gamblingitems.core.cheat.CheatRules.Tool;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.cheat.CasinoSecurity;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.util.List;
import java.util.function.IntUnaryOperator;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A shim keeps the better of two lines for one pull; spotted, the pull counts for nothing. */
public class SlotCheatGameTests implements FabricGameTest {
    private static SlotSetup setup() {
        return new SlotSetup(new ValueCatalog(List.of(
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("iron_ingot"), 1_000),
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("diamond"), 10_000))),
                new SlotSettings(1_000, 10_000, 40, 60));
    }

    /** The reels show these symbols, in this order, and then a losing line forever. */
    private static IntUnaryOperator reels(int... symbols) {
        int[] next = {0};
        return bound -> next[0] < symbols.length ? symbols[next[0]++] : next[0]++ % 3;
    }

    private static SlotMenu cabinet(ServerPlayer player, SimpleContainer vault, IntUnaryOperator draw) {
        player.getInventory().add(new ItemStack(ModContent.POCKET_CASINO));
        player.getInventory().add(new ItemStack(ModContent.cheatTool(Tool.REEL_SHIM)));
        vault.setItem(SlotSettings.INPUT_SLOT, new ItemStack(Items.DIAMOND));
        return new SlotMenu(1, player.getInventory(), setup(), vault, ContainerLevelAccess.NULL, draw);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void anUnnoticedShimKeepsTheBetterOfTwoLines(GameTestHelper helper) {
        CasinoSecurity.testing(bound -> bound - 1, null);
        try {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            SimpleContainer vault = new SimpleContainer(SlotSettings.VAULT_SIZE);
            SlotMenu menu = cabinet(player, vault, reels(0, 1, 2, 5, 5, 5));
            menu.broadcastChanges();
            helper.assertTrue(menu.cheatChance() >= 0, "The window offers the shim to whoever carries one");
            helper.assertTrue(menu.clickMenuButton(player, SlotMenu.CHEAT_BUTTON), "The shim is slipped in");
            menu.broadcastChanges();
            helper.assertTrue(menu.shimSet(), "It waits for the next pull");
            helper.assertTrue(menu.clickMenuButton(player, SlotMenu.SPIN_BUTTON), "The lever is pulled");
            helper.assertTrue(menu.multiplier() == dev.gamblingitems.core.slots.SlotRules.tripleOf(5),
                    "The losing line is replaced by three of a kind");
            helper.assertTrue(menu.lastWin() == 300_000, "And paid as such");
            helper.assertFalse(menu.shimSet(), "A shim lasts one pull");
        } finally {
            CasinoSecurity.testing(null, null);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aSpottedShimVoidsThePull(GameTestHelper helper) {
        CasinoSecurity.testing(bound -> 0, null);
        try {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            SimpleContainer vault = new SimpleContainer(SlotSettings.VAULT_SIZE);
            SlotMenu menu = cabinet(player, vault, reels(0, 0, 0));
            helper.assertTrue(menu.clickMenuButton(player, SlotMenu.CHEAT_BUTTON), "The shim is slipped in");
            helper.assertTrue(menu.clickMenuButton(player, SlotMenu.SPIN_BUTTON), "The lever is pulled");
            helper.assertTrue(menu.multiplier() == 0, "Even three of a kind counts for nothing once spotted");
            helper.assertTrue(vault.getItem(SlotSettings.INPUT_SLOT).isEmpty(), "The credit is the house's");
            helper.assertTrue(CasinoSecurity.banned(player), "The cheater is banned");
        } finally {
            CasinoSecurity.testing(null, null);
        }
        helper.succeed();
    }
}
