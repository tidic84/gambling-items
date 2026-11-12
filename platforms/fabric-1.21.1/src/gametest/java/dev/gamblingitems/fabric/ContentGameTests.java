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
    private static List<ItemStack> tab(GameTestHelper helper, net.minecraft.resources.ResourceKey<CreativeModeTab> key) {
        CreativeModeTabs.tryRebuildTabContents(FeatureFlags.REGISTRY.allFlags(), true,
                helper.getLevel().registryAccess());
        return List.copyOf(BuiltInRegistries.CREATIVE_MODE_TAB.get(key).getDisplayItems());
    }

    private static boolean holds(List<ItemStack> items, Item wanted) {
        for (ItemStack stack : items) {
            if (stack.is(wanted)) return true;
        }
        return false;
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everythingOfTheModIsInItsOwnTab(GameTestHelper helper) {
        List<ItemStack> casino = tab(helper, BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(ModContent.TAB).orElseThrow());
        List<ItemStack> functional = tab(helper, CreativeModeTabs.FUNCTIONAL_BLOCKS);
        java.util.List<Item> expected = new java.util.ArrayList<>(ModContent.creativeEntries());
        for (CaseRarity rarity : CaseRarity.values()) expected.add(ModContent.key(rarity));
        expected.addAll(List.of(ModContent.TERMINAL, ModContent.SLOT_MACHINE_ITEM, ModContent.CASHIER_ITEM,
                ModContent.PLINKO_ITEM, ModContent.NEON_TUBE_ITEM, ModContent.chip(100)));
        for (Item item : expected) {
            helper.assertTrue(holds(casino, item), BuiltInRegistries.ITEM.getKey(item) + " must be in the Gambling Items tab");
            helper.assertFalse(holds(functional, item), BuiltInRegistries.ITEM.getKey(item) + " must not be in the functional blocks tab");
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
