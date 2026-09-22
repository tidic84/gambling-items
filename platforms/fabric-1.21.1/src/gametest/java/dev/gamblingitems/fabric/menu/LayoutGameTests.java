package dev.gamblingitems.fabric.menu;

import dev.gamblingitems.fabric.battle.BattleMenu;
import dev.gamblingitems.fabric.bingo.BingoMenu;
import dev.gamblingitems.fabric.blackjack.BlackjackMenu;
import dev.gamblingitems.fabric.config.ModConfig;
import dev.gamblingitems.fabric.crash.CrashMenu;
import dev.gamblingitems.fabric.roulette.RouletteMenu;
import dev.gamblingitems.fabric.slots.SlotMenu;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Slots are drawn where the menu says they are, so two rows that overlap on paper overlap on
 * screen as well. These tests keep every window readable without anyone having to look at it.
 */
public class LayoutGameTests implements FabricGameTest {
    /** A slot is sixteen pixels of item plus its frame, and a label sits nine pixels above a row. */
    private static final int SLOT = 18, LABEL = 9;

    private static List<AbstractContainerMenu> menus(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        return List.of(
                new CrashMenu(1, player.getInventory(), ModConfig.crash()),
                new RouletteMenu(2, player.getInventory(), ModConfig.roulette()),
                new BlackjackMenu(3, player.getInventory(), ModConfig.blackjack()),
                new BattleMenu(4, player.getInventory(), ModConfig.battle()),
                new BingoMenu(5, player.getInventory(), ModConfig.bingo()),
                new SlotMenu(6, player.getInventory(), ModConfig.slots()));
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void noTwoSlotsOverlapInAnyWindow(GameTestHelper helper) {
        for (AbstractContainerMenu menu : menus(helper)) {
            String name = menu.getClass().getSimpleName();
            for (int first = 0; first < menu.slots.size(); first++) {
                for (int second = first + 1; second < menu.slots.size(); second++) {
                    Slot one = menu.slots.get(first), other = menu.slots.get(second);
                    boolean apart = one.x + SLOT <= other.x || other.x + SLOT <= one.x
                            || one.y + SLOT <= other.y || other.y + SLOT <= one.y;
                    helper.assertTrue(apart, name + " draws two slots on top of each other at "
                            + one.x + "," + one.y);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyRowHasRoomForItsLabel(GameTestHelper helper) {
        for (AbstractContainerMenu menu : menus(helper)) {
            String name = menu.getClass().getSimpleName();
            for (Slot row : menu.slots) {
                // Only the rows of the game carry a label; the inventory grid has none.
                if (row.container instanceof net.minecraft.world.entity.player.Inventory) continue;
                for (Slot other : menu.slots) {
                    if (other == row) continue;
                    // The line of text above a row must not land inside another row of slots.
                    boolean sameColumn = Math.abs(other.x - row.x) < SLOT;
                    boolean labelInside = row.y - LABEL >= other.y && row.y - LABEL < other.y + SLOT;
                    helper.assertFalse(sameColumn && labelInside,
                            name + " writes the label of the row at " + row.y + " inside the row at " + other.y);
                }
            }
        }
        helper.succeed();
    }
}
