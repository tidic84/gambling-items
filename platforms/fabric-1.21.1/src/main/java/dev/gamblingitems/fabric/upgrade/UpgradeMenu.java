package dev.gamblingitems.fabric.upgrade;

import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.block.GameStationBlock;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.math.BigDecimal;
import java.security.SecureRandom;
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

public final class UpgradeMenu extends AbstractContainerMenu {
    public static final int SPIN_BUTTON = ValueCatalog.MAX_ENTRIES;
    public static final int ANIMATION_TICKS = 60;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Player owner;
    private final Container vault;
    private final ContainerLevelAccess access;
    private final UpgradeSetup setup;
    private final java.util.function.DoubleSupplier draw;
    private final boolean portable;
    // selection, animation ticks, result (0 none / 1 win / 2 loss), frozen chance, cooldown
    private final SimpleContainerData data = new SimpleContainerData(7);
    private long animationEnd;

    public UpgradeMenu(int syncId, Inventory inventory, UpgradeSetup setup) { this(syncId, inventory, setup, false); }

    public UpgradeMenu(int syncId, Inventory inventory, UpgradeSetup setup, boolean portable) {
        this(syncId, inventory, setup, new SimpleContainer(2), ContainerLevelAccess.NULL, () -> 1, portable);
    }

    public UpgradeMenu(int syncId, Inventory inventory, UpgradeSetup setup,
                       Container vault, ContainerLevelAccess access) {
        this(syncId, inventory, setup, vault, access, RANDOM::nextDouble);
    }

    UpgradeMenu(int syncId, Inventory inventory, UpgradeSetup setup,
                Container vault, ContainerLevelAccess access, java.util.function.DoubleSupplier draw) {
        this(syncId, inventory, setup, vault, access, draw, access == ContainerLevelAccess.NULL);
    }

    private UpgradeMenu(int syncId, Inventory inventory, UpgradeSetup setup, Container vault,
                ContainerLevelAccess access, java.util.function.DoubleSupplier draw, boolean portable) {
        super(ModContent.UPGRADER_MENU, syncId);
        this.owner = inventory.player;
        this.setup = setup;
        this.vault = vault;
        this.access = access;
        this.draw = draw;
        this.portable = portable;
        data.set(0, -1);
        addDataSlots(data);
        addSlot(new Slot(vault, 0, 30, 72) {
            @Override public boolean mayPlace(ItemStack stack) { return !isAnimating() && setup.catalog().valueOf(stack) > 0; }
            @Override public boolean mayPickup(Player player) { return !isAnimating(); }
        });
        addSlot(new Slot(vault, 1, 206, 72) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return !isAnimating(); }
            @Override public boolean isActive() { return !isAnimating(); }
        });
        dev.gamblingitems.fabric.menu.CasinoLayout.inventory(inventory, this::addSlot);
    }

    public UpgradeSetup setup() { return setup; }
    /** Opened from a portable item, so its window shows the tabs of the other games of that item. */
    public boolean portable() { return portable; }
    public ValueCatalog catalog() { return setup.catalog(); }
    public int selectedIndex() { return data.get(0); }
    public int remainingTicks() { return data.get(1); }
    public int result() { return data.get(2); }
    public boolean isAnimating() { return remainingTicks() > 0; }
    public long inputValue() { return setup.catalog().valueOf(vault.getItem(0)); }
    public ValueCatalog.Entry selected() {
        return selectedIndex() < 0 || selectedIndex() >= setup.catalog().entries().size()
                ? null : setup.catalog().entries().get(selectedIndex());
    }
    public double chance() {
        if (isAnimating() || (inputValue() == 0 && result() != 0)) {
            return Float.intBitsToFloat((int) dev.gamblingitems.fabric.menu.ValueSync.read(data, 5));
        }
        return chanceFor(selected());
    }
    public double chanceFor(ValueCatalog.Entry target) {
        long input = inputValue();
        return target == null || input <= 0 || target.value() <= input ? 0
                : setup.rules().chance(input, target.value()).doubleValue();
    }
    public boolean canSpin() {
        var target = selected();
        return !isAnimating() && data.get(4) == 0 && vault.getItem(1).isEmpty()
                && target != null && inputValue() > 0 && target.value() > inputValue();
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer) || player != owner || player.isSpectator() || !stillValid(player)) return false;
        if (dev.gamblingitems.fabric.menu.TerminalTabs.matches(button))
            return dev.gamblingitems.fabric.menu.TerminalTabs.handle(player, button);
        if (button >= 0 && button < setup.catalog().entries().size() && !isAnimating()) {
            data.set(0, button);
            data.set(2, 0);
            broadcastChanges();
            return true;
        }
        if (access.evaluate((level, pos) -> level.getBlockEntity(pos) instanceof GameStationEntity station
                && station.animating(), false)) return false;
        if (button != SPIN_BUTTON || !canSpin() || dev.gamblingitems.fabric.Compat.coolingDown(player)) return false;
        long value = inputValue();
        var target = selected();
        boolean won = setup.rules().wins(value, target.value(), BigDecimal.valueOf(draw.getAsDouble()));
        // Resolve on the server once. The animation cannot change or repeat this payment.
        data.set(3, setup.rules().chance(value, target.value()).movePointRight(4).intValue());
        dev.gamblingitems.fabric.menu.ValueSync.write(data, 5,
                Float.floatToRawIntBits(setup.rules().chance(value, target.value()).floatValue()));
        data.set(2, won ? 1 : 2);
        animationEnd = player.level().getGameTime() + ANIMATION_TICKS;
        data.set(1, ANIMATION_TICKS);
        data.set(4, ANIMATION_TICKS);
        dev.gamblingitems.fabric.Compat.coolDown(player, ANIMATION_TICKS);
        vault.setItem(0, ItemStack.EMPTY);
        if (won) vault.setItem(1, target.stack());
        access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof GameStationEntity station)
                station.show(player.getName().getString(),
                        java.math.BigDecimal.valueOf(data.get(3), 2).toPlainString() + "%",
                        won ? "won" : "lost", won ? target.id().toString() : "", won, ANIMATION_TICKS);
        });
        broadcastChanges();
        return true;
    }

    @Override public void broadcastChanges() {
        if (!owner.level().isClientSide) {
            data.set(1, (int) Math.max(0, Math.min(ANIMATION_TICKS, animationEnd - owner.level().getGameTime())));
            data.set(4, (int) Math.ceil(dev.gamblingitems.fabric.Compat.cooldown(owner) * ANIMATION_TICKS));
        }
        super.broadcastChanges();
    }

    @Override public boolean stillValid(Player player) {
        if (player.level().isClientSide) return player == owner;
        return player == owner && player.isAlive() && access.evaluate(
                (level, pos) -> level.getBlockState(pos).getBlock() instanceof GameStationBlock station
                        && station.mode() == GameMode.UPGRADER
                        && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64,
                dev.gamblingitems.fabric.item.TerminalItem.hasAccess(player, GameMode.UPGRADER));
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
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!getSlot(0).mayPlace(stack) || !moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        // Rewards stay in the persistent vault until explicitly collected.
        // Return unused inputs where possible; an inventory overflow stays in the vault.
        if (player instanceof ServerPlayer && player.isAlive()) {
            ItemStack remaining = vault.getItem(0).copy();
            if (!remaining.isEmpty()) {
                // Inventory.add discards overflow in creative mode; slot transfer preserves it.
                moveItemStackTo(remaining, 2, slots.size(), false);
                vault.setItem(0, remaining);
            }
        }
    }
}
