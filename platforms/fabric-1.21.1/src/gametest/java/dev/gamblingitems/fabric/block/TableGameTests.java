package dev.gamblingitems.fabric.block;

import dev.gamblingitems.core.roulette.RouletteWheel;
import dev.gamblingitems.fabric.ModContent;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A table is six blocks, three across and two deep, with the game on the block the player stands
 * at. These tests keep that shape honest: it is built as one, broken as one, and every part leads
 * back to the same game.
 */
public class TableGameTests implements FabricGameTest {
    private static GameTableBlock[] tables() {
        return new GameTableBlock[] {ModContent.BLACKJACK_TABLE, ModContent.ROULETTE_TABLE,
                ModContent.BINGO_TABLE};
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aTableIsSixBlocksWithOneGameAmongThem(GameTestHelper helper) {
        for (GameTableBlock table : tables()) {
            BlockPos root = helper.absolutePos(new BlockPos(2, 1, 2));
            var state = table.defaultBlockState().setValue(GameTableBlock.FACING, Direction.NORTH);
            helper.getLevel().setBlock(root, state, 3);
            table.setPlacedBy(helper.getLevel(), root, state, null, ItemStack.EMPTY);
            for (int part = 0; part < 6; part++) {
                BlockPos pos = GameTableBlock.partPos(root, Direction.NORTH, part);
                var tile = helper.getLevel().getBlockState(pos);
                helper.assertTrue(tile.is(table) && tile.getValue(GameTableBlock.PART) == part,
                        "Each part of the table occupies a real block");
                helper.assertTrue(GameTableBlock.anchor(pos, tile).equals(root),
                        "Every part points back to the block the player stands at");
                helper.assertTrue((helper.getLevel().getBlockEntity(pos) != null)
                                == (part == GameTableBlock.ANCHOR_PART),
                        "Only one block of the table carries the game");
            }
            // Breaking any part takes the whole table with it.
            helper.getLevel().destroyBlock(GameTableBlock.partPos(root, Direction.NORTH, 5), false);
            for (int part = 0; part < 6; part++) {
                BlockPos pos = GameTableBlock.partPos(root, Direction.NORTH, part);
                helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.AIR),
                        "The whole table goes when one part does");
            }
        }
        helper.succeed();
    }

    /** Where to click on the top of a table facing north to land on that point of the felt. */
    private static BlockHitResult click(BlockPos anchor, float feltX, float feltY) {
        double localX = 0.5 - feltX / 128.0;
        double localZ = 1 - feltY / 128.0;
        return new BlockHitResult(new Vec3(anchor.getX() + localX, anchor.getY() + 1, anchor.getZ() + localZ),
                Direction.UP, anchor, false);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyControlOfATableSitsOnTheFeltAndAnswersThere(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));
        for (GameTableBlock table : tables()) {
            for (int phase = 0; phase < 4; phase++) {
                for (var control : TableLayout.controls(table.mode(), phase)) {
                    helper.assertTrue(TableLayout.reachable(control),
                            "Control " + control.label() + " must be printed on the felt");
                }
            }
            for (var control : TableLayout.controls(table.mode(), 0)) {
                BlockHitResult hit = click(anchor, control.x() + control.width() / 2f,
                        control.y() + control.height() / 2f);
                helper.assertTrue(TableLayout.button(table.mode(), Direction.NORTH, hit, anchor)
                                == control.action(),
                        "The middle of " + control.label() + " answers on " + table.mode().id());
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyPrintedNumberOfARouletteIsItsOwnBet(GameTestHelper helper) {
        for (int number = 0; number < RouletteWheel.POCKETS; number++) {
            float[] box = TableLayout.numberBox(number);
            var bet = TableLayout.betAt(box[0] + box[2] / 2, box[1] + box[3] / 2);
            helper.assertTrue(bet != null && bet.type() == RouletteWheel.BetType.STRAIGHT
                            && bet.choice() == number,
                    "Clicking the printed " + number + " backs that number");
        }
        for (int index = 0; index < 6; index++) {
            float[] box = TableLayout.outsideBox(index);
            var bet = TableLayout.betAt(box[0] + box[2] / 2, box[1] + box[3] / 2);
            helper.assertTrue(bet != null && bet.equals(TableLayout.outsideBet(index)),
                    "Clicking an even money area backs it");
        }
        // The side of the felt the wheel stands on is not a bet.
        helper.assertTrue(TableLayout.betAt(-124, -18) == null, "The wheel itself takes no chips");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theControlsAndTheBetsNeverShareTheSameSpot(GameTestHelper helper) {
        for (var control : TableLayout.controls(dev.gamblingitems.core.GameMode.ROULETTE, 0)) {
            for (float x = control.x(); x < control.x() + control.width(); x += 4) {
                for (float y = control.y(); y < control.y() + control.height(); y += 4) {
                    helper.assertTrue(TableLayout.betAt(x, y) == null,
                            "A control of the felt is never printed over a bet");
                }
            }
        }
        helper.succeed();
    }

    /**
     * The whole chain of a click on a printed number: the block the ray hits, the felt point it
     * becomes, the bet that is read there, and the chips that end up on it.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void clickingAPrintedNumberPutsTheChipsOnIt(GameTestHelper helper) {
        dev.gamblingitems.fabric.roulette.RouletteGames.clear();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        var state = ModContent.ROULETTE_TABLE.defaultBlockState()
                .setValue(GameTableBlock.FACING, Direction.NORTH);
        helper.getLevel().setBlock(anchor, state, 3);
        ModContent.ROULETTE_TABLE.setPlacedBy(helper.getLevel(), anchor, state, null, ItemStack.EMPTY);
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 1.5);
        var game = dev.gamblingitems.fabric.roulette.RouletteGames.host(helper.getLevel(), anchor);
        var vault = game.vault(player.getUUID());
        for (int slot = 0; slot < dev.gamblingitems.fabric.roulette.RouletteSettings.VAULT_SIZE; slot++) {
            vault.setItem(slot, ItemStack.EMPTY);
        }
        vault.setItem(dev.gamblingitems.fabric.roulette.RouletteSettings.INPUT_SLOT,
                new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 4));

        float[] box = TableLayout.numberBox(17);
        float feltX = box[0] + box[2] / 2, feltY = box[1] + box[3] / 2;
        // The printed numbers are on the far half of the felt, so the ray lands on a back block.
        BlockPos clicked = new BlockPos(anchor.getX(), anchor.getY(), anchor.getZ() + 1);
        double localX = 0.5 - feltX / 128.0, localZ = 1 - feltY / 128.0;
        var hit = new BlockHitResult(new Vec3(anchor.getX() + localX, anchor.getY() + 0.8125,
                anchor.getZ() + localZ), Direction.UP, clicked, false);
        helper.assertTrue(helper.getLevel().getBlockState(clicked).is(ModContent.ROULETTE_TABLE),
                "The far half of the table is a block of the table");
        helper.assertTrue(GameTableBlock.anchor(clicked, helper.getLevel().getBlockState(clicked))
                        .equals(anchor), "And it points back at the block that carries the game");
        helper.assertTrue(TableLayout.button(dev.gamblingitems.core.GameMode.ROULETTE,
                        Direction.NORTH, hit, anchor) < 0, "A number is not one of the controls");
        var read = TableLayout.betAt(feltX, feltY);
        helper.assertTrue(read != null && read.choice() == 17, "The click reads the number it landed on");

        helper.getLevel().getBlockState(clicked).useWithoutItem(helper.getLevel(), player, hit);
        var seat = game.seatOf(player.getUUID());
        helper.assertTrue(seat != null, "Clicking a number seats the player at the table");
        helper.assertTrue(seat.stakes().containsKey(read), "And puts the prepared chips on that number");

        // With nothing prepared, the area takes what is in the hand: a table is played standing up.
        dev.gamblingitems.fabric.block.StationInteractions.close(player.getUUID());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
        float[] red = TableLayout.outsideBox(2);
        var redHit = new BlockHitResult(new Vec3(
                anchor.getX() + 0.5 - (red[0] + red[2] / 2) / 128.0, anchor.getY() + 0.8125,
                anchor.getZ() + 1 - (red[1] + red[3] / 2) / 128.0), Direction.UP, clicked, false);
        helper.getLevel().getBlockState(clicked).useWithoutItem(helper.getLevel(), player, redHit);
        helper.assertTrue(game.seatOf(player.getUUID()).stakes()
                        .containsKey(TableLayout.outsideBet(2)),
                "The held item is put on the area that was clicked");
        dev.gamblingitems.fabric.block.StationInteractions.close(player.getUUID());
        dev.gamblingitems.fabric.roulette.RouletteGames.clear();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aTableIsThreeAcrossAndTwoDeepInEveryOrientation(GameTestHelper helper) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos root = new BlockPos(0, 0, 0);
            var seen = new java.util.HashSet<BlockPos>();
            for (int part = 0; part < 6; part++) seen.add(GameTableBlock.partPos(root, facing, part));
            helper.assertTrue(seen.size() == 6, "The six parts never share a block facing " + facing);
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : seen) {
                minX = Math.min(minX, pos.getX());
                maxX = Math.max(maxX, pos.getX());
                minZ = Math.min(minZ, pos.getZ());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            int across = facing.getAxis() == Direction.Axis.Z ? maxX - minX : maxZ - minZ;
            int deep = facing.getAxis() == Direction.Axis.Z ? maxZ - minZ : maxX - minX;
            helper.assertTrue(across == 2, "Three blocks across facing " + facing);
            helper.assertTrue(deep == 1, "Two blocks deep facing " + facing);
        }
        helper.succeed();
    }
}
