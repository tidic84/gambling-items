package dev.gamblingitems.fabric.block;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.menu.GameMenus;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A control that is drawn outside the monitor can never be pressed, whatever the server would
 * have accepted. These tests hold the panel to the surface the six blocks actually cover, and
 * check that pressing the middle of a control really triggers it, side blocks included.
 */
public class PanelReachGameTests implements FabricGameTest {
    /** Turns a point of the panel into the click a player would make on the north face. */
    private static BlockHitResult click(BlockPos pos, double panelX, double panelY) {
        double localX = 1 - (panelX / 128.0 + 0.5);
        double localY = 0.5 - panelY / 128.0;
        return new BlockHitResult(new Vec3(pos.getX() + localX, pos.getY() + localY, pos.getZ()),
                Direction.NORTH, pos, false);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyControlOfEveryGameSitsOnTheMonitor(GameTestHelper helper) {
        for (GameMode mode : GameMode.values()) {
            if (!GameMenus.AVAILABLE.contains(mode)) continue;
            for (int phase = 0; phase < 4; phase++) {
                for (StationPanel.Control control : StationPanel.controls(mode, phase)) {
                    helper.assertTrue(control.reachable(),
                            "Control " + control.label() + " of " + mode.id() + " must be on the monitor");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyControlAnsweresWhereItIsDrawn(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        for (GameMode mode : GameMode.values()) {
            if (!GameMenus.AVAILABLE.contains(mode)) continue;
            for (StationPanel.Control control : StationPanel.controls(mode, 0)) {
                BlockHitResult hit = click(pos, control.x() + control.width() / 2.0,
                        control.y() + control.height() / 2.0);
                helper.assertTrue(StationPanel.button(mode, Direction.NORTH, hit, pos) == control.action(),
                        "The middle of " + control.label() + " presses it on " + mode.id());
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void collectingIsReachableOnEveryGameThatPaysOut(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        for (GameMode mode : GameMode.values()) {
            if (!GameMenus.AVAILABLE.contains(mode)) continue;
            StationPanel.Control collect = null;
            for (StationPanel.Control control : StationPanel.controls(mode, 0)) {
                if (control.action() == 6) collect = control;
            }
            helper.assertTrue(collect != null, "Every game offers a way to take the winnings on " + mode.id());
            BlockHitResult hit = click(pos, collect.x() + collect.width() / 2.0,
                    collect.y() + collect.height() / 2.0);
            helper.assertTrue(StationPanel.button(mode, Direction.NORTH, hit, pos) == 6,
                    "Collecting answers on " + mode.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyControlHasAWordOnIt(GameTestHelper helper) {
        // A label without a translation shows its own key on the block, which reads as nonsense.
        for (String language : new String[] {"en_us", "fr_fr"}) {
            com.google.gson.JsonObject words = read(language);
            for (GameMode mode : GameMode.values()) {
                if (!GameMenus.AVAILABLE.contains(mode)) continue;
                for (int phase = 0; phase < 4; phase++) {
                    for (StationPanel.Control control : StationPanel.controls(mode, phase)) {
                        String key = "gui.gamblingitems.panel." + control.label();
                        helper.assertTrue(words.has(key),
                                key + " is missing from " + language + " for " + mode.id());
                    }
                }
            }
        }
        helper.succeed();
    }

    private static com.google.gson.JsonObject read(String language) {
        String path = "/assets/gamblingitems/lang/" + language + ".json";
        try (java.io.InputStream stream = StationPanel.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing " + path);
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
                    stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot read " + path, exception);
        }
    }
}
