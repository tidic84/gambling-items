package dev.gamblingitems.fabric.crash;

import dev.gamblingitems.core.crash.CrashRules;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.value.ItemBank;
import dev.gamblingitems.fabric.value.ValueCatalog;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * One shared crash round hosted by a station. The server owns the flight: it draws the secret
 * crash point, advances the multiplier on its own ticks and settles every bet itself.
 *
 * <p>A bet is any set of priced items; only their total value is played. The staked items never
 * leave the player's saved vault: they wait in the engaged slots and are only taken when the round
 * settles them. A brutal stop therefore leaves a stake with its owner rather than nowhere.
 */
public final class CrashGame {
    /** Independent of the client clock: only these ticks decide what a flight is worth. */
    public static final long DRAW_BOUND = 1_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int STATION_REFRESH_TICKS = 10;
    /** A round with nobody watching and nothing engaged is forgotten after this delay. */
    public static final int IDLE_TICKS = 1_200;

    public enum Phase {
        WAITING(0), BETTING(1), FLYING(2), CRASHED(3);

        private final int id;
        Phase(int id) { this.id = id; }
        public int id() { return id; }
        public static Phase fromId(int id) {
            for (Phase phase : values()) if (phase.id == id) return phase;
            throw new IllegalArgumentException("Unknown crash phase: " + id);
        }
    }

    /** One player's engagement in this round. The items themselves stay in that player's vault. */
    public static final class Bet {
        private final long stake;
        private int settledMultiplier;
        private long paid;
        private boolean lost;

        private Bet(long stake) { this.stake = stake; }

        public long stake() { return stake; }
        public int settledMultiplier() { return settledMultiplier; }
        public long paid() { return paid; }
        public boolean lost() { return lost; }
        public boolean engaged() { return settledMultiplier == 0 && !lost; }
    }

    private final ServerLevel level;
    private final BlockPos station;
    private final CrashSetup setup;
    private final LongSupplier draw;
    private final Map<UUID, Bet> bets = new LinkedHashMap<>();
    private Phase phase = Phase.WAITING;
    private int phaseTicks;
    private int flightTick;
    private int crashPoint;
    private int lastCrashPoint;
    private int idleTicks;
    private int stationTicks;

    CrashGame(ServerLevel level, BlockPos station, CrashSetup setup) {
        this(level, station, setup, () -> RANDOM.nextLong(DRAW_BOUND));
    }

    CrashGame(ServerLevel level, BlockPos station, CrashSetup setup, LongSupplier draw) {
        this.level = level;
        this.station = station;
        this.setup = setup;
        this.draw = draw;
    }

    public ServerLevel level() { return level; }
    public BlockPos station() { return station; }
    public CrashSetup setup() { return setup; }
    public CrashSettings settings() { return setup.settings(); }
    public ValueCatalog catalog() { return setup.catalog(); }
    public Phase phase() { return phase; }
    public int remainingTicks() { return phaseTicks; }
    public int flightTick() { return flightTick; }
    public Bet betOf(UUID player) { return bets.get(player); }
    /** The crash point of the previous round, public once it has happened. */
    public int lastCrashPoint() { return lastCrashPoint; }

    public int participants() { return bets.size(); }

    public long pot() {
        long total = 0;
        for (Bet bet : bets.values()) total += bet.stake();
        return total;
    }

    /** What every player sees. The crash point stays secret until the flight ends. */
    public int publicMultiplier() {
        return switch (phase) {
            case FLYING -> settings().rules().multiplierAt(flightTick);
            case CRASHED -> crashPoint;
            default -> CrashRules.START;
        };
    }

    public Container vault(UUID player) {
        return PlayerVaults.get(level.getServer()).forPlayer(player, VaultSection.CRASH);
    }

    /** Bets are accepted before the flight only, and one per player and per round. */
    public boolean acceptsBets() { return phase == Phase.WAITING || phase == Phase.BETTING; }

    /** The value waiting in the bet slots of this player. */
    public long stagedValue(UUID player) {
        return ItemBank.valueOf(catalog(), vault(player),
                CrashSettings.INPUT_SLOT, CrashSettings.INPUT_SLOT + CrashSettings.STAKE_SLOTS);
    }

    /** True when this player could be handed the largest win that stake could produce. */
    public boolean isPayable(UUID player, long stake) {
        return stake > 0 && ItemBank.canStore(catalog(), vault(player), CrashSettings.FIRST_PAYOUT_SLOT,
                CrashSettings.VAULT_SIZE, settings().maximumPayout(stake));
    }

    /**
     * Engages the items prepared in the bet slots. No fixed maximum applies: a bet is refused only
     * when the win it could produce would not fit in the winnings of that player.
     */
    public boolean place(UUID player, long stake) {
        if (!acceptsBets() || bets.containsKey(player)) return false;
        long staged = stagedValue(player);
        if (stake <= 0 || stake != staged || stake < settings().minimumStake()) return false;
        Container vault = vault(player);
        if (!ItemBank.isEmpty(vault, CrashSettings.ENGAGED_SLOT,
                CrashSettings.ENGAGED_SLOT + CrashSettings.STAKE_SLOTS)) {
            return false;
        }
        if (!isPayable(player, stake)) return false;
        ItemBank.move(vault, CrashSettings.INPUT_SLOT, CrashSettings.ENGAGED_SLOT, CrashSettings.STAKE_SLOTS);
        bets.put(player, new Bet(stake));
        idleTicks = 0;
        if (phase == Phase.WAITING) {
            phase = Phase.BETTING;
            phaseTicks = settings().bettingTicks();
        }
        return true;
    }

    /**
     * A manual cash out pays the multiplier every player can currently see.
     * The instant of a click is never a proof of precedence: once the flight has ended, it is refused.
     */
    public boolean cashOut(UUID player) {
        if (phase != Phase.FLYING) return false;
        Bet bet = bets.get(player);
        if (bet == null || !bet.engaged()) return false;
        settle(player, bet, settings().rules().multiplierAt(flightTick));
        return true;
    }

    public void tick() {
        switch (phase) {
            case WAITING -> idleTicks++;
            case CRASHED -> {
                if (--phaseTicks <= 0) reset();
            }
            case BETTING -> {
                if (--phaseTicks <= 0) takeOff();
            }
            case FLYING -> advance();
        }
        refreshStation();
    }

    /** True once nobody is engaged and nobody has opened this round for a while. */
    public boolean forgettable(int viewers) {
        return viewers == 0 && phase == Phase.WAITING && bets.isEmpty() && idleTicks > IDLE_TICKS;
    }

    private void takeOff() {
        crashPoint = settings().rules().crashPoint(draw.getAsLong(), DRAW_BOUND);
        phase = Phase.FLYING;
        flightTick = -1;
        phaseTicks = 0;
        advance();
    }

    /** One step of the flight: the multiplier of the next tick, or the end of the round. */
    private void advance() {
        int next = flightTick + 1;
        if (settings().rules().multiplierAt(next) >= crashPoint) crash();
        else flightTick = next;
    }

    private void crash() {
        for (Map.Entry<UUID, Bet> entry : bets.entrySet()) {
            Bet bet = entry.getValue();
            if (!bet.engaged()) continue;
            // The stake was already the table's: the engaged items leave and nothing is paid back.
            bet.lost = true;
            clearEngaged(vault(entry.getKey()));
        }
        lastCrashPoint = crashPoint;
        phase = Phase.CRASHED;
        phaseTicks = settings().resultTicks();
    }

    private void reset() {
        if (level.isLoaded(station) && level.getBlockEntity(station) instanceof GameStationEntity entity) {
            entity.clear();
        }
        phase = Phase.WAITING;
        phaseTicks = 0;
        flightTick = 0;
        crashPoint = 0;
        idleTicks = 0;
        bets.clear();
    }

    private void settle(UUID player, Bet bet, int multiplier) {
        long total = settings().rules().payout(bet.stake(), multiplier);
        Container vault = vault(player);
        clearEngaged(vault);
        // The room was checked when the bet was accepted; what is below the cheapest item of the
        // catalogue is the rounding the screen announces.
        ItemBank.store(catalog(), vault, CrashSettings.FIRST_PAYOUT_SLOT, CrashSettings.VAULT_SIZE, total);
        bet.settledMultiplier = multiplier;
        bet.paid = total;
    }

    private void clearEngaged(Container vault) {
        for (int slot = CrashSettings.ENGAGED_SLOT;
                slot < CrashSettings.ENGAGED_SLOT + CrashSettings.STAKE_SLOTS; slot++) {
            vault.setItem(slot, ItemStack.EMPTY);
        }
    }

    /**
     * Cancels an unfinished round and gives every engaged stake back exactly once.
     * A settled cash out is never repaid.
     */
    public void cancel() {
        for (Map.Entry<UUID, Bet> entry : bets.entrySet()) {
            Bet bet = entry.getValue();
            if (!bet.engaged()) continue;
            // The very items that were engaged go back where they were prepared.
            ItemBank.move(vault(entry.getKey()), CrashSettings.ENGAGED_SLOT, CrashSettings.INPUT_SLOT,
                    CrashSettings.STAKE_SLOTS);
            bet.lost = false;
            bet.settledMultiplier = CrashRules.START;
            bet.paid = bet.stake();
        }
        reset();
    }

    /** Value held in the winnings of this player. */
    public long winnings(Container vault) {
        return ItemBank.valueOf(catalog(), vault, CrashSettings.FIRST_PAYOUT_SLOT, CrashSettings.VAULT_SIZE);
    }

    /** The public screen of the station, refreshed while a flight is visible to nearby players. */
    private void refreshStation() {
        if (phase == Phase.WAITING || --stationTicks > 0) return;
        stationTicks = STATION_REFRESH_TICKS;
        if (!level.isLoaded(station)) return;
        if (!(level.getBlockEntity(station) instanceof GameStationEntity entity)) return;
        String multiplier = BigDecimal.valueOf(publicMultiplier(), 2).toPlainString() + "x";
        entity.show(participants() + " / " + BigDecimal.valueOf(pot(), 3).stripTrailingZeros().toPlainString(),
                phase == Phase.BETTING ? String.valueOf((phaseTicks + 19) / 20) : multiplier,
                "crash_result", "", phase != Phase.CRASHED, STATION_REFRESH_TICKS * 2);
    }
}
