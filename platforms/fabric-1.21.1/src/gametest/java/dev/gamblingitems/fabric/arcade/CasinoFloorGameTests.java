package dev.gamblingitems.fabric.arcade;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.machines.CabinetControls.Control;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.block.CabinetPlay;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.block.SlotMachineBlock;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** The casino floor: the table games on their cabinets, VIP cabinets, dyed themes, stools and the big win's light. */
public class CasinoFloorGameTests implements FabricGameTest {
    private static BlockPos place(GameTestHelper helper, Block block, int x) {
        BlockPos root = helper.absolutePos(new BlockPos(x, 1, 2));
        BlockState state = block.defaultBlockState().setValue(SlotMachineBlock.FACING, Direction.NORTH);
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

    @GameTest(template = EMPTY_STRUCTURE)
    public void theTableGamesArePlayedFromTheirDeck(GameTestHelper helper) {
        Object[][] games = {{ModContent.CRAPS, GameMode.CRAPS, Control.PICK_2, 1, 3},
                {ModContent.BACCARAT, GameMode.BACCARAT, Control.PICK_3, 2, 3},
                {ModContent.KENO, GameMode.KENO, Control.PLAY, 0, 6},
                {ModContent.SCRATCH_CARD, GameMode.SCRATCH_CARD, Control.PLAY, 0, 6}};
        int x = 0;
        for (Object[] game : games) {
            BlockPos root = place(helper, (Block) game[0], x);
            x += 2;
            GameMode mode = (GameMode) game[1];
            ServerPlayer player = player(helper, root, new ItemStack(Items.DIAMOND, 1));
            helper.assertTrue(CabinetPlay.use(player, mode, root, (Control) game[2]), mode.id() + " is played");
            helper.assertTrue(player.getMainHandItem().isEmpty(), mode.id() + ": the stake is played");
            GameStationEntity cabinet = (GameStationEntity) helper.getLevel().getBlockEntity(root);
            String[] shown = cabinet.reels.split(",");
            int event = Integer.parseInt(shown[0]);
            helper.assertTrue(shown.length == 3 && Integer.parseInt(shown[2]) == (int) game[3],
                    mode.id() + " remembers the bet played: " + cabinet.reels);
            helper.assertTrue(event >= 0 && event < (int) game[4], mode.id() + " shows an event of its game: " + event);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aVipCabinetWantsTheCard(GameTestHelper helper) {
        BlockPos root = place(helper, ModContent.KENO, 3);
        GameStationEntity cabinet = (GameStationEntity) helper.getLevel().getBlockEntity(root);
        cabinet.vip = true;
        ServerPlayer player = player(helper, root, new ItemStack(Items.DIAMOND, 1));
        CabinetPlay.use(player, GameMode.KENO, root, Control.PLAY);
        helper.assertTrue(player.getMainHandItem().getCount() == 1 && cabinet.reels.isEmpty(), "Without the card, nothing is played");
        player.getInventory().add(new ItemStack(ModContent.VIP_CARD));
        helper.assertTrue(CabinetPlay.use(player, GameMode.KENO, root, Control.PLAY) && player.getMainHandItem().isEmpty(),
                "With the card the stake is played");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aDyeGivesACabinetItsLookAndTakesItBack(GameTestHelper helper) {
        BlockPos root = place(helper, ModContent.SLOT_MACHINE, 3);
        GameStationEntity cabinet = (GameStationEntity) helper.getLevel().getBlockEntity(root);
        ItemStack dye = new ItemStack(Items.RED_DYE, 4);
        ServerPlayer player = player(helper, root, dye);
        CabinetPlay.paint(player, cabinet, dye, DyeColor.RED.getId());
        helper.assertTrue(cabinet.theme == 1 + DyeColor.RED.getId()
                && dev.gamblingitems.core.machines.CabinetTheme.of(cabinet.theme) == dev.gamblingitems.core.machines.CabinetTheme.VEGAS,
                "Red makes it Vegas");
        CabinetPlay.paint(player, cabinet, dye, DyeColor.RED.getId());
        helper.assertTrue(cabinet.theme == 0, "The same dye again brings back its own look");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
    public void aStoolSeatsOnePlayerAndItsSeatGoesWithThem(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlockAndUpdate(pos, ModContent.CASINO_STOOL.defaultBlockState());
        ServerPlayer player = player(helper, pos, ItemStack.EMPTY);
        StoolBlock.sit(helper.getLevel(), pos, player);
        helper.assertTrue(player.isPassenger(), "The player sits");
        ServerPlayer other = player(helper, pos, ItemStack.EMPTY);
        StoolBlock.sit(helper.getLevel(), pos, other);
        helper.assertFalse(other.isPassenger(), "One player per stool");
        player.stopRiding();
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(Display.BlockDisplay.class, new AABB(pos)).isEmpty(),
                    "The seat goes once it is left");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
    public void aBigWinLightsTheCabinetForItsCelebration(GameTestHelper helper) {
        BlockPos root = place(helper, ModContent.SLOT_MACHINE, 3);
        GameStationEntity cabinet = (GameStationEntity) helper.getLevel().getBlockEntity(root);
        cabinet.multiplier = 2_000;
        cabinet.animationTicks = 5;
        cabinet.show("winner", "", "slot_won", "", true, 100);
        helper.runAfterDelay(7, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(root).getValue(SlotMachineBlock.LIT), "Lit once the win shows: "
                    + helper.getLevel().getBlockState(root) + " celebrating " + cabinet.celebrating(helper.getLevel().getGameTime())
                    + " ticks " + helper.getLevel().getBlockTicks().hasScheduledTick(root, ModContent.SLOT_MACHINE));
            helper.assertTrue(helper.getLevel().getLightEmission(root) == 15, "And lights the room");
        });
        helper.runAfterDelay(5 + SlotMachineBlock.CELEBRATION + 3, () -> {
            helper.assertFalse(helper.getLevel().getBlockState(root).getValue(SlotMachineBlock.LIT), "Out after the celebration");
            helper.succeed();
        });
    }
}
