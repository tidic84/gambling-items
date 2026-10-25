package dev.gamblingitems.fabric.arcade;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.machines.CabinetControls.Control;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.block.CabinetPlay;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.block.SlotMachineBlock;
import dev.gamblingitems.fabric.config.ModConfig;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The arcade: the giant cabinets, the cashier's exact change, video poker, the shared race, the operator's switch. */
public class ArcadeGameTests implements FabricGameTest {
    private static BlockPos place(GameTestHelper helper, Block block) {
        BlockPos root = helper.absolutePos(new BlockPos(3, 1, 2));
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
    public void aGiantCabinetPaysTicketsAndKeepsTheStake(GameTestHelper helper) {
        BlockPos root = place(helper, ModContent.PLINKO);
        helper.assertTrue(helper.getLevel().getBlockState(root.west()).getBlock() == ModContent.PLINKO
                        && helper.getLevel().getBlockState(root.west()).getValue(SlotMachineBlock.RIGHT),
                "Plinko stands two blocks wide");
        ServerPlayer player = player(helper, root, new ItemStack(Items.DIAMOND, 1));
        helper.assertTrue(CabinetPlay.use(player, GameMode.PLINKO, root, Control.PLAY), "The ball is dropped");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "The stake is played");
        GameStationEntity cabinet = (GameStationEntity) helper.getLevel().getBlockEntity(root);
        helper.assertFalse(cabinet.reels.isEmpty(), "The cabinet shows the path drawn");
        var history = Arcade.ledger(player).history(player.getUUID(), Long.MAX_VALUE);
        helper.assertTrue(ModConfig.arcade().tickets() && history.size() == 1 && history.get(0).mode().equals("plinko"),
                "The play is written to the arcade ledger, paid in tickets");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theCashierChangesItemsIntoChipsAndBackExactly(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, ModContent.CASHIER.defaultBlockState());
        ServerPlayer player = player(helper, pos, ItemStack.EMPTY);
        player.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + 1.5);
        CashierSetup setup = new CashierSetup(ModConfig.values(), ModConfig.arcade(), java.util.List.of());
        SimpleContainer tray = new SimpleContainer(CashierMenu.TRAY_SLOTS);
        CashierMenu menu = new CashierMenu(1, player.getInventory(), setup, tray, ContainerLevelAccess.create(helper.getLevel(), pos));
        tray.setItem(0, new ItemStack(Items.DIAMOND, 3));
        long value = menu.itemValue();
        helper.assertTrue(value > 0, "A diamond has a value");
        helper.assertTrue(menu.clickMenuButton(player, CashierMenu.TO_CHIPS), "The items are changed");
        helper.assertTrue(menu.chipValue() > 0 && menu.chipValue() + menu.itemValue() == value, "Into chips, and change, for the same value: " + value + " -> " + menu.chipValue() + " + " + menu.itemValue()
                + " " + java.util.stream.IntStream.range(0, CashierMenu.TRAY_SLOTS).mapToObj(tray::getItem).filter(stack -> !stack.isEmpty()).toList());
        helper.assertTrue(menu.clickMenuButton(player, CashierMenu.TO_ITEMS), "The chips are changed back");
        helper.assertTrue(menu.chipValue() == 0 && menu.itemValue() == value, "Into items of the same value");
        helper.assertFalse(menu.clickMenuButton(player, CashierMenu.PRIZE), "A prize needs tickets");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void videoPokerDealsHoldsAndDraws(GameTestHelper helper) {
        BlockPos root = place(helper, ModContent.VIDEO_POKER);
        ServerPlayer player = player(helper, root, new ItemStack(Items.DIAMOND, 1));
        GameStationEntity cabinet = (GameStationEntity) helper.getLevel().getBlockEntity(root);
        helper.assertTrue(CabinetPlay.use(player, GameMode.VIDEO_POKER, root, Control.PLAY), "The red key deals");
        int[] dealt = VideoPoker.split(cabinet.cards);
        helper.assertTrue(dealt != null && cabinet.phase == VideoPoker.DEALT, "Five cards are dealt");
        helper.assertTrue(CabinetPlay.use(player, GameMode.VIDEO_POKER, root, Control.PICK_1)
                && cabinet.targetSlot == 1, "The first card is held");
        helper.runAfterDelay(ModConfig.machine(GameMode.VIDEO_POKER).settings().playTicks() + 1, () -> {
            helper.assertTrue(CabinetPlay.use(player, GameMode.VIDEO_POKER, root, Control.PLAY), "The red key draws");
            int[] drawn = VideoPoker.split(cabinet.cards);
            helper.assertTrue(cabinet.phase == VideoPoker.DRAWN && drawn != null && drawn[0] == dealt[0],
                    "The hand is drawn and the held card kept");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600)
    public void oneRaceSettlesEveryBet(GameTestHelper helper) {
        BlockPos root = place(helper, ModContent.HORSE_RACE);
        ServerPlayer first = player(helper, root, new ItemStack(Items.DIAMOND, 1));
        ServerPlayer second = player(helper, root, new ItemStack(Items.DIAMOND, 1));
        GameStationEntity cabinet = (GameStationEntity) helper.getLevel().getBlockEntity(root);
        helper.assertTrue(CabinetPlay.use(first, GameMode.HORSE_RACE, root, Control.PICK_1), "A bet on the first horse");
        helper.assertTrue(CabinetPlay.use(second, GameMode.HORSE_RACE, root, Control.PICK_2), "A bet on the second");
        helper.assertTrue(cabinet.phase == 0 && cabinet.publicBets.split("\n").length == 2, "Both bets wait for the start");
        helper.succeedWhen(() -> {
            helper.assertTrue(cabinet.phase == 1, "The race has started");
            long plays = Arcade.ledger(first).history(first.getUUID(), Long.MAX_VALUE).size()
                    + Arcade.ledger(second).history(second.getUUID(), Long.MAX_VALUE).size();
            helper.assertTrue(plays == 2, "And settled both bets at once");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void anOperatorClosesOneCabinet(GameTestHelper helper) {
        BlockPos root = place(helper, ModContent.LUCKY_WHEEL);
        var server = helper.getLevel().getServer();
        var source = server.createCommandSourceStack().withLevel(helper.getLevel());
        server.getCommands().performPrefixedCommand(source,
                "gamblingitems cabinet " + root.getX() + " " + root.getY() + " " + root.getZ() + " close");
        GameStationEntity cabinet = (GameStationEntity) helper.getLevel().getBlockEntity(root);
        helper.assertTrue(cabinet.closed, "The command closes the cabinet");
        ServerPlayer player = player(helper, root, new ItemStack(Items.DIAMOND, 1));
        CabinetPlay.use(player, GameMode.LUCKY_WHEEL, root, Control.PLAY);
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "A closed cabinet takes nothing");
        server.getCommands().performPrefixedCommand(source,
                "gamblingitems cabinet " + root.getX() + " " + root.getY() + " " + root.getZ() + " minimum 20000");
        helper.assertTrue(!cabinet.closed || cabinet.minimumStake == 20_000, "A smallest stake of its own is set");
        server.getCommands().performPrefixedCommand(source,
                "gamblingitems cabinet " + root.getX() + " " + root.getY() + " " + root.getZ() + " reset");
        helper.assertTrue(!cabinet.closed && cabinet.minimumStake == 0, "Reset gives it back to the configuration");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void neonTubesLightTheRoom(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlockAndUpdate(pos, ModContent.NEON_TUBE.defaultBlockState());
        helper.succeedWhen(() -> {
            int light = helper.getLevel().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos.east());
            helper.assertTrue(light >= 14, "A neon tube lights the block beside it, not " + light);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theCroupierKeepsTheCashiersWindowOpen(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, ModContent.CROUPIER.defaultBlockState());
        ServerPlayer player = player(helper, pos, ItemStack.EMPTY);
        CashierSetup setup = new CashierSetup(ModConfig.values(), ModConfig.arcade(), java.util.List.of());
        CashierMenu menu = new CashierMenu(1, player.getInventory(), setup, new SimpleContainer(CashierMenu.TRAY_SLOTS),
                ContainerLevelAccess.create(helper.getLevel(), pos));
        helper.assertTrue(menu.stillValid(player), "The croupier serves like the counter");
        helper.getLevel().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        helper.assertFalse(menu.stillValid(player), "Without him the window closes");
        helper.succeed();
    }
}
