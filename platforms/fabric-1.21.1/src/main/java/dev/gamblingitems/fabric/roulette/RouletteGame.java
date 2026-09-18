package dev.gamblingitems.fabric.roulette;

import dev.gamblingitems.core.roulette.RouletteRules;
import dev.gamblingitems.core.roulette.RouletteRules.Colour;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
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
 * One shared roulette round hosted by a station: bets, lock, spin, payment.
 * The server draws the slot when the bets close; the wheel drawn by a client only replays it.
 *
 * <p>A staked item never leaves the player's saved vault: it waits in the engaged slot and moves
 * to the winnings only when the round settles it.
 */
public final class RouletteGame {
    public static final long DRAW_BOUND = 1_000_000;
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

    /** One player's bet of this round. The items themselves stay in that player's vault. */
    public static final class Bet {
        private final int stake;
        private final Colour colour;
        private long paid;
        private boolean settled;

        private Bet(int stake, Colour colour) {
            this.stake = stake;
            this.colour = colour;
        }

        public int stake() { return stake; }
        public Colour colour() { return colour; }
        public long paid() { return paid; }
        public boolean settled() { return settled; }
        public boolean engaged() { return !settled; }
    }

    private final ServerLevel level;
    private final BlockPos station;
    private final RouletteSettings settings;
    private final LongSupplier draw;
    private final Map<UUID, Bet> bets = new LinkedHashMap<>();
    private Phase phase = Phase.WAITING;
    private int phaseTicks;
    private int resultSlot = -1;
    private int lastResultSlot = -1;
    private int idleTicks;
    private int stationTicks;

    RouletteGame(ServerLevel level, BlockPos station, RouletteSettings settings) {
        this(level, station, settings, () -> RANDOM.nextLong(DRAW_BOUND));
    }

    RouletteGame(ServerLevel level, BlockPos station, RouletteSettings settings, LongSupplier draw) {
        this.level = level;
        this.station = station;
        this.settings = settings;
        this.draw = draw;
    }

    public ServerLevel level() { return level; }
    public BlockPos station() { return station; }
    public RouletteSettings settings() { return settings; }
    public Phase phase() { return phase; }
    public int remainingTicks() { return phaseTicks; }
    public Bet betOf(UUID player) { return bets.get(player); }
    /** The slot of this spin once the bets are locked; the wheel only replays it. */
    public int resultSlot() { return resultSlot; }
    public int lastResultSlot() { return lastResultSlot; }
    public int participants() { return bets.size(); }

    public int pot() {
        int total = 0;
        for (Bet bet : bets.values()) total += bet.stake();
        return total;
    }

    public Container vault(UUID player) {
        return PlayerVaults.get(level.getServer()).forPlayer(player, VaultSection.ROULETTE);
    }

    public boolean acceptsBets() { return phase == Phase.WAITING || phase == Phase.BETTING; }

    public int largestStake(UUID player) {
        return settings.largestStake(freeWinningSpace(vault(player)));
    }

    /** Engages items already sitting in the vault, on one colour, until the spin settles them. */
    public boolean place(UUID player, Colour colour, int count) {
        if (colour == null || !acceptsBets() || bets.containsKey(player)) return false;
        if (count < settings.minimumStake() || count > largestStake(player)) return false;
        Container vault = vault(player);
        ItemStack staged = vault.getItem(RouletteSettings.INPUT_SLOT);
        if (!settings.isStake(staged) || staged.getCount() < count) return false;
        if (!vault.getItem(RouletteSettings.ENGAGED_SLOT).isEmpty()) return false;
        vault.removeItem(RouletteSettings.INPUT_SLOT, count);
        vault.setItem(RouletteSettings.ENGAGED_SLOT, settings.stakeStack(count));
        bets.put(player, new Bet(count, colour));
        idleTicks = 0;
        if (phase == Phase.WAITING) {
            phase = Phase.BETTING;
            phaseTicks = settings.bettingTicks();
        }
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
        return viewers == 0 && phase == Phase.WAITING && bets.isEmpty() && idleTicks > IDLE_TICKS;
    }

    /** Bets close here. The slot is drawn once, and no bet can be added or changed afterwards. */
    private void lock() {
        resultSlot = settings.rules().spin(draw.getAsLong(), DRAW_BOUND);
        phase = Phase.SPINNING;
        phaseTicks = settings.spinTicks();
    }

    private void settle() {
        RouletteRules rules = settings.rules();
        Colour result = rules.colourAt(resultSlot);
        for (Map.Entry<UUID, Bet> entry : bets.entrySet()) {
            Bet bet = entry.getValue();
            if (bet.settled()) continue;
            Container vault = vault(entry.getKey());
            // The stake was the table's from the moment it was engaged.
            vault.setItem(RouletteSettings.ENGAGED_SLOT, ItemStack.EMPTY);
            bet.paid = rules.payout(bet.stake(), bet.colour(), result);
            bet.settled = true;
            if (bet.paid > 0) store(vault, bet.paid);
        }
        lastResultSlot = resultSlot;
        phase = Phase.RESULT;
        phaseTicks = settings.resultTicks();
    }

    private void reset() {
        if (level.isLoaded(station) && level.getBlockEntity(station) instanceof GameStationEntity entity) {
            entity.clear();
        }
        phase = Phase.WAITING;
        phaseTicks = 0;
        resultSlot = -1;
        idleTicks = 0;
        bets.clear();
    }

    /** Cancels an unfinished round and gives every engaged stake back exactly once. */
    public void cancel() {
        for (Map.Entry<UUID, Bet> entry : bets.entrySet()) {
            Bet bet = entry.getValue();
            if (bet.settled()) continue;
            Container vault = vault(entry.getKey());
            ItemStack engaged = vault.getItem(RouletteSettings.ENGAGED_SLOT);
            int refund = engaged.isEmpty() ? bet.stake() : engaged.getCount();
            vault.setItem(RouletteSettings.ENGAGED_SLOT, ItemStack.EMPTY);
            store(vault, refund);
            bet.paid = refund;
            bet.settled = true;
        }
        reset();
    }

    public int winnings(Container vault) {
        int total = 0;
        for (int slot = RouletteSettings.FIRST_PAYOUT_SLOT; slot < RouletteSettings.VAULT_SIZE; slot++) {
            ItemStack stack = vault.getItem(slot);
            if (settings.isStake(stack)) total += stack.getCount();
        }
        return total;
    }

    public long freeWinningSpace(Container vault) {
        long free = 0;
        int limit = settings.stackLimit();
        for (int slot = RouletteSettings.FIRST_PAYOUT_SLOT; slot < RouletteSettings.VAULT_SIZE; slot++) {
            ItemStack stack = vault.getItem(slot);
            if (stack.isEmpty()) free += limit;
            else if (settings.isStake(stack)) free += Math.max(0, limit - stack.getCount());
        }
        return free;
    }

    private void store(Container vault, long count) {
        long left = count;
        int limit = settings.stackLimit();
        for (int slot = RouletteSettings.FIRST_PAYOUT_SLOT; slot < RouletteSettings.VAULT_SIZE && left > 0; slot++) {
            ItemStack stack = vault.getItem(slot);
            if (stack.isEmpty()) {
                int added = (int) Math.min(left, limit);
                vault.setItem(slot, settings.stakeStack(added));
                left -= added;
            } else if (settings.isStake(stack) && stack.getCount() < limit) {
                int added = (int) Math.min(left, limit - stack.getCount());
                stack.grow(added);
                vault.setChanged();
                left -= added;
            }
        }
        // A bet is only accepted when its largest payout fits, so this never silently drops items.
        if (left > 0) throw new IllegalStateException("The roulette winnings of a player are full");
    }

    /** The public screen of the station, refreshed while a round is visible to nearby players. */
    private void refreshStation() {
        if (phase == Phase.WAITING || --stationTicks > 0) return;
        stationTicks = STATION_REFRESH_TICKS;
        if (!level.isLoaded(station)) return;
        if (!(level.getBlockEntity(station) instanceof GameStationEntity entity)) return;
        String text = switch (phase) {
            case BETTING -> String.valueOf((phaseTicks + 19) / 20);
            case SPINNING -> ".".repeat((phaseTicks / 5 % 3) + 1);
            default -> settings.rules().colourAt(lastResultSlot).id().toUpperCase(java.util.Locale.ROOT);
        };
        entity.phase = phase.id();
        entity.targetSlot = resultSlot;
        entity.animationTicks = settings.spinTicks();
        entity.phaseEnd = level.getGameTime() + phaseTicks;
        entity.wheelColours = java.util.stream.IntStream.range(0, settings.rules().slots())
                .mapToObj(i -> settings.rules().colourAt(i).id()).collect(java.util.stream.Collectors.joining(","));
        entity.publicBets = bets.entrySet().stream().map(entry -> {
            var player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            String name = player == null ? entry.getKey().toString().substring(0, 8) : player.getGameProfile().getName();
            return name + " : " + entry.getValue().stake() + " " + entry.getValue().colour().id();
        }).collect(java.util.stream.Collectors.joining("\n"));
        entity.show(participants() + " / " + pot(), text, "roulette_result", "",
                phase == Phase.RESULT, STATION_REFRESH_TICKS * 2);
    }
}
