package dev.gamblingitems.fabric.slots;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.slots.SlotRules;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.block.GameSurface;
import dev.gamblingitems.fabric.menu.ValueSync;
import dev.gamblingitems.fabric.value.ItemBank;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.security.SecureRandom;
import java.util.function.IntUnaryOperator;
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
 * One player at one cabinet. A pull is resolved on the server the moment the lever is used; the
 * reels only replay a line that has already been paid.
 *
 * <p>The items played stay where they are: a winning line hands back the very items that were
 * staked and adds the profit in change, and a losing line is the only thing that takes them.
 */
public final class SlotMenu extends AbstractContainerMenu {
    public static final int SPIN_BUTTON = 1000, COLLECT_BUTTON = 1001;
    public static final int STAKE_X = 20, INPUT_Y = 146;
    public static final int INVENTORY_START = SlotSettings.STAKE_SLOTS;
    private static final SecureRandom RANDOM = new SecureRandom();
    // The three reels, the ticks left of the pull, then the values, then the state.
    private static final int FIRST_REEL = 0, TICKS = 3, MULTIPLIER = 4, STAKE = 5, WON = 7,
            WINNINGS = 9, STATE = 11;
    private static final int DATA_SIZE = 12;
    public static final int STATE_IDLE = 0, STATE_SPINNING = 1, STATE_RESULT = 2;

    private final Player owner;
    private final Container vault;
    private final ContainerLevelAccess access;
    private final SlotSetup setup;
    private final IntUnaryOperator draw;
    private final SimpleContainerData data = new SimpleContainerData(DATA_SIZE);
    private long animationEnd;

    public SlotMenu(int syncId, Inventory inventory, SlotSetup setup) {
        this(syncId, inventory, setup, new SimpleContainer(SlotSettings.VAULT_SIZE),
                ContainerLevelAccess.NULL);
    }

    public SlotMenu(int syncId, Inventory inventory, SlotSetup setup, Container vault,
                    ContainerLevelAccess access) {
        this(syncId, inventory, setup, vault, access, RANDOM::nextInt);
    }

    SlotMenu(int syncId, Inventory inventory, SlotSetup setup, Container vault,
             ContainerLevelAccess access, IntUnaryOperator draw) {
        super(ModContent.SLOT_MENU, syncId);
        this.owner = inventory.player;
        this.setup = setup;
        this.vault = vault;
        this.access = access;
        this.draw = draw;
        for (int reel = 0; reel < SlotRules.REELS; reel++) data.set(FIRST_REEL + reel, -1);
        addDataSlots(data);
        for (int index = 0; index < SlotSettings.STAKE_SLOTS; index++) {
            addSlot(new Slot(vault, SlotSettings.INPUT_SLOT + index, STAKE_X + index * 18, INPUT_Y) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return !spinning() && setup.catalog().valueOf(stack) > 0;
                }
                @Override public boolean mayPickup(Player player) { return !spinning(); }
            });
        }
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 79 + col * 18, 196 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 79 + col * 18, 254));
    }

    public SlotSetup setup() { return setup; }
    public SlotSettings settings() { return setup.settings(); }
    public ValueCatalog catalog() { return setup.catalog(); }
    public int remainingTicks() { return data.get(TICKS); }
    public boolean spinning() { return remainingTicks() > 0; }
    public int state() { return data.get(STATE); }
    public int multiplier() { return data.get(MULTIPLIER); }
    public long stake() { return ValueSync.read(data, STAKE); }
    public long lastWin() { return ValueSync.read(data, WON); }
    public long winnings() { return ValueSync.read(data, WINNINGS); }

    /** The line shown on the reels, or -1 on a reel that has never turned. */
    public int[] reels() {
        int[] reels = new int[SlotRules.REELS];
        for (int reel = 0; reel < SlotRules.REELS; reel++) reels[reel] = data.get(FIRST_REEL + reel);
        return reels;
    }

    /** What is waiting in the credit slots of this cabinet. */
    public long credit() {
        return ItemBank.valueOf(catalog(), vault, SlotSettings.INPUT_SLOT, SlotSettings.FIRST_PAYOUT_SLOT);
    }

    /** True when the best line of the paytable could be handed over right now. */
    public boolean isPayable(long stake) {
        return ItemBank.canStore(catalog(), vault, SlotSettings.FIRST_PAYOUT_SLOT,
                SlotSettings.VAULT_SIZE, settings().maximumPayout(stake));
    }

    public boolean canSpin() {
        long credit = credit();
        return !spinning() && credit >= settings().minimumStake() && isPayable(credit);
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer) || player != owner || player.isSpectator()
                || !stillValid(player)) {
            return false;
        }
        boolean handled = switch (button) {
            case SPIN_BUTTON -> pull(player);
            case COLLECT_BUTTON -> collect(player);
            default -> false;
        };
        if (handled) broadcastChanges();
        return handled;
    }

    /** One pull of the lever: drawn once, paid once, and only then shown. */
    private boolean pull(Player player) {
        if (!canSpin()) return false;
        long stake = credit();
        int[] reels = SlotRules.spin(draw);
        int multiplier = SlotRules.multiplier(reels);
        long paid = multiplier == 0 ? 0 : SlotRules.payout(stake, multiplier, settings().returnRate());
        if (multiplier == 0) {
            for (int slot = SlotSettings.INPUT_SLOT; slot < SlotSettings.FIRST_PAYOUT_SLOT; slot++) {
                vault.setItem(slot, ItemStack.EMPTY);
            }
        } else {
            // The items played stay in front of the player; only the profit is made up in change.
            long profit = Math.max(0, paid - stake);
            if (profit > 0) {
                ItemBank.store(catalog(), vault, SlotSettings.FIRST_PAYOUT_SLOT,
                        SlotSettings.VAULT_SIZE, profit);
            }
        }
        for (int reel = 0; reel < SlotRules.REELS; reel++) data.set(FIRST_REEL + reel, reels[reel]);
        data.set(MULTIPLIER, multiplier);
        data.set(STATE, STATE_SPINNING);
        ValueSync.write(data, STAKE, stake);
        ValueSync.write(data, WON, paid);
        animationEnd = player.level().getGameTime() + settings().spinTicks();
        data.set(TICKS, settings().spinTicks());
        showOnCabinet(player, reels, multiplier, paid);
        return true;
    }

    /** The cabinet plays the same line to everyone standing in front of it. */
    private void showOnCabinet(Player player, int[] reels, int multiplier, long paid) {
        StringBuilder line = new StringBuilder();
        for (int reel : reels) {
            if (line.length() > 0) line.append(',');
            line.append(reel);
        }
        access.execute((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof GameStationEntity cabinet)) return;
            cabinet.reels = line.toString();
            cabinet.animationTicks = settings().spinTicks();
            cabinet.multiplier = Math.max(0, multiplier);
            cabinet.show(player.getName().getString(),
                    java.math.BigDecimal.valueOf(multiplier, 2).stripTrailingZeros().toPlainString() + "x",
                    multiplier == 0 ? "slot_lost" : "slot_won",
                    "", multiplier > 0, settings().spinTicks() + settings().resultTicks());
        });
    }

    private boolean collect(Player player) {
        boolean moved = false;
        for (int slot = SlotSettings.FIRST_PAYOUT_SLOT; slot < SlotSettings.VAULT_SIZE; slot++) {
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
        if (!owner.level().isClientSide) {
            int left = (int) Math.max(0, animationEnd - owner.level().getGameTime());
            data.set(TICKS, Math.min(settings().spinTicks(), left));
            if (left == 0 && state() == STATE_SPINNING) data.set(STATE, STATE_RESULT);
            ValueSync.write(data, WINNINGS, ItemBank.valueOf(catalog(), vault,
                    SlotSettings.FIRST_PAYOUT_SLOT, SlotSettings.VAULT_SIZE));
            if (!spinning()) ValueSync.write(data, STAKE, credit());
        }
        super.broadcastChanges();
    }

    @Override public boolean stillValid(Player player) {
        if (player.level().isClientSide) return player == owner;
        return player == owner && player.isAlive() && access.evaluate(
                (level, pos) -> level.getBlockState(pos).getBlock() instanceof GameSurface surface
                        && surface.mode() == GameMode.SLOT_MACHINE
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
        if (index < INVENTORY_START) {
            if (!moveItemStackTo(stack, INVENTORY_START, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (catalog().valueOf(stack) <= 0
                    || !moveItemStackTo(stack, 0, SlotSettings.STAKE_SLOTS, false)) {
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
        // Winnings stay in the saved vault; the credit left in front of the machine comes back.
        if (player instanceof ServerPlayer && player.isAlive()) {
            for (int index = 0; index < SlotSettings.STAKE_SLOTS; index++) {
                int slot = SlotSettings.INPUT_SLOT + index;
                ItemStack staged = vault.getItem(slot).copy();
                if (staged.isEmpty()) continue;
                moveItemStackTo(staged, INVENTORY_START, slots.size(), false);
                vault.setItem(slot, staged);
            }
        }
    }
}
