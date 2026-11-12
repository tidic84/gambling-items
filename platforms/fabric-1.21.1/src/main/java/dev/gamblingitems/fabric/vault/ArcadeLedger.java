package dev.gamblingitems.fabric.vault;

import dev.gamblingitems.fabric.Nbt;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * The arcade tickets of every player: their balance, the last plays they made and the best wins of
 * the week. A play is settled the moment it is drawn, but its tickets only count, and only show in
 * the history and on the leaderboard, once the machine has revealed it: a balance must never tell
 * the outcome of a play still turning.
 *
 * <p>Times are server game ticks, which keep counting across restarts.
 */
public final class ArcadeLedger {
    /** How many plays a player's history keeps, and how many wins the week's leaderboard. */
    public static final int HISTORY = 20, LEADERBOARD = 10;

    /** One play, as a history line: the machine, what was staked, the multiplier and the tickets won. */
    public record Play(String mode, long stake, int multiplier, long tickets, long at) {}

    /** One win of the week's leaderboard. */
    public record Win(UUID player, String name, String mode, long tickets, int multiplier, long at) {}

    /** Tickets won by a play still turning, and the tick it is revealed. */
    private record Pending(long tickets, long at) {}

    private final Runnable changed;
    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, List<Pending>> pending = new HashMap<>();
    private final Map<UUID, Deque<Play>> history = new HashMap<>();
    private final List<Win> week = new ArrayList<>();
    private String weekKey = "";

    ArcadeLedger(Runnable changed) { this.changed = changed; }

    /**
     * Settles one play: its tickets count from {@code revealAt}. A win also enters the week's
     * leaderboard if it is among its best.
     */
    public void record(UUID player, String name, String mode, long stake, int multiplier, long tickets, long revealAt) {
        if (tickets < 0 || stake < 0) throw new IllegalArgumentException("A play cannot take tickets back");
        Deque<Play> plays = history.computeIfAbsent(player, key -> new ArrayDeque<>());
        plays.addFirst(new Play(mode, stake, multiplier, tickets, revealAt));
        while (plays.size() > HISTORY) plays.removeLast();
        if (tickets > 0) {
            pending.computeIfAbsent(player, key -> new ArrayList<>()).add(new Pending(tickets, revealAt));
            rollWeek();
            week.add(new Win(player, name, mode, tickets, multiplier, revealAt));
            week.sort(Comparator.comparingLong(Win::tickets).reversed().thenComparingLong(Win::at));
            while (week.size() > LEADERBOARD) week.remove(week.size() - 1);
        }
        changed.run();
    }

    /** The tickets a player can spend now: what was revealed, wins still turning left out. */
    public long balance(UUID player, long now) {
        fold(player, now);
        return balances.getOrDefault(player, 0L);
    }

    /** Spends tickets at the prize counter; false, and nothing spent, when the balance falls short. */
    public boolean spend(UUID player, long tickets, long now) {
        if (tickets <= 0) throw new IllegalArgumentException("A prize costs at least one ticket");
        long balance = balance(player, now);
        if (balance < tickets) return false;
        balances.put(player, balance - tickets);
        changed.run();
        return true;
    }

    /** A player's plays, newest first, the ones still turning left out. */
    public List<Play> history(UUID player, long now) {
        List<Play> shown = new ArrayList<>();
        for (Play play : history.getOrDefault(player, new ArrayDeque<>())) {
            if (play.at() <= now) shown.add(play);
        }
        return shown;
    }

    /** The best wins of this week, best first, the ones still turning left out. */
    public List<Win> leaderboard(long now) {
        rollWeek();
        List<Win> shown = new ArrayList<>();
        for (Win win : week) {
            if (win.at() <= now) shown.add(win);
        }
        return shown;
    }

    /** Moves every revealed win of a player into their balance. */
    private void fold(UUID player, long now) {
        List<Pending> waiting = pending.get(player);
        if (waiting == null) return;
        long gained = 0;
        for (var iterator = waiting.iterator(); iterator.hasNext(); ) {
            Pending win = iterator.next();
            if (win.at() > now) continue;
            gained += win.tickets();
            iterator.remove();
        }
        if (waiting.isEmpty()) pending.remove(player);
        if (gained == 0) return;
        balances.merge(player, gained, Long::sum);
        changed.run();
    }

    /** A new week, counted in UTC, starts an empty leaderboard. */
    private void rollWeek() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String key = today.get(IsoFields.WEEK_BASED_YEAR) + "-W" + today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (key.equals(weekKey)) return;
        weekKey = key;
        week.clear();
        changed.run();
    }

    void read(CompoundTag tag) {
        weekKey = Nbt.string(tag, "week");
        ListTag players = Nbt.compounds(tag, "players");
        for (int index = 0; index < players.size(); index++) {
            CompoundTag entry = Nbt.compound(players, index);
            UUID player = Nbt.uuid(entry, "player");
            long balance = Nbt.longValue(entry, "balance");
            if (balance > 0) balances.put(player, balance);
            ListTag waiting = Nbt.compounds(entry, "pending");
            for (int at = 0; at < waiting.size(); at++) {
                CompoundTag win = Nbt.compound(waiting, at);
                pending.computeIfAbsent(player, key -> new ArrayList<>())
                        .add(new Pending(Nbt.longValue(win, "tickets"), Nbt.longValue(win, "at")));
            }
            ListTag plays = Nbt.compounds(entry, "plays");
            Deque<Play> deque = new ArrayDeque<>();
            for (int at = 0; at < plays.size(); at++) {
                CompoundTag play = Nbt.compound(plays, at);
                deque.addLast(new Play(Nbt.string(play, "mode"), Nbt.longValue(play, "stake"),
                        Nbt.integer(play, "multiplier", 0), Nbt.longValue(play, "tickets"), Nbt.longValue(play, "at")));
            }
            if (!deque.isEmpty()) history.put(player, deque);
        }
        ListTag wins = Nbt.compounds(tag, "leaderboard");
        for (int index = 0; index < wins.size(); index++) {
            CompoundTag win = Nbt.compound(wins, index);
            week.add(new Win(Nbt.uuid(win, "player"), Nbt.string(win, "name"), Nbt.string(win, "mode"),
                    Nbt.longValue(win, "tickets"), Nbt.integer(win, "multiplier", 0), Nbt.longValue(win, "at")));
        }
    }

    CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putString("week", weekKey);
        ListTag players = new ListTag();
        Set<UUID> known = new HashSet<>(balances.keySet());
        known.addAll(pending.keySet());
        known.addAll(history.keySet());
        for (UUID player : known) {
            CompoundTag entry = new CompoundTag();
            Nbt.putUuid(entry, "player", player);
            entry.putLong("balance", balances.getOrDefault(player, 0L));
            ListTag waiting = new ListTag();
            for (Pending win : pending.getOrDefault(player, List.of())) {
                CompoundTag saved = new CompoundTag();
                saved.putLong("tickets", win.tickets());
                saved.putLong("at", win.at());
                waiting.add(saved);
            }
            entry.put("pending", waiting);
            ListTag plays = new ListTag();
            for (Play play : history.getOrDefault(player, new ArrayDeque<>())) {
                CompoundTag saved = new CompoundTag();
                saved.putString("mode", play.mode());
                saved.putLong("stake", play.stake());
                saved.putInt("multiplier", play.multiplier());
                saved.putLong("tickets", play.tickets());
                saved.putLong("at", play.at());
                plays.add(saved);
            }
            entry.put("plays", plays);
            players.add(entry);
        }
        tag.put("players", players);
        ListTag wins = new ListTag();
        for (Win win : week) {
            CompoundTag saved = new CompoundTag();
            Nbt.putUuid(saved, "player", win.player());
            saved.putString("name", win.name());
            saved.putString("mode", win.mode());
            saved.putLong("tickets", win.tickets());
            saved.putInt("multiplier", win.multiplier());
            saved.putLong("at", win.at());
            wins.add(saved);
        }
        tag.put("leaderboard", wins);
        return tag;
    }
}
