package dev.gamblingitems.fabric.roulette;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.roulette.RouletteWheel;
import dev.gamblingitems.core.roulette.RouletteWheel.Bet;
import dev.gamblingitems.core.roulette.RouletteWheel.BetType;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.block.GameSurface;
import dev.gamblingitems.fabric.menu.ValueSync;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One player's seat at a shared roulette. It sends intentions only: the spin, the number and every
 * payment belong to {@link RouletteGame} on the server.
 *
 * <p>Clicking an area of the table puts the prepared chips on it. Several areas may be covered.
 */
public final class RouletteMenu extends AbstractContainerMenu {
    /** A bet is sent as one button: its kind and, when it needs one, its number, dozen or column. */
    public static final int BET_BUTTON = 2000, COLLECT_BUTTON = 1100;
    public static final int STAKE_X = 16, ENGAGED_Y = 164, INPUT_Y = 192;
    public static final int INVENTORY_START = 2 * RouletteSettings.STAKE_SLOTS;
    // Small numbers first, then the values, which each need two slots to survive the packet.
    private static final int PHASE = 0, PHASE_TICKS = 1, RESULT = 2, PLAYERS = 3, SETTLED = 4,
            LAST_RESULT = 5, POT = 6, WINNINGS = 8, PLANNED = 10, STAKED = 12, PAID = 14;
    /** Then one bet per pair of slots: the area it covers, and what it holds. */
    private static final int FIRST_BET = 16;
    private static final int BET_FIELDS = 1 + ValueSync.SLOTS;
    private static final int DATA_SIZE = FIRST_BET + BET_FIELDS * RouletteGame.MAX_BETS;

    private final Player owner;
    private final Container vault;
    private final RouletteSetup setup;
    private final RouletteGame game;
    private final SimpleContainerData data = new SimpleContainerData(DATA_SIZE);

    public RouletteMenu(int syncId, Inventory inventory, RouletteSetup setup) {
        this(syncId, inventory, setup, new SimpleContainer(RouletteSettings.VAULT_SIZE), null);
    }

    public RouletteMenu(int syncId, Inventory inventory, RouletteSetup setup,
                        Container vault, RouletteGame game) {
        super(ModContent.ROULETTE_MENU, syncId);
        this.owner = inventory.player;
        this.setup = setup;
        this.vault = vault;
        this.game = game;
        addDataSlots(data);
        for (int index = 0; index < RouletteSettings.STAKE_SLOTS; index++) {
            addSlot(new Slot(vault, RouletteSettings.INPUT_SLOT + index, STAKE_X + index * 18, INPUT_Y) {
                // Any priced item may be played; what is unpriced could not be paid fairly.
                @Override public boolean mayPlace(ItemStack stack) { return setup.catalog().valueOf(stack) > 0; }
                @Override public boolean mayPickup(Player player) { return !engagedNow(player); }
            });
        }
        for (int index = 0; index < RouletteSettings.STAKE_SLOTS; index++) {
            addSlot(new Slot(vault, RouletteSettings.ENGAGED_SLOT + index, STAKE_X + index * 18, ENGAGED_Y) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                // Chips on the table belong to the round. What an interrupted round left does not.
                @Override public boolean mayPickup(Player player) { return !engagedNow(player); }
            });
        }
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 139 + col * 18, 213 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 139 + col * 18, 271));
    }

    public RouletteSetup setup() { return setup; }
    public RouletteSettings settings() { return setup.settings(); }
    public ValueCatalog catalog() { return setup.catalog(); }
    public RouletteGame.Phase phase() { return RouletteGame.Phase.fromId(data.get(PHASE)); }
    public int phaseTicks() { return data.get(PHASE_TICKS); }
    public int resultNumber() { return data.get(RESULT) - 1; }
    public int participants() { return data.get(PLAYERS); }
    public long pot() { return ValueSync.read(data, POT); }
    public long winnings() { return ValueSync.read(data, WINNINGS); }
    /** Value prepared in the chip slots, as the server counts it. */
    public long plannedStake() { return ValueSync.read(data, PLANNED); }
    public long staked() { return ValueSync.read(data, STAKED); }
    public long paid() { return ValueSync.read(data, PAID); }
    public boolean settled() { return data.get(SETTLED) != 0; }
    public int lastResultNumber() { return data.get(LAST_RESULT) - 1; }

    /** What this player has on a given area of the table, in value. */
    public long stakeOn(Bet bet) {
        int code = code(bet);
        for (int index = 0; index < RouletteGame.MAX_BETS; index++) {
            int slot = FIRST_BET + index * BET_FIELDS;
            if (data.get(slot) == code) return ValueSync.read(data, slot + 1);
        }
        return 0;
    }

    public static int code(Bet bet) { return bet.type().ordinal() * 64 + bet.choice() + 1; }

    public static Bet decode(int code) {
        if (code <= 0) return null;
        int value = code - 1;
        int type = value / 64;
        int choice = value % 64;
        BetType[] types = BetType.values();
        if (type >= types.length || choice >= types[type].choices()) return null;
        return new Bet(types[type], choice);
    }

    private boolean engagedNow(Player player) {
        if (game == null) return staked() > 0 && !settled();
        RouletteGame.Seat seat = game.seatOf(player.getUUID());
        return seat != null && seat.engaged();
    }

    public boolean canBet() {
        return plannedStake() >= settings().minimumStake()
                && (phase() == RouletteGame.Phase.WAITING || phase() == RouletteGame.Phase.BETTING);
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer) || player != owner || player.isSpectator() || !stillValid(player)) {
            return false;
        }
        if (game == null) return false;
        boolean handled;
        if (button == COLLECT_BUTTON) {
            handled = collect(player);
        } else {
            Bet bet = decode(button - BET_BUTTON);
            handled = bet != null && game.place(player.getUUID(), bet, game.stagedValue(player.getUUID()));
        }
        if (handled) broadcastChanges();
        return handled;
    }

    /** Moves the winnings into the inventory, keeping in the vault whatever does not fit. */
    private boolean collect(Player player) {
        boolean moved = false;
        for (int slot = RouletteSettings.FIRST_PAYOUT_SLOT; slot < RouletteSettings.VAULT_SIZE; slot++) {
            ItemStack stack = vault.getItem(slot).copy();
            if (stack.isEmpty()) continue;
            int before = stack.getCount();
            moveItemStackTo(stack, INVENTORY_START, slots.size(), false);
            if (stack.getCount() == before) continue;
            vault.setItem(slot, stack);
            moved = true;
        }
        return moved;
    }

    @Override public void broadcastChanges() {
        if (!owner.level().isClientSide && game != null) {
            RouletteGame.Seat seat = game.seatOf(owner.getUUID());
            data.set(PHASE, game.phase().id());
            data.set(PHASE_TICKS, game.remainingTicks());
            data.set(RESULT, game.resultNumber() + 1);
            data.set(PLAYERS, game.participants());
            data.set(SETTLED, seat != null && seat.settled() ? 1 : 0);
            data.set(LAST_RESULT, game.lastResultNumber() + 1);
            ValueSync.write(data, POT, game.pot());
            ValueSync.write(data, WINNINGS, game.winnings(vault));
            ValueSync.write(data, PLANNED, game.stagedValue(owner.getUUID()));
            ValueSync.write(data, STAKED, seat == null ? 0 : seat.total());
            ValueSync.write(data, PAID, seat == null ? 0 : seat.paid());
            int index = 0;
            if (seat != null) {
                for (Map.Entry<Bet, Long> stake : seat.stakes().entrySet()) {
                    if (index >= RouletteGame.MAX_BETS) break;
                    int slot = FIRST_BET + index * BET_FIELDS;
                    data.set(slot, code(stake.getKey()));
                    ValueSync.write(data, slot + 1, stake.getValue());
                    index++;
                }
            }
            for (; index < RouletteGame.MAX_BETS; index++) {
                int slot = FIRST_BET + index * BET_FIELDS;
                data.set(slot, 0);
                ValueSync.write(data, slot + 1, 0);
            }
        }
        super.broadcastChanges();
    }

    /** The round outlives its station; the player only needs to stay within reach of it. */
    @Override public boolean stillValid(Player player) {
        if (player.level().isClientSide) return player == owner;
        if (game == null || player != owner || !player.isAlive() || player.level() != game.level()) return false;
        var pos = game.station();
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                > RouletteGames.REACH * RouletteGames.REACH) {
            return false;
        }
        return player.getInventory().contains(new ItemStack(ModContent.TERMINAL))
                || (game.level().getBlockState(pos).getBlock() instanceof GameSurface surface
                        && surface.mode() == GameMode.ROULETTE);
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (player != owner || player.isSpectator() || !stillValid(player)) return;
        super.clicked(slot, button, type, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || player != owner || !stillValid(player)) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < INVENTORY_START) {
            if (!moveItemStackTo(stack, INVENTORY_START, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (catalog().valueOf(stack) <= 0
                    || !moveItemStackTo(stack, 0, RouletteSettings.STAKE_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        if (game != null) RouletteGames.leave(game, player.getUUID());
        // Chips on the table stay with the round; only what was being prepared comes back.
        if (player instanceof ServerPlayer && player.isAlive()) {
            for (int index = 0; index < RouletteSettings.STAKE_SLOTS; index++) {
                int slot = RouletteSettings.INPUT_SLOT + index;
                ItemStack staged = vault.getItem(slot).copy();
                if (staged.isEmpty()) continue;
                moveItemStackTo(staged, INVENTORY_START, slots.size(), false);
                vault.setItem(slot, staged);
            }
        }
    }

    /** Every area of the table, in the order the felt shows them. */
    public static Bet[] table() {
        Bet[] bets = new Bet[RouletteWheel.POCKETS + 2 * 4 + 3 + 3];
        int index = 0;
        for (int number = 0; number < RouletteWheel.POCKETS; number++) {
            bets[index++] = new Bet(BetType.STRAIGHT, number);
        }
        for (int choice = 0; choice < 3; choice++) bets[index++] = new Bet(BetType.DOZEN, choice);
        for (int choice = 0; choice < 3; choice++) bets[index++] = new Bet(BetType.COLUMN, choice);
        for (BetType type : new BetType[] {BetType.RED, BetType.BLACK, BetType.EVEN,
                BetType.ODD, BetType.LOW, BetType.HIGH}) {
            bets[index++] = new Bet(type, 0);
        }
        return java.util.Arrays.copyOf(bets, index);
    }
}
