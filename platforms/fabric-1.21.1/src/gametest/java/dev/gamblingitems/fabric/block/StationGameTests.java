package dev.gamblingitems.fabric.block;

import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.config.ModConfig;
import dev.gamblingitems.fabric.roulette.*;
import dev.gamblingitems.fabric.vault.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class StationGameTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void crashControlsMatchTheirWholeSurfaceInEveryOrientation(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(3, 4, 5);
        var mode = dev.gamblingitems.core.GameMode.CRASH;
        helper.assertTrue(StationPanel.controls(mode, 2).size() == 3, "Crash has only three controls");
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (var control : StationPanel.controls(mode, 2)) {
                for (double dx : new double[]{0.1, control.width() / 2.0, control.width() - 0.1}) {
                    for (double dy : new double[]{0.1, control.height() - 0.1}) {
                        double u = 0.5 + (control.x() + dx) / 128.0;
                        double y = 0.5 - (control.y() + dy) / 128.0;
                        Vec3 local = switch (facing) {
                            case NORTH -> new Vec3(1 - u, y, 0);
                            case SOUTH -> new Vec3(u, y, 1);
                            case WEST -> new Vec3(0, y, u);
                            default -> new Vec3(1, y, 1 - u);
                        };
                        Vec3 point = local.add(anchor.getX(), anchor.getY(), anchor.getZ());
                        var hit = new BlockHitResult(point, facing, BlockPos.containing(point), false);
                        helper.assertTrue(StationPanel.button(mode, facing, hit, anchor) == control.action(),
                                "The full drawn control including side tiles is clickable");
                        helper.assertTrue(StationPanel.button(mode, facing.getOpposite(), hit, anchor) == -1,
                                "Back face clicks are rejected");
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aLargeMonitorHasSixPartsOneControllerAndBreaksAsOne(GameTestHelper helper) {
        var block = ModContent.ROULETTE_STATION;
        BlockPos root = helper.absolutePos(new BlockPos(1, 1, 1));
        var state = block.defaultBlockState();
        helper.getLevel().setBlock(root, state, 3);
        block.setPlacedBy(helper.getLevel(), root, state, null, ItemStack.EMPTY);
        for (int part = 0; part < 6; part++) {
            BlockPos pos = GameStationBlock.partPos(root, Direction.NORTH, part);
            var tile = helper.getLevel().getBlockState(pos);
            helper.assertTrue(tile.is(block) && tile.getValue(GameStationBlock.PART) == part, "Each tile occupies a real block");
            helper.assertTrue(GameStationBlock.anchor(pos, tile).equals(root), "Every tile points to the same controller");
            helper.assertTrue((helper.getLevel().getBlockEntity(pos) != null) == (part == 1), "Only one game controller exists");
        }
        helper.getLevel().destroyBlock(GameStationBlock.partPos(root, Direction.NORTH, 5), false);
        for (int part = 0; part < 6; part++) helper.assertTrue(
                helper.getLevel().getBlockState(GameStationBlock.partPos(root, Direction.NORTH, part)).isAir(),
                "Breaking an extension removes the whole monitor");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void placementRefusesToOverwriteAnOccupiedTile(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var root = helper.absolutePos(new BlockPos(1, 1, 1));
        var stack = new ItemStack(ModContent.ROULETTE_STATION_ITEM);
        var hit = new BlockHitResult(root.getCenter(), Direction.UP, root, false);
        var context = new net.minecraft.world.item.context.BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
        var target = context.getClickedPos();
        var facing = context.getHorizontalDirection().getOpposite();
        BlockPos blocked = GameStationBlock.partPos(target, facing, 4);
        helper.getLevel().setBlock(blocked, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(ModContent.ROULETTE_STATION.getStateForPlacement(context) == null, "No partial placement over an obstacle");
        helper.assertTrue(helper.getLevel().getBlockState(blocked).is(net.minecraft.world.level.block.Blocks.STONE), "Obstacle remains intact");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void controlsMatchEveryOrientationAndRejectBackAndGaps(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 4, 5);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int button = 0; button < 9; button++) {
                double u = 0.5 + (StationPanel.LEFT + button % 3 * StationPanel.CELL_WIDTH + 10) / 128.0;
                double y = 0.5 - (StationPanel.TOP + button / 3 * StationPanel.ROW_HEIGHT + 6) / 128.0;
                Vec3 point = switch (facing) {
                    case NORTH -> new Vec3(1 - u, y, 0);
                    case SOUTH -> new Vec3(u, y, 1);
                    case WEST -> new Vec3(0, y, u);
                    default -> new Vec3(1, y, 1 - u);
                };
                BlockPos tile = GameStationBlock.partPos(pos, facing, (int) Math.floor(u) + 1 + 3 * (int) Math.floor(y));
                var hit = new BlockHitResult(point.add(pos.getX(), pos.getY(), pos.getZ()), facing, tile, false);
                helper.assertTrue(StationPanel.button(facing, hit, pos) == button, "Rendered button maps to the same server action");
                helper.assertTrue(StationPanel.button(facing.getOpposite(), hit, pos) == -1, "Back of screen cannot activate controls");
            }
        }
        var gap = new BlockHitResult(new Vec3(3 + 0.5 - (StationPanel.LEFT + StationPanel.CELL_WIDTH - 1) / 128.0, 4.5 - (StationPanel.TOP + 6) / 128.0, 5), Direction.NORTH, pos, false);
        helper.assertTrue(StationPanel.button(Direction.NORTH, gap) == -1, "Control gutters are inactive");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void twoPlayersBetOnTheBlockWithoutOpeningAnInventory(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModContent.ROULETTE_STATION);
        var station = (GameStationEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        var first = helper.makeMockServerPlayerInLevel();
        var second = helper.makeMockServerPlayerInLevel();
        for (var player : new net.minecraft.server.level.ServerPlayer[]{first, second}) {
            player.setPos(station.getBlockPos().getCenter());
            player.setItemInHand(InteractionHand.MAIN_HAND, ModConfig.roulette().stakeStack(10));
        }
        var game = RouletteGames.host(helper.getLevel(), station.getBlockPos());
        try {
            StationInteractions.click(first, station, 0);
            helper.assertTrue(first.getMainHandItem().getCount() == 9, "One item deposited exactly once");
            StationInteractions.click(second, station, 1);
            helper.assertTrue(second.getMainHandItem().isEmpty(), "The stack is deposited");
            StationInteractions.click(first, station, 3);
            StationInteractions.click(second, station, 4);
            helper.assertTrue(game.participants() == 2 && game.pot() == 11, "Both seats share one round");
            helper.assertTrue(first.containerMenu == first.inventoryMenu && second.containerMenu == second.inventoryMenu,
                    "Playing on a station does not open a GUI");
            StationInteractions.click(first, station, 2);
            StationInteractions.click(first, station, 3);
            helper.assertTrue(game.pot() == 11, "An engaged stake cannot be withdrawn or duplicated");
            game.tick();
            helper.assertTrue(station.publicBets.contains("red") && station.publicBets.contains("black"), "Bets are broadcast to spectators");
            first.setPos(station.getBlockPos().getCenter().add(20, 0, 0));
            StationInteractions.click(first, station, 1);
            helper.assertTrue(first.getMainHandItem().getCount() == 9, "Distant clicks cannot deposit");
        } finally {
            StationInteractions.close(first.getUUID());
            StationInteractions.close(second.getUUID());
            game.cancel();
            RouletteGames.clear();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aPublicCaseOpeningHasOneOwnerAndCannotBeOverwritten(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModContent.CASE_STATION);
        var station = (GameStationEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        var first = helper.makeMockServerPlayerInLevel();
        var second = helper.makeMockServerPlayerInLevel();
        var definition = ModConfig.cases().cases().get(0);
        for (var player : new net.minecraft.server.level.ServerPlayer[]{first, second}) {
            player.setPos(station.getBlockPos().getCenter());
            player.setItemInHand(InteractionHand.MAIN_HAND, definition.priceStack());
            StationInteractions.click(player, station, 1);
        }
        try {
            StationInteractions.click(first, station, 4);
            var firstVault = PlayerVaults.get(first.server).forPlayer(first.getUUID(), VaultSection.CASE_OPENING);
            var secondVault = PlayerVaults.get(second.server).forPlayer(second.getUUID(), VaultSection.CASE_OPENING);
            helper.assertTrue(station.animating() && !firstVault.getItem(1).isEmpty(), "Opening resolves on server with a public animation");
            StationInteractions.click(second, station, 4);
            StationInteractions.click(second, station, 6);
            helper.assertTrue(secondVault.getItem(0).getCount() == definition.priceCount() && secondVault.getItem(1).isEmpty(),
                    "Another player cannot overwrite the opening or take its reward");
            StationInteractions.click(first, station, 6);
            helper.assertTrue(!firstVault.getItem(1).isEmpty(), "Reward is not collectable during the animation");
        } finally {
            StationInteractions.close(first.getUUID());
            StationInteractions.close(second.getUUID());
        }
        helper.succeed();
    }
}
