package dev.gamblingitems.fabric.block;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.machines.CabinetControls;
import dev.gamblingitems.core.machines.CabinetControls.Control;
import dev.gamblingitems.core.machines.MachineTable;
import dev.gamblingitems.core.slots.SlotRules;
import dev.gamblingitems.fabric.Compat;
import dev.gamblingitems.fabric.arcade.HorseRaces;
import dev.gamblingitems.fabric.arcade.VideoPoker;
import dev.gamblingitems.fabric.config.ModConfig;
import dev.gamblingitems.fabric.machines.MachineMenu;
import dev.gamblingitems.fabric.machines.MachineSettings;
import dev.gamblingitems.fabric.machines.MachineSetup;
import dev.gamblingitems.fabric.slots.SlotMenu;
import dev.gamblingitems.fabric.slots.SlotSettings;
import dev.gamblingitems.fabric.slots.SlotSetup;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.BooleanSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Playing a cabinet at the cabinet itself: pull its lever, turn its crank, press its buttons with a
 * valued item in hand and that stack is played, through the same menu and the same checks as the
 * window it used to have. Winnings paid in items wait in the machine until the player touches a
 * control with an empty hand; tickets go straight to the player's arcade balance.
 *
 * <p>A cabinet has no window any more: a click anywhere else on it tells how it is played, and a
 * sneaking click writes its rules and odds in the chat.
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

    /** Answers a click on a control of a cabinet; false when the click was not on a control that answers. */
    public static boolean use(ServerPlayer player, GameMode mode, BlockPos anchor, Control control) {
        Level level = player.level();
        if (control == Control.NONE || player.isSpectator()
                || !(level.getBlockEntity(anchor) instanceof GameStationEntity cabinet)) return false;
        CabinetMenu menu = menu(player, mode, anchor);
        // A closed machine says so.
        if (menu == null) return false;
        ItemStack hand = player.getMainHandItem();
        if (mode == GameMode.VIDEO_POKER) {
            MachineSetup setup = setup(mode, cabinet);
            if (control == Control.PLAY && !menu.accepts(hand) && !VideoPoker.open(cabinet) && menu.collectAll()) {
                return collected(player);
            }
            int card = control.pick();
            return card >= 0 ? VideoPoker.hold(player, cabinet, card) : VideoPoker.press(player, cabinet, setup, hand);
        }
        if (mode == GameMode.HORSE_RACE) {
            if (!menu.accepts(hand)) return menu.collectAll() && collected(player);
            return HorseRaces.bet(player, cabinet, setup(mode, cabinet), control.pick(), hand);
        }
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
        if (!menu.accepts(hand)) return menu.collectAll() && collected(player);
        if (busy) return refuse(player, "world_play_busy");
        int choice = control.pick() >= 0 ? control.pick()
                : menu.choices() > 1 ? Math.max(0, cabinet.targetSlot) % menu.choices() : 0;
        if (choice >= menu.choices()) return false;
        ItemStack played = hand.copy();
        int before = hand.getCount();
        // A cabinet's own largest stake keeps the rest of the stack in hand.
        ItemStack offered = hand.copyWithCount(Math.min(hand.getCount(), largestCount(menu, hand, cabinet.maximumStake)));
        int offeredCount = offered.getCount();
        if (offeredCount == 0 || !menu.playFromHand(offered, choice)) {
            Compat.message(player, Component.translatable("gui.gamblingitems.world_play_refused", amount(menu.minimumStake())), true);
            return true;
        }
        hand.shrink(offeredCount - offered.getCount());
        Compat.message(player, Component.translatable("gui.gamblingitems.world_play_started",
                Component.literal((before - hand.getCount()) + " × ").append(played.getHoverName())), true);
        return true;
    }

    /** The most items of this stack a cabinet's own largest stake allows; the whole stack without one. */
    public static int largestCount(CabinetMenu menu, ItemStack hand, long maximum) {
        if (maximum <= 0) return hand.getCount();
        long unit = menu.valueOf(hand.copyWithCount(1));
        return unit <= 0 ? 0 : (int) Math.min(hand.getCount(), maximum / unit);
    }

    private static boolean collected(ServerPlayer player) {
        Compat.message(player, Component.translatable("gui.gamblingitems.world_play_collected"), true);
        return true;
    }

    public static boolean refuse(ServerPlayer player, String key, Object... arguments) {
        Compat.message(player, Component.translatable("gui.gamblingitems." + key, arguments), true);
        return true;
    }

    /** A value as players read it: 1000 is 1. */
    public static String amount(long value) {
        return BigDecimal.valueOf(value, 3).stripTrailingZeros().toPlainString();
    }

    /** A click away from the controls: how the cabinet is played, or, sneaking, its rules and odds. */
    public static void explain(ServerPlayer player, GameMode mode, BlockPos anchor, boolean rules) {
        Component name = Component.translatable("gui.gamblingitems.mode." + mode.id());
        if (closed(mode, player.level().getBlockEntity(anchor))) {
            Compat.message(player, Component.translatable("gui.gamblingitems.machine_disabled", name), true);
            return;
        }
        if (!rules) {
            Compat.message(player, Component.translatable("gui.gamblingitems.world_hint." + mode.id()), true);
            return;
        }
        Compat.message(player, Component.literal("— ").append(name).append(" —").withStyle(ChatFormatting.GOLD), false);
        Compat.message(player, Component.translatable("gui.gamblingitems.rules." + mode.id()).withStyle(ChatFormatting.GRAY), false);
        if (mode == GameMode.SLOT_MACHINE) {
            Compat.message(player, Component.translatable("gui.gamblingitems.world_rules_slot", times(SlotRules.tripleOf(0)),
                    times(SlotRules.tripleOf(SlotRules.SYMBOLS - 1)), times(SlotRules.PAIR)), false);
            return;
        }
        MachineSetup setup = ModConfig.machine(mode);
        MachineTable table = setup.settings().table();
        for (int choice = 0; choice < table.choices(); choice++) {
            MutableComponent line = table.choices() > 1
                    ? Component.translatable("gui.gamblingitems.world_rules_choice", choice + 1).append(" ") : Component.empty();
            var odds = table.odds(choice);
            boolean first = true;
            for (int prize = 0; prize < odds.size(); prize++) {
                if (odds.get(prize).multiplier() == 0 || mode == GameMode.VIDEO_POKER) continue;
                if (!first) line.append(", ");
                first = false;
                line.append(times(odds.get(prize).multiplier()) + " " + percent(table.chance(choice, prize)));
            }
            if (mode == GameMode.VIDEO_POKER) {
                int[] paytable = table.multipliers();
                for (int hand = paytable.length - 1; hand >= 0; hand--) {
                    Compat.message(player, Component.translatable("gui.gamblingitems.world_rules_line",
                            Component.translatable("gui.gamblingitems.poker_hand." + hand), times(paytable[hand])), false);
                }
                break;
            }
            Compat.message(player, line, false);
        }
        BigDecimal back = table.returnToPlayer(0).multiply(setup.settings().returnRate()).movePointRight(2).setScale(2, RoundingMode.HALF_UP);
        Compat.message(player, Component.translatable("gui.gamblingitems.world_rules_return", back.stripTrailingZeros().toPlainString() + "%",
                amount(setup.settings().minimumStake())).withStyle(ChatFormatting.DARK_GRAY), false);
    }

    private static String times(int multiplier) {
        return BigDecimal.valueOf(multiplier, 2).stripTrailingZeros().toPlainString() + "x";
    }

    private static String percent(BigDecimal chance) {
        return chance.movePointRight(2).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    private static boolean closed(GameMode mode, Object entity) {
        if (entity instanceof GameStationEntity cabinet && cabinet.closed) return true;
        return mode != GameMode.SLOT_MACHINE && !ModConfig.machine(mode).settings().enabled();
    }

    /** The server's tuning of a machine, with this cabinet's own settings over it. */
    public static MachineSetup setup(GameMode mode, GameStationEntity cabinet) {
        MachineSetup setup = ModConfig.machine(mode);
        MachineSettings settings = setup.settings();
        if (!cabinet.closed && cabinet.minimumStake <= 0 && cabinet.maximumStake <= 0) return setup;
        long minimum = cabinet.minimumStake > 0 ? cabinet.minimumStake : settings.minimumStake();
        long maximum = cabinet.maximumStake > 0 ? Math.max(minimum, cabinet.maximumStake) : settings.maximumStake();
        if (maximum != 0 && maximum < minimum) maximum = minimum;
        return new MachineSetup(setup.catalog(), new MachineSettings(settings.enabled() && !cabinet.closed, minimum, maximum,
                settings.returnBasisPoints(), settings.playTicks(), settings.resultTicks(), settings.table()), setup.ticketValue());
    }

    /** The player's menu for the machine, never opened; null for a machine closed on this server or at this cabinet. */
    public static CabinetMenu menu(ServerPlayer player, GameMode mode, BlockPos anchor) {
        ContainerLevelAccess access = ContainerLevelAccess.create(player.level(), anchor);
        PlayerVaults vaults = PlayerVaults.get(player.server);
        GameStationEntity cabinet = player.level().getBlockEntity(anchor) instanceof GameStationEntity entity ? entity : null;
        if (cabinet == null || closed(mode, cabinet)) return null;
        if (mode == GameMode.SLOT_MACHINE) {
            SlotSetup setup = ModConfig.slots();
            if (cabinet.minimumStake > 0) {
                SlotSettings settings = setup.settings();
                setup = new SlotSetup(setup.catalog(), new SlotSettings(cabinet.minimumStake, settings.returnBasisPoints(),
                        settings.spinTicks(), settings.resultTicks()), setup.ticketValue());
            }
            return new SlotMenu(0, player.getInventory(), setup, vaults.forPlayer(player.getUUID(), VaultSection.SLOT_MACHINE), access);
        }
        return new MachineMenu(0, player.getInventory(), setup(mode, cabinet),
                vaults.forPlayer(player.getUUID(), VaultSection.fromId(mode.id())), access);
    }
}
