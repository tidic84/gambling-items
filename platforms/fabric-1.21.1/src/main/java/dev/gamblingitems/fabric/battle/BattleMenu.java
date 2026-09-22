package dev.gamblingitems.fabric.battle;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.battle.BattleRules;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.block.GameSurface;
import dev.gamblingitems.fabric.cases.CaseDefinition;
import dev.gamblingitems.fabric.menu.ValueSync;
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
 * One seat at a case battle. It sends intentions only: the draws, the scores and the prize belong
 * to {@link BattleLobby} on the server.
 */
public final class BattleMenu extends AbstractContainerMenu {
    public static final int JOIN_BUTTON = 1000, LEAVE_BUTTON = 1001, START_BUTTON = 1002,
            COLLECT_BUTTON = 1003, CASE_BUTTON = 1100, ROUNDS_BUTTON = 1200;
    public static final int STAKE_X = 16, ENGAGED_Y = 118, INPUT_Y = 148;
    public static final int INVENTORY_START = 2 * BattleSettings.STAKE_SLOTS;
    // Small numbers first, then the prize, which needs two slots to survive the packet.
    private static final int PHASE = 0, PHASE_TICKS = 1, CASE_INDEX = 2, ROUNDS = 3, ROUND = 4,
            PLAYERS = 5, SEATED = 6, MY_SEAT = 7, WINNER = 8, PRIZE = 9;
    /** Then one seat per group of slots: its score, what it opened, and whether it is taken. */
    private static final int FIRST_SEAT = 11;
    private static final int SEAT_FIELDS = 2 + ValueSync.SLOTS;
    private static final int DATA_SIZE = FIRST_SEAT + SEAT_FIELDS * BattleRules.MAX_PLAYERS;

    private final Player owner;
    private final Container vault;
    private final BattleSetup setup;
    private final BattleLobby lobby;
    private final SimpleContainerData data = new SimpleContainerData(DATA_SIZE);

    public BattleMenu(int syncId, Inventory inventory, BattleSetup setup) {
        this(syncId, inventory, setup, new SimpleContainer(BattleSettings.VAULT_SIZE), null);
    }

    public BattleMenu(int syncId, Inventory inventory, BattleSetup setup, Container vault, BattleLobby lobby) {
        super(ModContent.BATTLE_MENU, syncId);
        this.owner = inventory.player;
        this.setup = setup;
        this.vault = vault;
        this.lobby = lobby;
        addDataSlots(data);
        for (int index = 0; index < BattleSettings.STAKE_SLOTS; index++) {
            addSlot(new Slot(vault, BattleSettings.INPUT_SLOT + index, STAKE_X + index * 18, INPUT_Y) {
                // Only the key of the chosen case is an entry.
                @Override public boolean mayPlace(ItemStack stack) { return isEntry(stack); }
                @Override public boolean mayPickup(Player player) { return true; }
            });
        }
        for (int index = 0; index < BattleSettings.STAKE_SLOTS; index++) {
            addSlot(new Slot(vault, BattleSettings.ENGAGED_SLOT + index, STAKE_X + index * 18, ENGAGED_Y) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                // A paid entry belongs to the lobby until it starts or the seat leaves.
                @Override public boolean mayPickup(Player player) { return !seated(); }
            });
        }
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 99 + col * 18, 177 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 99 + col * 18, 235));
    }

    public BattleSetup setup() { return setup; }
    public BattleSettings settings() { return setup.settings(); }
    public BattleLobby.Phase phase() { return BattleLobby.Phase.fromId(data.get(PHASE)); }
    public int phaseTicks() { return data.get(PHASE_TICKS); }
    public int caseIndex() { return data.get(CASE_INDEX); }
    public int rounds() { return data.get(ROUNDS); }
    public int round() { return data.get(ROUND); }
    public int players() { return data.get(PLAYERS); }
    public boolean seated() { return data.get(SEATED) != 0; }
    public int mySeat() { return data.get(MY_SEAT) - 1; }
    public int winnerSeat() { return data.get(WINNER) - 1; }
    public long prize() { return ValueSync.read(data, PRIZE); }

    public CaseDefinition definition() {
        CaseDefinition chosen = setup.cases().cases().get(caseIndex());
        return chosen == null ? setup.cases().cases().get(0) : chosen;
    }

    /** The score of a seat, and how many cases it has opened so far. */
    public long scoreOf(int seat) { return ValueSync.read(data, FIRST_SEAT + seat * SEAT_FIELDS); }
    public int openedBy(int seat) { return data.get(FIRST_SEAT + seat * SEAT_FIELDS + ValueSync.SLOTS); }
    public boolean seatTaken(int seat) {
        return data.get(FIRST_SEAT + seat * SEAT_FIELDS + ValueSync.SLOTS + 1) != 0;
    }

    public ItemStack entry() {
        CaseDefinition definition = definition();
        return definition.priceStack().copyWithCount(definition.priceCount() * rounds());
    }

    private boolean isEntry(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(stack, definition().priceStack());
    }

    /** How many keys of the chosen case wait in the entry row. */
    public int preparedKeys() {
        int count = 0;
        for (int slot = BattleSettings.INPUT_SLOT;
                slot < BattleSettings.INPUT_SLOT + BattleSettings.STAKE_SLOTS; slot++) {
            ItemStack stack = vault.getItem(slot);
            if (isEntry(stack)) count += stack.getCount();
        }
        return count;
    }

    public boolean canJoin() {
        return phase() == BattleLobby.Phase.LOBBY && !seated()
                && players() < BattleRules.MAX_PLAYERS && preparedKeys() >= entry().getCount();
    }

    public boolean canStart() {
        return phase() == BattleLobby.Phase.LOBBY && BattleRules.acceptsPlayers(players());
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer) || player != owner || player.isSpectator() || !stillValid(player)) {
            return false;
        }
        if (lobby == null) return false;
        boolean handled;
        if (button >= CASE_BUTTON && button < CASE_BUTTON + setup.cases().cases().cases().size()) {
            handled = lobby.choose(setup.cases().cases().get(button - CASE_BUTTON), lobby.rounds());
        } else if (button >= ROUNDS_BUTTON && button <= ROUNDS_BUTTON + BattleRules.MAX_ROUNDS) {
            handled = lobby.choose(lobby.definition(), button - ROUNDS_BUTTON);
        } else {
            handled = switch (button) {
                case JOIN_BUTTON -> lobby.join(player.getUUID(), player.getGameProfile().getName());
                case LEAVE_BUTTON -> lobby.leave(player.getUUID());
                case START_BUTTON -> lobby.start();
                case COLLECT_BUTTON -> collect(player);
                default -> false;
            };
        }
        if (handled) broadcastChanges();
        return handled;
    }

    private boolean collect(Player player) {
        boolean moved = false;
        for (int slot = BattleSettings.FIRST_PAYOUT_SLOT; slot < BattleSettings.VAULT_SIZE; slot++) {
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
        if (!owner.level().isClientSide && lobby != null) {
            var seats = lobby.seats();
            data.set(PHASE, lobby.phase().id());
            data.set(PHASE_TICKS, lobby.remainingTicks());
            data.set(CASE_INDEX, Math.max(0, setup.cases().cases().cases().indexOf(lobby.definition())));
            data.set(ROUNDS, lobby.rounds());
            data.set(ROUND, lobby.round());
            data.set(PLAYERS, lobby.players());
            data.set(SEATED, lobby.seatOf(owner.getUUID()) != null ? 1 : 0);
            int mine = 0, champion = 0;
            for (int seat = 0; seat < BattleRules.MAX_PLAYERS; seat++) {
                boolean taken = seat < seats.size();
                int slot = FIRST_SEAT + seat * SEAT_FIELDS;
                ValueSync.write(data, slot, taken ? seats.get(seat).score() : 0);
                data.set(slot + ValueSync.SLOTS, taken ? seats.get(seat).opened().size() : 0);
                data.set(slot + ValueSync.SLOTS + 1, taken ? 1 : 0);
                if (taken && seats.get(seat).player().equals(owner.getUUID())) mine = seat + 1;
                if (taken && seats.get(seat).player().equals(lobby.winner())) champion = seat + 1;
            }
            data.set(MY_SEAT, mine);
            data.set(WINNER, champion);
            ValueSync.write(data, PRIZE, lobby.prize());
        }
        super.broadcastChanges();
    }

    /** The battle outlives its station; the player only needs to stay within reach of it. */
    @Override public boolean stillValid(Player player) {
        if (player.level().isClientSide) return player == owner;
        if (lobby == null || player != owner || !player.isAlive() || player.level() != lobby.level()) return false;
        var pos = lobby.station();
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                > BattleLobbies.REACH * BattleLobbies.REACH) {
            return false;
        }
        return player.getInventory().contains(new ItemStack(ModContent.TERMINAL))
                || (lobby.level().getBlockState(pos).getBlock() instanceof GameSurface surface
                        && surface.mode() == GameMode.CASE_BATTLE);
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
            if (!isEntry(stack) || !moveItemStackTo(stack, 0, BattleSettings.STAKE_SLOTS, false)) {
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
        if (lobby != null) BattleLobbies.leave(lobby, player.getUUID());
        // A paid entry stays with the lobby; only what was being prepared comes back.
        if (player instanceof ServerPlayer && player.isAlive()) {
            for (int index = 0; index < BattleSettings.STAKE_SLOTS; index++) {
                int slot = BattleSettings.INPUT_SLOT + index;
                ItemStack staged = vault.getItem(slot).copy();
                if (staged.isEmpty()) continue;
                moveItemStackTo(staged, INVENTORY_START, slots.size(), false);
                vault.setItem(slot, staged);
            }
        }
    }
}
