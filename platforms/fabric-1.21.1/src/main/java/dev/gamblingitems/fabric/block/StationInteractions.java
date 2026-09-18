package dev.gamblingitems.fabric.block;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.cases.CaseMenu;
import dev.gamblingitems.fabric.config.ModConfig;
import dev.gamblingitems.fabric.crash.*;
import dev.gamblingitems.fabric.menu.GameMenus;
import dev.gamblingitems.fabric.roulette.*;
import dev.gamblingitems.fabric.tradeup.TradeUpMenu;
import dev.gamblingitems.fabric.upgrade.UpgradeMenu;
import dev.gamblingitems.fabric.vault.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Server-only seats. Reuses the validated game actions without opening a client container. */
public final class StationInteractions {
    private record Seat(ServerPlayer player, GameStationEntity station, AbstractContainerMenu menu) {}
    private static final Map<UUID, Seat> SEATS = new HashMap<>();
    private StationInteractions() {}

    public static void click(ServerPlayer player, GameStationEntity station, int button) {
        if (button < 0 || button > 8 || player.isSpectator() || !player.isAlive()
                || player.level() != station.getLevel()
                || player.distanceToSqr(station.getBlockPos().getCenter()) > 36
                || player.containerMenu != player.inventoryMenu) return;
        Seat seat = SEATS.get(player.getUUID());
        if (seat != null && seat.station != station) { close(player.getUUID()); seat = null; }
        if (button == 8) {
            close(player.getUUID());
            GameMenus.open(player, station.mode(), ContainerLevelAccess.create(player.level(), station.getBlockPos()));
            return;
        }
        if (seat == null) {
            seat = new Seat(player, station, create(player, station));
            SEATS.put(player.getUUID(), seat);
        }
        AbstractContainerMenu menu = seat.menu;
        menu.broadcastChanges();
        boolean changed = false;
        if (menu instanceof CrashMenu crash) {
            clickCrash(player, station, crash, button);
            return;
        }
        if (button <= 1) changed = deposit(player, menu, button == 0 ? 1 : player.getMainHandItem().getCount());
        else if (button == 2) {
            for (int i = 0; i < inputs(menu); i++) changed |= !menu.quickMoveStack(player, i).isEmpty();
        } else if (button == 6) {
            if (menu instanceof RouletteMenu) changed = menu.clickMenuButton(player, RouletteMenu.COLLECT_BUTTON);
            else if (menu instanceof CrashMenu) changed = menu.clickMenuButton(player, CrashMenu.COLLECT_BUTTON);
            else changed = !menu.quickMoveStack(player, inputs(menu)).isEmpty();
        } else if (button == 7 || (button == 5 && menu instanceof CrashMenu)) {
            player.displayClientMessage(Component.translatable("gui.gamblingitems.panel.help"), false);
            changed = true;
        } else if (menu instanceof RouletteMenu) {
            // Colour enum order is not part of the panel layout.
            var colour = switch (button) {
                case 3 -> dev.gamblingitems.core.roulette.RouletteRules.Colour.RED;
                case 4 -> dev.gamblingitems.core.roulette.RouletteRules.Colour.BLACK;
                default -> dev.gamblingitems.core.roulette.RouletteRules.Colour.GREEN;
            };
            changed = menu.clickMenuButton(player, RouletteMenu.BET_BUTTON + colour.ordinal());
        } else if (menu instanceof CrashMenu) {
            changed = menu.clickMenuButton(player, button == 3 ? CrashMenu.BET_BUTTON : CrashMenu.CASH_OUT_BUTTON);
        } else if (button == 4) {
            if (!station.animating()) changed = menu.clickMenuButton(player, 1000);
        } else if (menu instanceof CaseMenu cases) {
            changed = menu.clickMenuButton(player, Math.floorMod(cases.selectedIndex() + (button == 3 ? -1 : 1), cases.setup().cases().cases().size()));
        } else if (menu instanceof UpgradeMenu upgrade) {
            changed = menu.clickMenuButton(player, Math.floorMod(upgrade.selectedIndex() + (button == 3 ? -1 : 1), upgrade.catalog().entries().size()));
        }
        menu.broadcastChanges();
        String preview = preview(menu);
        boolean personal = menu instanceof CaseMenu || menu instanceof UpgradeMenu || menu instanceof TradeUpMenu;
        if (personal && !station.animating()) {
            if (button != 4) station.clear();
            if (menu instanceof CaseMenu cases) station.reelItems = cases.selected().rewards().stream()
                    .map(entry -> entry.item().toString()).collect(java.util.stream.Collectors.joining(","));
            if (menu instanceof UpgradeMenu upgrade && upgrade.selected() != null)
                station.rollingText = String.format(Locale.ROOT, "%.1f%%", upgrade.chance() * 100);
        }
        station.preview(player.getName().getString(), preview);
        player.displayClientMessage(Component.translatable(changed ? "gui.gamblingitems.panel.accepted" : "gui.gamblingitems.panel.rejected")
                .append(" ").append(preview), true);
    }

    private static void clickCrash(ServerPlayer player, GameStationEntity station, CrashMenu menu, int button) {
        boolean changed = false;
        String feedback = "rejected";
        if (button == 0 && !menu.isEngaged()) {
            changed = deposit(player, menu, player.isShiftKeyDown() ? player.getMainHandItem().getCount() : 1);
            feedback = "crash_prepared";
        } else if (button == 4) {
            if (menu.phase() == CrashGame.Phase.FLYING) {
                changed = menu.clickMenuButton(player, CrashMenu.CASH_OUT_BUTTON);
                if (changed) menu.clickMenuButton(player, CrashMenu.COLLECT_BUTTON);
                feedback = "crash_paid";
            } else {
                changed = menu.clickMenuButton(player, CrashMenu.BET_BUTTON);
                feedback = "crash_engaged";
            }
        } else if (button == 6) {
            changed = menu.clickMenuButton(player, CrashMenu.COLLECT_BUTTON);
            for (int i = 0; i < CrashMenu.INVENTORY_START; i++) changed |= !menu.quickMoveStack(player, i).isEmpty();
            feedback = "accepted";
        }
        menu.broadcastChanges();
        station.preview(player.getName().getString(), String.valueOf(menu.plannedStake()));
        player.displayClientMessage(Component.translatable("gui.gamblingitems.panel." + (changed ? feedback : "rejected"))
                .append(" ").append(Component.translatable("gui.gamblingitems.panel.crash_balance",
                        menu.plannedStake(), menu.stake(), menu.paid(), menu.winnings())), true);
    }

    private static AbstractContainerMenu create(ServerPlayer player, GameStationEntity station) {
        var access = ContainerLevelAccess.create(player.level(), station.getBlockPos());
        var vault = PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.fromId(station.mode().id()));
        return switch (station.mode()) {
            case UPGRADER -> new UpgradeMenu(0, player.getInventory(), ModConfig.upgrader(), vault, access);
            case TRADE_UP -> new TradeUpMenu(0, player.getInventory(), ModConfig.tradeUp(), vault, access);
            case CASE_OPENING -> new CaseMenu(0, player.getInventory(), ModConfig.cases(), vault, access);
            case ROULETTE -> {
                var game = RouletteGames.host(player.serverLevel(), station.getBlockPos());
                RouletteGames.join(game, player.getUUID());
                yield new RouletteMenu(0, player.getInventory(), game.settings(), vault, game);
            }
            case CRASH -> {
                var game = CrashGames.host(player.serverLevel(), station.getBlockPos());
                CrashGames.join(game, player.getUUID());
                yield new CrashMenu(0, player.getInventory(), game.setup(), vault, game);
            }
            default -> throw new IllegalArgumentException("Unavailable station");
        };
    }

    private static int inputs(AbstractContainerMenu menu) {
        return menu instanceof TradeUpMenu ? TradeUpMenu.INPUT_SLOTS : menu instanceof CrashMenu ? CrashSettings.STAKE_SLOTS : 1;
    }

    private static boolean deposit(ServerPlayer player, AbstractContainerMenu menu, int count) {
        ItemStack held = player.getMainHandItem();
        int left = Math.min(count, held.getCount());
        int original = left;
        for (int i = 0; i < inputs(menu) && left > 0; i++) {
            Slot slot = menu.getSlot(i);
            if (!slot.mayPlace(held)) continue;
            ItemStack current = slot.getItem();
            if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, held)) continue;
            int amount = Math.min(left, slot.getMaxStackSize(held) - current.getCount());
            if (amount <= 0) continue;
            ItemStack next = held.copyWithCount(current.getCount() + amount);
            slot.setByPlayer(next);
            held.shrink(amount);
            left -= amount;
        }
        player.getInventory().setChanged();
        return left != original;
    }

    private static String preview(AbstractContainerMenu menu) {
        if (menu instanceof CaseMenu cases) return cases.selected().name() + " | " + cases.selected().priceCount() + " x "
                + cases.selected().priceStack().getHoverName().getString();
        if (menu instanceof UpgradeMenu upgrade) return upgrade.selected() == null ? "-" : upgrade.selected().stack().getHoverName().getString()
                + " | " + String.format(Locale.ROOT, "%.1f%%", upgrade.chance() * 100);
        if (menu instanceof TradeUpMenu trade) return trade.stakedUnits() + " / " + trade.setup().settings().requiredUnits();
        if (menu instanceof RouletteMenu roulette) return roulette.plannedStake() + " x " + roulette.settings().stakeStack(1).getHoverName().getString();
        if (menu instanceof CrashMenu crash) return String.valueOf(crash.plannedStake());
        return "";
    }

    public static void tick(MinecraftServer server) {
        for (Seat seat : new ArrayList<>(SEATS.values())) {
            ServerPlayer player = seat.player;
            if (player.hasDisconnected() || !player.isAlive() || player.level() != seat.station.getLevel()
                    || seat.station.isRemoved() || player.distanceToSqr(seat.station.getBlockPos().getCenter()) > 64
                    || player.containerMenu != player.inventoryMenu) close(player.getUUID());
            else seat.menu.broadcastChanges();
        }
    }

    public static void close(UUID player) {
        Seat seat = SEATS.remove(player);
        if (seat != null) seat.menu.removed(seat.player);
    }

    public static void stop(MinecraftServer server) {
        for (UUID player : new ArrayList<>(SEATS.keySet())) close(player);
    }
}
