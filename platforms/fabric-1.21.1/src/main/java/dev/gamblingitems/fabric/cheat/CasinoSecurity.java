package dev.gamblingitems.fabric.cheat;

import dev.gamblingitems.core.cheat.CheatRules;
import dev.gamblingitems.core.cheat.CheatRules.Tool;
import dev.gamblingitems.fabric.Compat;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.vault.CasinoLedger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The security of the casino. Every trick goes through {@link #attempt}: the server draws whether it is
 * spotted, warms the suspicion of a player who got away with it, and punishes one who did not.
 *
 * <p>A caught cheater loses the tool, the stake of the round and the right to play for a while. The
 * whole room is told. Security cameras placed around the tables make every trick riskier.
 */
public final class CasinoSecurity {
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Players near a caught cheater hear about it. */
    private static final double WITNESS_RANGE = 48;
    /** How often the cameras around a player are counted again for the chance shown on screen. */
    private static final int CHANCE_REFRESH_TICKS = 20;
    private static SecuritySettings settings = SecuritySettings.defaults();
    private static IntUnaryOperator draw = RANDOM::nextInt;
    private static LongSupplier clock = System::currentTimeMillis;
    private static final Set<UUID> TO_CLOSE = new HashSet<>();
    private record Cached(Tool tool, long tick, int chance) {}
    private static final Map<UUID, Cached> CHANCES = new HashMap<>();

    public enum Result {
        /** The player does not carry the tool, or the trick cannot be used right now. */
        REFUSED,
        /** The trick worked and nobody noticed. */
        UNNOTICED,
        /** The trick was spotted: the round is lost and the player is banned. */
        CAUGHT
    }

    private CasinoSecurity() {}

    public static void configure(SecuritySettings loaded) { settings = loaded; }
    public static SecuritySettings settings() { return settings; }

    /** Tests replace the dice and the clock to play a scene they can check. */
    public static void testing(IntUnaryOperator draws, LongSupplier time) {
        draw = draws == null ? RANDOM::nextInt : draws;
        clock = time == null ? System::currentTimeMillis : time;
        CHANCES.clear();
    }

    public static long now() { return clock.getAsLong(); }

    /** The first stack of this tool the player carries, offhand included; empty when there is none. */
    public static ItemStack tool(Player player, Tool tool) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModContent.cheatTool(tool))) return stack;
        }
        return ItemStack.EMPTY;
    }

    public static boolean carries(Player player, Tool tool) { return settings.enabled() && !tool(player, tool).isEmpty(); }

    /** The suspicion weighing on a player right now, cooled down since it was last written. */
    public static int heat(MinecraftServer server, UUID player) {
        CasinoLedger.Record record = CasinoLedger.get(server).find(player);
        if (record == null) return 0;
        return CheatRules.cooled(settings.rules(), record.heat, now() - record.heatAt);
    }

    /** Security cameras within sight of a player, wherever they play from. */
    public static int cameras(ServerPlayer player) {
        int radius = settings.cameraRadius();
        if (radius == 0) return 0;
        BlockPos centre = player.blockPosition();
        int seen = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++)
            for (int y = -radius; y <= radius; y++)
                for (int z = -radius; z <= radius; z++) {
                    pos.set(centre.getX() + x, centre.getY() + y, centre.getZ() + z);
                    if (player.level().isLoaded(pos) && player.level().getBlockState(pos).is(ModContent.SECURITY_CAMERA)) seen++;
                }
        return seen;
    }

    /** How likely this trick is to be spotted right now, in basis points. */
    public static int chance(ServerPlayer player, Tool tool) {
        return CheatRules.detectionChance(settings.rules(), settings.baseChance(tool),
                heat(player.server, player.getUUID()), cameras(player));
    }

    /**
     * The chance a game window shows beside its trick, or -1 when the player carries no such tool.
     * Cameras are counted again once a second rather than on every tick.
     */
    public static int shownChance(ServerPlayer player, Tool tool) {
        if (!carries(player, tool)) return -1;
        long tick = player.server.getTickCount();
        Cached cached = CHANCES.get(player.getUUID());
        if (cached != null && cached.tool == tool && tick - cached.tick < CHANCE_REFRESH_TICKS) return cached.chance;
        int chance = chance(player, tool);
        CHANCES.put(player.getUUID(), new Cached(tool, tick, chance));
        return chance;
    }

    /**
     * Uses one trick. The server draws whether it is seen; an unnoticed trick wears the tool and
     * warms the player's suspicion, a spotted one confiscates the tool and bans the player. The caller
     * makes the trick work, or forfeits the round, according to the answer.
     */
    public static Result attempt(ServerPlayer player, Tool tool) {
        if (!settings.enabled() || banned(player)) return Result.REFUSED;
        ItemStack stack = tool(player, tool);
        if (stack.isEmpty()) return Result.REFUSED;
        int chance = chance(player, tool);
        CHANCES.remove(player.getUUID());
        CasinoLedger ledger = CasinoLedger.get(player.server);
        CasinoLedger.Record record = ledger.record(player.getUUID());
        int heat = heat(player.server, player.getUUID());
        record.tricks++;
        if (CheatRules.spotted(chance, draw.applyAsInt(CheatRules.CERTAIN))) {
            stack.shrink(1);
            punish(player.server, player.getUUID(), player.getGameProfile().getName(),
                    Component.translatable("gui.gamblingitems.security.caught", player.getDisplayName(),
                            new ItemStack(ModContent.cheatTool(tool)).getHoverName()));
            return Result.CAUGHT;
        }
        record.heat = CheatRules.warmed(heat, settings.heat(tool));
        record.heatAt = now();
        wear(stack);
        Compat.message(player, Component.translatable("gui.gamblingitems.security.unnoticed", record.heat), true);
        return Result.UNNOTICED;
    }

    /**
     * The gaming commission looks at a bet a player placed against their own success. Caught, the
     * ticket is seized and its buyer banned; unnoticed, it pays but leaves suspicion behind.
     *
     * @return true when the commission caught it
     */
    public static boolean investigate(MinecraftServer server, UUID suspect, String name, int baseChance, int addedHeat) {
        if (!settings.enabled()) return false;
        int heat = heat(server, suspect);
        int chance = CheatRules.detectionChance(settings.rules(), baseChance, heat, 0);
        CasinoLedger.Record record = CasinoLedger.get(server).record(suspect);
        if (CheatRules.spotted(chance, draw.applyAsInt(CheatRules.CERTAIN))) {
            punish(server, suspect, name, Component.translatable("gui.gamblingitems.security.insider", name));
            return true;
        }
        record.heat = CheatRules.warmed(heat, addedHeat);
        record.heatAt = now();
        return false;
    }

    private static void punish(MinecraftServer server, UUID suspect, String name, Component announcement) {
        CasinoLedger.Record record = CasinoLedger.get(server).record(suspect);
        record.caught++;
        record.heat = settings.rules().caughtHeat();
        record.heatAt = now();
        record.bannedUntil = now() + settings.rules().banMillis();
        CHANCES.remove(suspect);
        ServerPlayer culprit = server.getPlayerList().getPlayer(suspect);
        if (culprit == null) {
            server.getPlayerList().broadcastSystemMessage(announcement, false);
            return;
        }
        for (ServerPlayer witness : server.getPlayerList().getPlayers()) {
            if (witness == culprit || (witness.level() == culprit.level()
                    && witness.distanceToSqr(culprit) <= WITNESS_RANGE * WITNESS_RANGE)) {
                Compat.message(witness, announcement, false);
            }
        }
        culprit.level().playSound(null, culprit.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1, 0.6f);
        Compat.message(culprit, Component.translatable("gui.gamblingitems.security.banned", banSeconds(server, suspect)), true);
        // The window is closed on the next tick, once the action that was caught has finished.
        TO_CLOSE.add(suspect);
    }

    private static void wear(ItemStack stack) {
        if (stack.getMaxDamage() <= 0) return;
        stack.setDamageValue(stack.getDamageValue() + 1);
        if (stack.getDamageValue() >= stack.getMaxDamage()) stack.shrink(1);
    }

    public static boolean banned(ServerPlayer player) { return banSeconds(player.server, player.getUUID()) > 0; }

    /** Seconds left of a ban, zero when the player may play. */
    public static long banSeconds(MinecraftServer server, UUID player) {
        CasinoLedger.Record record = CasinoLedger.get(server).find(player);
        if (record == null) return 0;
        return Math.max(0, (record.bannedUntil - now() + 999) / 1000);
    }

    /** True, with a message, when a banned player tries to open a game. */
    public static boolean refuses(ServerPlayer player) {
        long left = banSeconds(player.server, player.getUUID());
        if (left <= 0) return false;
        Compat.message(player, Component.translatable("gui.gamblingitems.security.still_banned", left), true);
        return true;
    }

    /** Clears a player's file: no ban, no suspicion. */
    public static void pardon(MinecraftServer server, UUID player) {
        CasinoLedger.Record record = CasinoLedger.get(server).record(player);
        record.heat = 0;
        record.heatAt = now();
        record.bannedUntil = 0;
        CHANCES.remove(player);
    }

    public static void tick(MinecraftServer server) {
        if (TO_CLOSE.isEmpty()) return;
        for (UUID id : TO_CLOSE) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && player.containerMenu != player.inventoryMenu) player.closeContainer();
            dev.gamblingitems.fabric.block.StationInteractions.close(id);
        }
        TO_CLOSE.clear();
    }

    public static void clear() {
        TO_CLOSE.clear();
        CHANCES.clear();
    }
}
