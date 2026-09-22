package dev.gamblingitems.fabric.roulette;

import dev.gamblingitems.core.roulette.RouletteWheel;
import dev.gamblingitems.core.roulette.RouletteWheel.Bet;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.value.ItemBank;
import dev.gamblingitems.fabric.value.ValueCatalog;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * One shared roulette round hosted by a station: bets, lock, spin, payment.
 * The server draws the pocket when the bets close; the wheel drawn by a client only replays it.
 *
 * <p>A player may cover several areas of the table in the same round, each with any priced items.
 * The items themselves stay in that player's saved vault: they wait on the table and are only taken
 * when the round settles.
 */
public final class RouletteGame {
    public static final long DRAW_BOUND = 1_000_000;
    /** How many areas of the table one player may cover in the same round. */
    public static final int MAX_BETS = 8;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int STATION_REFRESH_TICKS = 10;
    public static final int IDLE_TICKS = 1_200;

    public enum Phase {
        WAITING(0), BETTING(1), SPINNING(2), RESULT(3);

        private final int id;
        Phase(int id) { this.id = id; }
        public int id() { return id; }
        public static Phase fromId(int id) {
            for (Phase phase : values()) if (phase.id == id) return phase;
            throw new IllegalArgumentException("Unknown roulette phase: " + id);
        }
    }

    /** What one player has on the table this round, area by area. */
    public static final class Seat {
        private final Map<Bet, Long> stakes = new LinkedHashMap<>();
        private long paid;
        private boolean settled;

        public Map<Bet, Long> stakes() { return Collections.unmodifiableMap(stakes); }
        public long paid() { return paid; }
        public boolean settled() { return settled; }
        public boolean engaged() { return !settled; }

        public long total() {
            long total = 0;
            for (long stake : stakes.values()) total += stake;
            return total;
        }

        /** The most this seat could be owed if every area it covers came out at once. */
        public long largestWin() {
            long most = 0;
            for (Map.Entry<Bet, Long> entry : stakes.entrySet()) {
                most += entry.getValue() * entry.getKey().payout();
            }
            return most;
        }
    }

    private final ServerLevel level;
    private final BlockPos station;
    private final RouletteSetup setup;
    private final LongSupplier draw;
    private final Map<UUID, Seat> seats = new LinkedHashMap<>();
    private Phase phase = Phase.WAITING;
    private int phaseTicks;
    private int resultNumber = -1;
    private int lastResultNumber = -1;
    private int idleTicks;
    private int stationTicks;

    RouletteGame(ServerLevel level, BlockPos station, RouletteSetup setup) {
        this(level, station, setup, () -> RANDOM.nextLong(DRAW_BOUND));
    }

    RouletteGame(ServerLevel level, BlockPos station, RouletteSetup setup, LongSupplier draw) {
        this.level = level;
        this.station = station;
        this.setup = setup;
        this.draw = draw;
    }

    public ServerLevel level() { return level; }
    public BlockPos station() { return station; }
    public RouletteSetup setup() { return setup; }
    public RouletteSettings settings() { return setup.settings(); }
    public ValueCatalog catalog() { return setup.catalog(); }
    public Phase phase() { return phase; }
    public int remainingTicks() { return phaseTicks; }
    public Seat seatOf(UUID player) { return seats.get(player); }
    /** The number of this spin once the bets are locked; the wheel only replays it. */
    public int resultNumber() { return resultNumber; }
    public int lastResultNumber() { return lastResultNumber; }
    public int participants() { return seats.size(); }

    public long pot() {
        long total = 0;
        for (Seat seat : seats.values()) total += seat.total();
        return total;
    }

    public Container vault(UUID player) {
        return PlayerVaults.get(level.getServer()).forPlayer(player, VaultSection.ROULETTE);
    }

    public boolean acceptsBets() { return phase == Phase.WAITING || phase == Phase.BETTING; }

    /** The value waiting in the chip slots of this player. */
    public long stagedValue(UUID player) {
        return ItemBank.valueOf(catalog(), vault(player),
                RouletteSettings.INPUT_SLOT, RouletteSettings.INPUT_SLOT + RouletteSettings.STAKE_SLOTS);
    }

    /** True when every area this seat covers could be paid at once, the new bet included. */
    public boolean isPayable(UUID player, Bet bet, long stake) {
        Seat seat = seats.get(player);
        long most = (seat == null ? 0 : seat.largestWin()) + stake * bet.payout();
        return ItemBank.canStore(catalog(), vault(player), RouletteSettings.FIRST_PAYOUT_SLOT,
                RouletteSettings.VAULT_SIZE, most);
    }

    /**
     * Puts the items prepared in the chip slots onto one area of the table.
     * Several areas may be covered in the same round, each with its own chips.
     */
    public boolean place(UUID player, Bet bet, long stake) {
        if (bet == null || !acceptsBets()) return false;
        long staged = stagedValue(player);
        if (stake <= 0 || stake != staged || stake < settings().minimumStake()) return false;
        Seat seat = seats.get(player);
        if (seat != null && !seat.engaged()) return false;
        if (seat != null && !seat.stakes.containsKey(bet) && seat.stakes.size() >= MAX_BETS) return false;
        if (!isPayable(player, bet, stake)) return false;
        Container vault = vault(player);
        if (!engageChips(vault)) return false;
        if (seat == null) {
            seat = new Seat();
            seats.put(player, seat);
        }
        seat.stakes.merge(bet, stake, Long::sum);
        idleTicks = 0;
        if (phase == Phase.WAITING) {
            phase = Phase.BETTING;
            phaseTicks = settings().bettingTicks();
        }
        stationTicks = 0;
        refreshStation();
        return true;
    }

    /** Moves the prepared chips onto the table, merging with what is already there. */
    private boolean engageChips(Container vault) {
        int firstEngaged = RouletteSettings.ENGAGED_SLOT;
        int endEngaged = firstEngaged + RouletteSettings.STAKE_SLOTS;
        for (int slot = RouletteSettings.INPUT_SLOT;
                slot < RouletteSettings.INPUT_SLOT + RouletteSettings.STAKE_SLOTS; slot++) {
            ItemStack staged = vault.getItem(slot);
            if (staged.isEmpty()) continue;
            for (int target = firstEngaged; target < endEngaged && !staged.isEmpty(); target++) {
                ItemStack onTable = vault.getItem(target);
                if (onTable.isEmpty()) {
                    vault.setItem(target, staged.copy());
                    staged = ItemStack.EMPTY;
                } else if (ItemStack.isSameItemSameComponents(onTable, staged)
                        && onTable.getCount() < onTable.getMaxStackSize()) {
                    int moved = Math.min(staged.getCount(), onTable.getMaxStackSize() - onTable.getCount());
                    onTable.grow(moved);
                    staged = staged.copy();
                    staged.shrink(moved);
                }
            }
            if (!staged.isEmpty()) return false;
            vault.setItem(slot, ItemStack.EMPTY);
        }
        vault.setChanged();
        return true;
    }

    public void tick() {
        switch (phase) {
            case WAITING -> idleTicks++;
            case BETTING -> {
                if (--phaseTicks <= 0) lock();
            }
            case SPINNING -> {
                if (--phaseTicks <= 0) settle();
            }
            case RESULT -> {
                if (--phaseTicks <= 0) reset();
            }
        }
        refreshStation();
    }

    public boolean forgettable(int viewers) {
        return viewers == 0 && phase == Phase.WAITING && seats.isEmpty() && idleTicks > IDLE_TICKS;
    }

    /** Bets close here. The pocket is drawn once, and no bet can be added or changed afterwards. */
    private void lock() {
        resultNumber = RouletteWheel.spin(draw.getAsLong(), DRAW_BOUND);
        phase = Phase.SPINNING;
        phaseTicks = settings().spinTicks();
    }

    private void settle() {
        for (Map.Entry<UUID, Seat> entry : seats.entrySet()) {
            Seat seat = entry.getValue();
            if (seat.settled()) continue;
            Container vault = vault(entry.getKey());
            long owed = 0;
            for (Map.Entry<Bet, Long> stake : seat.stakes.entrySet()) {
                owed += RouletteWheel.payout(stake.getValue(), stake.getKey(), resultNumber);
            }
            seat.paid = owed;
            seat.settled = true;
            if (owed >= seat.total()) {
                // The chips come back as they are; only what was won on top is made up in change.
                long left = ItemBank.handBack(catalog(), vault, RouletteSettings.ENGAGED_SLOT,
                        RouletteSettings.FIRST_PAYOUT_SLOT, RouletteSettings.FIRST_PAYOUT_SLOT,
                        RouletteSettings.VAULT_SIZE);
                long profit = owed - seat.total() + left;
                if (profit > 0) {
                    ItemBank.store(catalog(), vault, RouletteSettings.FIRST_PAYOUT_SLOT,
                            RouletteSettings.VAULT_SIZE, profit);
                }
            } else {
                // The table keeps the chips of a losing round and pays what the winning areas owe.
                clearChips(vault);
                if (owed > 0) {
                    ItemBank.store(catalog(), vault, RouletteSettings.FIRST_PAYOUT_SLOT,
                            RouletteSettings.VAULT_SIZE, owed);
                }
            }
        }
        lastResultNumber = resultNumber;
        phase = Phase.RESULT;
        phaseTicks = settings().resultTicks();
    }

    private void reset() {
        if (level.isLoaded(station) && level.getBlockEntity(station) instanceof GameStationEntity entity) {
            entity.clear();
        }
        phase = Phase.WAITING;
        phaseTicks = 0;
        resultNumber = -1;
        idleTicks = 0;
        seats.clear();
    }

    /** Cancels an unfinished round and gives every chip still on the table back exactly once. */
    public void cancel() {
        for (Map.Entry<UUID, Seat> entry : seats.entrySet()) {
            Seat seat = entry.getValue();
            if (seat.settled()) continue;
            // The very items that were placed go back where they were prepared.
            ItemBank.move(vault(entry.getKey()), RouletteSettings.ENGAGED_SLOT,
                    RouletteSettings.INPUT_SLOT, RouletteSettings.STAKE_SLOTS);
            seat.paid = seat.total();
            seat.settled = true;
        }
        reset();
    }

    private void clearChips(Container vault) {
        for (int slot = RouletteSettings.ENGAGED_SLOT;
                slot < RouletteSettings.ENGAGED_SLOT + RouletteSettings.STAKE_SLOTS; slot++) {
            vault.setItem(slot, ItemStack.EMPTY);
        }
    }

    /** Value held in the winnings of this player. */
    public long winnings(Container vault) {
        return ItemBank.valueOf(catalog(), vault, RouletteSettings.FIRST_PAYOUT_SLOT,
                RouletteSettings.VAULT_SIZE);
    }

    /**
     * The public screen of the station, refreshed while a round is visible to nearby players.
     * What everyone may see is published here: the phase, the pocket once it is known, and who
     * covers which areas of the felt.
     */
    private void refreshStation() {
        if (phase == Phase.WAITING || --stationTicks > 0) return;
        stationTicks = STATION_REFRESH_TICKS;
        if (!level.isLoaded(station)) return;
        if (!(level.getBlockEntity(station) instanceof GameStationEntity entity)) return;
        String text = switch (phase) {
            case BETTING -> String.valueOf((phaseTicks + 19) / 20);
            case SPINNING -> ".".repeat((phaseTicks / 5 % 3) + 1);
            default -> String.valueOf(lastResultNumber);
        };
        entity.phase = phase.id();
        entity.phaseEnd = level.getGameTime() + phaseTicks;
        entity.publicBets = publicBets();
        entity.stakeItems = stakeItems();
        // The wheel of a client needs the pocket and the pace; it works out the rest itself.
        entity.targetSlot = phase == Phase.SPINNING || phase == Phase.RESULT ? resultNumber : lastResultNumber;
        entity.animationTicks = settings().spinTicks();
        entity.show(participants() + " / "
                        + java.math.BigDecimal.valueOf(pot(), 3).stripTrailingZeros().toPlainString(),
                text, "roulette_result", "", phase == Phase.RESULT, STATION_REFRESH_TICKS * 2);
    }

    /** The very items engaged on this table, so the felt can lay them out for everyone. */
    private String stakeItems() {
        StringBuilder text = new StringBuilder();
        for (UUID player : seats.keySet()) {
            String items = ItemBank.describe(vault(player), RouletteSettings.ENGAGED_SLOT,
                    RouletteSettings.FIRST_PAYOUT_SLOT, 3);
            if (items.isEmpty()) continue;
            if (text.length() > 0) text.append(',');
            text.append(items);
        }
        return text.toString();
    }

    /** One line per seat: who is playing, on which areas, and what the round paid them. */
    private String publicBets() {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<UUID, Seat> entry : seats.entrySet()) {
            var player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            String name = player == null ? entry.getKey().toString().substring(0, 8)
                    : player.getGameProfile().getName();
            StringBuilder areas = new StringBuilder();
            for (Map.Entry<Bet, Long> stake : entry.getValue().stakes().entrySet()) {
                if (areas.length() > 0) areas.append(", ");
                areas.append(label(stake.getKey())).append(' ')
                        .append(java.math.BigDecimal.valueOf(stake.getValue(), 3)
                                .stripTrailingZeros().toPlainString());
            }
            if (text.length() > 0) text.append('\n');
            text.append(name).append(" : ").append(areas);
            Seat seat = entry.getValue();
            if (seat.settled()) {
                text.append(" -> ").append(java.math.BigDecimal.valueOf(seat.paid(), 3)
                        .stripTrailingZeros().toPlainString());
            }
        }
        return text.toString();
    }

    /** How an area reads on a public screen: its kind, and the number or group it covers. */
    private static String label(Bet bet) {
        return switch (bet.type()) {
            case STRAIGHT -> String.valueOf(bet.choice());
            case DOZEN -> "dozen" + (bet.choice() + 1);
            case COLUMN -> "column" + (bet.choice() + 1);
            default -> bet.type().id();
        };
    }
}
