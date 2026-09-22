package dev.gamblingitems.fabric.bingo;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.bingo.BingoRules;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.block.GameSurface;
import dev.gamblingitems.fabric.menu.ValueSync;
import dev.gamblingitems.fabric.value.ValueCatalog;
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
 * One player's card at a shared bingo. It sends intentions only: the drum, the marks and the
 * payments belong to {@link BingoGame} on the server.
 */
public final class BingoMenu extends AbstractContainerMenu {
    public static final int BUY_BUTTON = 1000, COLLECT_BUTTON = 1001;
    public static final int STAKE_X = 198, ENGAGED_Y = 172, INPUT_Y = 140;
    public static final int INVENTORY_START = 2 * BingoSettings.STAKE_SLOTS;
    /** How many of the drawn numbers a screen is told about, newest last. */
    public static final int SHOWN_DRAWS = 12;
    // Small numbers first, then the values, then the card and the numbers drawn.
    private static final int PHASE = 0, PHASE_TICKS = 1, PLAYERS = 2, LAST_NUMBER = 3, STATE = 4,
            MISSING = 5, POT = 6, PAID = 8, WINNINGS = 10, PLANNED = 12;
    private static final int FIRST_SQUARE = 14;
    private static final int FIRST_DRAW = FIRST_SQUARE + BingoRules.SIZE;
    private static final int DATA_SIZE = FIRST_DRAW + SHOWN_DRAWS;
    public static final int STATE_NONE = 0, STATE_PLAYING = 1, STATE_WON = 2, STATE_LOST = 3;

    private final Player owner;
    private final Container vault;
    private final BingoSetup setup;
    private final BingoGame game;
    private final SimpleContainerData data = new SimpleContainerData(DATA_SIZE);

    public BingoMenu(int syncId, Inventory inventory, BingoSetup setup) {
        this(syncId, inventory, setup, new SimpleContainer(BingoSettings.VAULT_SIZE), null);
    }

    public BingoMenu(int syncId, Inventory inventory, BingoSetup setup, Container vault, BingoGame game) {
        super(ModContent.BINGO_MENU, syncId);
        this.owner = inventory.player;
        this.setup = setup;
        this.vault = vault;
        this.game = game;
        addDataSlots(data);
        for (int index = 0; index < BingoSettings.STAKE_SLOTS; index++) {
            addSlot(new Slot(vault, BingoSettings.INPUT_SLOT + index, STAKE_X + index * 18, INPUT_Y) {
                @Override public boolean mayPlace(ItemStack stack) { return setup.catalog().valueOf(stack) > 0; }
                @Override public boolean mayPickup(Player player) { return !playing(); }
            });
        }
        for (int index = 0; index < BingoSettings.STAKE_SLOTS; index++) {
            addSlot(new Slot(vault, BingoSettings.ENGAGED_SLOT + index, STAKE_X + index * 18, ENGAGED_Y) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                // What paid for a card belongs to the round until it settles.
                @Override public boolean mayPickup(Player player) { return !playing(); }
            });
        }
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 109 + col * 18, 234 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 109 + col * 18, 292));
    }

    public BingoSetup setup() { return setup; }
    public BingoSettings settings() { return setup.settings(); }
    public ValueCatalog catalog() { return setup.catalog(); }
    public BingoGame.Phase phase() { return BingoGame.Phase.fromId(data.get(PHASE)); }
    public int phaseTicks() { return data.get(PHASE_TICKS); }
    public int players() { return data.get(PLAYERS); }
    public int lastNumber() { return data.get(LAST_NUMBER); }
    public int state() { return data.get(STATE); }
    /** How many squares this card still needs for its best line. */
    public int missing() { return data.get(MISSING); }
    public long pot() { return ValueSync.read(data, POT); }
    public long paid() { return ValueSync.read(data, PAID); }
    public long winnings() { return ValueSync.read(data, WINNINGS); }
    public long plannedStake() { return ValueSync.read(data, PLANNED); }

    /** The card of this player: the number of each square, or zero for the free one. */
    public int[] card() {
        int[] card = new int[BingoRules.SIZE];
        for (int square = 0; square < BingoRules.SIZE; square++) {
            card[square] = data.get(FIRST_SQUARE + square);
        }
        return card;
    }

    /** True when that square has been drawn; the free square is always marked. */
    public boolean marked(int square) {
        if (square == BingoRules.FREE_SQUARE) return true;
        int number = data.get(FIRST_SQUARE + square);
        // A marked square is sent as its number plus the drum size, which no plain number reaches.
        return number > BingoRules.NUMBERS;
    }

    public int numberAt(int square) {
        int number = data.get(FIRST_SQUARE + square);
        return number > BingoRules.NUMBERS ? number - BingoRules.NUMBERS : number;
    }

    /** The last numbers the drum gave out, oldest first. */
    public int[] recentDraws() {
        int[] draws = new int[SHOWN_DRAWS];
        for (int index = 0; index < SHOWN_DRAWS; index++) draws[index] = data.get(FIRST_DRAW + index);
        return draws;
    }

    private boolean playing() {
        if (game != null) {
            BingoGame.Card card = game.cardOf(owner.getUUID());
            return card != null && !card.settled();
        }
        return state() == STATE_PLAYING;
    }

    public boolean hasCard() { return state() != STATE_NONE; }

    public boolean canBuy() {
        return state() == STATE_NONE && plannedStake() >= settings().cardPrice()
                && (phase() == BingoGame.Phase.WAITING || phase() == BingoGame.Phase.BETTING);
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer) || player != owner || player.isSpectator() || !stillValid(player)) {
            return false;
        }
        if (game == null) return false;
        boolean handled = switch (button) {
            case BUY_BUTTON -> game.buy(player.getUUID(), player.getGameProfile().getName());
            case COLLECT_BUTTON -> collect(player);
            default -> false;
        };
        if (handled) broadcastChanges();
        return handled;
    }

    private boolean collect(Player player) {
        boolean moved = false;
        for (int slot = BingoSettings.FIRST_PAYOUT_SLOT; slot < BingoSettings.VAULT_SIZE; slot++) {
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
            BingoGame.Card card = game.cardOf(owner.getUUID());
            data.set(PHASE, game.phase().id());
            data.set(PHASE_TICKS, game.remainingTicks());
            data.set(PLAYERS, game.players());
            data.set(LAST_NUMBER, game.lastNumber());
            data.set(STATE, card == null ? STATE_NONE
                    : !card.settled() ? STATE_PLAYING : card.winner() ? STATE_WON : STATE_LOST);
            ValueSync.write(data, POT, game.pot());
            ValueSync.write(data, PAID, card == null ? 0 : card.paid());
            ValueSync.write(data, WINNINGS, game.winnings(vault));
            ValueSync.write(data, PLANNED, game.stagedValue(owner.getUUID()));
            boolean[] drawn = new boolean[BingoRules.NUMBERS + 1];
            for (int number : game.drawn()) drawn[number] = true;
            int[] squares = card == null ? new int[BingoRules.SIZE] : card.squares();
            boolean[] marks = card == null ? new boolean[BingoRules.SIZE]
                    : BingoRules.marks(squares, drawn);
            for (int square = 0; square < BingoRules.SIZE; square++) {
                int number = squares[square];
                // A marked number is sent raised by the size of the drum, so one slot carries both.
                data.set(FIRST_SQUARE + square,
                        marks[square] && number > 0 ? number + BingoRules.NUMBERS : number);
            }
            data.set(MISSING, card == null ? BingoRules.COLUMNS : BingoRules.missing(marks));
            var numbers = game.drawn();
            for (int index = 0; index < SHOWN_DRAWS; index++) {
                int position = numbers.size() - SHOWN_DRAWS + index;
                data.set(FIRST_DRAW + index, position < 0 ? 0 : numbers.get(position));
            }
        }
        super.broadcastChanges();
    }

    /** The round outlives its table; the player only needs to stay within reach of it. */
    @Override public boolean stillValid(Player player) {
        if (player.level().isClientSide) return player == owner;
        if (game == null || player != owner || !player.isAlive() || player.level() != game.level()) return false;
        var pos = game.station();
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                > BingoGames.REACH * BingoGames.REACH) {
            return false;
        }
        return player.getInventory().contains(new ItemStack(ModContent.TERMINAL))
                || (game.level().getBlockState(pos).getBlock() instanceof GameSurface surface
                        && surface.mode() == GameMode.BINGO);
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
                    || !moveItemStackTo(stack, 0, BingoSettings.STAKE_SLOTS, false)) {
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
        if (game != null) BingoGames.leave(game, player.getUUID());
        // A paid card stays with the round; only what was being prepared comes back.
        if (player instanceof ServerPlayer && player.isAlive()) {
            for (int index = 0; index < BingoSettings.STAKE_SLOTS; index++) {
                int slot = BingoSettings.INPUT_SLOT + index;
                ItemStack staged = vault.getItem(slot).copy();
                if (staged.isEmpty()) continue;
                moveItemStackTo(staged, INVENTORY_START, slots.size(), false);
                vault.setItem(slot, staged);
            }
        }
    }
}
