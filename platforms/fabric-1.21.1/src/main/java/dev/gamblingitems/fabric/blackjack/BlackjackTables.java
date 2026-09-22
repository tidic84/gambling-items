package dev.gamblingitems.fabric.blackjack;

import dev.gamblingitems.fabric.config.ModConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * The blackjack hands of this server. A hand belongs to its player, not to a screen or a block:
 * closing the terminal, walking away or reconnecting finds the same hand waiting.
 *
 * <p>A table is only forgotten once its player has left. Dropping the table of a connected player
 * would leave their open screen bound to a table nobody ticks any more, which looks exactly like a
 * game that refuses to start again.
 */
public final class BlackjackTables {
    private static final Map<UUID, BlackjackTable> TABLES = new HashMap<>();

    private BlackjackTables() {}

    public static BlackjackTable of(MinecraftServer server, UUID player) {
        return TABLES.computeIfAbsent(player, key -> new BlackjackTable(server, key, ModConfig.blackjack()));
    }

    public static BlackjackTable existing(UUID player) { return TABLES.get(player); }

    public static void tick(MinecraftServer server) {
        for (BlackjackTable table : new ArrayList<>(TABLES.values())) table.tick();
        Iterator<Map.Entry<UUID, BlackjackTable>> tables = TABLES.entrySet().iterator();
        while (tables.hasNext()) {
            Map.Entry<UUID, BlackjackTable> entry = tables.next();
            boolean online = server.getPlayerList().getPlayer(entry.getKey()) != null;
            if (!online && entry.getValue().forgettable()) {
                // The player is gone and owes nothing: the table can be cleared away.
                tables.remove();
            }
        }
    }

    /** Cancels every unfinished hand and gives each engaged stake back once. */
    public static void stopping(MinecraftServer server) {
        List<BlackjackTable> running = new ArrayList<>(TABLES.values());
        TABLES.clear();
        for (BlackjackTable table : running) table.cancel();
    }

    /** Only for tests and for a server that has fully stopped. */
    public static void clear() { TABLES.clear(); }
}
