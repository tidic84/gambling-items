package dev.gamblingitems.fabric.roulette;

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
 * The running roulette rounds of this server, held outside the world, exactly like the crash tables.
 * Unloading the chunk of a station does not suspend a spin, and breaking it releases no stake.
 */
public final class RouletteGames {
    /** How far a portable terminal may join the round announced by a station. */
    public static final double REACH = 64;
    private static final Map<GlobalPos, RouletteGame> GAMES = new HashMap<>();
    private static final Map<GlobalPos, Set<UUID>> VIEWERS = new HashMap<>();

    private RouletteGames() {}

    private static GlobalPos key(RouletteGame game) {
        return GlobalPos.of(game.level().dimension(), game.station());
    }

    public static RouletteGame host(ServerLevel level, BlockPos pos) {
        return GAMES.computeIfAbsent(GlobalPos.of(level.dimension(), pos),
                key -> new RouletteGame(level, pos.immutable(), ModConfig.roulette()));
    }

    /** The round a portable terminal may join; a terminal never opens a new table on its own. */
    public static RouletteGame nearest(ServerPlayer player) {
        RouletteGame best = null;
        double closest = REACH * REACH;
        for (RouletteGame game : GAMES.values()) {
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

    public static void join(RouletteGame game, UUID player) {
        VIEWERS.computeIfAbsent(key(game), key -> new HashSet<>()).add(player);
    }

    public static void leave(RouletteGame game, UUID player) {
        Set<UUID> viewers = VIEWERS.get(key(game));
        if (viewers != null) viewers.remove(player);
    }

    public static void tick(MinecraftServer server) {
        for (RouletteGame game : new ArrayList<>(GAMES.values())) game.tick();
        Iterator<Map.Entry<GlobalPos, RouletteGame>> games = GAMES.entrySet().iterator();
        while (games.hasNext()) {
            Map.Entry<GlobalPos, RouletteGame> entry = games.next();
            Set<UUID> viewers = VIEWERS.getOrDefault(entry.getKey(), Set.of());
            if (entry.getValue().forgettable(viewers.size())) {
                games.remove();
                VIEWERS.remove(entry.getKey());
            }
        }
    }

    /** Cancels every unfinished round and returns each engaged stake once. */
    public static void stopping(MinecraftServer server) {
        List<RouletteGame> running = new ArrayList<>(GAMES.values());
        GAMES.clear();
        VIEWERS.clear();
        for (RouletteGame game : running) game.cancel();
    }

    /** Only for tests and for a server that has fully stopped. */
    public static void clear() {
        GAMES.clear();
        VIEWERS.clear();
    }
}
