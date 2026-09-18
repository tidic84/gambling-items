package dev.gamblingitems.fabric.cases;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.cases.CaseRules;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.block.GameStationBlock;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.math.RoundingMode;
import java.security.SecureRandom;
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

/** Pay the announced price, the server draws once, then the reel replays that draw. */
public final class CaseMenu extends AbstractContainerMenu {
    public static final int OPEN_BUTTON = 1000;
    public static final int ANIMATION_TICKS = 70;
    public static final int PRICE_SLOT = 0;
    public static final int REWARD_SLOT = 1;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Player owner;
    private final Container vault;
    private final ContainerLevelAccess access;
    private final CaseSetup setup;
    private final IntSupplier draw;
    // animation ticks, won reward index plus one, cooldown ticks, selected case
    private final SimpleContainerData data = new SimpleContainerData(4);
    private long animationEnd;

    public CaseMenu(int syncId, Inventory inventory, CaseSetup setup) {
        this(syncId, inventory, setup, new SimpleContainer(REWARD_SLOT + 1), ContainerLevelAccess.NULL);
    }

    public CaseMenu(int syncId, Inventory inventory, CaseSetup setup,
                    Container vault, ContainerLevelAccess access) {
        this(syncId, inventory, setup, vault, access, () -> RANDOM.nextInt(CaseRules.TOTAL_WEIGHT));
    }

    CaseMenu(int syncId, Inventory inventory, CaseSetup setup,
             Container vault, ContainerLevelAccess access, IntSupplier draw) {
        super(ModContent.CASE_MENU, syncId);
        this.owner = inventory.player;
        this.setup = setup;
        this.vault = vault;
        this.access = access;
        this.draw = draw;
        addDataSlots(data);
        addSlot(new Slot(vault, PRICE_SLOT, 20, 100) {
            @Override public boolean mayPlace(ItemStack stack) {
                return !isAnimating() && setup.cases().isPrice(stack);
            }
            @Override public boolean mayPickup(Player player) { return !isAnimating(); }
        });
        addSlot(new Slot(vault, REWARD_SLOT, 146, 100) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return !isAnimating(); }
            @Override public boolean isActive() { return !isAnimating(); }
        });
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 79 + col * 18, 155 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 79 + col * 18, 213));
    }

    public CaseSetup setup() { return setup; }
    public ValueCatalog catalog() { return setup.catalog(); }
    public int selectedIndex() { return data.get(3); }
    public CaseDefinition selected() { return setup.cases().get(selectedIndex()); }
    public int remainingTicks() { return data.get(0); }
    public boolean isAnimating() { return remainingTicks() > 0; }
    public int resultIndex() { return data.get(1) - 1; }
    public ItemStack payment() { return vault.getItem(PRICE_SLOT); }
    public ItemStack reward() { return vault.getItem(REWARD_SLOT); }

    /** True when the inserted items pay for the selected case here and now. */
    public boolean isPaid() {
        CaseDefinition definition = selected();
        return definition != null && definition.pays(payment());
    }

    public boolean canOpen() {
        return !isAnimating() && data.get(2) == 0 && reward().isEmpty() && isPaid();
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer) || player != owner || player.isSpectator() || !stillValid(player)) return false;
        if (button >= 0 && button < setup.cases().cases().size() && !isAnimating()) {
            // A past result belongs to the case it was drawn from, never to the newly selected one.
            if (button != selectedIndex()) data.set(1, 0);
            data.set(3, button);
            broadcastChanges();
            return true;
        }
        if (button != OPEN_BUTTON || !canOpen() || player.getCooldowns().isOnCooldown(ModContent.TERMINAL)) return false;
        // The opening keeps this definition: a later configuration change cannot alter it.
        CaseDefinition definition = selected();
        int index = CaseRules.select(definition.weightArray(), draw.getAsInt());
        CaseReward reward = definition.rewards().get(index);
        // Resolve on the server once. The reel cannot change or repeat this opening.
        vault.removeItem(PRICE_SLOT, definition.priceCount());
        vault.setItem(REWARD_SLOT, reward.stack());
        data.set(1, index + 1);
        animationEnd = player.level().getGameTime() + ANIMATION_TICKS;
        data.set(0, ANIMATION_TICKS);
        data.set(2, ANIMATION_TICKS);
        player.getCooldowns().addCooldown(ModContent.TERMINAL, ANIMATION_TICKS);
        access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof GameStationEntity station) {
                station.show(player.getName().getString(),
                        definition.percentOf(index).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%",
                        "case_result", reward.item().toString(), true, ANIMATION_TICKS);
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
                        && station.mode() == GameMode.CASE_OPENING
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
            if (!getSlot(PRICE_SLOT).mayPlace(stack) || !moveItemStackTo(stack, PRICE_SLOT, PRICE_SLOT + 1, false)) {
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
        // The reward stays in the persistent vault until it is collected.
        // Unspent payment returns to the inventory; an overflow stays recoverable in the vault.
        if (player instanceof ServerPlayer && player.isAlive()) {
            ItemStack remaining = payment().copy();
            if (!remaining.isEmpty()) {
                moveItemStackTo(remaining, REWARD_SLOT + 1, slots.size(), false);
                vault.setItem(PRICE_SLOT, remaining);
            }
        }
    }
}
