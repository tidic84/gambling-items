package dev.gamblingitems.fabric.roulette;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.roulette.RouletteRules.Colour;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.block.GameStationBlock;
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
 * One player's seat at a shared roulette. It sends intentions only: the spin, the slot and every
 * payment belong to {@link RouletteGame} on the server.
 */
public final class RouletteMenu extends AbstractContainerMenu {
    /** One button per colour, then the button that empties the winnings. */
    public static final int BET_BUTTON = 1000, COLLECT_BUTTON = 1100;
    public static final int INVENTORY_START = 2;
    public static final int STATE_NONE = 0, STATE_ENGAGED = 1, STATE_WON = 2, STATE_LOST = 3;

    private final Player owner;
    private final Container vault;
    private final RouletteSettings settings;
    private final RouletteGame game;
    // phase, phase ticks, result slot plus one, my stake, my colour plus one, what I was paid,
    // players, pot, my winnings, my largest bet, previous result plus one, my state
    private final SimpleContainerData data = new SimpleContainerData(12);

    public RouletteMenu(int syncId, Inventory inventory, RouletteSettings settings) {
        this(syncId, inventory, settings, new SimpleContainer(RouletteSettings.VAULT_SIZE), null);
    }

    public RouletteMenu(int syncId, Inventory inventory, RouletteSettings settings,
                        Container vault, RouletteGame game) {
        super(ModContent.ROULETTE_MENU, syncId);
        this.owner = inventory.player;
        this.settings = settings;
        this.vault = vault;
        this.game = game;
        addDataSlots(data);
        addSlot(new Slot(vault, RouletteSettings.INPUT_SLOT, 20, 128) {
            @Override public boolean mayPlace(ItemStack stack) { return settings.isStake(stack); }
        });
        addSlot(new Slot(vault, RouletteSettings.ENGAGED_SLOT, 50, 128) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            // An engaged stake belongs to the round. What an interrupted round left behind does not.
            @Override public boolean mayPickup(Player player) { return !engagedNow(player); }
        });
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 79 + col * 18, 155 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 79 + col * 18, 213));
    }

    public RouletteSettings settings() { return settings; }
    public RouletteGame.Phase phase() { return RouletteGame.Phase.fromId(data.get(0)); }
    public int phaseTicks() { return data.get(1); }
    public int resultSlot() { return data.get(2) - 1; }
    public int stake() { return data.get(3); }
    public Colour colour() { return data.get(4) == 0 ? null : Colour.values()[data.get(4) - 1]; }
    public int paid() { return data.get(5); }
    public int participants() { return data.get(6); }
    public int pot() { return data.get(7); }
    public int winnings() { return data.get(8); }
    public int largestStake() { return data.get(9); }
    public int lastResultSlot() { return data.get(10) - 1; }
    public int state() { return data.get(11); }
    public boolean isEngaged() { return state() == STATE_ENGAGED; }

    private boolean engagedNow(Player player) {
        if (game == null) return isEngaged();
        RouletteGame.Bet bet = game.betOf(player.getUUID());
        return bet != null && bet.engaged();
    }

    /** How many items the next bet would engage, given the bet slot and what a win would need. */
    public int plannedStake() {
        return Math.min(vault.getItem(RouletteSettings.INPUT_SLOT).getCount(), largestStake());
    }

    public boolean canBet() {
        return state() == STATE_NONE && plannedStake() >= settings.minimumStake()
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
            int colour = button - BET_BUTTON;
            handled = colour >= 0 && colour < Colour.values().length
                    && plannedStake() >= settings.minimumStake()
                    && game.place(player.getUUID(), Colour.values()[colour], plannedStake());
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
            RouletteGame.Bet bet = game.betOf(owner.getUUID());
            data.set(0, game.phase().id());
            data.set(1, game.remainingTicks());
            data.set(2, game.resultSlot() + 1);
            data.set(3, bet == null ? 0 : bet.stake());
            data.set(4, bet == null ? 0 : bet.colour().ordinal() + 1);
            data.set(5, bet == null ? 0 : (int) Math.min(Integer.MAX_VALUE, bet.paid()));
            data.set(6, game.participants());
            data.set(7, game.pot());
            data.set(8, game.winnings(vault));
            data.set(9, game.largestStake(owner.getUUID()));
            data.set(10, game.lastResultSlot() + 1);
            data.set(11, bet == null ? STATE_NONE
                    : bet.engaged() ? STATE_ENGAGED : bet.paid() > 0 ? STATE_WON : STATE_LOST);
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
                || (game.level().getBlockState(pos).getBlock() instanceof GameStationBlock station
                        && station.mode() == GameMode.ROULETTE);
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
            if (!settings.isStake(stack)
                    || !moveItemStackTo(stack, RouletteSettings.INPUT_SLOT, RouletteSettings.INPUT_SLOT + 1, false)) {
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
        // An engaged stake stays with the round; only the items still being prepared come back.
        if (player instanceof ServerPlayer && player.isAlive()) {
            ItemStack staged = vault.getItem(RouletteSettings.INPUT_SLOT).copy();
            if (!staged.isEmpty()) {
                moveItemStackTo(staged, INVENTORY_START, slots.size(), false);
                vault.setItem(RouletteSettings.INPUT_SLOT, staged);
            }
        }
    }
}
