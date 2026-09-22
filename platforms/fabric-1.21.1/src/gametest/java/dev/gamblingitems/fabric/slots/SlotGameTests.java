package dev.gamblingitems.fabric.slots;

import dev.gamblingitems.core.slots.SlotRules;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.block.SlotMachineBlock;
import dev.gamblingitems.fabric.value.ItemBank;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.util.List;
import java.util.function.IntUnaryOperator;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * A cabinet pays what its paytable says, keeps the items it was played with when it owes them,
 * and stands as one block whichever half is broken.
 */
public class SlotGameTests implements FabricGameTest {
    private static final long IRON = 1_000;

    private static SlotSetup setup() {
        ValueCatalog catalog = new ValueCatalog(List.of(
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("copper_ingot"), 100),
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("iron_ingot"), IRON),
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("diamond"), 10_000)));
        return new SlotSetup(catalog, new SlotSettings(IRON, 10_000, 40, 60));
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // Without a cabinet in front of it, a menu stays valid for whoever carries a terminal.
        player.getInventory().add(new ItemStack(ModContent.TERMINAL));
        return player;
    }

    private static SlotMenu menu(GameTestHelper helper, ServerPlayer player, SimpleContainer vault,
                                 IntUnaryOperator draw) {
        return new SlotMenu(1, player.getInventory(), setup(), vault, ContainerLevelAccess.NULL, draw);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aLosingLineTakesTheCreditAndNothingElse(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        SimpleContainer vault = new SimpleContainer(SlotSettings.VAULT_SIZE);
        vault.setItem(SlotSettings.INPUT_SLOT, new ItemStack(Items.IRON_INGOT, 2));
        // Three different symbols: the line pays nothing.
        int[] line = {0, 1, 2};
        int[] next = {0};
        SlotMenu menu = menu(helper, player, vault, bound -> line[next[0]++ % line.length]);
        helper.assertTrue(menu.canSpin(), "Two ingots are above the smallest pull");
        helper.assertTrue(menu.clickMenuButton(player, SlotMenu.SPIN_BUTTON), "The lever answers");
        helper.assertTrue(menu.multiplier() == 0, "That line pays nothing");
        helper.assertTrue(vault.getItem(SlotSettings.INPUT_SLOT).isEmpty(), "And the credit is taken");
        helper.assertTrue(ItemBank.valueOf(setup().catalog(), vault, SlotSettings.FIRST_PAYOUT_SLOT,
                SlotSettings.VAULT_SIZE) == 0, "Nothing is owed");
        helper.assertTrue(menu.remainingTicks() > 0, "The reels are still turning");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aWinningLineHandsBackWhatWasPlayedAndAddsTheProfit(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        SimpleContainer vault = new SimpleContainer(SlotSettings.VAULT_SIZE);
        vault.setItem(SlotSettings.INPUT_SLOT, new ItemStack(Items.IRON_INGOT, 2));
        // Three cherries: the cheapest line of the paytable, four times the credit.
        SlotMenu menu = menu(helper, player, vault, bound -> 0);
        helper.assertTrue(menu.clickMenuButton(player, SlotMenu.SPIN_BUTTON), "The lever answers");
        helper.assertTrue(menu.multiplier() == SlotRules.tripleOf(0), "Three alike pay their line");
        helper.assertTrue(menu.lastWin() == 8 * IRON, "Two ingots at four times pay eight");
        helper.assertTrue(vault.getItem(SlotSettings.INPUT_SLOT).is(Items.IRON_INGOT)
                        && vault.getItem(SlotSettings.INPUT_SLOT).getCount() == 2,
                "The very items played stay in front of the player");
        helper.assertTrue(ItemBank.valueOf(setup().catalog(), vault, SlotSettings.FIRST_PAYOUT_SLOT,
                SlotSettings.VAULT_SIZE) == 6 * IRON, "Only the profit is made up in change");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void twoAlikePayTheConsolationLine(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        SimpleContainer vault = new SimpleContainer(SlotSettings.VAULT_SIZE);
        vault.setItem(SlotSettings.INPUT_SLOT, new ItemStack(Items.IRON_INGOT, 10));
        int[] line = {3, 3, 5};
        int[] next = {0};
        SlotMenu menu = menu(helper, player, vault, bound -> line[next[0]++ % line.length]);
        helper.assertTrue(menu.clickMenuButton(player, SlotMenu.SPIN_BUTTON), "The lever answers");
        helper.assertTrue(menu.multiplier() == SlotRules.PAIR, "Two alike pay the consolation");
        helper.assertTrue(menu.lastWin() == 14 * IRON, "Ten ingots at 1.4 pay fourteen");
        helper.assertTrue(ItemBank.valueOf(setup().catalog(), vault, SlotSettings.FIRST_PAYOUT_SLOT,
                SlotSettings.VAULT_SIZE) == 4 * IRON, "Four of profit on top of what was played");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aPullNeedsTheSmallestBetAndRoomForTheBestLine(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        SimpleContainer vault = new SimpleContainer(SlotSettings.VAULT_SIZE);
        SlotMenu menu = menu(helper, player, vault, bound -> 0);
        helper.assertFalse(menu.canSpin(), "An empty machine plays nothing");
        vault.setItem(SlotSettings.INPUT_SLOT, new ItemStack(Items.COPPER_INGOT, 5));
        helper.assertFalse(menu.canSpin(), "Half the smallest pull is not a pull");
        vault.setItem(SlotSettings.INPUT_SLOT, new ItemStack(Items.DIRT, 64));
        helper.assertTrue(menu.credit() == 0, "An unpriced item is worth nothing");
        helper.assertFalse(menu.canSpin(), "So it plays nothing either");
        vault.setItem(SlotSettings.INPUT_SLOT, new ItemStack(Items.IRON_INGOT, 1));
        for (int slot = SlotSettings.FIRST_PAYOUT_SLOT; slot < SlotSettings.VAULT_SIZE; slot++) {
            vault.setItem(slot, new ItemStack(Items.DIRT, 1));
        }
        helper.assertFalse(menu.canSpin(), "A full payout row could not be paid, so nothing is played");
        helper.assertFalse(menu.clickMenuButton(player, SlotMenu.SPIN_BUTTON), "And the lever refuses");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aCabinetIsThreeBlocksWithOneGameAmongThem(GameTestHelper helper) {
        BlockPos root = helper.absolutePos(new BlockPos(2, 1, 2));
        var state = ModContent.SLOT_MACHINE.defaultBlockState()
                .setValue(SlotMachineBlock.FACING, Direction.NORTH);
        helper.getLevel().setBlock(root, state, 3);
        ModContent.SLOT_MACHINE.setPlacedBy(helper.getLevel(), root, state, null, ItemStack.EMPTY);
        for (int part = 0; part < SlotMachineBlock.PARTS; part++) {
            BlockPos pos = SlotMachineBlock.partPos(root, part);
            var tile = helper.getLevel().getBlockState(pos);
            helper.assertTrue(tile.is(ModContent.SLOT_MACHINE)
                            && tile.getValue(SlotMachineBlock.PART) == part,
                    "Each half of the cabinet occupies a real block");
            helper.assertTrue(SlotMachineBlock.anchor(pos, tile).equals(root),
                    "Both halves point back to the block the player stands at");
            helper.assertTrue((helper.getLevel().getBlockEntity(pos) != null)
                            == (part == SlotMachineBlock.ANCHOR_PART),
                    "Only the bottom block carries the game");
        }
        // The roof is half a block high, so a cabinet stands two blocks and a half.
        var roof = helper.getLevel().getBlockState(SlotMachineBlock.partPos(root, SlotMachineBlock.PARTS - 1));
        helper.assertTrue(roof.getShape(helper.getLevel(),
                        SlotMachineBlock.partPos(root, SlotMachineBlock.PARTS - 1)).max(
                        net.minecraft.core.Direction.Axis.Y) == 0.5,
                "The roof block is only half full");
        // Breaking the marquee takes the whole cabinet with it.
        helper.getLevel().destroyBlock(SlotMachineBlock.partPos(root, 1), false);
        for (int part = 0; part < SlotMachineBlock.PARTS; part++) {
            helper.assertTrue(helper.getLevel().getBlockState(SlotMachineBlock.partPos(root, part))
                            .is(Blocks.AIR), "The whole cabinet goes when one half does");
        }
        helper.succeed();
    }
}
