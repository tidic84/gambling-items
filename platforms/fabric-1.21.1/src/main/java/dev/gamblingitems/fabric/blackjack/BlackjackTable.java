package dev.gamblingitems.fabric.blackjack;

import dev.gamblingitems.core.blackjack.BlackjackRules;
import dev.gamblingitems.core.blackjack.BlackjackRules.Outcome;
import dev.gamblingitems.fabric.value.ItemBank;
import dev.gamblingitems.fabric.value.ValueCatalog;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * One player's hand against the house. The server owns the deck: it shuffles once per hand, deals
 * from it and decides every outcome. A client only ever sees the cards that are face up.
 *
 * <p>The staked items stay in the player's saved vault until the hand settles, so closing the
 * screen, disconnecting or a brutal stop never separates a player from an unsettled stake.
 */
public final class BlackjackTable {
    private static final SecureRandom RANDOM = new SecureRandom();

    public enum Phase {
        IDLE(0), PLAYER(1), DEALER(2), DONE(3);

        private final int id;
        Phase(int id) { this.id = id; }
        public int id() { return id; }
        public static Phase fromId(int id) {
            for (Phase phase : values()) if (phase.id == id) return phase;
            throw new IllegalArgumentException("Unknown blackjack phase: " + id);
        }
    }

    private final MinecraftServer server;
    private final UUID player;
    private final BlackjackSetup setup;
    private final IntUnaryOperator draw;
    private final List<Integer> deck = new ArrayList<>();
    private final List<Integer> hand = new ArrayList<>();
    private final List<Integer> dealer = new ArrayList<>();
    private int dealt;
    private long stake;
    private boolean doubled;
    private Phase phase = Phase.IDLE;
    private Outcome outcome = Outcome.PLAYING;
    private long paid;
    private int delay;
    private int idleTicks;

    BlackjackTable(MinecraftServer server, UUID player, BlackjackSetup setup) {
        this(server, player, setup, RANDOM::nextInt);
    }

    BlackjackTable(MinecraftServer server, UUID player, BlackjackSetup setup, IntUnaryOperator draw) {
        this.server = server;
        this.player = player;
        this.setup = setup;
        this.draw = draw;
    }

    public UUID player() { return player; }
    public BlackjackSetup setup() { return setup; }
    public BlackjackSettings settings() { return setup.settings(); }
    public ValueCatalog catalog() { return setup.catalog(); }
    public Phase phase() { return phase; }
    public Outcome outcome() { return outcome; }
    public long stake() { return stake; }
    public long paid() { return paid; }
    public boolean doubled() { return doubled; }
    public List<Integer> hand() { return Collections.unmodifiableList(hand); }

    /** The dealer cards a client may see: the hole card stays hidden while the player acts. */
    public List<Integer> visibleDealer() {
        if (phase == Phase.PLAYER && dealer.size() > 1) return List.of(dealer.get(0));
        return Collections.unmodifiableList(dealer);
    }

    public int handTotal() { return hand.isEmpty() ? 0 : BlackjackRules.total(hand); }
    public int dealerTotal() { return visibleDealer().isEmpty() ? 0 : BlackjackRules.total(visibleDealer()); }
    public boolean dealerHidden() { return phase == Phase.PLAYER && dealer.size() > 1; }

    public Container vault() {
        return PlayerVaults.get(server).forPlayer(player, VaultSection.BLACKJACK);
    }

    public long stagedValue() {
        return ItemBank.valueOf(catalog(), vault(),
                BlackjackSettings.INPUT_SLOT, BlackjackSettings.INPUT_SLOT + BlackjackSettings.STAKE_SLOTS);
    }

    /** True when the largest win this stake could produce would fit in the winnings. */
    public boolean isPayable(long amount) {
        return amount > 0 && ItemBank.canStore(catalog(), vault(), BlackjackSettings.FIRST_PAYOUT_SLOT,
                BlackjackSettings.VAULT_SIZE, BlackjackRules.maximumPayout(amount));
    }

    public boolean canDeal() {
        long staged = stagedValue();
        return phase == Phase.IDLE && staged >= settings().minimumStake() && isPayable(staged);
    }

    /** Engages the prepared chips and deals the first four cards. */
    public boolean deal() {
        if (!canDeal()) return false;
        long staged = stagedValue();
        Container vault = vault();
        if (!ItemBank.isEmpty(vault, BlackjackSettings.ENGAGED_SLOT,
                BlackjackSettings.ENGAGED_SLOT + BlackjackSettings.STAKE_SLOTS)) {
            return false;
        }
        ItemBank.move(vault, BlackjackSettings.INPUT_SLOT, BlackjackSettings.ENGAGED_SLOT,
                BlackjackSettings.STAKE_SLOTS);
        stake = staged;
        doubled = false;
        paid = 0;
        outcome = Outcome.PLAYING;
        hand.clear();
        dealer.clear();
        deck.clear();
        deck.addAll(BlackjackRules.shuffle(draw));
        dealt = 0;
        hand.add(next());
        dealer.add(next());
        hand.add(next());
        dealer.add(next());
        phase = Phase.PLAYER;
        idleTicks = 0;
        // A natural is settled at once, against a dealer who may hold one as well.
        if (BlackjackRules.isNatural(hand)) stand();
        return true;
    }

    public boolean canHit() { return phase == Phase.PLAYER && hand.size() < BlackjackSettings.MAX_CARDS; }

    public boolean hit() {
        if (!canHit()) return false;
        hand.add(next());
        if (BlackjackRules.isBust(hand) || hand.size() >= BlackjackSettings.MAX_CARDS) reveal();
        return true;
    }

    /** Doubling asks for the same stake again, then takes one card and stops. */
    public boolean canDouble() {
        return phase == Phase.PLAYER && hand.size() == 2 && !doubled
                && stagedValue() >= stake && isPayable(stake * 2);
    }

    public boolean doubleDown() {
        if (!canDouble()) return false;
        Container vault = vault();
        long staged = stagedValue();
        // Only the announced amount is engaged; anything else stays where the player prepared it.
        long taken = ItemBank.valueOf(catalog(), vault, BlackjackSettings.INPUT_SLOT,
                BlackjackSettings.INPUT_SLOT + BlackjackSettings.STAKE_SLOTS);
        if (taken < stake || staged < stake) return false;
        if (!engageAll(vault)) return false;
        stake += taken;
        doubled = true;
        hand.add(next());
        reveal();
        return true;
    }

    public boolean stand() {
        if (phase != Phase.PLAYER) return false;
        reveal();
        return true;
    }

    /** The dealer turns the hole card and starts drawing, one card every few ticks. */
    private void reveal() {
        phase = Phase.DEALER;
        delay = settings().dealerDelayTicks();
    }

    public void tick() {
        switch (phase) {
            case DEALER -> {
                if (--delay > 0) return;
                delay = settings().dealerDelayTicks();
                if (!BlackjackRules.isBust(hand) && BlackjackRules.dealerDraws(dealer)
                        && !BlackjackRules.isNatural(hand) && dealer.size() < BlackjackSettings.MAX_CARDS) {
                    dealer.add(next());
                    return;
                }
                settle();
            }
            case DONE -> {
                if (--delay <= 0) {
                    phase = Phase.IDLE;
                    idleTicks = 0;
                }
            }
            case IDLE -> idleTicks++;
            default -> { }
        }
    }

    private void settle() {
        outcome = BlackjackRules.outcome(hand, dealer);
        paid = BlackjackRules.payout(stake, outcome);
        Container vault = vault();
        if (paid >= stake) {
            // The chips that were staked come back as they are; only what was won is made up in change.
            long left = ItemBank.handBack(catalog(), vault, BlackjackSettings.ENGAGED_SLOT,
                    BlackjackSettings.FIRST_PAYOUT_SLOT, BlackjackSettings.FIRST_PAYOUT_SLOT,
                    BlackjackSettings.VAULT_SIZE);
            long profit = paid - stake + left;
            if (profit > 0) {
                ItemBank.store(catalog(), vault, BlackjackSettings.FIRST_PAYOUT_SLOT,
                        BlackjackSettings.VAULT_SIZE, profit);
            }
        } else {
            clearEngaged(vault);
            if (paid > 0) {
                ItemBank.store(catalog(), vault, BlackjackSettings.FIRST_PAYOUT_SLOT,
                        BlackjackSettings.VAULT_SIZE, paid);
            }
        }
        phase = Phase.DONE;
        delay = settings().resultTicks();
    }

    /** Cancels a hand in progress and gives the engaged chips back exactly once. */
    public void cancel() {
        if (phase == Phase.IDLE || phase == Phase.DONE) return;
        ItemBank.move(vault(), BlackjackSettings.ENGAGED_SLOT, BlackjackSettings.INPUT_SLOT,
                BlackjackSettings.STAKE_SLOTS);
        hand.clear();
        dealer.clear();
        stake = 0;
        paid = 0;
        outcome = Outcome.PLAYING;
        phase = Phase.IDLE;
    }

    public boolean forgettable() { return phase == Phase.IDLE && idleTicks > 1_200; }

    public long winnings() {
        return ItemBank.valueOf(catalog(), vault(), BlackjackSettings.FIRST_PAYOUT_SLOT,
                BlackjackSettings.VAULT_SIZE);
    }

    private boolean engageAll(Container vault) {
        int engaged = BlackjackSettings.ENGAGED_SLOT;
        int end = engaged + BlackjackSettings.STAKE_SLOTS;
        for (int slot = BlackjackSettings.INPUT_SLOT;
                slot < BlackjackSettings.INPUT_SLOT + BlackjackSettings.STAKE_SLOTS; slot++) {
            ItemStack staged = vault.getItem(slot);
            if (staged.isEmpty()) continue;
            boolean placed = false;
            for (int target = engaged; target < end && !placed; target++) {
                ItemStack chips = vault.getItem(target);
                if (chips.isEmpty()) {
                    vault.setItem(target, staged.copy());
                    placed = true;
                } else if (ItemStack.isSameItemSameComponents(chips, staged)
                        && chips.getCount() + staged.getCount() <= chips.getMaxStackSize()) {
                    chips.grow(staged.getCount());
                    placed = true;
                }
            }
            if (!placed) return false;
            vault.setItem(slot, ItemStack.EMPTY);
        }
        vault.setChanged();
        return true;
    }

    private void clearEngaged(Container vault) {
        for (int slot = BlackjackSettings.ENGAGED_SLOT;
                slot < BlackjackSettings.ENGAGED_SLOT + BlackjackSettings.STAKE_SLOTS; slot++) {
            vault.setItem(slot, ItemStack.EMPTY);
        }
    }

    /** The next card of the shoe. A hand never reshuffles halfway through. */
    private int next() {
        if (dealt >= deck.size()) deck.addAll(BlackjackRules.shuffle(draw));
        return deck.get(dealt++);
    }
}
