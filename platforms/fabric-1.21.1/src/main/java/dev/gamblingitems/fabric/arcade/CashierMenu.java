package dev.gamblingitems.fabric.arcade;

import dev.gamblingitems.fabric.Compat;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.menu.CasinoLayout;
import dev.gamblingitems.fabric.menu.ValueSync;
import dev.gamblingitems.fabric.value.ItemBank;
import dev.gamblingitems.fabric.value.ValueCatalog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The cashier: items of value changed into casino chips and back, at their catalogue value, and the
 * prize counter where arcade tickets buy prizes. Nothing here is a game: every exchange gives back
 * exactly what it takes, less what is below the smallest chip or item, which stays in the tray.
 *
 * <p>The tray is the player's own saved container, so what is put down is never lost; it comes back
 * to the inventory when the window closes.
 */
public final class CashierMenu extends AbstractContainerMenu {
    public static final int TRAY_SLOTS = 5;
    public static final int TO_CHIPS = 1000, TO_ITEMS = 1001, PRIZE = 1100;
    public static final int TRAY_X = CasinoLayout.SLIP_X + 9, TRAY_Y = CasinoLayout.SLIP_Y + 24;
    public static final int INVENTORY_START = TRAY_SLOTS;
    private static final int BALANCE = 0, DATA_SIZE = ValueSync.SLOTS;

    private final Player owner;
    private final Container tray;
    private final ContainerLevelAccess access;
    private final CashierSetup setup;
    private final SimpleContainerData data = new SimpleContainerData(DATA_SIZE);

    public CashierMenu(int syncId, Inventory inventory, CashierSetup setup) {
        this(syncId, inventory, setup, new SimpleContainer(TRAY_SLOTS), ContainerLevelAccess.NULL);
    }

    public CashierMenu(int syncId, Inventory inventory, CashierSetup setup, Container tray, ContainerLevelAccess access) {
        super(ModContent.CASHIER_MENU, syncId);
        this.owner = inventory.player;
        this.setup = setup;
        this.tray = tray;
        this.access = access;
        addDataSlots(data);
        for (int index = 0; index < TRAY_SLOTS; index++) {
            addSlot(new Slot(tray, index, TRAY_X + index * 18, TRAY_Y) {
                @Override public boolean mayPlace(ItemStack stack) { return setup.catalog().valueOf(stack) > 0; }
            });
        }
        CasinoLayout.inventory(inventory, this::addSlot);
    }

    public CashierSetup setup() { return setup; }
    public ArcadeSettings arcade() { return setup.arcade(); }
    public long balance() { return ValueSync.read(data, BALANCE); }

    /** The value of the tray: its chips, and everything else. */
    public long chipValue() { return value(true); }
    public long itemValue() { return value(false); }

    private long value(boolean chips) {
        long total = 0;
        for (int slot = 0; slot < TRAY_SLOTS; slot++) {
            ItemStack stack = tray.getItem(slot);
            if (isChip(stack) == chips) total += setup.catalog().valueOf(stack);
        }
        return total;
    }

    public static boolean isChip(ItemStack stack) {
        for (int chip : ArcadeSettings.CHIPS) if (stack.is(ModContent.chip(chip))) return true;
        return false;
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer server) || player != owner || player.isSpectator() || !stillValid(player)) return false;
        boolean handled;
        if (button == TO_CHIPS) handled = toChips();
        else if (button == TO_ITEMS) handled = toItems();
        else if (button >= PRIZE && button < PRIZE + arcade().prizes().size()) handled = buy(server, button - PRIZE);
        else handled = false;
        if (handled) broadcastChanges();
        return handled;
    }

    /**
     * Every item of value in the tray becomes chips, dearest first, and the rest comes back in change.
     * Change must be exact: when the rest cannot be paid in items, a few chips fewer are tried, then nothing is done.
     */
    private boolean toChips() {
        long value = itemValue();
        long chipValue = arcade().chipValue();
        ItemStack[] before = snapshot();
        for (long units = value / chipValue, least = Math.max(1, units - 25); units >= least; units--) {
            if (change(value, units, chipValue)) {
                tray.setChanged();
                return true;
            }
            restore(before);
        }
        return false;
    }

    private boolean change(long value, long units, long chipValue) {
        for (int slot = 0; slot < TRAY_SLOTS; slot++) {
            if (!tray.getItem(slot).isEmpty() && !isChip(tray.getItem(slot))) tray.setItem(slot, ItemStack.EMPTY);
        }
        long rest = value - units * chipValue;
        for (int index = ArcadeSettings.CHIPS.length - 1; index >= 0; index--) {
            int chip = ArcadeSettings.CHIPS[index];
            if (!place(new ItemStack(ModContent.chip(chip)), units / chip)) return false;
            units %= chip;
        }
        return rest == 0 || ItemBank.canStore(setup.catalog(), tray, 0, TRAY_SLOTS, rest)
                && ItemBank.store(setup.catalog(), tray, 0, TRAY_SLOTS, rest) == 0;
    }

    /** Every chip in the tray is changed back into items of the catalogue, dearest first. */
    private boolean toItems() {
        long value = chipValue();
        if (value == 0) return false;
        ItemStack[] before = snapshot();
        for (int slot = 0; slot < TRAY_SLOTS; slot++) {
            if (isChip(tray.getItem(slot))) tray.setItem(slot, ItemStack.EMPTY);
        }
        if (!ItemBank.canStore(setup.catalog(), tray, 0, TRAY_SLOTS, value)
                || ItemBank.store(setup.catalog(), tray, 0, TRAY_SLOTS, value) != 0) {
            restore(before);
            return false;
        }
        tray.setChanged();
        return true;
    }

    /** Puts count items of a kind into the empty slots of the tray; false if they do not all fit. */
    private boolean place(ItemStack kind, long count) {
        int limit = kind.getMaxStackSize();
        for (int slot = 0; slot < TRAY_SLOTS && count > 0; slot++) {
            if (!tray.getItem(slot).isEmpty()) continue;
            int placed = (int) Math.min(limit, count);
            tray.setItem(slot, kind.copyWithCount(placed));
            count -= placed;
        }
        return count == 0;
    }

    private ItemStack[] snapshot() {
        ItemStack[] copy = new ItemStack[TRAY_SLOTS];
        for (int slot = 0; slot < TRAY_SLOTS; slot++) copy[slot] = tray.getItem(slot).copy();
        return copy;
    }

    private void restore(ItemStack[] copy) {
        for (int slot = 0; slot < TRAY_SLOTS; slot++) tray.setItem(slot, copy[slot]);
    }

    /** One prize of the counter, if the tickets are there; it goes to the inventory, or at the feet when full. */
    private boolean buy(ServerPlayer player, int index) {
        ArcadeSettings.Prize prize = arcade().prizes().get(index);
        if (!Arcade.ledger(player).spend(player.getUUID(), prize.tickets(), player.level().getGameTime())) return false;
        player.getInventory().placeItemBackInInventory(new ItemStack(Compat.item(prize.item()), prize.count()));
        return true;
    }

    @Override public void broadcastChanges() {
        if (owner instanceof ServerPlayer server) {
            ValueSync.write(data, BALANCE, Arcade.ledger(server).balance(server.getUUID(), server.level().getGameTime()));
        }
        super.broadcastChanges();
    }

    @Override public boolean stillValid(Player player) {
        if (player.level().isClientSide) return player == owner;
        return player == owner && player.isAlive() && access.evaluate(
                (level, pos) -> level.getBlockState(pos).getBlock() instanceof CashierBlock
                        && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64,
                false);
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
        } else if (setup.catalog().valueOf(stack) <= 0 || !moveItemStackTo(stack, 0, TRAY_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        // What fits goes back to the inventory; anything else waits in the saved tray for next time.
        if (!(player instanceof ServerPlayer) || !player.isAlive()) return;
        for (int slot = 0; slot < TRAY_SLOTS; slot++) {
            ItemStack stack = tray.getItem(slot).copy();
            if (stack.isEmpty()) continue;
            moveItemStackTo(stack, INVENTORY_START, slots.size(), false);
            tray.setItem(slot, stack);
        }
    }

    /** The value of a stack for the cashier, from the screen. */
    public long valueOf(ItemStack stack) { return setup.catalog().valueOf(stack); }

    public ValueCatalog catalog() { return setup.catalog(); }
}
