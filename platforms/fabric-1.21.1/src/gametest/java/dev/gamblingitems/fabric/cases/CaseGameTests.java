package dev.gamblingitems.fabric.cases;

import dev.gamblingitems.core.cases.CaseRules;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class CaseGameTests implements FabricGameTest {
    private static final int STARTER = 0, RICH = 1;

    private static ValueCatalog.Entry entry(String path, long value) {
        return new ValueCatalog.Entry(ResourceLocation.withDefaultNamespace(path), value);
    }

    private static ResourceLocation id(String path) { return ResourceLocation.withDefaultNamespace(path); }

    private static CaseDefinition definition(String name, String priceItem, int priceCount, String[][] rewards) {
        List<Long> raw = new ArrayList<>();
        for (String[] reward : rewards) raw.add(Long.parseLong(reward[2]));
        int[] weights = CaseRules.normalize(raw);
        List<CaseReward> lines = new ArrayList<>();
        for (int index = 0; index < weights.length; index++) {
            lines.add(new CaseReward(id(rewards[index][0]), Integer.parseInt(rewards[index][1]), weights[index]));
        }
        return new CaseDefinition(name, name, id(priceItem), priceCount, lines);
    }

    private CaseSetup setup() {
        ValueCatalog catalog = new ValueCatalog(List.of(
                entry("coal", 150), entry("iron_ingot", 1000), entry("gold_ingot", 2000),
                entry("emerald", 5000), entry("diamond", 10000), entry("diamond_block", 90000)));
        CaseDefinition starter = definition("starter", "iron_ingot", 5, new String[][] {
                {"coal", "8", "700"}, {"gold_ingot", "2", "250"}, {"diamond", "1", "50"}});
        CaseDefinition rich = definition("rich", "diamond", 1, new String[][] {
                {"emerald", "3", "700"}, {"diamond", "2", "250"}, {"diamond_block", "1", "50"}});
        return new CaseSetup(catalog, new CaseCatalog(List.of(starter, rich)));
    }

    private ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(ModContent.TERMINAL));
        return player;
    }

    private CaseMenu menu(ServerPlayer player, SimpleContainer vault, int draw) {
        return new CaseMenu(1, player.getInventory(), setup(), vault, ContainerLevelAccess.NULL, () -> draw);
    }

    private SimpleContainer paid(Item item, int count) {
        SimpleContainer vault = new SimpleContainer(CaseMenu.REWARD_SLOT + 1);
        vault.setItem(CaseMenu.PRICE_SLOT, new ItemStack(item, count));
        return vault;
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void openingTakesThePriceOnceAndPaysOnce(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        SimpleContainer vault = paid(Items.IRON_INGOT, 7);
        CaseMenu menu = menu(player, vault, 0);
        helper.assertTrue(menu.canOpen(), "Seven ingots pay for a five ingot case");
        helper.assertTrue(menu.clickMenuButton(player, CaseMenu.OPEN_BUTTON), "First opening accepted");
        helper.assertTrue(vault.getItem(CaseMenu.PRICE_SLOT).getCount() == 2, "Only the price is taken");
        ItemStack reward = vault.getItem(CaseMenu.REWARD_SLOT);
        helper.assertTrue(reward.is(Items.COAL) && reward.getCount() == 8, "The lowest draw gives the first line");
        helper.assertFalse(menu.clickMenuButton(player, CaseMenu.OPEN_BUTTON), "Duplicate rejected");
        helper.assertTrue(vault.getItem(CaseMenu.PRICE_SLOT).getCount() == 2, "A refused opening takes nothing");
        helper.assertTrue(menu.quickMoveStack(player, CaseMenu.REWARD_SLOT).isEmpty(), "Reward locked while rolling");
        menu.removed(player);
        helper.assertTrue(vault.getItem(CaseMenu.REWARD_SLOT).is(Items.COAL), "Closing preserves the reward");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void refusesAnIncompleteOrForeignPayment(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        SimpleContainer vault = paid(Items.IRON_INGOT, 4);
        CaseMenu menu = menu(player, vault, 0);
        helper.assertFalse(menu.canOpen(), "Four ingots do not pay for five");
        helper.assertFalse(menu.clickMenuButton(player, CaseMenu.OPEN_BUTTON), "Nothing is opened");
        helper.assertTrue(vault.getItem(CaseMenu.PRICE_SLOT).getCount() == 4, "A refused opening takes nothing");
        vault.setItem(CaseMenu.PRICE_SLOT, new ItemStack(Items.GOLD_INGOT, 64));
        helper.assertFalse(menu.canOpen(), "Another valued item is not this price");
        ItemStack renamed = new ItemStack(Items.IRON_INGOT, 5);
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Special ingot"));
        helper.assertFalse(menu.getSlot(CaseMenu.PRICE_SLOT).mayPlace(renamed), "Do not price custom data");
        helper.assertFalse(menu.getSlot(CaseMenu.PRICE_SLOT).mayPlace(new ItemStack(Items.DIRT)), "Unlisted item refused");
        helper.assertFalse(menu.getSlot(CaseMenu.REWARD_SLOT).mayPlace(new ItemStack(Items.DIAMOND)),
                "Cannot insert into the reward slot");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void selectionChoosesThePriceAndTheTable(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        SimpleContainer vault = paid(Items.DIAMOND, 1);
        CaseMenu menu = menu(player, vault, 0);
        helper.assertTrue(menu.selectedIndex() == STARTER, "The first case is selected by default");
        helper.assertFalse(menu.canOpen(), "A diamond does not pay for the iron case");
        helper.assertTrue(menu.clickMenuButton(player, RICH), "Selecting another case is allowed");
        helper.assertTrue(menu.canOpen(), "A diamond pays for the diamond case");
        helper.assertTrue(menu.clickMenuButton(player, CaseMenu.OPEN_BUTTON), "Opening accepted");
        helper.assertTrue(vault.getItem(CaseMenu.REWARD_SLOT).is(Items.EMERALD), "The selected table is used");
        helper.assertFalse(menu.clickMenuButton(player, STARTER), "Selection is locked while rolling");
        helper.assertTrue(menu.selectedIndex() == RICH, "The opened case stays on screen");
        helper.assertFalse(menu.clickMenuButton(player, 2), "An unknown case is refused");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyDrawLandsOnAnAnnouncedReward(GameTestHelper helper) {
        CaseDefinition definition = setup().cases().get(STARTER);
        int total = 0;
        for (CaseReward reward : definition.rewards()) {
            helper.assertTrue(reward.weight() > 0, "An announced reward stays reachable");
            total += reward.weight();
        }
        helper.assertTrue(total == CaseRules.TOTAL_WEIGHT, "Weights cover the whole table");
        helper.assertTrue(CaseRules.select(definition.weightArray(), CaseRules.TOTAL_WEIGHT - 1)
                == definition.rewards().size() - 1, "The last draw gives the last line");
        ValueCatalog catalog = setup().catalog();
        // 8 coal at 150, 2 gold at 2000 and 1 diamond at 10000, weighted 70 / 25 / 5 percent.
        helper.assertTrue(definition.averageValue(catalog).setScale(0, RoundingMode.HALF_UP)
                .compareTo(new BigDecimal("2340")) == 0, "Average follows the announced table");
        helper.assertTrue(definition.priceValue(catalog) == 5000, "The price is valued like any stack");
        helper.assertTrue(definition.returnRate(catalog).setScale(4, RoundingMode.HALF_UP)
                .compareTo(new BigDecimal("0.4680")) == 0, "The return is computed, never assumed");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void foreignPlayersCannotOpenSomebodyElsesCase(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        ServerPlayer other = player(helper);
        SimpleContainer vault = paid(Items.IRON_INGOT, 5);
        CaseMenu menu = menu(owner, vault, 0);
        helper.assertFalse(menu.clickMenuButton(other, CaseMenu.OPEN_BUTTON), "Foreign opening refused");
        helper.assertFalse(menu.clickMenuButton(other, RICH), "Foreign selection refused");
        helper.assertTrue(vault.getItem(CaseMenu.PRICE_SLOT).getCount() == 5, "A refused opening takes nothing");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fullInventoryKeepsPaymentAndRewardInTheVault(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        SimpleContainer vault = paid(Items.IRON_INGOT, 5);
        vault.setItem(CaseMenu.REWARD_SLOT, new ItemStack(Items.DIAMOND));
        menu(player, vault, 0).removed(player);
        helper.assertTrue(vault.getItem(CaseMenu.PRICE_SLOT).is(Items.IRON_INGOT), "Overflow stays recoverable");
        helper.assertTrue(vault.getItem(CaseMenu.REWARD_SLOT).is(Items.DIAMOND), "Reward stays recoverable");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void destroyedStationCannotOpenCases(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModContent.CASE_STATION);
        BlockPos pos = helper.absolutePos(relative);
        ServerPlayer player = player(helper);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        SimpleContainer vault = paid(Items.IRON_INGOT, 5);
        CaseMenu menu = new CaseMenu(1, player.getInventory(), setup(), vault,
                ContainerLevelAccess.create(helper.getLevel(), pos), () -> 0);
        helper.assertTrue(menu.stillValid(player), "Nearby station accessible");
        helper.setBlock(relative, Blocks.AIR);
        helper.assertFalse(menu.clickMenuButton(player, CaseMenu.OPEN_BUTTON), "Destroyed station refuses openings");
        helper.assertTrue(vault.getItem(CaseMenu.PRICE_SLOT).getCount() == 5, "Payment retained");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tradeUpStationDoesNotHostTheCaseMenu(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModContent.TRADE_UP_STATION);
        BlockPos pos = helper.absolutePos(relative);
        ServerPlayer player = player(helper);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        player.getInventory().setItem(0, ItemStack.EMPTY);
        CaseMenu menu = new CaseMenu(1, player.getInventory(), setup(), paid(Items.IRON_INGOT, 5),
                ContainerLevelAccess.create(helper.getLevel(), pos), () -> 0);
        helper.assertFalse(menu.stillValid(player), "Each station hosts its own game");
        helper.succeed();
    }
}
