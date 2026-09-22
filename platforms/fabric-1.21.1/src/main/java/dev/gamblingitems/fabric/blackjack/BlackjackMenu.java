package dev.gamblingitems.fabric.blackjack;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.blackjack.BlackjackRules.Outcome;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.block.GameSurface;
import dev.gamblingitems.fabric.menu.ValueSync;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.util.List;
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
 * One seat at a blackjack table. It sends intentions only: the deck, the dealer and every payment
 * belong to {@link BlackjackTable} on the server, and the hole card is never sent before it turns.
 */
public final class BlackjackMenu extends AbstractContainerMenu {
    public static final int DEAL_BUTTON = 1000, HIT_BUTTON = 1001, STAND_BUTTON = 1002,
            DOUBLE_BUTTON = 1003, COLLECT_BUTTON = 1004;
    public static final int STAKE_X = 16, ENGAGED_Y = 118, INPUT_Y = 148;
    public static final int INVENTORY_START = 2 * BlackjackSettings.STAKE_SLOTS;
    // Small numbers first, then the values, which each need two slots to survive the packet.
    private static final int PHASE = 0, OUTCOME = 1, PAYABLE = 2, HAND_TOTAL = 3, DEALER_TOTAL = 4,
            DEALER_HIDDEN = 5, STAKE = 6, PAID = 8, WINNINGS = 10, PLANNED = 12;
    private static final int FIRST_CARD = 14;
    private static final int DATA_SIZE = FIRST_CARD + 2 * BlackjackSettings.MAX_CARDS;

    private final Player owner;
    private final Container vault;
    private final BlackjackSetup setup;
    private final BlackjackTable table;
    private final ContainerLevelAccess access;
    private final SimpleContainerData data = new SimpleContainerData(DATA_SIZE);

    public BlackjackMenu(int syncId, Inventory inventory, BlackjackSetup setup) {
        this(syncId, inventory, setup, new SimpleContainer(BlackjackSettings.VAULT_SIZE), null,
                ContainerLevelAccess.NULL);
    }

    public BlackjackMenu(int syncId, Inventory inventory, BlackjackSetup setup, Container vault,
                         BlackjackTable table, ContainerLevelAccess access) {
        super(ModContent.BLACKJACK_MENU, syncId);
        this.owner = inventory.player;
        this.setup = setup;
        this.vault = vault;
        this.table = table;
        this.access = access;
        addDataSlots(data);
        for (int index = 0; index < BlackjackSettings.STAKE_SLOTS; index++) {
            addSlot(new Slot(vault, BlackjackSettings.INPUT_SLOT + index, STAKE_X + index * 18, INPUT_Y) {
                @Override public boolean mayPlace(ItemStack stack) { return setup.catalog().valueOf(stack) > 0; }
                @Override public boolean mayPickup(Player player) { return !isPlaying(); }
            });
        }
        for (int index = 0; index < BlackjackSettings.STAKE_SLOTS; index++) {
            addSlot(new Slot(vault, BlackjackSettings.ENGAGED_SLOT + index, STAKE_X + index * 18, ENGAGED_Y) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                // The chips of a hand in play belong to the table until it settles.
                @Override public boolean mayPickup(Player player) { return !isPlaying(); }
            });
        }
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 99 + col * 18, 177 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 99 + col * 18, 235));
    }

    public BlackjackSetup setup() { return setup; }
    public BlackjackSettings settings() { return setup.settings(); }
    public ValueCatalog catalog() { return setup.catalog(); }
    public BlackjackTable.Phase phase() { return BlackjackTable.Phase.fromId(data.get(PHASE)); }
    public Outcome outcome() { return Outcome.values()[data.get(OUTCOME)]; }
    public long stake() { return ValueSync.read(data, STAKE); }
    public long paid() { return ValueSync.read(data, PAID); }
    public long winnings() { return ValueSync.read(data, WINNINGS); }
    public long plannedStake() { return ValueSync.read(data, PLANNED); }
    public boolean isPayable() { return data.get(PAYABLE) != 0; }
    public int handTotal() { return data.get(HAND_TOTAL); }
    public int dealerTotal() { return data.get(DEALER_TOTAL); }
    public boolean dealerHidden() { return data.get(DEALER_HIDDEN) != 0; }

    /** The cards a client may draw: the player's hand, then the dealer's face up cards. */
    public List<Integer> hand() { return cards(FIRST_CARD); }
    public List<Integer> dealer() { return cards(FIRST_CARD + BlackjackSettings.MAX_CARDS); }

    private List<Integer> cards(int first) {
        var cards = new java.util.ArrayList<Integer>();
        for (int index = 0; index < BlackjackSettings.MAX_CARDS; index++) {
            int card = data.get(first + index);
            if (card > 0) cards.add(card - 1);
        }
        return cards;
    }

    private boolean isPlaying() {
        if (table != null) {
            return table.phase() == BlackjackTable.Phase.PLAYER || table.phase() == BlackjackTable.Phase.DEALER;
        }
        return phase() == BlackjackTable.Phase.PLAYER || phase() == BlackjackTable.Phase.DEALER;
    }

    public boolean canDeal() {
        return phase() == BlackjackTable.Phase.IDLE && plannedStake() >= settings().minimumStake() && isPayable();
    }

    public boolean canAct() { return phase() == BlackjackTable.Phase.PLAYER; }

    public boolean canDouble() {
        return canAct() && hand().size() == 2 && plannedStake() >= stake() && stake() > 0;
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer) || player != owner || player.isSpectator() || !stillValid(player)) {
            return false;
        }
        if (table == null) return false;
        boolean handled = switch (button) {
            case DEAL_BUTTON -> table.deal();
            case HIT_BUTTON -> table.hit();
            case STAND_BUTTON -> table.stand();
            case DOUBLE_BUTTON -> table.doubleDown();
            case COLLECT_BUTTON -> collect(player);
            default -> false;
        };
        if (handled) broadcastChanges();
        return handled;
    }

    private boolean collect(Player player) {
        boolean moved = false;
        for (int slot = BlackjackSettings.FIRST_PAYOUT_SLOT; slot < BlackjackSettings.VAULT_SIZE; slot++) {
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
        if (!owner.level().isClientSide && table != null) {
            data.set(PHASE, table.phase().id());
            data.set(OUTCOME, table.outcome().ordinal());
            data.set(PAYABLE, table.isPayable(Math.max(table.stagedValue(), table.stake())) ? 1 : 0);
            data.set(HAND_TOTAL, table.handTotal());
            data.set(DEALER_TOTAL, table.dealerTotal());
            data.set(DEALER_HIDDEN, table.dealerHidden() ? 1 : 0);
            ValueSync.write(data, STAKE, table.stake());
            ValueSync.write(data, PAID, table.paid());
            ValueSync.write(data, WINNINGS, table.winnings());
            ValueSync.write(data, PLANNED, table.stagedValue());
            writeCards(FIRST_CARD, table.hand());
            writeCards(FIRST_CARD + BlackjackSettings.MAX_CARDS, table.visibleDealer());
            showOnTable();
        }
        super.broadcastChanges();
    }

    /** Puts the hand on the felt of the block being played on, for everyone standing around. */
    private void showOnTable() {
        access.execute((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof dev.gamblingitems.fabric.block.GameStationEntity entity)) {
                return;
            }
            StringBuilder cards = new StringBuilder();
            for (int card : table.hand()) {
                if (cards.length() > 0) cards.append(',');
                cards.append(card);
            }
            cards.append('|');
            boolean first = true;
            for (int card : table.visibleDealer()) {
                if (!first) cards.append(',');
                cards.append(card);
                first = false;
            }
            if (table.dealerHidden()) cards.append(first ? "?" : ",?");
            entity.cards = cards.toString();
            entity.phase = table.phase().id();
            entity.previewPlayer = owner.getGameProfile().getName();
            entity.previewText = String.valueOf(table.handTotal());
            entity.setChanged();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
        });
    }

    private void writeCards(int first, List<Integer> cards) {
        for (int index = 0; index < BlackjackSettings.MAX_CARDS; index++) {
            data.set(first + index, index < cards.size() ? cards.get(index) + 1 : 0);
        }
    }

    @Override public boolean stillValid(Player player) {
        if (player.level().isClientSide) return player == owner;
        if (table == null || player != owner || !player.isAlive()) return false;
        return access.evaluate(
                (level, pos) -> level.getBlockState(pos).getBlock() instanceof GameSurface surface
                        && surface.mode() == GameMode.BLACKJACK
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
                    || !moveItemStackTo(stack, 0, BlackjackSettings.STAKE_SLOTS, false)) {
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
        // A hand in play stays at the table; only what was being prepared comes back.
        if (player instanceof ServerPlayer && player.isAlive() && !isPlaying()) {
            for (int index = 0; index < BlackjackSettings.STAKE_SLOTS; index++) {
                int slot = BlackjackSettings.INPUT_SLOT + index;
                ItemStack staged = vault.getItem(slot).copy();
                if (staged.isEmpty()) continue;
                moveItemStackTo(staged, INVENTORY_START, slots.size(), false);
                vault.setItem(slot, staged);
            }
        }
    }
}
