package dev.gamblingitems.fabric.block;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.battle.*;
import dev.gamblingitems.fabric.bingo.*;
import dev.gamblingitems.fabric.blackjack.*;
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

    /** The seat of this player at this block, opened if they did not have one yet. */
    private static Seat seat(ServerPlayer player, GameStationEntity station) {
        if (player.isSpectator() || !player.isAlive() || player.level() != station.getLevel()
                || player.distanceToSqr(station.getBlockPos().getCenter()) > 36
                || player.containerMenu != player.inventoryMenu) return null;
        Seat seat = SEATS.get(player.getUUID());
        if (seat != null && seat.station != station) { close(player.getUUID()); seat = null; }
        if (seat == null) {
            seat = new Seat(player, station, create(player, station));
            SEATS.put(player.getUUID(), seat);
        }
        return seat;
    }

    /**
     * Puts the prepared chips on one area of a felt. A roulette table is played on directly:
     * clicking a printed number is the same bet as choosing it in the window.
     */
    public static void bet(ServerPlayer player, GameStationEntity station,
                           dev.gamblingitems.core.roulette.RouletteWheel.Bet bet) {
        Seat seat = seat(player, station);
        if (seat == null || !(seat.menu instanceof RouletteMenu roulette)) return;
        roulette.broadcastChanges();
        // A table is played with what is in the hand: an area that is clicked with nothing
        // prepared takes the held item first, one of it, or the whole stack when crouching.
        if (roulette.plannedStake() < roulette.settings().minimumStake()) {
            deposit(player, roulette,
                    player.isShiftKeyDown() ? player.getMainHandItem().getCount() : 1);
            roulette.broadcastChanges();
        }
        boolean changed = roulette.clickMenuButton(player, RouletteMenu.BET_BUTTON + RouletteMenu.code(bet));
        roulette.broadcastChanges();
        publish(station, roulette);
        station.preview(player.getName().getString(), preview(roulette));
        String area = bet.type().needsChoice() ? bet.type().id() + " " + bet.choice() : bet.type().id();
        player.displayClientMessage(Component.translatable(changed
                        ? "gui.gamblingitems.panel.accepted" : "gui.gamblingitems.panel.rejected")
                .append(" ").append(area), true);
    }

    public static void click(ServerPlayer player, GameStationEntity station, int button) {
        if (button < 0 || button > 8 || player.isSpectator() || !player.isAlive()
                || player.level() != station.getLevel()
                || player.distanceToSqr(station.getBlockPos().getCenter()) > 36) return;
        Seat seat = SEATS.get(player.getUUID());
        if (seat != null && seat.station != station) { close(player.getUUID()); seat = null; }
        if (button == 8) {
            close(player.getUUID());
            GameMenus.open(player, station.mode(), ContainerLevelAccess.create(player.level(), station.getBlockPos()));
            return;
        }
        seat = seat(player, station);
        if (seat == null) return;
        AbstractContainerMenu menu = seat.menu;
        menu.broadcastChanges();
        boolean changed = false;
        if (menu instanceof CrashMenu crash) {
            clickCrash(player, station, crash, button);
            return;
        }
        if (button <= 1) changed = deposit(player, menu,
                player.isShiftKeyDown() || button == 1 ? player.getMainHandItem().getCount() : 1);
        else if (button == 2) {
            for (int i = 0; i < inputs(menu); i++) changed |= !menu.quickMoveStack(player, i).isEmpty();
        } else if (button == 6) {
            if (menu instanceof RouletteMenu) changed = menu.clickMenuButton(player, RouletteMenu.COLLECT_BUTTON);
            else if (menu instanceof CrashMenu) changed = menu.clickMenuButton(player, CrashMenu.COLLECT_BUTTON);
            else if (menu instanceof BlackjackMenu) changed = menu.clickMenuButton(player, BlackjackMenu.COLLECT_BUTTON);
            else if (menu instanceof BattleMenu) changed = menu.clickMenuButton(player, BattleMenu.COLLECT_BUTTON);
            else if (menu instanceof BingoMenu) changed = menu.clickMenuButton(player, BingoMenu.COLLECT_BUTTON);
            else changed = !menu.quickMoveStack(player, inputs(menu)).isEmpty();
        } else if (button == 7 || (button == 5 && menu instanceof CrashMenu)) {
            player.displayClientMessage(Component.translatable("gui.gamblingitems.panel.help"), false);
            changed = true;
        } else if (menu instanceof RouletteMenu) {
            // The panel offers the three simplest areas of the felt; the screen offers the whole table.
            var bet = switch (button) {
                case 3 -> new dev.gamblingitems.core.roulette.RouletteWheel.Bet(
                        dev.gamblingitems.core.roulette.RouletteWheel.BetType.RED, 0);
                case 4 -> new dev.gamblingitems.core.roulette.RouletteWheel.Bet(
                        dev.gamblingitems.core.roulette.RouletteWheel.BetType.BLACK, 0);
                default -> new dev.gamblingitems.core.roulette.RouletteWheel.Bet(
                        dev.gamblingitems.core.roulette.RouletteWheel.BetType.STRAIGHT, 0);
            };
            changed = menu.clickMenuButton(player, RouletteMenu.BET_BUTTON + RouletteMenu.code(bet));
        } else if (menu instanceof BlackjackMenu) {
            // Deal, hit, stand: a hand can be played entirely from the block.
            changed = menu.clickMenuButton(player, switch (button) {
                case 3 -> BlackjackMenu.DEAL_BUTTON;
                case 4 -> BlackjackMenu.HIT_BUTTON;
                default -> BlackjackMenu.STAND_BUTTON;
            });
        } else if (menu instanceof BingoMenu) {
            // Buying a card is the only move a bingo asks of a player.
            changed = button == 3 && menu.clickMenuButton(player, BingoMenu.BUY_BUTTON);
        } else if (menu instanceof BattleMenu) {
            // Join, leave, start: a lobby is filled in front of everyone.
            changed = menu.clickMenuButton(player, switch (button) {
                case 3 -> BattleMenu.JOIN_BUTTON;
                case 4 -> BattleMenu.START_BUTTON;
                default -> BattleMenu.LEAVE_BUTTON;
            });
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
        publish(station, menu);
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
        if (button <= 1 && !menu.isEngaged()) {
            changed = deposit(player, menu,
                    player.isShiftKeyDown() || button == 1 ? player.getMainHandItem().getCount() : 1);
            feedback = "crash_prepared";
        } else if (button == 4 || button == 3) {
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
                yield new RouletteMenu(0, player.getInventory(), game.setup(), vault, game);
            }
            case CRASH -> {
                var game = CrashGames.host(player.serverLevel(), station.getBlockPos());
                CrashGames.join(game, player.getUUID());
                yield new CrashMenu(0, player.getInventory(), game.setup(), vault, game);
            }
            case BLACKJACK -> {
                var table = BlackjackTables.of(player.server, player.getUUID());
                yield new BlackjackMenu(0, player.getInventory(), table.setup(), vault, table, access);
            }
            case BINGO -> {
                var game = BingoGames.host(player.serverLevel(), station.getBlockPos());
                BingoGames.join(game, player.getUUID());
                yield new BingoMenu(0, player.getInventory(), game.setup(), vault, game);
            }
            case CASE_BATTLE -> {
                var lobby = BattleLobbies.host(player.serverLevel(), station.getBlockPos());
                BattleLobbies.join(lobby, player.getUUID());
                yield new BattleMenu(0, player.getInventory(), lobby.setup(), vault, lobby);
            }
            default -> throw new IllegalArgumentException("Unavailable station");
        };
    }

    private static int inputs(AbstractContainerMenu menu) {
        if (menu instanceof TradeUpMenu) return TradeUpMenu.INPUT_SLOTS;
        if (menu instanceof CrashMenu) return CrashSettings.STAKE_SLOTS;
        if (menu instanceof RouletteMenu) return dev.gamblingitems.fabric.roulette.RouletteSettings.STAKE_SLOTS;
        if (menu instanceof BlackjackMenu) return BlackjackSettings.STAKE_SLOTS;
        if (menu instanceof BattleMenu) return BattleSettings.STAKE_SLOTS;
        if (menu instanceof BingoMenu) return BingoSettings.STAKE_SLOTS;
        return 1;
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

    /** What a blackjack seat shows to everyone: the cards on the felt, dealer hole card aside. */
    private static String cards(BlackjackMenu menu) {
        StringBuilder text = new StringBuilder();
        for (int card : menu.hand()) {
            if (text.length() > 0) text.append(',');
            text.append(card);
        }
        text.append('|');
        boolean first = true;
        for (int card : menu.dealer()) {
            if (!first) text.append(',');
            text.append(card);
            first = false;
        }
        if (menu.dealerHidden()) text.append(first ? "?" : ",?");
        return text.toString();
    }

    /** Puts on the block whatever the seat is showing, so nearby players follow the game. */
    private static void publish(GameStationEntity station, AbstractContainerMenu menu) {
        if (menu instanceof BlackjackMenu blackjack) {
            station.cards = cards(blackjack);
            station.phase = blackjack.phase().id();
        }
        station.stakeItems = staked(menu);
    }

    /**
     * The items this seat has on the table, prepared or already engaged, so the felt can show
     * them. Every game keeps its prepared row first and its engaged row right after it.
     */
    private static String staked(AbstractContainerMenu menu) {
        int inputs = inputs(menu);
        StringBuilder text = new StringBuilder();
        int written = 0;
        for (int slot = 0; slot < inputs * 2 && slot < menu.slots.size() && written < 6; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (stack.isEmpty()) continue;
            if (text.length() > 0) text.append(',');
            text.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    .append('*').append(stack.getCount());
            written++;
        }
        return text.toString();
    }

    private static String preview(AbstractContainerMenu menu) {
        if (menu instanceof CaseMenu cases) return cases.selected().name() + " | " + cases.selected().priceCount() + " x "
                + cases.selected().priceStack().getHoverName().getString();
        if (menu instanceof UpgradeMenu upgrade) return upgrade.selected() == null ? "-" : upgrade.selected().stack().getHoverName().getString()
                + " | " + String.format(Locale.ROOT, "%.1f%%", upgrade.chance() * 100);
        if (menu instanceof TradeUpMenu trade) return trade.stakedUnits() + " / " + trade.setup().settings().requiredUnits();
        if (menu instanceof RouletteMenu roulette) return String.valueOf(roulette.plannedStake());
        if (menu instanceof CrashMenu crash) return String.valueOf(crash.plannedStake());
        if (menu instanceof BlackjackMenu blackjack) return blackjack.handTotal() + " / " + blackjack.dealerTotal();
        if (menu instanceof BattleMenu battle) return battle.players() + " / " + battle.rounds();
        if (menu instanceof BingoMenu bingo) return bingo.players() + " / " + bingo.missing();
        return "";
    }

    public static void tick(MinecraftServer server) {
        for (Seat seat : new ArrayList<>(SEATS.values())) {
            ServerPlayer player = seat.player;
            if (player.hasDisconnected() || !player.isAlive() || player.level() != seat.station.getLevel()
                    || seat.station.isRemoved() || player.distanceToSqr(seat.station.getBlockPos().getCenter()) > 64
                    || player.containerMenu != player.inventoryMenu) close(player.getUUID());
            else {
                // A dealer draws on its own: the block follows the hand without anyone clicking.
                seat.menu.broadcastChanges();
                publish(seat.station, seat.menu);
                seat.station.preview(player.getName().getString(), preview(seat.menu));
            }
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
