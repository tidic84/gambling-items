package dev.gamblingitems.fabric.tradeup;

import dev.gamblingitems.core.tradeup.TradeUpRules;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class TradeUpGameTests implements FabricGameTest {
    private static ValueCatalog.Entry entry(String path, long value) {
        return new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace(path), value);
    }

    private TradeUpSetup setup() {
        return new TradeUpSetup(new ValueCatalog(List.of(
                entry("iron_ingot", 1000), entry("quartz", 1200), entry("gold_ingot", 2000),
                entry("emerald", 5000), entry("iron_block", 9000), entry("diamond", 10000))),
                new TradeUpSettings(5, 12_500, 7_100, 40_000, 6));
    }

    private ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(ModContent.TERMINAL));
        return player;
    }

    private TradeUpMenu menu(ServerPlayer player, SimpleContainer vault, int draw) {
        return new TradeUpMenu(1, player.getInventory(), setup(), vault, ContainerLevelAccess.NULL, () -> draw);
    }

    private SimpleContainer stake(int count, net.minecraft.world.item.Item item) {
        SimpleContainer vault = new SimpleContainer(TradeUpMenu.REWARD_SLOT + 1);
        for (int slot = 0; slot < count; slot++) vault.setItem(slot, new ItemStack(item));
        return vault;
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void acceptedTradeConsumesEveryUnitOnce(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        SimpleContainer vault = stake(5, Items.IRON_INGOT);
        TradeUpMenu menu = menu(player, vault, 0);
        helper.assertTrue(menu.canSpin(), "Five comparable items form a contract");
        helper.assertTrue(menu.clickMenuButton(player, TradeUpMenu.SPIN_BUTTON), "First trade accepted");
        for (int slot = 0; slot < TradeUpMenu.INPUT_SLOTS; slot++) {
            helper.assertTrue(vault.getItem(slot).isEmpty(), "Every unit consumed");
        }
        ItemStack reward = vault.getItem(TradeUpMenu.REWARD_SLOT);
        helper.assertFalse(reward.isEmpty(), "A trade always returns one item");
        helper.assertTrue(reward.is(Items.QUARTZ), "The lowest draw gives the cheapest reward");
        helper.assertFalse(menu.clickMenuButton(player, TradeUpMenu.SPIN_BUTTON), "Duplicate rejected");
        helper.assertTrue(menu.quickMoveStack(player, TradeUpMenu.REWARD_SLOT).isEmpty(), "Reward locked while rolling");
        menu.removed(player);
        helper.assertTrue(vault.getItem(TradeUpMenu.REWARD_SLOT).is(Items.QUARTZ), "Closing preserves the reward");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyRewardBeatsEachStakedItem(GameTestHelper helper) {
        TradeUpSetup setup = setup();
        SimpleContainer vault = stake(5, Items.IRON_INGOT);
        TradeUpTable table = TradeUpTable.build(setup, vault, TradeUpMenu.INPUT_SLOTS).orElseThrow();
        int total = 0;
        for (int index = 0; index < table.rewards().size(); index++) {
            helper.assertTrue(table.rewards().get(index).value() > table.bestUnitValue(),
                    "Reward " + table.rewards().get(index).id() + " must beat one staked item");
            helper.assertTrue(table.weights().get(index) > 0, "An announced reward stays reachable");
            total += table.weights().get(index);
        }
        helper.assertTrue(total == TradeUpRules.TOTAL_WEIGHT, "Weights cover the whole table");
        helper.assertTrue(table.totalStake() == 5000, "Stake value is the sum of the units");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void refusesStakesThatAreNotComparable(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        SimpleContainer vault = stake(4, Items.IRON_INGOT);
        vault.setItem(4, new ItemStack(Items.GOLD_INGOT));
        TradeUpMenu menu = menu(player, vault, 0);
        helper.assertFalse(menu.canSpin(), "A ratio above the maximum is refused");
        helper.assertFalse(menu.clickMenuButton(player, TradeUpMenu.SPIN_BUTTON), "Nothing is traded");
        helper.assertTrue(vault.getItem(4).is(Items.GOLD_INGOT), "A refused trade consumes nothing");
        vault.setItem(4, new ItemStack(Items.QUARTZ));
        helper.assertTrue(menu.canSpin(), "A ratio inside the maximum is accepted");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void requiresExactlyTheConfiguredNumberOfUnits(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        SimpleContainer vault = stake(4, Items.IRON_INGOT);
        TradeUpMenu menu = menu(player, vault, 0);
        helper.assertFalse(menu.canSpin(), "Four units are not a contract");
        helper.assertFalse(menu.clickMenuButton(player, TradeUpMenu.SPIN_BUTTON), "Nothing is traded");
        // One slot may hold several units; only the total count matters.
        vault.setItem(0, new ItemStack(Items.IRON_INGOT, 2));
        helper.assertTrue(menu.stakedUnits() == 5, "Units are counted, not slots");
        helper.assertTrue(menu.canSpin(), "Five units in four slots form a contract");
        vault.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        helper.assertFalse(menu.canSpin(), "Six units are refused");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void refusesModifiedUnlistedAndForeignActions(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        ServerPlayer other = player(helper);
        SimpleContainer vault = stake(5, Items.IRON_INGOT);
        TradeUpMenu menu = menu(owner, vault, 0);
        ItemStack renamed = new ItemStack(Items.IRON_INGOT);
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Special ingot"));
        helper.assertFalse(menu.getSlot(0).mayPlace(renamed), "Do not price custom data");
        helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT)), "Unlisted item refused");
        helper.assertTrue(menu.getSlot(0).getMaxStackSize() == 5, "One slot cannot exceed the contract");
        helper.assertFalse(menu.getSlot(TradeUpMenu.REWARD_SLOT).mayPlace(new ItemStack(Items.DIAMOND)),
                "Cannot insert into the reward slot");
        helper.assertFalse(menu.clickMenuButton(other, TradeUpMenu.SPIN_BUTTON), "Foreign trade refused");
        helper.assertTrue(vault.getItem(0).is(Items.IRON_INGOT), "A refused trade consumes nothing");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fullInventoryKeepsStakeAndRewardInTheVault(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        SimpleContainer vault = stake(5, Items.IRON_INGOT);
        vault.setItem(TradeUpMenu.REWARD_SLOT, new ItemStack(Items.DIAMOND));
        menu(player, vault, 0).removed(player);
        helper.assertTrue(vault.getItem(0).is(Items.IRON_INGOT), "Overflow stays recoverable");
        helper.assertTrue(vault.getItem(TradeUpMenu.REWARD_SLOT).is(Items.DIAMOND), "Reward stays recoverable");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void destroyedStationCannotAcceptTrades(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModContent.TRADE_UP_STATION);
        BlockPos pos = helper.absolutePos(relative);
        ServerPlayer player = player(helper);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        SimpleContainer vault = stake(5, Items.IRON_INGOT);
        TradeUpMenu menu = new TradeUpMenu(1, player.getInventory(), setup(), vault,
                ContainerLevelAccess.create(helper.getLevel(), pos), () -> 0);
        helper.assertTrue(menu.stillValid(player), "Nearby station accessible");
        helper.setBlock(relative, Blocks.AIR);
        helper.assertFalse(menu.clickMenuButton(player, TradeUpMenu.SPIN_BUTTON), "Destroyed station refuses trades");
        helper.assertTrue(vault.getItem(0).is(Items.IRON_INGOT), "Stake retained");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void upgradeStationDoesNotHostTheTradeUpMenu(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModContent.UPGRADE_STATION);
        BlockPos pos = helper.absolutePos(relative);
        ServerPlayer player = player(helper);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        player.getInventory().setItem(0, ItemStack.EMPTY);
        TradeUpMenu menu = new TradeUpMenu(1, player.getInventory(), setup(), stake(5, Items.IRON_INGOT),
                ContainerLevelAccess.create(helper.getLevel(), pos), () -> 0);
        helper.assertFalse(menu.stillValid(player), "Each station hosts its own game");
        helper.succeed();
    }
}
