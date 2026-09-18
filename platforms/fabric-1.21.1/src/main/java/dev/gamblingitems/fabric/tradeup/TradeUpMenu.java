package dev.gamblingitems.fabric.tradeup;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.tradeup.TradeUpRules;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.block.GameStationBlock;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.function.IntSupplier;
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

/** Several comparable items become one better item. The server decides before the reel moves. */
public final class TradeUpMenu extends AbstractContainerMenu {
    public static final int SPIN_BUTTON = 1000;
    public static final int ANIMATION_TICKS = 70;
    public static final int INPUT_SLOTS = 5;
    public static final int REWARD_SLOT = INPUT_SLOTS;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Player owner;
    private final Container vault;
    private final ContainerLevelAccess access;
    private final TradeUpSetup setup;
    private final IntSupplier draw;
    // animation ticks, won reward index plus one, cooldown ticks
    private final SimpleContainerData data = new SimpleContainerData(3);
    private long animationEnd;

    public TradeUpMenu(int syncId, Inventory inventory, TradeUpSetup setup) {
        this(syncId, inventory, setup, new SimpleContainer(REWARD_SLOT + 1), ContainerLevelAccess.NULL);
    }

    public TradeUpMenu(int syncId, Inventory inventory, TradeUpSetup setup,
                       Container vault, ContainerLevelAccess access) {
        this(syncId, inventory, setup, vault, access, () -> RANDOM.nextInt(TradeUpRules.TOTAL_WEIGHT));
    }

    TradeUpMenu(int syncId, Inventory inventory, TradeUpSetup setup,
                Container vault, ContainerLevelAccess access, IntSupplier draw) {
        super(ModContent.TRADE_UP_MENU, syncId);
        this.owner = inventory.player;
        this.setup = setup;
        this.vault = vault;
        this.access = access;
        this.draw = draw;
        addDataSlots(data);
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            addSlot(new Slot(vault, slot, 20 + slot * 22, 88) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return !isAnimating() && setup.catalog().unitValue(stack) > 0;
                }
                @Override public boolean mayPickup(Player player) { return !isAnimating(); }
                // Never hold more units than one contract consumes.
                @Override public int getMaxStackSize() { return setup.settings().requiredUnits(); }
            });
        }
        addSlot(new Slot(vault, REWARD_SLOT, 146, 88) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return !isAnimating(); }
            @Override public boolean isActive() { return !isAnimating(); }
        });
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 79 + col * 18, 155 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 79 + col * 18, 213));
    }

    public TradeUpSetup setup() { return setup; }
    public ValueCatalog catalog() { return setup.catalog(); }
    public int remainingTicks() { return data.get(0); }
    public boolean isAnimating() { return remainingTicks() > 0; }
    public int resultIndex() { return data.get(1) - 1; }
    public ItemStack reward() { return vault.getItem(REWARD_SLOT); }

    public Optional<TradeUpTable> table() { return TradeUpTable.build(setup, vault, INPUT_SLOTS); }

    public int stakedUnits() {
        int units = 0;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) units += vault.getItem(slot).getCount();
        return units;
    }

    public long stakeValue() {
        long total = 0;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) total += setup.catalog().valueOf(vault.getItem(slot));
        return total;
    }

    public boolean canSpin() {
        return !isAnimating() && data.get(2) == 0 && reward().isEmpty() && table().isPresent();
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer) || player != owner || player.isSpectator() || !stillValid(player)) return false;
        if (access.evaluate((level, pos) -> level.getBlockEntity(pos) instanceof GameStationEntity station
                && station.animating(), false)) return false;
        if (button != SPIN_BUTTON || !canSpin() || player.getCooldowns().isOnCooldown(ModContent.TERMINAL)) return false;
        TradeUpTable table = table().orElse(null);
        if (table == null) return false;
        // Resolve on the server once. The reel cannot change or repeat this trade.
        int index = setup.settings().rules().select(table.weightArray(), draw.getAsInt());
        ValueCatalog.Entry reward = table.rewards().get(index);
        for (int slot = 0; slot < INPUT_SLOTS; slot++) vault.setItem(slot, ItemStack.EMPTY);
        vault.setItem(REWARD_SLOT, reward.stack());
        data.set(1, index + 1);
        animationEnd = player.level().getGameTime() + ANIMATION_TICKS;
        data.set(0, ANIMATION_TICKS);
        data.set(2, ANIMATION_TICKS);
        player.getCooldowns().addCooldown(ModContent.TERMINAL, ANIMATION_TICKS);
        access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof GameStationEntity station) {
                station.reelItems = table.rewards().stream().map(entry -> entry.id().toString())
                        .collect(java.util.stream.Collectors.joining(","));
                station.show(player.getName().getString(), table.percentOf(index).setScale(2,
                                java.math.RoundingMode.HALF_UP).toPlainString() + "%",
                        "trade_up_result", reward.id().toString(), true, ANIMATION_TICKS);
            }
        });
        broadcastChanges();
        return true;
    }

    @Override public void broadcastChanges() {
        if (!owner.level().isClientSide) {
            data.set(0, (int) Math.max(0, Math.min(ANIMATION_TICKS, animationEnd - owner.level().getGameTime())));
            data.set(2, (int) Math.ceil(owner.getCooldowns().getCooldownPercent(ModContent.TERMINAL, 0) * ANIMATION_TICKS));
        }
        super.broadcastChanges();
    }

    @Override public boolean stillValid(Player player) {
        if (player.level().isClientSide) return player == owner;
        return player == owner && player.isAlive() && access.evaluate(
                (level, pos) -> level.getBlockState(pos).getBlock() instanceof GameStationBlock station
                        && station.mode() == GameMode.TRADE_UP
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
        if (index <= REWARD_SLOT) {
            if (!moveItemStackTo(stack, REWARD_SLOT + 1, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!getSlot(0).mayPlace(stack) || !moveItemStackTo(stack, 0, INPUT_SLOTS, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        // The reward stays in the persistent vault until it is collected.
        // Unused stakes return to the inventory; an overflow stays recoverable in the vault.
        if (player instanceof ServerPlayer && player.isAlive()) {
            for (int slot = 0; slot < INPUT_SLOTS; slot++) {
                ItemStack remaining = vault.getItem(slot).copy();
                if (remaining.isEmpty()) continue;
                moveItemStackTo(remaining, REWARD_SLOT + 1, slots.size(), false);
                vault.setItem(slot, remaining);
            }
        }
    }
}
