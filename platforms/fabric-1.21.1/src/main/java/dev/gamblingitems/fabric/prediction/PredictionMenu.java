package dev.gamblingitems.fabric.prediction;

import dev.gamblingitems.core.prediction.PredictionMarket;
import dev.gamblingitems.core.prediction.PredictionMarket.Side;
import dev.gamblingitems.core.prediction.PredictionMarket.Status;
import dev.gamblingitems.fabric.Compat;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.Nbt;
import dev.gamblingitems.fabric.cheat.CasinoSecurity;
import dev.gamblingitems.fabric.menu.ValueSync;
import dev.gamblingitems.fabric.value.ItemBank;
import dev.gamblingitems.fabric.value.ValueCatalog;
import dev.gamblingitems.fabric.vault.CasinoLedger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
 * One player at the prediction market. It sends intentions only: every price, purchase and payout is
 * decided by {@link PredictionMarkets} on the server.
 *
 * <p>The questions themselves travel as display items in slots nobody can reach: Minecraft already
 * synchronises the data of an item, so a question, its words and its price reach the screen without a
 * packet of this mod. The last of those slots carries the question being written.
 */
public final class PredictionMenu extends AbstractContainerMenu {
    public static final int ROWS = 6;
    public static final int FIRST_SELECT = 0, PAGE_PREVIOUS = 10, PAGE_NEXT = 11, BUY_YES = 20, BUY_NO = 21,
            CASH = 22, COLLECT = 23, CLAIM = 24, PROPOSE_YES = 25, PROPOSE_NO = 26, DISPUTE = 27,
            DRAFT_KIND = 30, SUBJECT_PREVIOUS = 31, SUBJECT_NEXT = 32, TARGET_PREVIOUS = 33, TARGET_NEXT = 34,
            TIME_PREVIOUS = 35, TIME_NEXT = 36, PUBLISH = 37;
    /** Where the slots of the window sit, read by the screen so both sides cannot drift apart. */
    public static final int STAKE_X = 214, STAKE_Y = 178, TICKET_X = 318, TICKET_Y = 178, QUESTION_X = 348, QUESTION_Y = 178;
    public static final int INVENTORY_X = 12, INVENTORY_Y = 204, HOTBAR_Y = 262;
    public static final int BOARD_START = PredictionSettings.FIRST_PAYOUT_SLOT - PredictionSettings.INPUT_SLOT;
    public static final int BOARD_SIZE = ROWS + 1, DRAFT = ROWS;
    public static final int INVENTORY_START = BOARD_START + BOARD_SIZE;
    private static final int PAGE = 0, PAGES = 1, SELECTED = 2, TICKET_STATE = 3, BANNED = 4,
            STAKE = 5, WINNINGS = 7, CREDIT = 9, TICKET_VALUE = 11, QUOTE_YES = 13, QUOTE_NO = 15, TICKET_SHARES = 17;
    private static final int DATA_SIZE = 19;
    private static final int REFRESH_TICKS = 20;

    private final Player owner;
    private final Container vault;
    private final PredictionSetup setup;
    private final ContainerLevelAccess access;
    private final SimpleContainer board = new SimpleContainer(BOARD_SIZE);
    private final SimpleContainerData data = new SimpleContainerData(DATA_SIZE);
    private boolean portable;
    // The server side keeps what is selected and what is being written.
    private int page, selectedId = -1;
    private final List<Integer> shown = new ArrayList<>();
    private MarketKind draftKind = MarketKind.ADVANCEMENT;
    private int draftSubject, draftTarget, draftThreshold = MarketKind.ADVANCEMENT.defaultThreshold(), draftTime = 2;
    private int refresh;
    private ItemStack lastTicket = ItemStack.EMPTY;

    public PredictionMenu(int syncId, Inventory inventory, PredictionSetup setup, boolean portable) {
        this(syncId, inventory, setup, new SimpleContainer(PredictionSettings.VAULT_SIZE), ContainerLevelAccess.NULL);
        this.portable = portable;
    }

    public PredictionMenu(int syncId, Inventory inventory, PredictionSetup setup, Container vault, ContainerLevelAccess access) {
        super(ModContent.PREDICTION_MENU, syncId);
        this.owner = inventory.player;
        this.setup = setup;
        this.vault = vault;
        this.access = access;
        this.portable = access == ContainerLevelAccess.NULL;
        addDataSlots(data);
        for (int index = 0; index < PredictionSettings.STAKE_SLOTS; index++) {
            addSlot(new Slot(vault, PredictionSettings.INPUT_SLOT + index, STAKE_X + index * 18, STAKE_Y) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return setup.catalog().valueOf(stack) > 0 && !stack.is(ModContent.PREDICTION_TICKET);
                }
            });
        }
        addSlot(new Slot(vault, PredictionSettings.TICKET_SLOT, TICKET_X, TICKET_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(ModContent.PREDICTION_TICKET); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(vault, PredictionSettings.QUESTION_SLOT, QUESTION_X, QUESTION_Y) {
            // The question is written on any item named at an anvil; it is read, never taken.
            @Override public boolean mayPlace(ItemStack stack) { return Compat.named(stack) && !stack.is(ModContent.PREDICTION_TICKET); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int index = 0; index < BOARD_SIZE; index++) {
            addSlot(new Slot(board, index, -1000, -1000) {
                @Override public boolean isActive() { return false; }
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
            });
        }
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
    }

    public PredictionSetup setup() { return setup; }
    public boolean portable() { return portable; }
    public PredictionSettings settings() { return setup.settings(); }
    public ValueCatalog catalog() { return setup.catalog(); }
    public int page() { return data.get(PAGE); }
    public int pages() { return Math.max(1, data.get(PAGES)); }
    /** The row selected on this page, or -1. */
    public int selected() { return data.get(SELECTED) - 1; }
    public int ticketState() { return data.get(TICKET_STATE); }
    public int bannedSeconds() { return data.get(BANNED); }
    public long stake() { return ValueSync.read(data, STAKE); }
    public long winnings() { return ValueSync.read(data, WINNINGS); }
    public long credit() { return ValueSync.read(data, CREDIT); }
    public long ticketValue() { return ValueSync.read(data, TICKET_VALUE); }
    public long ticketShares() { return ValueSync.read(data, TICKET_SHARES); }
    /** Shares the prepared stake would buy, in thousandths. */
    public long quote(Side side) { return ValueSync.read(data, side.yes() ? QUOTE_YES : QUOTE_NO); }

    /** A question on this page, as the server described it; null for an empty row. */
    public MarketView row(int index) {
        if (index < 0 || index >= ROWS) return null;
        ItemStack stack = board.getItem(index);
        return stack.isEmpty() ? null : MarketView.load(Compat.itemData(stack, MarketView.KEY));
    }

    public MarketView selectedView() { return row(selected()); }

    /** The question being written, as the server reads it. */
    public CompoundTag draft() { return Compat.itemData(board.getItem(DRAFT), "gamblingitems_draft"); }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer seated) || player != owner || player.isSpectator() || !stillValid(player)) {
            return false;
        }
        if (dev.gamblingitems.fabric.menu.TerminalTabs.matches(button))
            return dev.gamblingitems.fabric.menu.TerminalTabs.handle(player, button);
        boolean handled;
        if (button >= FIRST_SELECT && button < FIRST_SELECT + ROWS) handled = select(button - FIRST_SELECT);
        else handled = switch (button) {
            case PAGE_PREVIOUS -> turn(-1);
            case PAGE_NEXT -> turn(1);
            case BUY_YES -> buy(seated, Side.YES);
            case BUY_NO -> buy(seated, Side.NO);
            case CASH -> cash(seated);
            case COLLECT -> collect();
            case CLAIM -> claim(seated);
            case PROPOSE_YES -> propose(seated, Side.YES);
            case PROPOSE_NO -> propose(seated, Side.NO);
            case DISPUTE -> dispute(seated);
            case DRAFT_KIND -> {
                draftKind = MarketKind.values()[(draftKind.ordinal() + 1) % MarketKind.values().length];
                draftThreshold = draftKind.defaultThreshold();
                yield true;
            }
            case SUBJECT_PREVIOUS -> { draftSubject--; yield true; }
            case SUBJECT_NEXT -> { draftSubject++; yield true; }
            case TARGET_PREVIOUS -> target(-1);
            case TARGET_NEXT -> target(1);
            case TIME_PREVIOUS -> { draftTime = Math.max(0, draftTime - 1); yield true; }
            case TIME_NEXT -> { draftTime = Math.min(PredictionSettings.DURATIONS.length - 1, draftTime + 1); yield true; }
            case PUBLISH -> publish(seated);
            default -> false;
        };
        if (handled) {
            refresh = 0;
            broadcastChanges();
        }
        return handled;
    }

    private boolean select(int row) {
        if (row >= shown.size()) return false;
        selectedId = shown.get(row);
        return true;
    }

    private boolean turn(int step) {
        int next = page + step;
        if (next < 0 || next >= Math.max(1, data.get(PAGES))) return false;
        page = next;
        return true;
    }

    private boolean target(int step) {
        if (draftKind == MarketKind.ADVANCEMENT) {
            draftTarget = Math.floorMod(draftTarget + step, PredictionMarkets.ADVANCEMENTS.size());
        } else if (draftKind.counted()) {
            int jump = draftKind.maximumThreshold() > 64 ? 5 : 1;
            draftThreshold = Math.max(1, Math.min(draftKind.maximumThreshold(), draftThreshold + step * jump));
        } else return false;
        return true;
    }

    private MarketRecord selectedRecord(MinecraftServer server) {
        return selectedId < 0 ? null : PredictionMarkets.find(server, selectedId);
    }

    private boolean propose(ServerPlayer player, Side answer) {
        return PredictionMarkets.propose(player.server, player, selectedRecord(player.server), answer);
    }

    private boolean dispute(ServerPlayer player) {
        return PredictionMarkets.dispute(player.server, player, selectedRecord(player.server));
    }

    private long staged() {
        return ItemBank.valueOf(catalog(), vault, PredictionSettings.INPUT_SLOT, PredictionSettings.TICKET_SLOT);
    }

    private void clearStake() {
        for (int slot = PredictionSettings.INPUT_SLOT; slot < PredictionSettings.TICKET_SLOT; slot++) vault.setItem(slot, ItemStack.EMPTY);
    }

    /** The whole prepared stake buys one side; the ticket waits with the winnings. */
    private boolean buy(ServerPlayer player, Side side) {
        if (CasinoSecurity.refuses(player)) return false;
        MarketRecord record = selectedRecord(player.server);
        long spend = staged();
        if (record == null || spend <= 0
                || ItemBank.freeSlots(vault, PredictionSettings.FIRST_PAYOUT_SLOT, PredictionSettings.VAULT_SIZE) == 0) {
            return false;
        }
        PredictionMarket.Purchase purchase = PredictionMarkets.buy(player.server, record, side, spend);
        if (purchase == null) return false;
        clearStake();
        ItemStack ticket = Tickets.create(record, purchase, player.getUUID());
        for (int slot = PredictionSettings.FIRST_PAYOUT_SLOT; slot < PredictionSettings.VAULT_SIZE; slot++) {
            if (vault.getItem(slot).isEmpty()) {
                vault.setItem(slot, ticket);
                break;
            }
        }
        Compat.message(player, Component.translatable("gui.gamblingitems.market.bought", Tickets.shares(purchase.shares()),
                MarketView.side(side), record.id()), true);
        return true;
    }

    /** Sells or cashes the ticket in its slot. What it pays waits with the winnings, in items. */
    private boolean cash(ServerPlayer player) {
        ItemStack stack = vault.getItem(PredictionSettings.TICKET_SLOT);
        Tickets.Ticket ticket = Tickets.read(stack);
        PredictionMarkets.Quote quote = PredictionMarkets.quote(player.server, ticket);
        if (quote.state() == PredictionMarkets.Quote.UNKNOWN || quote.state() == PredictionMarkets.Quote.WAITING) return false;
        if (!ItemBank.canStore(catalog(), vault, PredictionSettings.FIRST_PAYOUT_SLOT, PredictionSettings.VAULT_SIZE, quote.value())) {
            Compat.message(player, Component.translatable("gui.gamblingitems.market.no_room"), true);
            return false;
        }
        PredictionMarkets.Cashing cashing = PredictionMarkets.cash(player.server, ticket);
        if (!cashing.done()) return false;
        vault.setItem(PredictionSettings.TICKET_SLOT, ItemStack.EMPTY);
        if (cashing.value() > 0) {
            ItemBank.store(catalog(), vault, PredictionSettings.FIRST_PAYOUT_SLOT, PredictionSettings.VAULT_SIZE, cashing.value());
        }
        if (!cashing.seized()) Compat.message(player, Component.translatable("gui.gamblingitems.market.cashed",
                Tickets.value(cashing.value())), true);
        return true;
    }

    private boolean collect() {
        boolean moved = false;
        for (int slot = PredictionSettings.FIRST_PAYOUT_SLOT; slot < PredictionSettings.VAULT_SIZE; slot++) {
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

    /** What the questions this player opened left over, paid into the winnings when it fits. */
    private boolean claim(ServerPlayer player) {
        CasinoLedger ledger = CasinoLedger.get(player.server);
        long credit = ledger.credit(player.getUUID());
        if (credit <= 0 || !ItemBank.canStore(catalog(), vault, PredictionSettings.FIRST_PAYOUT_SLOT,
                PredictionSettings.VAULT_SIZE, credit)) return false;
        ItemBank.store(catalog(), vault, PredictionSettings.FIRST_PAYOUT_SLOT, PredictionSettings.VAULT_SIZE, credit);
        ledger.takeCredit(player.getUUID(), credit);
        return true;
    }

    private List<ServerPlayer> subjects(MinecraftServer server) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) if (!player.isSpectator()) players.add(player);
        players.sort(Comparator.comparing(player -> player.getGameProfile().getName().toLowerCase(java.util.Locale.ROOT)));
        return players;
    }

    private ServerPlayer draftSubject(MinecraftServer server) {
        List<ServerPlayer> players = subjects(server);
        if (players.isEmpty()) return null;
        return players.get(Math.floorMod(draftSubject, players.size()));
    }

    private String draftQuestion() {
        ItemStack paper = vault.getItem(PredictionSettings.QUESTION_SLOT);
        return Compat.named(paper) ? PredictionMarkets.clean(paper.getHoverName().getString()) : "";
    }

    /** The prepared stake becomes the subsidy of a new question. */
    private boolean publish(ServerPlayer player) {
        if (CasinoSecurity.refuses(player)) return false;
        ServerPlayer subject = draftKind.aboutPlayer() ? draftSubject(player.server) : null;
        PredictionMarkets.Opening opening = PredictionMarkets.open(player.server, player.getUUID(),
                player.getGameProfile().getName(), draftKind, subject,
                PredictionMarkets.ADVANCEMENTS.get(Math.floorMod(draftTarget, PredictionMarkets.ADVANCEMENTS.size())),
                draftThreshold, draftQuestion(), PredictionSettings.DURATIONS[draftTime], staged());
        if (opening.record() == null) {
            Compat.message(player, Component.translatable("gui.gamblingitems.market.refused." + opening.refusal(),
                    Tickets.value(settings().minimumSubsidy())), true);
            return false;
        }
        clearStake();
        selectedId = opening.record().id();
        page = 0;
        return true;
    }

    @Override public void broadcastChanges() {
        if (!owner.level().isClientSide && owner instanceof ServerPlayer player) {
            // The questions are listed again once a second, and at once when the stake or the ticket changes.
            ItemStack ticket = vault.getItem(PredictionSettings.TICKET_SLOT);
            if (staged() != ValueSync.read(data, STAKE) || !ItemStack.matches(ticket, lastTicket)) refresh = 0;
            if (--refresh <= 0) {
                refresh = REFRESH_TICKS;
                lastTicket = ticket.copy();
                write(player);
            }
        }
        super.broadcastChanges();
    }

    private void write(ServerPlayer player) {
        MinecraftServer server = player.server;
        long now = CasinoSecurity.now();
        List<MarketRecord> listed = PredictionMarkets.listed(server);
        int pages = Math.max(1, (listed.size() + ROWS - 1) / ROWS);
        page = Math.min(page, pages - 1);
        if (selectedId < 0 && !listed.isEmpty()) selectedId = listed.get(0).id();
        shown.clear();
        int selectedRow = -1;
        for (int row = 0; row < ROWS; row++) {
            int index = page * ROWS + row;
            if (index >= listed.size()) {
                board.setItem(row, ItemStack.EMPTY);
                continue;
            }
            MarketRecord record = listed.get(index);
            shown.add(record.id());
            if (record.id() == selectedId) selectedRow = row;
            ItemStack card = new ItemStack(ModContent.PREDICTION_TICKET);
            Compat.setItemData(card, MarketView.KEY, MarketView.of(record, player.getUUID(), now).save());
            board.setItem(row, card);
        }
        board.setItem(DRAFT, draftCard(player));
        data.set(PAGE, page);
        data.set(PAGES, pages);
        data.set(SELECTED, selectedRow + 1);
        data.set(BANNED, (int) Math.min(Short.MAX_VALUE, CasinoSecurity.banSeconds(server, player.getUUID())));
        long spend = staged();
        ValueSync.write(data, STAKE, spend);
        ValueSync.write(data, WINNINGS, ItemBank.valueOf(catalog(), vault, PredictionSettings.FIRST_PAYOUT_SLOT,
                PredictionSettings.VAULT_SIZE));
        ValueSync.write(data, CREDIT, CasinoLedger.get(server).credit(player.getUUID()));
        MarketRecord selected = selectedRecord(server);
        PredictionMarket.Purchase yes = selected == null ? null : selected.market().quote(Side.YES, spend);
        PredictionMarket.Purchase no = selected == null ? null : selected.market().quote(Side.NO, spend);
        ValueSync.write(data, QUOTE_YES, yes == null ? 0 : yes.shares());
        ValueSync.write(data, QUOTE_NO, no == null ? 0 : no.shares());
        Tickets.Ticket ticket = Tickets.read(vault.getItem(PredictionSettings.TICKET_SLOT));
        PredictionMarkets.Quote quote = PredictionMarkets.quote(server, ticket);
        data.set(TICKET_STATE, quote.state());
        ValueSync.write(data, TICKET_VALUE, quote.value());
        ValueSync.write(data, TICKET_SHARES, ticket == null ? 0 : ticket.shares());
    }

    /** The question being written, checked as it would be on publishing. */
    private ItemStack draftCard(ServerPlayer player) {
        ServerPlayer subject = draftKind.aboutPlayer() ? draftSubject(player.server) : null;
        String target = PredictionMarkets.ADVANCEMENTS.get(Math.floorMod(draftTarget, PredictionMarkets.ADVANCEMENTS.size()));
        int minutes = PredictionSettings.DURATIONS[draftTime];
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", draftKind.id());
        tag.putString("subject", subject == null ? "" : subject.getGameProfile().getName());
        tag.putString("target", target);
        tag.putInt("threshold", draftThreshold);
        tag.putString("question", draftQuestion());
        tag.putInt("minutes", minutes);
        String refusal = PredictionMarkets.check(player.server, player.getUUID(), draftKind, subject, target,
                draftThreshold, draftQuestion(), minutes, staged());
        tag.putString("refusal", refusal == null ? "" : refusal);
        ItemStack card = new ItemStack(ModContent.PREDICTION_TICKET);
        Compat.setItemData(card, "gamblingitems_draft", tag);
        return card;
    }

    /** The draft as the screen reads it. */
    public static String draftString(CompoundTag tag, String key) { return Nbt.string(tag, key); }

    @Override public boolean stillValid(Player player) {
        if (player.level().isClientSide) return player == owner;
        if (player != owner || !player.isAlive()) return false;
        return access.evaluate((level, pos) -> level.getBlockState(pos).is(ModContent.ODDS_BOARD)
                        && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64,
                player.getInventory().contains(new ItemStack(ModContent.POCKET_CASINO)));
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (player != owner || player.isSpectator() || !stillValid(player)) return;
        if (slot >= BOARD_START && slot < INVENTORY_START) return;
        super.clicked(slot, button, type, player);
        refresh = 0;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || player != owner || !stillValid(player)) return ItemStack.EMPTY;
        if (index >= BOARD_START && index < INVENTORY_START) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < BOARD_START) {
            if (!moveItemStackTo(stack, INVENTORY_START, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.is(ModContent.PREDICTION_TICKET)) {
            if (!moveItemStackTo(stack, PredictionSettings.TICKET_SLOT, PredictionSettings.TICKET_SLOT + 1, false)) return ItemStack.EMPTY;
        } else if (catalog().valueOf(stack) > 0) {
            if (!moveItemStackTo(stack, 0, PredictionSettings.STAKE_SLOTS, false)) return ItemStack.EMPTY;
        } else if (Compat.named(stack)) {
            if (!moveItemStackTo(stack, PredictionSettings.QUESTION_SLOT, PredictionSettings.QUESTION_SLOT + 1, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        refresh = 0;
        return original;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        // The winnings stay in the saved vault; the stake, the ticket and the question come back.
        if (player instanceof ServerPlayer && player.isAlive()) {
            for (int slot = PredictionSettings.INPUT_SLOT; slot < PredictionSettings.FIRST_PAYOUT_SLOT; slot++) {
                ItemStack staged = vault.getItem(slot).copy();
                if (staged.isEmpty()) continue;
                moveItemStackTo(staged, INVENTORY_START, slots.size(), false);
                vault.setItem(slot, staged);
            }
        }
    }
}
