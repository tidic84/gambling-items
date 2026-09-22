package dev.gamblingitems.fabric.crash;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.crash.CrashRules;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.block.GameSurface;
import dev.gamblingitems.fabric.menu.ValueSync;
import dev.gamblingitems.fabric.value.ValueCatalog;
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
 * One player's window on a shared round. It sends intentions only: the flight, the multiplier and
 * every settlement belong to {@link CrashGame} on the server.
 *
 * <p>The bet is whatever priced items the player puts in the bet row; only their value is played.
 */
public final class CrashMenu extends AbstractContainerMenu {
    public static final int BET_BUTTON = 1000, CASH_OUT_BUTTON = 1001, COLLECT_BUTTON = 1002;
    /** Where the two rows of the wager sit, read by the screen so both sides cannot drift apart. */
    public static final int STAKE_X = 16, ENGAGED_Y = 96, INPUT_Y = 126;
    public static final int INVENTORY_START = 2 * CrashSettings.STAKE_SLOTS;
    public static final int STATE_NONE = 0, STATE_ENGAGED = 1, STATE_CASHED = 2, STATE_LOST = 3;

    private final Player owner;
    private final Container vault;
    private final CrashSetup setup;
    private final CrashGame game;
    // Small numbers first, then the values, which each need two slots to survive the packet.
    private static final int PHASE = 0, MULTIPLIER = 1, PHASE_TICKS = 2, SETTLEMENT = 3, PLAYERS = 4,
            STATE = 5, LAST_POINT = 6, FLIGHT_TICK = 7, PAYABLE = 8,
            STAKE = 9, PAID = 11, POT = 13, WINNINGS = 15, PLANNED = 17;
    private static final int DATA_SIZE = 19;
    private final SimpleContainerData data = new SimpleContainerData(DATA_SIZE);

    public CrashMenu(int syncId, Inventory inventory, CrashSetup setup) {
        this(syncId, inventory, setup, new SimpleContainer(CrashSettings.VAULT_SIZE), null);
    }

    public CrashMenu(int syncId, Inventory inventory, CrashSetup setup, Container vault, CrashGame game) {
        super(ModContent.CRASH_MENU, syncId);
        this.owner = inventory.player;
        this.setup = setup;
        this.vault = vault;
        this.game = game;
        data.set(MULTIPLIER, CrashRules.START);
        addDataSlots(data);
        for (int index = 0; index < CrashSettings.STAKE_SLOTS; index++) {
            addSlot(new Slot(vault, CrashSettings.INPUT_SLOT + index, STAKE_X + index * 18, INPUT_Y) {
                // Any priced item may be staked; what is unpriced could not be played fairly.
                @Override public boolean mayPlace(ItemStack stack) { return setup.catalog().valueOf(stack) > 0; }
                @Override public boolean mayPickup(Player player) { return !engagedNow(player); }
            });
        }
        for (int index = 0; index < CrashSettings.STAKE_SLOTS; index++) {
            addSlot(new Slot(vault, CrashSettings.ENGAGED_SLOT + index, STAKE_X + index * 18, ENGAGED_Y) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                // An engaged stake belongs to the round. What an interrupted round left behind does not.
                @Override public boolean mayPickup(Player player) { return !engagedNow(player); }
            });
        }
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 79 + col * 18, 155 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 79 + col * 18, 213));
    }

    public CrashSetup setup() { return setup; }
    public CrashSettings settings() { return setup.settings(); }
    public ValueCatalog catalog() { return setup.catalog(); }
    public CrashGame.Phase phase() { return CrashGame.Phase.fromId(data.get(PHASE)); }
    public int multiplier() { return data.get(MULTIPLIER); }
    public int phaseTicks() { return data.get(PHASE_TICKS); }
    public long stake() { return ValueSync.read(data, STAKE); }
    public int settlement() { return data.get(SETTLEMENT); }
    public long paid() { return ValueSync.read(data, PAID); }
    public int participants() { return data.get(PLAYERS); }
    public long pot() { return ValueSync.read(data, POT); }
    public int state() { return data.get(STATE); }
    public int lastCrashPoint() { return data.get(LAST_POINT); }
    public long winnings() { return ValueSync.read(data, WINNINGS); }
    public int flightTick() { return data.get(FLIGHT_TICK); }
    /** Value prepared in the bet row, as the server counts it. */
    public long plannedStake() { return ValueSync.read(data, PLANNED); }
    public boolean isPayable() { return data.get(PAYABLE) != 0; }
    public boolean isEngaged() { return state() == STATE_ENGAGED; }

    /** The server answers from the round itself, the client from the state it was sent. */
    private boolean engagedNow(Player player) {
        if (game == null) return isEngaged();
        CrashGame.Bet bet = game.betOf(player.getUUID());
        return bet != null && bet.engaged();
    }

    public boolean canBet() {
        return state() == STATE_NONE && plannedStake() >= settings().minimumStake() && isPayable()
                && (phase() == CrashGame.Phase.WAITING || phase() == CrashGame.Phase.BETTING);
    }

    public boolean canCashOut() { return isEngaged() && phase() == CrashGame.Phase.FLYING; }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer) || player != owner || player.isSpectator() || !stillValid(player)) {
            return false;
        }
        if (game == null) return false;
        boolean handled = switch (button) {
            case BET_BUTTON -> game.place(player.getUUID(), game.stagedValue(player.getUUID()));
            case CASH_OUT_BUTTON -> game.cashOut(player.getUUID());
            case COLLECT_BUTTON -> collect(player);
            default -> false;
        };
        if (handled) broadcastChanges();
        return handled;
    }

    /** Moves the winnings into the inventory, keeping in the vault whatever does not fit. */
    private boolean collect(Player player) {
        boolean moved = false;
        for (int slot = CrashSettings.FIRST_PAYOUT_SLOT; slot < CrashSettings.VAULT_SIZE; slot++) {
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
            CrashGame.Bet bet = game.betOf(owner.getUUID());
            long staged = game.stagedValue(owner.getUUID());
            data.set(PHASE, game.phase().id());
            data.set(MULTIPLIER, game.publicMultiplier());
            data.set(PHASE_TICKS, game.remainingTicks());
            data.set(SETTLEMENT, bet == null ? 0 : bet.settledMultiplier());
            data.set(PLAYERS, game.participants());
            data.set(STATE, bet == null ? STATE_NONE
                    : bet.engaged() ? STATE_ENGAGED : bet.lost() ? STATE_LOST : STATE_CASHED);
            data.set(LAST_POINT, game.lastCrashPoint());
            data.set(FLIGHT_TICK, game.flightTick());
            data.set(PAYABLE, game.isPayable(owner.getUUID(), staged) ? 1 : 0);
            ValueSync.write(data, STAKE, bet == null ? 0 : bet.stake());
            ValueSync.write(data, PAID, bet == null ? 0 : bet.paid());
            ValueSync.write(data, POT, game.pot());
            ValueSync.write(data, WINNINGS, game.winnings(vault));
            ValueSync.write(data, PLANNED, staged);
        }
        super.broadcastChanges();
    }

    /** The round outlives its station; the player only needs to stay within reach of it. */
    @Override public boolean stillValid(Player player) {
        if (player.level().isClientSide) return player == owner;
        if (game == null || player != owner || !player.isAlive() || player.level() != game.level()) return false;
        var pos = game.station();
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                > CrashGames.REACH * CrashGames.REACH) {
            return false;
        }
        return player.getInventory().contains(new ItemStack(ModContent.TERMINAL))
                || (game.level().getBlockState(pos).getBlock() instanceof GameSurface surface
                        && surface.mode() == GameMode.CRASH);
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
                    || !moveItemStackTo(stack, 0, CrashSettings.STAKE_SLOTS, false)) {
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
        if (game != null) CrashGames.leave(game, player.getUUID());
        // An engaged stake stays with the round; only the items still being prepared come back.
        if (player instanceof ServerPlayer && player.isAlive()) {
            for (int index = 0; index < CrashSettings.STAKE_SLOTS; index++) {
                int slot = CrashSettings.INPUT_SLOT + index;
                ItemStack staged = vault.getItem(slot).copy();
                if (staged.isEmpty()) continue;
                moveItemStackTo(staged, INVENTORY_START, slots.size(), false);
                vault.setItem(slot, staged);
            }
        }
    }
}
