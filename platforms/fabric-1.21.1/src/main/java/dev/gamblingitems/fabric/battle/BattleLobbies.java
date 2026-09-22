package dev.gamblingitems.fabric.battle;

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
 * The case battle lobbies of this server, held outside the world like the other shared games.
 * Breaking the station of a running battle does not stop it, and does not release any entry.
 */
public final class BattleLobbies {
    public static final double REACH = 64;
    private static final Map<GlobalPos, BattleLobby> LOBBIES = new HashMap<>();
    private static final Map<GlobalPos, Set<UUID>> VIEWERS = new HashMap<>();

    private BattleLobbies() {}

    private static GlobalPos key(BattleLobby lobby) {
        return GlobalPos.of(lobby.level().dimension(), lobby.station());
    }

    public static BattleLobby host(ServerLevel level, BlockPos pos) {
        return LOBBIES.computeIfAbsent(GlobalPos.of(level.dimension(), pos),
                key -> new BattleLobby(level, pos.immutable(), ModConfig.battle()));
    }

    /** The lobby a portable terminal may join: an existing one, announced by a nearby station. */
    public static BattleLobby nearest(ServerPlayer player) {
        BattleLobby best = null;
        double closest = REACH * REACH;
        for (BattleLobby lobby : LOBBIES.values()) {
            if (lobby.level() != player.level()) continue;
            BlockPos pos = lobby.station();
            double distance = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distance <= closest) {
                closest = distance;
                best = lobby;
            }
        }
        return best;
    }

    public static void join(BattleLobby lobby, UUID player) {
        VIEWERS.computeIfAbsent(key(lobby), key -> new HashSet<>()).add(player);
    }

    public static void leave(BattleLobby lobby, UUID player) {
        Set<UUID> viewers = VIEWERS.get(key(lobby));
        if (viewers != null) viewers.remove(player);
    }

    public static void tick(MinecraftServer server) {
        for (BattleLobby lobby : new ArrayList<>(LOBBIES.values())) lobby.tick();
        Iterator<Map.Entry<GlobalPos, BattleLobby>> lobbies = LOBBIES.entrySet().iterator();
        while (lobbies.hasNext()) {
            Map.Entry<GlobalPos, BattleLobby> entry = lobbies.next();
            Set<UUID> viewers = VIEWERS.getOrDefault(entry.getKey(), Set.of());
            if (entry.getValue().forgettable(viewers.size())) {
                lobbies.remove();
                VIEWERS.remove(entry.getKey());
            }
        }
    }

    /** Cancels every lobby and battle, returning each entry once. */
    public static void stopping(MinecraftServer server) {
        List<BattleLobby> running = new ArrayList<>(LOBBIES.values());
        LOBBIES.clear();
        VIEWERS.clear();
        for (BattleLobby lobby : running) lobby.cancel();
    }

    /** Only for tests and for a server that has fully stopped. */
    public static void clear() {
        LOBBIES.clear();
        VIEWERS.clear();
    }
}
