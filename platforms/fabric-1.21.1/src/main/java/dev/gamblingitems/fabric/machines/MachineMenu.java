package dev.gamblingitems.fabric.machines;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.machines.MachineTable;
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
 * One player at one prize machine. A play is drawn and paid on the server the moment it is asked
 * for; the machine then only replays an outcome that is already settled.
 *
 * <p>The items played stay where they are as long as the play gives back at least the stake: the
 * profit is added in change. A play that gives back less takes the credit and hands back what it
 * pays in change, so nothing is ever paid twice and nothing is lost when the window closes.
 *
 * <p>A play follows one order: check the player and the machine, check the credit, check that the
 * best prize could be handed over, draw, settle the vault, then show the outcome. A repeated or late
 * request finds the machine playing and is refused.
 */
public final class MachineMenu extends AbstractContainerMenu {
    public static final int PLAY_BUTTON = 1000, COLLECT_BUTTON = 1001, CHOICE_BUTTON = 1100;
    public static final int STAKE_X = 20, INPUT_Y = 146;
    public static final int INVENTORY_START = MachineSettings.STAKE_SLOTS;
    private static final SecureRandom RANDOM = new SecureRandom();
    // What the machine shows, the choice, the ticks left of the play, then the values, then the state.
    private static final int SHOWN = 0, DETAIL = 1, CHOICE = 3, TICKS = 4, MULTIPLIER = 5, STAKE = 7,
            WON = 9, WINNINGS = 11, STATE = 13;
    private static final int DATA_SIZE = 14;
    public static final int STATE_IDLE = 0, STATE_PLAYING = 1, STATE_RESULT = 2;

    private final Player owner;
    private final Container vault;
    private final ContainerLevelAccess access;
    private final MachineSetup setup;
    private final IntUnaryOperator draw;
    private final SimpleContainerData data = new SimpleContainerData(DATA_SIZE);
    private long animationEnd;

    public MachineMenu(int syncId, Inventory inventory, MachineSetup setup) {
        this(syncId, inventory, setup, new SimpleContainer(MachineSettings.VAULT_SIZE), ContainerLevelAccess.NULL);
    }

    public MachineMenu(int syncId, Inventory inventory, MachineSetup setup, Container vault, ContainerLevelAccess access) {
        this(syncId, inventory, setup, vault, access, RANDOM::nextInt);
    }

    MachineMenu(int syncId, Inventory inventory, MachineSetup setup, Container vault,
                ContainerLevelAccess access, IntUnaryOperator draw) {
        super(ModContent.MACHINE_MENU, syncId);
        this.owner = inventory.player;
        this.setup = setup;
        this.vault = vault;
        this.access = access;
        this.draw = draw;
        data.set(SHOWN, -1);
        addDataSlots(data);
        for (int index = 0; index < MachineSettings.STAKE_SLOTS; index++) {
            addSlot(new Slot(vault, MachineSettings.INPUT_SLOT + index, STAKE_X + index * 18, INPUT_Y) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return !playing() && setup.catalog().valueOf(stack) > 0;
                }
                @Override public boolean mayPickup(Player player) { return !playing(); }
            });
        }
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 79 + col * 18, 196 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 79 + col * 18, 254));
    }

    public MachineSetup setup() { return setup; }
    public MachineSettings settings() { return setup.settings(); }
    public MachineTable table() { return settings().table(); }
    public GameMode mode() { return settings().mode(); }
    public ValueCatalog catalog() { return setup.catalog(); }
    public int remainingTicks() { return data.get(TICKS); }
    public boolean playing() { return remainingTicks() > 0; }
    public int state() { return data.get(STATE); }
    public int choice() { return data.get(CHOICE); }
    /** What the last play showed, as {@link MachineTable#play} describes it; -1 before any play. */
    public int shown() { return data.get(SHOWN); }
    public int detail() { return (int) ValueSync.read(data, DETAIL); }
    public int multiplier() { return (int) ValueSync.read(data, MULTIPLIER); }
    public long stake() { return ValueSync.read(data, STAKE); }
    public long lastWin() { return ValueSync.read(data, WON); }
    public long winnings() { return ValueSync.read(data, WINNINGS); }

    /** What is waiting in the credit slots of this machine. */
    public long credit() {
        return ItemBank.valueOf(catalog(), vault, MachineSettings.INPUT_SLOT, MachineSettings.FIRST_PAYOUT_SLOT);
    }

    /** True when the best prize of the machine could be handed over right now. */
    public boolean isPayable(long stake) {
        return ItemBank.canStore(catalog(), vault, MachineSettings.FIRST_PAYOUT_SLOT,
                MachineSettings.VAULT_SIZE, settings().maximumPayout(stake));
    }

    public boolean canPlay() {
        long credit = credit();
        return settings().enabled() && !playing() && settings().accepts(credit) && isPayable(credit);
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer) || player != owner || player.isSpectator() || !stillValid(player)) {
            return false;
        }
        boolean handled;
        if (button == PLAY_BUTTON) handled = play(player);
        else if (button == COLLECT_BUTTON) handled = collect();
        else if (button >= CHOICE_BUTTON && button < CHOICE_BUTTON + table().choices()) handled = choose(button - CHOICE_BUTTON);
        else handled = false;
        if (handled) broadcastChanges();
        return handled;
    }

    /** A claw is moved, or a target picked, only between two plays. */
    private boolean choose(int choice) {
        if (playing() || choice == choice()) return false;
        data.set(CHOICE, choice);
        return true;
    }

    /** One play: drawn once, settled once, and only then shown. */
    private boolean play(Player player) {
        if (!canPlay()) return false;
        long stake = credit();
        MachineTable.Outcome outcome = table().play(choice(), draw);
        long paid = outcome.multiplier() == 0 ? 0 : MachineTable.payout(stake, outcome.multiplier(), settings().returnRate());
        if (paid >= stake) {
            // The items played stay in front of the player; only the profit is made up in change.
            if (paid > stake) ItemBank.store(catalog(), vault, MachineSettings.FIRST_PAYOUT_SLOT,
                    MachineSettings.VAULT_SIZE, paid - stake);
        } else {
            for (int slot = MachineSettings.INPUT_SLOT; slot < MachineSettings.FIRST_PAYOUT_SLOT; slot++) {
                vault.setItem(slot, ItemStack.EMPTY);
            }
            if (paid > 0) ItemBank.store(catalog(), vault, MachineSettings.FIRST_PAYOUT_SLOT, MachineSettings.VAULT_SIZE, paid);
        }
        data.set(SHOWN, outcome.shown()[0]);
        ValueSync.write(data, DETAIL, outcome.shown()[1]);
        ValueSync.write(data, MULTIPLIER, outcome.multiplier());
        data.set(STATE, STATE_PLAYING);
        ValueSync.write(data, STAKE, stake);
        ValueSync.write(data, WON, paid);
        animationEnd = player.level().getGameTime() + settings().playTicks();
        data.set(TICKS, settings().playTicks());
        showOnMachine(player, outcome, paid >= stake);
        return true;
    }

    /** The machine plays the same outcome to everyone standing in front of it. */
    private void showOnMachine(Player player, MachineTable.Outcome outcome, boolean won) {
        access.execute((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof GameStationEntity machine)) return;
            machine.reels = outcome.shown()[0] + "," + outcome.shown()[1] + "," + choice();
            machine.wheelColours = encode(table());
            machine.animationTicks = settings().playTicks();
            machine.multiplier = outcome.multiplier();
            machine.show(player.getName().getString(),
                    java.math.BigDecimal.valueOf(outcome.multiplier(), 2).stripTrailingZeros().toPlainString() + "x",
                    outcome.multiplier() == 0 ? "machine_result_lost" : won ? "machine_result_won" : "machine_result_back", "",
                    won && outcome.multiplier() > 0,
                    settings().playTicks() + settings().resultTicks());
        });
    }

    /** The prizes as the cabinet draws them for every viewer: "multiplier:weight", comma separated. */
    public static String encode(MachineTable table) {
        int[] multipliers = table.multipliers(), weights = table.weights();
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < multipliers.length; index++) {
            if (index > 0) text.append(',');
            text.append(multipliers[index]).append(':').append(index < weights.length ? weights[index] : 0);
        }
        return text.toString();
    }

    /** Shows the machine's prizes on its cabinet before anyone has played it, once per change of configuration. */
    static void publishTable(ContainerLevelAccess access, MachineTable table) {
        access.execute((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof GameStationEntity machine)) return;
            String encoded = encode(table);
            if (encoded.equals(machine.wheelColours)) return;
            machine.wheelColours = encoded;
            machine.setChanged();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
        });
    }

    private boolean collect() {
        boolean moved = false;
        for (int slot = MachineSettings.FIRST_PAYOUT_SLOT; slot < MachineSettings.VAULT_SIZE; slot++) {
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
            data.set(TICKS, Math.min(settings().playTicks(), left));
            if (left == 0 && state() == STATE_PLAYING) data.set(STATE, STATE_RESULT);
            ValueSync.write(data, WINNINGS, ItemBank.valueOf(catalog(), vault,
                    MachineSettings.FIRST_PAYOUT_SLOT, MachineSettings.VAULT_SIZE));
            if (!playing()) ValueSync.write(data, STAKE, credit());
        }
        super.broadcastChanges();
    }

    /** A machine is only played at its block: it has no portable item. */
    @Override public boolean stillValid(Player player) {
        if (player.level().isClientSide) return player == owner;
        return player == owner && player.isAlive() && access.evaluate(
                (level, pos) -> level.getBlockState(pos).getBlock() instanceof GameSurface surface
                        && surface.mode() == mode()
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
        } else {
            if (catalog().valueOf(stack) <= 0
                    || !moveItemStackTo(stack, 0, MachineSettings.STAKE_SLOTS, false)) {
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
            for (int index = 0; index < MachineSettings.STAKE_SLOTS; index++) {
                int slot = MachineSettings.INPUT_SLOT + index;
                ItemStack staged = vault.getItem(slot).copy();
                if (staged.isEmpty()) continue;
                moveItemStackTo(staged, INVENTORY_START, slots.size(), false);
                vault.setItem(slot, staged);
            }
        }
    }
}
