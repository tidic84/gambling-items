package dev.gamblingitems.fabric.machines;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.machines.MachineTable;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.value.ItemBank;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.util.List;
import java.util.function.IntUnaryOperator;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * A prize machine draws once, settles once, keeps the items played when it owes them,
 * and refuses what its rules do not allow: a second play mid-animation, a stake too small, a closed machine.
 */
public class MachineGameTests implements FabricGameTest {
    private static final long IRON = 1_000;
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    // Four even sectors: nothing, twice the stake, half of it, one and a half. Exactly 100% back.
    private static final MachineTable WHEEL = new MachineTable(GameMode.LUCKY_WHEEL,
            new int[] {0, 200, 50, 150}, new int[] {1, 1, 1, 1});
    private static final MachineTable TARGETS = new MachineTable(GameMode.HIGH_STAKES,
            new int[] {200, 1000}, new int[0]);
    private static final MachineTable CLAW = new MachineTable(GameMode.PRIZE_CLAW,
            new int[] {150, 300}, new int[] {5000, 2500});

    private static ValueCatalog catalog() {
        return new ValueCatalog(List.of(
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("copper_ingot"), 100),
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("iron_ingot"), IRON),
                new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace("diamond"), 10_000)));
    }

    private static MachineSetup setup(MachineTable table, boolean enabled) {
        return new MachineSetup(catalog(), new MachineSettings(enabled, IRON, 0, 10_000, 40, 60, table));
    }

    private static ServerPlayer player(GameTestHelper helper, Block machine) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = helper.absolutePos(MACHINE);
        helper.getLevel().setBlockAndUpdate(pos, machine.defaultBlockState());
        player.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + 2.5);
        return player;
    }

    private static MachineMenu menu(GameTestHelper helper, ServerPlayer player, MachineSetup setup,
                                    SimpleContainer vault, IntUnaryOperator draw) {
        ContainerLevelAccess access = ContainerLevelAccess.create(player.level(), helper.absolutePos(MACHINE));
        return new MachineMenu(1, player.getInventory(), setup, vault, access, draw);
    }

    /** Lands the wheel on one sector; the stop inside the sector is always its start. */
    private static IntUnaryOperator sector(int index) {
        return bound -> bound == 1000 ? 0 : index;
    }

    private static SimpleContainer credit(int ingots) {
        SimpleContainer vault = new SimpleContainer(MachineSettings.VAULT_SIZE);
        vault.setItem(MachineSettings.INPUT_SLOT, new ItemStack(Items.IRON_INGOT, ingots));
        return vault;
    }

    private static long owed(SimpleContainer vault) {
        return ItemBank.valueOf(catalog(), vault, MachineSettings.FIRST_PAYOUT_SLOT, MachineSettings.VAULT_SIZE);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void anEmptySectorTakesTheCreditAndNothingElse(GameTestHelper helper) {
        ServerPlayer player = player(helper, ModContent.LUCKY_WHEEL);
        SimpleContainer vault = credit(2);
        MachineMenu menu = menu(helper, player, setup(WHEEL, true), vault, sector(0));
        helper.assertTrue(menu.clickMenuButton(player, MachineMenu.PLAY_BUTTON), "The wheel answers");
        helper.assertTrue(menu.shown() == 0 && menu.multiplier() == 0, "It stopped on the empty sector");
        helper.assertTrue(vault.getItem(MachineSettings.INPUT_SLOT).isEmpty(), "The credit is taken");
        helper.assertTrue(owed(vault) == 0, "Nothing is owed");
        helper.assertTrue(menu.playing(), "The wheel is still turning");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aWinKeepsTheItemsPlayedAndAddsOnlyTheProfit(GameTestHelper helper) {
        ServerPlayer player = player(helper, ModContent.LUCKY_WHEEL);
        SimpleContainer vault = credit(2);
        MachineMenu menu = menu(helper, player, setup(WHEEL, true), vault, sector(1));
        helper.assertTrue(menu.clickMenuButton(player, MachineMenu.PLAY_BUTTON), "The wheel answers");
        helper.assertTrue(menu.lastWin() == 4 * IRON, "Two ingots at twice pay four");
        helper.assertTrue(vault.getItem(MachineSettings.INPUT_SLOT).is(Items.IRON_INGOT)
                && vault.getItem(MachineSettings.INPUT_SLOT).getCount() == 2, "The very ingots played stay");
        helper.assertTrue(owed(vault) == 2 * IRON, "Only the profit is made up in change");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aPrizeBelowTheStakeIsPaidInChange(GameTestHelper helper) {
        ServerPlayer player = player(helper, ModContent.LUCKY_WHEEL);
        SimpleContainer vault = credit(2);
        MachineMenu menu = menu(helper, player, setup(WHEEL, true), vault, sector(2));
        helper.assertTrue(menu.clickMenuButton(player, MachineMenu.PLAY_BUTTON), "The wheel answers");
        helper.assertTrue(vault.getItem(MachineSettings.INPUT_SLOT).isEmpty(), "The credit is spent");
        helper.assertTrue(owed(vault) == IRON, "Half of two ingots comes back");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aSecondPlayWaitsForTheFirstToEnd(GameTestHelper helper) {
        ServerPlayer player = player(helper, ModContent.LUCKY_WHEEL);
        SimpleContainer vault = credit(2);
        int[] draws = {0};
        MachineMenu menu = menu(helper, player, setup(WHEEL, true), vault, bound -> {
            draws[0]++;
            return bound == 1000 ? 0 : 1;
        });
        helper.assertTrue(menu.clickMenuButton(player, MachineMenu.PLAY_BUTTON), "The first play goes");
        int after = draws[0];
        helper.assertFalse(menu.clickMenuButton(player, MachineMenu.PLAY_BUTTON), "A repeated request is refused");
        helper.assertTrue(draws[0] == after, "And draws nothing");
        helper.assertTrue(owed(vault) == 2 * IRON, "The first win is paid once");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aStakeBelowTheSmallestPlayIsRefused(GameTestHelper helper) {
        ServerPlayer player = player(helper, ModContent.LUCKY_WHEEL);
        SimpleContainer vault = new SimpleContainer(MachineSettings.VAULT_SIZE);
        vault.setItem(MachineSettings.INPUT_SLOT, new ItemStack(Items.COPPER_INGOT, 9));
        MachineMenu menu = menu(helper, player, setup(WHEEL, true), vault, sector(1));
        helper.assertFalse(menu.canPlay(), "Nine copper are below one iron");
        helper.assertFalse(menu.clickMenuButton(player, MachineMenu.PLAY_BUTTON), "The wheel stays still");
        helper.assertTrue(vault.getItem(MachineSettings.INPUT_SLOT).getCount() == 9, "The copper is untouched");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aClosedMachineTakesNothing(GameTestHelper helper) {
        ServerPlayer player = player(helper, ModContent.LUCKY_WHEEL);
        SimpleContainer vault = credit(2);
        MachineMenu menu = menu(helper, player, setup(WHEEL, false), vault, sector(1));
        helper.assertFalse(menu.clickMenuButton(player, MachineMenu.PLAY_BUTTON), "A disabled machine does not play");
        helper.assertTrue(vault.getItem(MachineSettings.INPUT_SLOT).getCount() == 2, "The credit is untouched");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aMachineOnlyAnswersAtItsOwnBlock(GameTestHelper helper) {
        // The menu of a wheel opened in front of a high stakes terminal is no longer valid.
        ServerPlayer player = player(helper, ModContent.HIGH_STAKES);
        SimpleContainer vault = credit(2);
        MachineMenu menu = menu(helper, player, setup(WHEEL, true), vault, sector(1));
        helper.assertFalse(menu.clickMenuButton(player, MachineMenu.PLAY_BUTTON), "The wrong machine refuses");
        helper.assertTrue(vault.getItem(MachineSettings.INPUT_SLOT).getCount() == 2, "The credit is untouched");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aTargetIsChosenBetweenPlaysAndPaysWhenTheNeedleStopsOnTheArc(GameTestHelper helper) {
        ServerPlayer player = player(helper, ModContent.HIGH_STAKES);
        SimpleContainer vault = credit(1);
        int[] needle = {99};
        MachineMenu menu = menu(helper, player, setup(TARGETS, true), vault, bound -> needle[0]);
        helper.assertTrue(menu.clickMenuButton(player, MachineMenu.CHOICE_BUTTON + 1), "The 10x target is picked");
        helper.assertFalse(menu.clickMenuButton(player, MachineMenu.CHOICE_BUTTON + 2), "There is no third target");
        helper.assertTrue(menu.clickMenuButton(player, MachineMenu.PLAY_BUTTON), "The terminal engages");
        helper.assertTrue(menu.lastWin() == 10 * IRON, "The last hundredth of the arc still pays the target");
        helper.assertFalse(menu.clickMenuButton(player, MachineMenu.CHOICE_BUTTON), "No target change mid-play");
        helper.assertTrue(menu.choice() == 1, "The target stays");

        SimpleContainer second = credit(1);
        needle[0] = 100;
        MachineMenu missed = menu(helper, player, setup(TARGETS, true), second, bound -> needle[0]);
        helper.assertTrue(missed.clickMenuButton(player, MachineMenu.PLAY_BUTTON), "The terminal engages at 2x");
        helper.assertTrue(missed.lastWin() == 0 && second.getItem(MachineSettings.INPUT_SLOT).isEmpty(),
                "Just past the arc loses the stake");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aClawHoldsItsPrizeOnlyWithinItsGrip(GameTestHelper helper) {
        ServerPlayer player = player(helper, ModContent.PRIZE_CLAW);
        SimpleContainer vault = credit(2);
        MachineMenu menu = menu(helper, player, setup(CLAW, true), vault, bound -> 2499);
        helper.assertTrue(menu.clickMenuButton(player, MachineMenu.CHOICE_BUTTON + 1), "The claw moves right");
        helper.assertTrue(menu.clickMenuButton(player, MachineMenu.PLAY_BUTTON), "The claw drops");
        helper.assertTrue(menu.detail() == 1 && menu.lastWin() == 6 * IRON, "Inside a quarter grip it holds");

        SimpleContainer second = credit(2);
        MachineMenu slipped = menu(helper, player, setup(CLAW, true), second, bound -> 2500);
        helper.assertTrue(slipped.clickMenuButton(player, MachineMenu.CHOICE_BUTTON + 1), "The claw moves right");
        helper.assertTrue(slipped.clickMenuButton(player, MachineMenu.PLAY_BUTTON), "The claw drops");
        helper.assertTrue(slipped.detail() == 0 && slipped.lastWin() == 0, "Past its grip the prize slips");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void closingTheMachineHandsBackTheCreditNotTheWinnings(GameTestHelper helper) {
        ServerPlayer player = player(helper, ModContent.LUCKY_WHEEL);
        SimpleContainer vault = credit(2);
        MachineMenu menu = menu(helper, player, setup(WHEEL, true), vault, sector(1));
        helper.assertTrue(menu.clickMenuButton(player, MachineMenu.PLAY_BUTTON), "The wheel answers");
        menu.removed(player);
        helper.assertTrue(vault.getItem(MachineSettings.INPUT_SLOT).isEmpty(), "The credit left the machine");
        helper.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 2, "Back in the player's inventory");
        helper.assertTrue(owed(vault) == 2 * IRON, "The profit waits in the saved vault");
        helper.succeed();
    }
}
