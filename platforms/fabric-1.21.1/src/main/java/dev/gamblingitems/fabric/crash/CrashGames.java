package dev.gamblingitems.fabric.crash;

import dev.gamblingitems.fabric.config.ModConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The running crash rounds of this server, held outside the world.
 * Unloading the chunk of a station does not suspend its flight, and breaking the station does not
 * release the stakes: the round keeps running here until it settles.
 */
public final class CrashGames {
    /** How far a portable terminal may join the round announced by a station. */
    public static final double REACH = 64;
    private static final Map<GlobalPos, CrashGame> GAMES = new HashMap<>();
    private static final Map<GlobalPos, Set<UUID>> VIEWERS = new HashMap<>();

    private CrashGames() {}

    private static GlobalPos key(CrashGame game) { return GlobalPos.of(game.level().dimension(), game.station()); }

    /** The round of this station, created on first use with the configuration of the moment. */
    public static CrashGame host(ServerLevel level, BlockPos pos) {
        return GAMES.computeIfAbsent(GlobalPos.of(level.dimension(), pos),
                key -> new CrashGame(level, pos.immutable(), ModConfig.crash()));
    }

    /**
     * The round a portable terminal may join: an existing one, announced by a nearby station.
     * A terminal never opens a new table on its own.
     */
    public static CrashGame nearest(ServerPlayer player) {
        CrashGame best = null;
        double closest = REACH * REACH;
        for (CrashGame game : GAMES.values()) {
            if (game.level() != player.level()) continue;
            BlockPos pos = game.station();
            double distance = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distance <= closest) {
                closest = distance;
                best = game;
            }
        }
        return best;
    }

    public static void join(CrashGame game, UUID player) {
        VIEWERS.computeIfAbsent(key(game), key -> new HashSet<>()).add(player);
    }

    public static void leave(CrashGame game, UUID player) {
        Set<UUID> viewers = VIEWERS.get(key(game));
        if (viewers != null) viewers.remove(player);
    }

    public static void tick(MinecraftServer server) {
        for (CrashGame game : new ArrayList<>(GAMES.values())) game.tick();
        Iterator<Map.Entry<GlobalPos, CrashGame>> games = GAMES.entrySet().iterator();
        while (games.hasNext()) {
            Map.Entry<GlobalPos, CrashGame> entry = games.next();
            Set<UUID> viewers = VIEWERS.getOrDefault(entry.getKey(), Set.of());
            if (entry.getValue().forgettable(viewers.size())) {
                games.remove();
                VIEWERS.remove(entry.getKey());
            }
        }
    }

    /**
     * Cancels every unfinished round and returns each engaged stake once.
     * A cash out already settled is never repaid.
     */
    public static void stopping(MinecraftServer server) {
        List<CrashGame> running = new ArrayList<>(GAMES.values());
        GAMES.clear();
        VIEWERS.clear();
        for (CrashGame game : running) game.cancel();
    }

    /** Only for tests and for a server that has fully stopped. */
    public static void clear() {
        GAMES.clear();
        VIEWERS.clear();
    }

    public static void register(CrashGame game) { GAMES.put(key(game), game); }
}
