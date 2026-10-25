package dev.gamblingitems.fabric.client;

import dev.gamblingitems.fabric.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Visual check of the furniture: builds a small casino floor with every station, table and
 * cabinet, then photographs it from a few places, from afar and up close. Only active with
 * -Dgamblingitems.clientsmoke=true and -Dgamblingitems.clientsmoke.gallery=true.
 */
final class SmokeGallery {
    static final boolean ENABLED = Boolean.getBoolean("gamblingitems.clientsmoke.gallery");
    private static final int START = 60, STEP = 50, SETTLE = 40;
    private static final Block[] CABINETS = {ModContent.SLOT_MACHINE, ModContent.LUCKY_WHEEL, ModContent.COIN_PUSHER,
            ModContent.PRIZE_CLAW, ModContent.PACHINKO, ModContent.CAPSULE_GACHA, ModContent.HIGH_STAKES};
    private static final Block[] STATIONS = {ModContent.UPGRADE_STATION, ModContent.TRADE_UP_STATION,
            ModContent.CASE_STATION, ModContent.CRASH_STATION, ModContent.ROULETTE_STATION,
            ModContent.BLACKJACK_STATION, ModContent.BATTLE_STATION, ModContent.BINGO_STATION};
    private static final Block[] TABLES = {ModContent.BLACKJACK_TABLE, ModContent.ROULETTE_TABLE, ModContent.BINGO_TABLE};
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static BlockPos origin;

    private record Shot(String name, double x, double y, double z, float yaw, float pitch, int cabinet) {
        Shot(String name, double x, double y, double z, float yaw, float pitch) { this(name, x, y, z, yaw, pitch, -1); }
    }
    /** For each cabinet, a play caught halfway: what it shows ("outcome,detail,choice") and what it pays. */
    private static final String[] OUTCOMES = {"7,7,7", "11,500,0", "6,300,0", "2,1,2", "4,15,0", "4,500,0", "5,7,5"};

    private SmokeGallery() {}

    static void tick(Minecraft minecraft, int ticks) {
        var server = minecraft.getSingleplayerServer();
        var id = minecraft.player.getUUID();
        if (ticks == 20) server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) return;
            origin = player.blockPosition();
            build(player.serverLevel(), origin, player);
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            plan(origin);
        });
        if (ticks < START || origin == null) return;
        minecraft.options.hideGui = true;
        // A screenshot of the pause menu shows nothing of the room.
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.screen instanceof net.minecraft.client.gui.screens.PauseScreen) minecraft.setScreen(null);
        int step = (ticks - START) / STEP, phase = (ticks - START) % STEP;
        if (step >= SHOTS.size()) {
            minecraft.stop();
            return;
        }
        Shot shot = SHOTS.get(step);
        if (phase == 0) server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) return;
            player.connection.teleport(shot.x(), shot.y(), shot.z(), shot.yaw(), shot.pitch());
            if (shot.cabinet() >= 0) play(player.serverLevel(), shot.cabinet());
        });
        if (phase == SETTLE) ClientSmoke.shot(minecraft, "gamblingitems-gallery-" + shot.name() + ".png");
    }

    private static void build(ServerLevel level, BlockPos o, ServerPlayer player) {
        level.setDayTime(6000);
        level.setWeatherParameters(12000, 0, false, false);
        BlockState floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        for (int x = -24; x <= 24; x++) {
            for (int z = -12; z <= 18; z++) {
                level.setBlock(o.offset(x, -1, z), floor, 2);
                for (int y = 0; y <= 7; y++) level.setBlock(o.offset(x, y, z), air, 2);
            }
        }
        for (int i = 0; i < CABINETS.length; i++) place(level, o.offset(-6 + 2 * i, 0, -6), CABINETS[i], Direction.SOUTH, player);
        // Stations hang on a wall.
        for (int x = -16; x <= 16; x++) {
            for (int y = 0; y <= 3; y++) level.setBlock(o.offset(x, y, 11), Blocks.DARK_PRISMARINE.defaultBlockState(), 2);
        }
        for (int i = 0; i < STATIONS.length; i++) place(level, o.offset(-14 + 4 * i, 0, 10), STATIONS[i], Direction.NORTH, player);
        for (int i = 0; i < TABLES.length; i++) place(level, o.offset(16, 0, -6 + 5 * i), TABLES[i], Direction.WEST, player);
    }

    /** Starts a play on a cabinet as the server would show it, long enough to be caught midway. */
    private static void play(ServerLevel level, int index) {
        BlockPos pos = origin.offset(-6 + 2 * index, 0, -6);
        if (!(level.getBlockEntity(pos) instanceof dev.gamblingitems.fabric.block.GameStationEntity station)) return;
        station.reels = OUTCOMES[index];
        station.animationTicks = 90;
        station.multiplier = 1500;
        if (index > 0) {
            var mode = station.mode();
            var table = dev.gamblingitems.fabric.config.ModConfig.machine(mode).settings().table();
            station.wheelColours = dev.gamblingitems.fabric.machines.MachineMenu.encode(table);
            String[] parts = OUTCOMES[index].split(",");
            int at = Integer.parseInt(parts[0]);
            station.multiplier = table.multipliers()[Math.min(at, table.multipliers().length - 1)];
        }
        station.show("Steve", "", "machine_result_won", "", true, 150);
    }

    private static void place(ServerLevel level, BlockPos pos, Block block, Direction facing, ServerPlayer player) {
        BlockState state = block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        level.setBlock(pos, state, 3);
        block.setPlacedBy(level, pos, state, player, ItemStack.EMPTY);
    }

    private static void plan(BlockPos o) {
        double x = o.getX() + .5, y = o.getY(), z = o.getZ() + .5;
        SHOTS.clear();
        SHOTS.add(new Shot("cabinets", x, y, z + 1, 180, 6));
        SHOTS.add(new Shot("cabinets_angle", x + 6, y + 1.5, z - 1, 135, 14));
        String[] cabinets = {"slot_machine", "lucky_wheel", "coin_pusher", "prize_claw", "pachinko", "capsule_gacha", "high_stakes"};
        for (int i = 0; i < cabinets.length; i++) {
            SHOTS.add(new Shot("cabinet_" + cabinets[i], x - 6 + 2 * i, y, z - 6 + 2.6, 180, 4, i));
            SHOTS.add(new Shot("cabinet_" + cabinets[i] + "_side", x - 6 + 2 * i + 1.6, y + .2, z - 6 + 1.9, 140, 10));
        }
        SHOTS.add(new Shot("stations", x, y + .5, z, 0, 0));
        String[] stations = {"upgrade", "trade_up", "case", "crash", "roulette", "blackjack", "battle", "bingo"};
        for (int i = 0; i < stations.length; i++) {
            SHOTS.add(new Shot("station_" + stations[i], x - 14 + 4 * i, y, z + 10 - 4, 0, -6));
        }
        SHOTS.add(new Shot("tables", x + 9, y + 1.5, z + 3, -90, 22));
        String[] tables = {"blackjack", "roulette", "bingo"};
        for (int i = 0; i < tables.length; i++) {
            SHOTS.add(new Shot("table_" + tables[i], x + 13, y + .5, z - 6 + 5 * i, -90, 35));
        }
    }
}
