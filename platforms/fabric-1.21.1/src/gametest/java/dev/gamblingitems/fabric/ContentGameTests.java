package dev.gamblingitems.fabric;

import dev.gamblingitems.core.cases.CaseRarity;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Everything this mod adds has to be reachable in creative, or it does not exist for a builder. */
public class ContentGameTests implements FabricGameTest {
    private static List<ItemStack> functionalBlocks(GameTestHelper helper) {
        CreativeModeTabs.tryRebuildTabContents(FeatureFlags.REGISTRY.allFlags(), true,
                helper.getLevel().registryAccess());
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.FUNCTIONAL_BLOCKS);
        return List.copyOf(tab.getDisplayItems());
    }

    private static boolean holds(List<ItemStack> items, Item wanted) {
        for (ItemStack stack : items) {
            if (stack.is(wanted)) return true;
        }
        return false;
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyStationAndKeyIsInTheFunctionalBlocksTab(GameTestHelper helper) {
        List<ItemStack> items = functionalBlocks(helper);
        Item[] expected = {
                ModContent.TERMINAL,
                ModContent.UPGRADE_STATION_ITEM, ModContent.TRADE_UP_STATION_ITEM,
                ModContent.CASE_STATION_ITEM, ModContent.CRASH_STATION_ITEM,
                ModContent.ROULETTE_STATION_ITEM, ModContent.BLACKJACK_STATION_ITEM,
                ModContent.BATTLE_STATION_ITEM,
                ModContent.BLACKJACK_TABLE_ITEM, ModContent.ROULETTE_TABLE_ITEM,
                ModContent.BINGO_STATION_ITEM, ModContent.BINGO_TABLE_ITEM,
                ModContent.SLOT_MACHINE_ITEM,
        };
        for (Item item : expected) {
            helper.assertTrue(holds(items, item),
                    BuiltInRegistries.ITEM.getKey(item) + " must be in the functional blocks tab");
        }
        for (CaseRarity rarity : CaseRarity.values()) {
            helper.assertTrue(holds(items, ModContent.key(rarity)),
                    "The " + rarity.id() + " key must be in the functional blocks tab");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aTableHostsTheGameItIsBuiltFor(GameTestHelper helper) {
        var tables = new dev.gamblingitems.fabric.block.GameTableBlock[] {
                ModContent.BLACKJACK_TABLE, ModContent.ROULETTE_TABLE};
        var modes = new dev.gamblingitems.core.GameMode[] {
                dev.gamblingitems.core.GameMode.BLACKJACK, dev.gamblingitems.core.GameMode.ROULETTE};
        for (int index = 0; index < tables.length; index++) {
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(1, 1, 1 + index);
            helper.setBlock(pos, tables[index]);
            var entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
            helper.assertTrue(entity instanceof dev.gamblingitems.fabric.block.GameStationEntity,
                    "A table carries the block entity that shows its game");
            helper.assertTrue(((dev.gamblingitems.fabric.block.GameStationEntity) entity).mode() == modes[index],
                    "And it hosts the game it was built for");
            helper.assertTrue(tables[index].mode() == modes[index], "The block knows its own game");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyPlayableGameHasABlockOfItsOwn(GameTestHelper helper) {
        for (var mode : dev.gamblingitems.core.GameMode.values()) {
            if (!dev.gamblingitems.fabric.menu.GameMenus.AVAILABLE.contains(mode)) continue;
            boolean found = false;
            for (var block : BuiltInRegistries.BLOCK) {
                // A game is played on a monitor, on a table or on a cabinet; any of them will do.
                if (block instanceof dev.gamblingitems.fabric.block.GameSurface surface
                        && surface.mode() == mode) {
                    found = true;
                }
            }
            helper.assertTrue(found, "The " + mode.id() + " game must have a block to play it on");
        }
        helper.succeed();
    }
}
