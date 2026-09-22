package dev.gamblingitems.fabric.bingo;

import dev.gamblingitems.core.bingo.BingoRules;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.value.ItemBank;
import dev.gamblingitems.fabric.value.ValueCatalog;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;

/**
 * One shared bingo round hosted by a station or a table: cards are bought, then the drum turns
 * and every card is marked by the same numbers. The first cards to fill a line share the pot.
 *
 * <p>The items paid for a card wait in the vault of their owner until the round settles, so a
 * disconnection or a brutal stop never separates a player from an unsettled entry.
 */
public final class BingoGame {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int STATION_REFRESH_TICKS = 10;
    public static final int IDLE_TICKS = 1_200;

    public enum Phase {
        WAITING(0), BETTING(1), DRAWING(2), RESULT(3);

        private final int id;
        Phase(int id) { this.id = id; }
        public int id() { return id; }
        public static Phase fromId(int id) {
            for (Phase phase : values()) if (phase.id == id) return phase;
            throw new IllegalArgumentException("Unknown bingo phase: " + id);
        }
    }

    /** One player's card in this round, and what it was paid. */
    public static final class Card {
        private final int[] squares;
        private final String name;
        private final long price;
        private long paid;
        private boolean settled;
        private boolean winner;

        private Card(int[] squares, String name, long price) {
            this.squares = squares;
            this.name = name;
            this.price = price;
        }

        public int[] squares() { return squares.clone(); }
        public String name() { return name; }
        public long price() { return price; }
        public long paid() { return paid; }
        public boolean settled() { return settled; }
        public boolean winner() { return winner; }
    }

    private final ServerLevel level;
    private final BlockPos station;
    private final BingoSetup setup;
    private final IntUnaryOperator draw;
    private final Map<UUID, Card> cards = new LinkedHashMap<>();
    private final List<Integer> drum = new ArrayList<>();
    private final List<Integer> drawn = new ArrayList<>();
    private final boolean[] marked = new boolean[BingoRules.NUMBERS + 1];
    private Phase phase = Phase.WAITING;
    private int phaseTicks;
    private int drawTicks;
    private int idleTicks;
    private int stationTicks;

    BingoGame(ServerLevel level, BlockPos station, BingoSetup setup) {
        this(level, station, setup, RANDOM::nextInt);
    }

    BingoGame(ServerLevel level, BlockPos station, BingoSetup setup, IntUnaryOperator draw) {
        this.level = level;
        this.station = station;
        this.setup = setup;
        this.draw = draw;
    }

    public ServerLevel level() { return level; }
    public BlockPos station() { return station; }
    public BingoSetup setup() { return setup; }
    public BingoSettings settings() { return setup.settings(); }
    public ValueCatalog catalog() { return setup.catalog(); }
    public Phase phase() { return phase; }
    public int remainingTicks() { return phaseTicks; }
    public Card cardOf(UUID player) { return cards.get(player); }
    public int players() { return cards.size(); }
    public List<Integer> drawn() { return Collections.unmodifiableList(drawn); }
    public int lastNumber() { return drawn.isEmpty() ? 0 : drawn.get(drawn.size() - 1); }

    public long pot() {
        long total = 0;
        for (Card card : cards.values()) total += card.price();
        return total;
    }

    public Container vault(UUID player) {
        return PlayerVaults.get(level.getServer()).forPlayer(player, VaultSection.BINGO);
    }

    public boolean sellsCards() { return phase == Phase.WAITING || phase == Phase.BETTING; }

    /** The value waiting in the entry slots of this player. */
    public long stagedValue(UUID player) {
        return ItemBank.valueOf(catalog(), vault(player),
                BingoSettings.INPUT_SLOT, BingoSettings.INPUT_SLOT + BingoSettings.STAKE_SLOTS);
    }

    /** True when this player could be handed the whole pot, which is the most a card can win. */
    public boolean isPayable(UUID player) {
        long most = Math.max(pot() + settings().cardPrice(), settings().cardPrice() * 2);
        return ItemBank.canStore(catalog(), vault(player), BingoSettings.FIRST_PAYOUT_SLOT,
                BingoSettings.VAULT_SIZE, most);
    }

    /** Buys the card of this round with the items prepared in the vault. */
    public boolean buy(UUID player, String name) {
        if (!sellsCards() || cards.containsKey(player)) return false;
        long price = settings().cardPrice();
        if (stagedValue(player) < price) return false;
        if (!isPayable(player)) return false;
        Container vault = vault(player);
        if (!ItemBank.isEmpty(vault, BingoSettings.ENGAGED_SLOT,
                BingoSettings.ENGAGED_SLOT + BingoSettings.STAKE_SLOTS)) {
            return false;
        }
        // The whole prepared row is engaged; a player prepares exactly what a card costs.
        ItemBank.move(vault, BingoSettings.INPUT_SLOT, BingoSettings.ENGAGED_SLOT, BingoSettings.STAKE_SLOTS);
        long paid = ItemBank.valueOf(catalog(), vault, BingoSettings.ENGAGED_SLOT,
                BingoSettings.FIRST_PAYOUT_SLOT);
        cards.put(player, new Card(BingoRules.card(draw), name, paid));
        idleTicks = 0;
        if (phase == Phase.WAITING) {
            phase = Phase.BETTING;
            phaseTicks = settings().bettingTicks();
        }
        stationTicks = 0;
        refreshStation();
        return true;
    }

    public void tick() {
        switch (phase) {
            case WAITING -> idleTicks++;
            case BETTING -> {
                if (--phaseTicks <= 0) start();
            }
            case DRAWING -> {
                if (--drawTicks > 0) return;
                drawTicks = settings().drawTicks();
                drawNumber();
            }
            case RESULT -> {
                if (--phaseTicks <= 0) reset();
            }
        }
        refreshStation();
    }

    public boolean forgettable(int viewers) {
        return viewers == 0 && phase == Phase.WAITING && cards.isEmpty() && idleTicks > IDLE_TICKS;
    }

    /** The drum is filled and closed here: no card can be bought once it turns. */
    private void start() {
        if (cards.isEmpty()) {
            reset();
            return;
        }
        drum.clear();
        drawn.clear();
        java.util.Arrays.fill(marked, false);
        for (int number = 1; number <= BingoRules.NUMBERS; number++) drum.add(number);
        phase = Phase.DRAWING;
        drawTicks = settings().drawTicks();
    }

    private void drawNumber() {
        if (drum.isEmpty()) {
            settle(List.of());
            return;
        }
        int number = drum.remove(draw.applyAsInt(drum.size()));
        drawn.add(number);
        marked[number] = true;
        List<UUID> winners = new ArrayList<>();
        for (Map.Entry<UUID, Card> entry : cards.entrySet()) {
            if (BingoRules.hasLine(BingoRules.marks(entry.getValue().squares, marked))) {
                winners.add(entry.getKey());
            }
        }
        // Every card that filled a line on this very number shares the pot.
        if (!winners.isEmpty()) settle(winners);
    }

    private void settle(List<UUID> winners) {
        long pot = pot();
        long share = winners.isEmpty() ? 0
                : BingoRules.share(pot, winners.size(), settings().returnRate());
        for (Map.Entry<UUID, Card> entry : cards.entrySet()) {
            Card card = entry.getValue();
            Container vault = vault(entry.getKey());
            card.settled = true;
            card.winner = winners.contains(entry.getKey());
            if (card.winner) {
                // The items paid for the card come back, and the winnings are made up in change.
                long left = ItemBank.handBack(catalog(), vault, BingoSettings.ENGAGED_SLOT,
                        BingoSettings.FIRST_PAYOUT_SLOT, BingoSettings.FIRST_PAYOUT_SLOT,
                        BingoSettings.VAULT_SIZE);
                long profit = Math.max(0, share - card.price()) + left;
                if (profit > 0) {
                    ItemBank.store(catalog(), vault, BingoSettings.FIRST_PAYOUT_SLOT,
                            BingoSettings.VAULT_SIZE, profit);
                }
                card.paid = share;
            } else {
                clearEngaged(vault);
                card.paid = 0;
            }
        }
        phase = Phase.RESULT;
        phaseTicks = settings().resultTicks();
    }

    private void reset() {
        if (level.isLoaded(station) && level.getBlockEntity(station) instanceof GameStationEntity entity) {
            entity.clear();
        }
        cards.clear();
        drawn.clear();
        drum.clear();
        java.util.Arrays.fill(marked, false);
        phase = Phase.WAITING;
        phaseTicks = 0;
        idleTicks = 0;
    }

    /** Cancels an unfinished round and gives every entry back exactly once. */
    public void cancel() {
        for (Map.Entry<UUID, Card> entry : cards.entrySet()) {
            Card card = entry.getValue();
            if (card.settled()) continue;
            ItemBank.move(vault(entry.getKey()), BingoSettings.ENGAGED_SLOT, BingoSettings.INPUT_SLOT,
                    BingoSettings.STAKE_SLOTS);
            card.settled = true;
            card.paid = card.price();
        }
        reset();
    }

    public long winnings(Container vault) {
        return ItemBank.valueOf(catalog(), vault, BingoSettings.FIRST_PAYOUT_SLOT, BingoSettings.VAULT_SIZE);
    }

    private void clearEngaged(Container vault) {
        for (int slot = BingoSettings.ENGAGED_SLOT; slot < BingoSettings.FIRST_PAYOUT_SLOT; slot++) {
            vault.setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
        }
    }

    /** The public screen: how many cards are in, what has been drawn, and who filled a line. */
    private void refreshStation() {
        if (phase == Phase.WAITING || --stationTicks > 0) return;
        stationTicks = STATION_REFRESH_TICKS;
        if (!level.isLoaded(station)) return;
        if (!(level.getBlockEntity(station) instanceof GameStationEntity entity)) return;
        StringBuilder board = new StringBuilder();
        for (Card card : cards.values()) {
            if (board.length() > 0) board.append('\n');
            board.append(card.name()).append(" : ")
                    .append(BingoRules.missing(BingoRules.marks(card.squares, marked)));
            if (card.winner()) board.append(" *");
        }
        entity.phase = phase.id();
        entity.phaseEnd = level.getGameTime() + phaseTicks;
        entity.publicBets = board.toString();
        StringBuilder staked = new StringBuilder();
        for (UUID player : cards.keySet()) {
            String items = ItemBank.describe(vault(player), BingoSettings.ENGAGED_SLOT,
                    BingoSettings.FIRST_PAYOUT_SLOT, 2);
            if (items.isEmpty()) continue;
            if (staked.length() > 0) staked.append(',');
            staked.append(items);
        }
        entity.stakeItems = staked.toString();
        entity.targetSlot = lastNumber();
        StringBuilder called = new StringBuilder();
        for (int number : drawn) {
            if (called.length() > 0) called.append(',');
            called.append(number);
        }
        entity.drawn = called.toString();
        entity.show(players() + " / "
                        + java.math.BigDecimal.valueOf(pot(), 3).stripTrailingZeros().toPlainString(),
                phase == Phase.BETTING ? String.valueOf((phaseTicks + 19) / 20)
                        : String.valueOf(lastNumber()),
                "bingo_result", "", phase == Phase.RESULT, STATION_REFRESH_TICKS * 2);
    }
}
