package dev.gamblingitems.fabric.block;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.machines.CabinetControls;
import dev.gamblingitems.core.machines.CabinetControls.Control;
import dev.gamblingitems.fabric.Compat;
import dev.gamblingitems.fabric.config.ModConfig;
import dev.gamblingitems.fabric.machines.MachineMenu;
import dev.gamblingitems.fabric.machines.MachineSetup;
import dev.gamblingitems.fabric.slots.SlotMenu;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Playing a cabinet at the cabinet itself: pull its lever, turn its crank, press its button with a
 * valued item in hand and that stack is played, through the same menu and the same checks as its
 * window. The winnings wait in the machine, as they do behind the window, until the player touches
 * the same control with an empty hand. Anywhere else on the cabinet still opens its window.
 */
public final class CabinetPlay {
    private CabinetPlay() {}

    /** The control under a click on a cabinet, read in the space of its model. */
    public static Control control(GameMode mode, BlockState state, BlockPos anchor, Vec3 hit) {
        double x = hit.x - anchor.getX(), y = hit.y - anchor.getY(), z = hit.z - anchor.getZ();
        // The model is turned clockwise a quarter at a time from north; turn the click back as many times.
        Direction facing = state.getValue(SlotMachineBlock.FACING);
        for (int step = 0; step < (facing.get2DDataValue() + 2) % 4; step++) {
            double turned = x;
            x = z;
            z = 1 - turned;
        }
        return CabinetControls.at(mode, 16 - x * 16, y * 16, z * 16);
    }

    /**
     * Stages as much of the stack as the machine accepts, from the whole stack down, in one slot of
     * the vault; returns the count staged, the slot left empty when none is accepted.
     */
    public static int stage(ItemStack hand, Container vault, int slot, BooleanSupplier playable) {
        for (int count = hand.getCount(); count > 0; count--) {
            vault.setItem(slot, hand.copyWithCount(count));
            if (playable.getAsBoolean()) return count;
        }
        vault.setItem(slot, ItemStack.EMPTY);
        return 0;
    }

    /** Answers a click on a control of a cabinet; false when the click should open its window instead. */
    public static boolean use(ServerPlayer player, GameMode mode, BlockPos anchor, Control control) {
        Level level = player.level();
        if (control == Control.NONE || player.isSpectator()
                || !(level.getBlockEntity(anchor) instanceof GameStationEntity cabinet)) return false;
        CabinetMenu menu = menu(player, mode, anchor);
        // A closed machine opens its window, which says so.
        if (menu == null) return false;
        boolean busy = level.getGameTime() < cabinet.startedAt + cabinet.animationTicks;
        if (control == Control.NEXT) {
            if (menu.choices() <= 1) return false;
            if (busy) return refuse(player, "world_play_busy");
            cabinet.targetSlot = (Math.max(0, cabinet.targetSlot) + 1) % menu.choices();
            // Shown on the cabinet as a choice, no longer as the last outcome.
            cabinet.reels = "-1,0," + cabinet.targetSlot;
            cabinet.setChanged();
            level.sendBlockUpdated(anchor, cabinet.getBlockState(), cabinet.getBlockState(), 3);
            Compat.message(player, Component.translatable("gui.gamblingitems.world_play_position",
                    cabinet.targetSlot + 1, menu.choices()), true);
            return true;
        }
        ItemStack hand = player.getMainHandItem();
        if (!menu.accepts(hand)) {
            if (!menu.collectAll()) return false;
            Compat.message(player, Component.translatable("gui.gamblingitems.world_play_collected"), true);
            return true;
        }
        if (busy) return refuse(player, "world_play_busy");
        ItemStack played = hand.copy();
        int before = hand.getCount();
        int choice = menu.choices() > 1 ? Math.max(0, cabinet.targetSlot) % menu.choices() : 0;
        if (!menu.playFromHand(hand, choice)) {
            Compat.message(player, Component.translatable("gui.gamblingitems.world_play_refused",
                    java.math.BigDecimal.valueOf(menu.minimumStake(), 3).stripTrailingZeros().toPlainString()), true);
            return true;
        }
        Compat.message(player, Component.translatable("gui.gamblingitems.world_play_started",
                Component.literal((before - hand.getCount()) + " × ").append(played.getHoverName())), true);
        return true;
    }

    private static boolean refuse(ServerPlayer player, String key) {
        Compat.message(player, Component.translatable("gui.gamblingitems." + key), true);
        return true;
    }

    /** The player's menu for the machine, never opened; null for a machine closed on this server. */
    private static CabinetMenu menu(ServerPlayer player, GameMode mode, BlockPos anchor) {
        ContainerLevelAccess access = ContainerLevelAccess.create(player.level(), anchor);
        PlayerVaults vaults = PlayerVaults.get(player.server);
        if (mode == GameMode.SLOT_MACHINE) {
            return new SlotMenu(0, player.getInventory(), ModConfig.slots(),
                    vaults.forPlayer(player.getUUID(), VaultSection.SLOT_MACHINE), access);
        }
        MachineSetup setup = ModConfig.machine(mode);
        if (!setup.settings().enabled()) return null;
        return new MachineMenu(0, player.getInventory(), setup,
                vaults.forPlayer(player.getUUID(), VaultSection.fromId(mode.id())), access);
    }
}
