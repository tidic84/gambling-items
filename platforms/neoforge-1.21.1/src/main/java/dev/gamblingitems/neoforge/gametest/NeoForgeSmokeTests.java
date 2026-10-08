package dev.gamblingitems.neoforge.gametest;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.config.ModConfig;
import dev.gamblingitems.neoforge.GamblingItemsNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What the NeoForge adapter itself wires: the game rules are covered by the Fabric GameTests,
 * which run the same code. Left out of the published jar.
 */
@GameTestHolder(GamblingItemsNeoForge.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeSmokeTests {
    private NeoForgeSmokeTests() {}

    @GameTest(template = "empty")
    public static void configLoadsWhenTheServerStarts(GameTestHelper helper) {
        helper.assertTrue(ModConfig.values() != null, "The configuration is loaded");
        helper.assertTrue(ModConfig.slots() != null, "Every game is configured");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aStationKeepsItsBlockEntity(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModContent.UPGRADE_STATION);
        helper.assertTrue(helper.getBlockEntity(pos) instanceof GameStationEntity, "The station has its entity");
        helper.assertTrue(ModContent.UPGRADER_MENU != null && ModContent.TERMINAL != null,
                "Menus and items are registered");
        helper.succeed();
    }
}
