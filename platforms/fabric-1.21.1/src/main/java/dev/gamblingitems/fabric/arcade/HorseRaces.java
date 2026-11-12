package dev.gamblingitems.fabric.arcade;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.machines.MachineTable;
import dev.gamblingitems.fabric.Compat;
import dev.gamblingitems.fabric.block.CabinetPlay;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.config.ModConfig;
import dev.gamblingitems.fabric.machines.MachineMenu;
import dev.gamblingitems.fabric.machines.MachineSettings;
import dev.gamblingitems.fabric.machines.MachineSetup;
import dev.gamblingitems.fabric.value.ItemBank;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The mechanical horse race, a round shared by everyone at the cabinet. The first bet opens the
 * betting for {@code arcade.raceBettingTicks}; anyone may then put a stake on a horse at its lane
 * button. When the betting closes, one horse is drawn by its strength, every bet is settled at once
 * and the race shows it; each player is told the result when the winner crosses the line.
 *
 * <p>The stakes are held by the round until the race starts: a server stopping before that gives
 * every one of them back, into the player's machine, where it is collected with an empty hand.
 */
public final class HorseRaces {
    private static final SecureRandom RANDOM = new SecureRandom();
    static IntUnaryOperator draw = RANDOM::nextInt;
    private static final Map<Key, Round> ROUNDS = new HashMap<>();

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {}

    private record Bet(UUID player, String name, int horse, long stake, ItemStack staked) {}

    private static final class Round {
        final List<Bet> bets = new ArrayList<>();
        long startAt;
        /** Set once the race runs: when it ends, and the horse that wins it. */
        long endAt = -1;
        int winner = -1;
        final Map<UUID, Long> won = new HashMap<>();
    }

    private HorseRaces() {}

    /** A bet on a horse with the stack in hand; refused while the horses run. */
    public static boolean bet(ServerPlayer player, GameStationEntity cabinet, MachineSetup setup, int horse, ItemStack hand) {
        MachineSettings settings = setup.settings();
        if (horse < 0 || horse >= settings.table().choices()) return false;
        Key key = new Key(player.level().dimension(), cabinet.getBlockPos().immutable());
        Round round = ROUNDS.get(key);
        long now = player.level().getGameTime();
        if (round != null && round.endAt >= 0) return CabinetPlay.refuse(player, "world_play_busy");
        long unit = setup.catalog().valueOf(hand.copyWithCount(1));
        int count = hand.getCount();
        if (settings.maximumStake() > 0 && unit > 0) count = (int) Math.min(count, settings.maximumStake() / unit);
        long stake = count <= 0 ? 0 : setup.catalog().valueOf(hand.copyWithCount(count));
        if (!settings.enabled() || stake <= 0 || !settings.accepts(stake)) {
            return CabinetPlay.refuse(player, "world_play_refused", CabinetPlay.amount(settings.minimumStake()));
        }
        if (!setup.paysTickets()) {
            // Paid in items, every bet of this player in the round must fit in the machine at once.
            long owed = settings.maximumPayout(stake);
            if (round != null) {
                for (Bet other : round.bets) if (other.player().equals(player.getUUID())) owed += settings.maximumPayout(other.stake());
            }
            SimpleContainer vault = PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.HORSE_RACE);
            if (!ItemBank.canStore(setup.catalog(), vault, MachineSettings.FIRST_PAYOUT_SLOT, MachineSettings.VAULT_SIZE, owed)) {
                return CabinetPlay.refuse(player, "world_play_refused", CabinetPlay.amount(settings.minimumStake()));
            }
        }
        if (round == null) {
            round = new Round();
            round.startAt = now + ModConfig.arcade().raceBettingTicks();
            ROUNDS.put(key, round);
        }
        ItemStack staked = hand.copyWithCount(count);
        hand.shrink(count);
        round.bets.add(new Bet(player.getUUID(), player.getName().getString(), horse, stake, staked));
        cabinet.phase = 0;
        cabinet.phaseEnd = round.startAt;
        cabinet.publicBets = describe(round);
        cabinet.wheelColours = MachineMenu.encode(settings.table());
        cabinet.clearResult();
        Compat.message(player, Component.translatable("gui.gamblingitems.horse_race_bet",
                Component.literal(count + " × ").append(staked.getHoverName()), horse + 1), true);
        return true;
    }

    /** "name:horse:stake" for every bet, one per line, for the cabinet to show. */
    private static String describe(Round round) {
        StringBuilder text = new StringBuilder();
        for (Bet bet : round.bets) {
            if (text.length() > 0) text.append('\n');
            text.append(bet.name()).append(':').append(bet.horse()).append(':').append(bet.stake());
        }
        return text.toString();
    }

    /** Starts the races whose betting closed, and tells the players of the ones that finished. */
    public static void tick(MinecraftServer server) {
        for (Iterator<Map.Entry<Key, Round>> iterator = ROUNDS.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            Key key = entry.getKey();
            Round round = entry.getValue();
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) continue;
            long now = level.getGameTime();
            if (round.endAt < 0 && now >= round.startAt) {
                if (!(level.getBlockEntity(key.pos()) instanceof GameStationEntity cabinet)) {
                    // The cabinet is gone or unloaded: the stakes go back rather than racing unseen.
                    refund(server, round);
                    iterator.remove();
                    continue;
                }
                start(level, cabinet, round);
            } else if (round.endAt >= 0 && now >= round.endAt) {
                announce(server, round);
                iterator.remove();
            }
        }
    }

    /** One draw for the whole round: every bet is settled now, and the race shows it. */
    private static void start(ServerLevel level, GameStationEntity cabinet, Round round) {
        MachineSetup setup = CabinetPlay.setup(GameMode.HORSE_RACE, cabinet);
        MachineSettings settings = setup.settings();
        MachineTable table = settings.table();
        MachineTable.Outcome outcome = table.play(0, draw);
        int winner = outcome.shown()[0];
        long now = level.getGameTime();
        long revealAt = now + settings.playTicks();
        int[] multipliers = table.multipliers();
        long bestTickets = 0;
        for (Bet bet : round.bets) {
            int multiplier = bet.horse() == winner ? multipliers[winner] : 0;
            long paid = multiplier == 0 ? 0 : MachineTable.payout(bet.stake(), multiplier, settings.returnRate());
            long tickets = 0;
            if (setup.paysTickets()) {
                tickets = paid / setup.ticketValue();
                PlayerVaults.get(level.getServer()).arcade().record(bet.player(), bet.name(), GameMode.HORSE_RACE.id(),
                        bet.stake(), multiplier, tickets, revealAt);
            } else if (paid > 0) {
                SimpleContainer vault = PlayerVaults.get(level.getServer()).forPlayer(bet.player(), VaultSection.HORSE_RACE);
                ItemBank.store(setup.catalog(), vault, MachineSettings.FIRST_PAYOUT_SLOT, MachineSettings.VAULT_SIZE, paid);
            }
            round.won.merge(bet.player(), setup.paysTickets() ? tickets : paid, Long::sum);
            bestTickets = Math.max(bestTickets, tickets);
        }
        round.winner = winner;
        round.endAt = revealAt;
        boolean anyWinner = round.bets.stream().anyMatch(bet -> bet.horse() == winner);
        cabinet.reels = winner + "," + outcome.shown()[1] + ",0";
        cabinet.phase = 1;
        cabinet.publicBets = describe(round);
        cabinet.animationTicks = settings.playTicks();
        cabinet.multiplier = multipliers[winner];
        cabinet.tickets = bestTickets;
        cabinet.show("", "", "horse_race_winner", "", anyWinner, settings.playTicks() + settings.resultTicks());
    }

    /** Once the winner crosses the line, each player who bet hears what the race paid them. */
    private static void announce(MinecraftServer server, Round round) {
        boolean tickets = ModConfig.arcade().tickets();
        for (var entry : round.won.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            Component text = entry.getValue() > 0
                    ? Component.translatable(tickets ? "gui.gamblingitems.horse_race_won_tickets" : "gui.gamblingitems.horse_race_won",
                            round.winner + 1, tickets ? String.valueOf(entry.getValue()) : CabinetPlay.amount(entry.getValue()))
                    : Component.translatable("gui.gamblingitems.horse_race_lost", round.winner + 1);
            Compat.message(player, text, true);
        }
    }

    /** Gives every stake of a round that never ran back, into each player's machine. */
    private static void refund(MinecraftServer server, Round round) {
        for (Bet bet : round.bets) {
            SimpleContainer vault = PlayerVaults.get(server).forPlayer(bet.player(), VaultSection.HORSE_RACE);
            ItemStack left = bet.staked().copy();
            for (int slot = MachineSettings.FIRST_PAYOUT_SLOT; slot < MachineSettings.VAULT_SIZE && !left.isEmpty(); slot++) {
                if (vault.getItem(slot).isEmpty()) {
                    vault.setItem(slot, left);
                    left = ItemStack.EMPTY;
                }
            }
            // A full machine gets the value back in change instead, never nothing.
            if (!left.isEmpty()) ItemBank.store(ModConfig.machine(GameMode.HORSE_RACE).catalog(), vault,
                    MachineSettings.FIRST_PAYOUT_SLOT, MachineSettings.VAULT_SIZE, bet.stake());
        }
    }

    /** An interrupted betting window gives every stake back exactly once. */
    public static void stopping(MinecraftServer server) {
        for (Round round : ROUNDS.values()) {
            if (round.endAt < 0) refund(server, round);
        }
        ROUNDS.clear();
    }

    public static void clear() { ROUNDS.clear(); }
}
