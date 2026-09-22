package dev.gamblingitems.fabric.battle;

import dev.gamblingitems.core.battle.BattleRules;
import dev.gamblingitems.core.cases.CaseRules;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.cases.CaseDefinition;
import dev.gamblingitems.fabric.cases.CaseReward;
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
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * One case battle hosted by a station: two to four seats open the same cases, round by round,
 * and the best total takes every item that was opened.
 *
 * <p>Entries are paid in keys, which wait in the vault of their owner until the lobby locks.
 * A seat that leaves before the launch gets its keys back once; once the battle runs, it runs to
 * the end even if everyone disconnects, and the prizes go to the winner's vault.
 */
public final class BattleLobby {
    public static final long DRAW_BOUND = 1_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int STATION_REFRESH_TICKS = 10;
    public static final int IDLE_TICKS = 2_400;

    public enum Phase {
        LOBBY(0), RUNNING(1), DONE(2);

        private final int id;
        Phase(int id) { this.id = id; }
        public int id() { return id; }
        public static Phase fromId(int id) {
            for (Phase phase : values()) if (phase.id == id) return phase;
            throw new IllegalArgumentException("Unknown battle phase: " + id);
        }
    }

    /** One seat of the battle: who sits there, what they opened, and what it is worth. */
    public static final class Seat {
        private final UUID player;
        private final String name;
        private final List<ItemStack> opened = new ArrayList<>();
        private long score;

        private Seat(UUID player, String name) {
            this.player = player;
            this.name = name;
        }

        public UUID player() { return player; }
        public String name() { return name; }
        public List<ItemStack> opened() { return Collections.unmodifiableList(opened); }
        public long score() { return score; }
    }

    private final ServerLevel level;
    private final BlockPos station;
    private final BattleSetup setup;
    private final LongSupplier draw;
    private final Map<UUID, Seat> seats = new LinkedHashMap<>();
    private CaseDefinition definition;
    private int rounds = 3;
    private Phase phase = Phase.LOBBY;
    private int phaseTicks;
    private int round;
    private int roundTicks;
    private UUID winner;
    private long prize;
    private int idleTicks;
    private int stationTicks;

    BattleLobby(ServerLevel level, BlockPos station, BattleSetup setup) {
        this(level, station, setup, () -> RANDOM.nextLong(DRAW_BOUND));
    }

    BattleLobby(ServerLevel level, BlockPos station, BattleSetup setup, LongSupplier draw) {
        this.level = level;
        this.station = station;
        this.setup = setup;
        this.draw = draw;
        this.definition = setup.cases().cases().get(0);
    }

    public ServerLevel level() { return level; }
    public BlockPos station() { return station; }
    public BattleSetup setup() { return setup; }
    public BattleSettings settings() { return setup.settings(); }
    public ValueCatalog catalog() { return setup.cases().catalog(); }
    public CaseDefinition definition() { return definition; }
    public int rounds() { return rounds; }
    public Phase phase() { return phase; }
    public int remainingTicks() { return phaseTicks; }
    public int round() { return round; }
    public UUID winner() { return winner; }
    public long prize() { return prize; }
    public int players() { return seats.size(); }
    public Seat seatOf(UUID player) { return seats.get(player); }
    public List<Seat> seats() { return List.copyOf(seats.values()); }

    public Container vault(UUID player) {
        return PlayerVaults.get(level.getServer()).forPlayer(player, VaultSection.CASE_BATTLE);
    }

    /** What one seat must pay to enter: one key of the chosen case per round. */
    public ItemStack entry() {
        ItemStack price = definition.priceStack();
        price.setCount(definition.priceCount() * rounds);
        return price;
    }

    public boolean open() { return phase == Phase.LOBBY; }

    /** The host chooses the case and the number of rounds, while the lobby is still empty. */
    public boolean choose(CaseDefinition chosen, int wantedRounds) {
        if (!open() || !seats.isEmpty() || chosen == null || !BattleRules.acceptsRounds(wantedRounds)) return false;
        definition = chosen;
        rounds = wantedRounds;
        return true;
    }

    /** Pays the entry of one seat with the keys prepared in the vault. */
    public boolean join(UUID player, String name) {
        if (!open() || seats.containsKey(player) || seats.size() >= BattleRules.MAX_PLAYERS) return false;
        Container vault = vault(player);
        ItemStack entry = entry();
        int owed = entry.getCount();
        for (int slot = BattleSettings.INPUT_SLOT;
                slot < BattleSettings.INPUT_SLOT + BattleSettings.STAKE_SLOTS && owed > 0; slot++) {
            ItemStack staged = vault.getItem(slot);
            if (!ItemStack.isSameItemSameComponents(staged, definition.priceStack())) continue;
            owed -= staged.getCount();
        }
        if (owed > 0) return false;
        moveEntry(vault, entry.getCount());
        seats.put(player, new Seat(player, name));
        idleTicks = 0;
        phaseTicks = settings().lobbyTicks();
        stationTicks = 0;
        refreshStation();
        return true;
    }

    /** Moves exactly the entry from the prepared row to the reserved row. */
    private void moveEntry(Container vault, int count) {
        int owed = count;
        for (int slot = BattleSettings.INPUT_SLOT;
                slot < BattleSettings.INPUT_SLOT + BattleSettings.STAKE_SLOTS && owed > 0; slot++) {
            ItemStack staged = vault.getItem(slot);
            if (!ItemStack.isSameItemSameComponents(staged, definition.priceStack())) continue;
            int taken = Math.min(owed, staged.getCount());
            staged.shrink(taken);
            owed -= taken;
            store(vault, BattleSettings.ENGAGED_SLOT, BattleSettings.FIRST_PAYOUT_SLOT,
                    definition.priceStack().copyWithCount(taken));
        }
        vault.setChanged();
    }

    /** A seat may leave while the lobby is open; its entry comes back exactly once. */
    public boolean leave(UUID player) {
        if (!open() || !seats.containsKey(player)) return false;
        refund(player);
        seats.remove(player);
        return true;
    }

    private void refund(UUID player) {
        Container vault = vault(player);
        for (int slot = BattleSettings.ENGAGED_SLOT; slot < BattleSettings.FIRST_PAYOUT_SLOT; slot++) {
            ItemStack reserved = vault.getItem(slot);
            if (reserved.isEmpty()) continue;
            vault.setItem(slot, ItemStack.EMPTY);
            store(vault, BattleSettings.INPUT_SLOT, BattleSettings.ENGAGED_SLOT, reserved);
        }
        vault.setChanged();
    }

    /** True once every seat is paid and the lobby holds enough players to start. */
    public boolean ready() { return open() && BattleRules.acceptsPlayers(seats.size()); }

    public boolean start() {
        if (!ready()) return false;
        phase = Phase.RUNNING;
        round = 0;
        roundTicks = settings().roundTicks();
        phaseTicks = settings().roundTicks() * rounds;
        stationTicks = 0;
        refreshStation();
        return true;
    }

    public void tick() {
        switch (phase) {
            case LOBBY -> {
                idleTicks++;
                if (seats.isEmpty()) return;
                if (--phaseTicks <= 0) {
                    // A lobby that filled up starts on its own; one that never did gives the keys back.
                    if (!start()) {
                        for (UUID player : List.copyOf(seats.keySet())) leave(player);
                    }
                }
            }
            case RUNNING -> {
                if (--roundTicks > 0) return;
                roundTicks = settings().roundTicks();
                playRound();
            }
            case DONE -> {
                if (--phaseTicks <= 0) reset();
            }
        }
        refreshStation();
    }

    /** One round: the same case is opened once for every seat, each with its own draw. */
    private void playRound() {
        for (Seat seat : seats.values()) {
            int index = CaseRules.select(definition.weightArray(), (int) (draw.getAsLong()
                    * CaseRules.TOTAL_WEIGHT / DRAW_BOUND));
            CaseReward reward = definition.rewards().get(index);
            ItemStack stack = reward.stack();
            seat.opened.add(stack);
            seat.score += catalog().valueOf(stack);
        }
        round++;
        if (round >= rounds) settle();
    }

    /** The best total takes everything opened in the battle; a tie is drawn between the leaders. */
    private void settle() {
        List<Seat> order = new ArrayList<>(seats.values());
        List<Long> scores = new ArrayList<>();
        for (Seat seat : order) scores.add(seat.score());
        int index = BattleRules.winner(scores, draw.getAsLong(), DRAW_BOUND);
        Seat champion = order.get(index);
        winner = champion.player();
        prize = 0;
        Container vault = vault(winner);
        for (Seat seat : order) {
            for (ItemStack stack : seat.opened) {
                prize += catalog().valueOf(stack);
                store(vault, BattleSettings.FIRST_PAYOUT_SLOT, BattleSettings.VAULT_SIZE, stack.copy());
            }
            // Every entry was the table's from the moment the battle started.
            clear(vault(seat.player()), BattleSettings.ENGAGED_SLOT, BattleSettings.FIRST_PAYOUT_SLOT);
        }
        vault.setChanged();
        phase = Phase.DONE;
        phaseTicks = settings().resultTicks();
    }

    private void reset() {
        if (level.isLoaded(station) && level.getBlockEntity(station) instanceof GameStationEntity entity) {
            entity.clear();
        }
        seats.clear();
        phase = Phase.LOBBY;
        phaseTicks = 0;
        round = 0;
        winner = null;
        prize = 0;
        idleTicks = 0;
    }

    /** Cancels a lobby or a running battle, giving every entry back exactly once. */
    public void cancel() {
        if (phase == Phase.LOBBY) {
            for (UUID player : List.copyOf(seats.keySet())) leave(player);
        } else if (phase == Phase.RUNNING) {
            // An interrupted battle pays nobody: every entry goes back to its seat.
            for (UUID player : List.copyOf(seats.keySet())) refund(player);
        }
        reset();
    }

    public boolean forgettable(int viewers) {
        return viewers == 0 && phase == Phase.LOBBY && seats.isEmpty() && idleTicks > IDLE_TICKS;
    }

    private void clear(Container vault, int from, int to) {
        for (int slot = from; slot < to; slot++) vault.setItem(slot, ItemStack.EMPTY);
    }

    /** Puts a stack in the first slot of a range that can take it. */
    private void store(Container vault, int from, int to, ItemStack stack) {
        ItemStack left = stack.copy();
        for (int slot = from; slot < to && !left.isEmpty(); slot++) {
            ItemStack current = vault.getItem(slot);
            if (current.isEmpty()) {
                vault.setItem(slot, left);
                return;
            }
            if (ItemStack.isSameItemSameComponents(current, left)
                    && current.getCount() < current.getMaxStackSize()) {
                int moved = Math.min(left.getCount(), current.getMaxStackSize() - current.getCount());
                current.grow(moved);
                left.shrink(moved);
            }
        }
        // The prizes of a battle can fill a vault; what does not fit stays where it can be seen.
        if (!left.isEmpty() && to == BattleSettings.VAULT_SIZE) {
            vault.setItem(BattleSettings.VAULT_SIZE - 1, left);
        }
    }

    /** The public screen of the station: who is in, what they opened, and who won. */
    private void refreshStation() {
        if (--stationTicks > 0) return;
        stationTicks = STATION_REFRESH_TICKS;
        if (!level.isLoaded(station)) return;
        if (!(level.getBlockEntity(station) instanceof GameStationEntity entity)) return;
        StringBuilder bets = new StringBuilder();
        for (Seat seat : seats.values()) {
            if (bets.length() > 0) bets.append('\n');
            bets.append(seat.name()).append(" : ")
                    .append(java.math.BigDecimal.valueOf(seat.score(), 3).stripTrailingZeros().toPlainString());
            if (winner != null && winner.equals(seat.player())) bets.append(" *");
        }
        entity.phase = phase.id();
        entity.phaseEnd = level.getGameTime() + phaseTicks;
        entity.publicBets = bets.toString();
        entity.show(seats.size() + " / " + BattleRules.MAX_PLAYERS,
                phase == Phase.RUNNING ? (round + " / " + rounds) : definition.title().getString(),
                "battle_result", "", phase == Phase.DONE, STATION_REFRESH_TICKS * 2);
    }
}
