package dev.gamblingitems.fabric.block;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.machines.CabinetControls.Control;
import dev.gamblingitems.fabric.ModContent;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A cabinet played at its lever: the right control is found whichever way it faces, and the hand pays the stake. */
public class CabinetPlayGameTests implements FabricGameTest {
    private static BlockPos place(GameTestHelper helper, Block block, Direction facing) {
        BlockPos root = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockState state = block.defaultBlockState().setValue(SlotMachineBlock.FACING, facing);
        helper.getLevel().setBlock(root, state, 3);
        block.setPlacedBy(helper.getLevel(), root, state, null, ItemStack.EMPTY);
        return root;
    }

    private static ServerPlayer player(GameTestHelper helper, BlockPos root, ItemStack hand) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(root.getX() + .5, root.getY(), root.getZ() - 1.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, hand);
        return player;
    }

    /** Where a point of the model (u, y, z in sixteenths) lies in the world, for a cabinet facing that way. */
    private static Vec3 world(BlockPos root, Direction facing, double u, double y, double z) {
        double x = (16 - u) / 16, w = z / 16;
        for (int step = 0; step < (facing.get2DDataValue() + 2) % 4; step++) {
            double turned = x;
            x = 1 - w;
            w = turned;
        }
        return new Vec3(root.getX() + x, root.getY() + y / 16, root.getZ() + w);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void stagingKeepsTheLargestCountTheMachineTakes(GameTestHelper helper) {
        SimpleContainer vault = new SimpleContainer(2);
        ItemStack hand = new ItemStack(Items.DIAMOND, 10);
        int count = CabinetPlay.stage(hand, vault, 0, () -> vault.getItem(0).getCount() <= 3);
        helper.assertTrue(count == 3 && vault.getItem(0).getCount() == 3, "The largest accepted count is staged");
        helper.assertTrue(hand.getCount() == 10, "Staging leaves the hand to the caller");
        helper.assertTrue(CabinetPlay.stage(hand, vault, 0, () -> false) == 0 && vault.getItem(0).isEmpty(),
                "A stack the machine never takes leaves the slot empty");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theLeverIsFoundWhicheverWayTheCabinetFaces(GameTestHelper helper) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos root = place(helper, ModContent.SLOT_MACHINE, facing);
            BlockState state = helper.getLevel().getBlockState(root);
            helper.assertTrue(CabinetPlay.control(GameMode.SLOT_MACHINE, state, root, world(root, facing, 16, 22, 6)) == Control.PLAY,
                    "The lever on the right flank plays, facing " + facing);
            helper.assertTrue(CabinetPlay.control(GameMode.SLOT_MACHINE, state, root, world(root, facing, 8, 24, 0)) == Control.NONE,
                    "The reels are not a control, facing " + facing);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aValuedStackInHandPlaysTheCabinetOnce(GameTestHelper helper) {
        BlockPos root = place(helper, ModContent.SLOT_MACHINE, Direction.NORTH);
        ServerPlayer player = player(helper, root, ItemStack.EMPTY);
        helper.assertFalse(CabinetPlay.use(player, GameMode.SLOT_MACHINE, root, Control.PLAY),
                "An empty hand with nothing waiting plays nothing");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 64));
        helper.assertTrue(CabinetPlay.use(player, GameMode.SLOT_MACHINE, root, Control.PLAY), "The lever is pulled");
        helper.assertTrue(player.getMainHandItem().getCount() < 64, "The stake is taken from the hand");
        GameStationEntity cabinet = (GameStationEntity) helper.getLevel().getBlockEntity(root);
        helper.assertFalse(cabinet.reels.isEmpty(), "The cabinet shows the line drawn");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 64));
        helper.assertTrue(CabinetPlay.use(player, GameMode.SLOT_MACHINE, root, Control.PLAY), "A second pull is answered");
        helper.assertTrue(player.getMainHandItem().getCount() == 64, "But refused while the reels still turn");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theJoystickMovesTheClawToTheNextPrize(GameTestHelper helper) {
        BlockPos root = place(helper, ModContent.PRIZE_CLAW, Direction.NORTH);
        ServerPlayer player = player(helper, root, ItemStack.EMPTY);
        GameStationEntity cabinet = (GameStationEntity) helper.getLevel().getBlockEntity(root);
        helper.assertTrue(CabinetPlay.use(player, GameMode.PRIZE_CLAW, root, Control.NEXT), "The joystick answers");
        helper.assertTrue(cabinet.targetSlot == 1 && cabinet.reels.equals("-1,0,1"), "The claw stands over the next prize");
        helper.succeed();
    }
}
