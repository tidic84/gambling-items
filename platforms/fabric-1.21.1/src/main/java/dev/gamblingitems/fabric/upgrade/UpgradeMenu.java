package dev.gamblingitems.fabric.upgrade;

import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.block.UpgradeStationEntity;
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
    public static final int SPIN_BUTTON = 1000;
    public static final int ANIMATION_TICKS = 60;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Player owner;
    private final Container vault;
    private final ContainerLevelAccess access;
    private final UpgradeCatalog catalog;
    private final java.util.function.DoubleSupplier draw;
    // selection, animation ticks, result (0 none / 1 win / 2 loss), frozen chance, cooldown
    private final SimpleContainerData data = new SimpleContainerData(5);
    private long animationEnd;

    public UpgradeMenu(int syncId, Inventory inventory, UpgradeCatalog catalog) {
        this(syncId, inventory, catalog, new SimpleContainer(2), ContainerLevelAccess.NULL);
    }

    public UpgradeMenu(int syncId, Inventory inventory, UpgradeCatalog catalog,
                       Container vault, ContainerLevelAccess access) {
        this(syncId, inventory, catalog, vault, access, RANDOM::nextDouble);
    }

    UpgradeMenu(int syncId, Inventory inventory, UpgradeCatalog catalog,
                Container vault, ContainerLevelAccess access, java.util.function.DoubleSupplier draw) {
        super(ModContent.UPGRADER_MENU, syncId);
        this.owner = inventory.player;
        this.catalog = catalog;
        this.vault = vault;
        this.access = access;
        this.draw = draw;
        data.set(0, -1);
        addDataSlots(data);
        addSlot(new Slot(vault, 0, 20, 62) {
            @Override public boolean mayPlace(ItemStack stack) { return !isAnimating() && catalog.valueOf(stack) > 0; }
            @Override public boolean mayPickup(Player player) { return !isAnimating(); }
        });
        addSlot(new Slot(vault, 1, 146, 62) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return !isAnimating(); }
            @Override public boolean isActive() { return !isAnimating(); }
        });
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 79 + col * 18, 155 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 79 + col * 18, 213));
    }

    public UpgradeCatalog catalog() { return catalog; }
    public int selectedIndex() { return data.get(0); }
    public int remainingTicks() { return data.get(1); }
    public int result() { return data.get(2); }
    public boolean isAnimating() { return remainingTicks() > 0; }
    public long inputValue() { return catalog.valueOf(vault.getItem(0)); }
    public UpgradeCatalog.Entry selected() {
        return selectedIndex() < 0 || selectedIndex() >= catalog.entries().size()
                ? null : catalog.entries().get(selectedIndex());
    }
    public double chance() {
        if (isAnimating() || (inputValue() == 0 && result() != 0)) return data.get(3) / 10_000.0;
        var target = selected();
        long input = inputValue();
        return target == null || input <= 0 || target.value() <= input ? 0
                : catalog.rules().chance(input, target.value()).doubleValue();
    }
    public boolean canSpin() {
        var target = selected();
        return !isAnimating() && data.get(4) == 0 && vault.getItem(1).isEmpty()
                && target != null && inputValue() > 0 && target.value() > inputValue();
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer) || player != owner || player.isSpectator() || !stillValid(player)) return false;
        if (button >= 0 && button < catalog.entries().size() && !isAnimating()) {
            data.set(0, button);
            data.set(2, 0);
            broadcastChanges();
            return true;
        }
        if (button != SPIN_BUTTON || !canSpin() || player.getCooldowns().isOnCooldown(ModContent.TERMINAL)) return false;
        long value = inputValue();
        var target = selected();
        boolean won = catalog.rules().wins(value, target.value(), BigDecimal.valueOf(draw.getAsDouble()));
        // Resolve on the server once. The animation cannot change or repeat this payment.
        data.set(3, catalog.rules().chance(value, target.value()).movePointRight(4).intValue());
        data.set(2, won ? 1 : 2);
        animationEnd = player.level().getGameTime() + ANIMATION_TICKS;
        data.set(1, ANIMATION_TICKS);
        data.set(4, ANIMATION_TICKS);
        player.getCooldowns().addCooldown(ModContent.TERMINAL, ANIMATION_TICKS);
        vault.setItem(0, ItemStack.EMPTY);
        if (won) vault.setItem(1, target.stack());
        access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof UpgradeStationEntity station)
                station.showResult(target.id(), data.get(3), won, player.getName().getString());
        });
        broadcastChanges();
        return true;
    }

    @Override public void broadcastChanges() {
        if (!owner.level().isClientSide) {
            data.set(1, (int) Math.max(0, Math.min(ANIMATION_TICKS, animationEnd - owner.level().getGameTime())));
            data.set(4, (int) Math.ceil(owner.getCooldowns().getCooldownPercent(ModContent.TERMINAL, 0) * ANIMATION_TICKS));
        }
        super.broadcastChanges();
    }

    @Override public boolean stillValid(Player player) {
        if (player.level().isClientSide) return player == owner;
        return player == owner && player.isAlive() && access.evaluate(
                (level, pos) -> level.getBlockState(pos).is(ModContent.STATION)
                        && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64,
                player.getInventory().contains(new ItemStack(ModContent.TERMINAL)));
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
