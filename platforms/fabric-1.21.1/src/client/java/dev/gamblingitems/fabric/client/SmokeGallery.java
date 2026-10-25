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
    /** The arcade row along the back wall, two blocks wide each, played for real through their controls. */
    private static final Block[] ARCADE = {ModContent.PLINKO, ModContent.BIG_SIX, ModContent.VIDEO_POKER, ModContent.HORSE_RACE};
    private static final dev.gamblingitems.core.machines.CabinetControls.Control[] ARCADE_CONTROLS = {
            dev.gamblingitems.core.machines.CabinetControls.Control.PLAY, dev.gamblingitems.core.machines.CabinetControls.Control.PICK_1,
            dev.gamblingitems.core.machines.CabinetControls.Control.PLAY, dev.gamblingitems.core.machines.CabinetControls.Control.PICK_1};
    /** Arcade shots carry their cabinet as ARCADE_SHOT + index. */
    private static final int ARCADE_SHOT = 100;
    /** The table games in their own row, stools in front, played for real; their shots carry TABLE_SHOT + index. */
    private static final Block[] TABLE_GAMES = {ModContent.CRAPS, ModContent.BACCARAT, ModContent.KENO, ModContent.SCRATCH_CARD};
    private static final dev.gamblingitems.core.machines.CabinetControls.Control[] TABLE_CONTROLS = {
            dev.gamblingitems.core.machines.CabinetControls.Control.PICK_1, dev.gamblingitems.core.machines.CabinetControls.Control.PICK_2,
            dev.gamblingitems.core.machines.CabinetControls.Control.PLAY, dev.gamblingitems.core.machines.CabinetControls.Control.PLAY};
    private static final int TABLE_SHOT = 200, TABLE_Z = 2;
    /** Four slot machines dyed Vegas, neon, retro, and one reserved for VIPs; THEME_SHOT + index wins big on one. */
    private static final net.minecraft.world.item.DyeColor[] THEMES = {net.minecraft.world.item.DyeColor.RED,
            net.minecraft.world.item.DyeColor.CYAN, net.minecraft.world.item.DyeColor.WHITE, null};
    private static final int THEME_SHOT = 300, THEME_Z = 5;
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static BlockPos origin;

    /** A view; for a cabinet, the play it shows, started {@code ahead} ticks before the view moves in. */
    private record Shot(String name, double x, double y, double z, float yaw, float pitch, int cabinet, int ahead) {
        Shot(String name, double x, double y, double z, float yaw, float pitch) { this(name, x, y, z, yaw, pitch, -1, 0); }
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
            // Night shots show what is lit; everything else is photographed at noon.
            player.serverLevel().setDayTime(shot.name().startsWith("night") ? 18000 : 6000);
            player.connection.teleport(shot.x(), shot.y(), shot.z(), shot.yaw(), shot.pitch());
            if (shot.cabinet() >= THEME_SHOT) bigWin(player.serverLevel(), shot.cabinet() - THEME_SHOT);
            else if (shot.cabinet() >= TABLE_SHOT) {
                int index = shot.cabinet() - TABLE_SHOT;
                playAt(player, origin.offset(-14 + 2 * index, 0, TABLE_Z), TABLE_GAMES[index], TABLE_CONTROLS[index], shot.ahead());
            } else if (shot.cabinet() >= ARCADE_SHOT) playArcade(player, shot.cabinet() - ARCADE_SHOT, shot.ahead());
            else if (shot.cabinet() >= 0) play(player.serverLevel(), shot.cabinet(), shot.ahead());
        });
        // The server's teleport moves the player, but the client keeps its own look; aim it as the shot asks.
        if (phase > 1 && phase <= SETTLE) {
            minecraft.player.setYRot(shot.yaw());
            minecraft.player.setXRot(shot.pitch());
        }
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
        // The arcade: a back wall with its neon, carpet in front, the wide cabinets, the cashier, the board and the croupier.
        for (int x = -14; x <= 18; x++) {
            for (int y = 0; y <= 4; y++) level.setBlock(o.offset(x, y, -12), Blocks.DARK_PRISMARINE.defaultBlockState(), 2);
            level.setBlock(o.offset(x, 4, -11), ModContent.NEON_TUBE.defaultBlockState()
                    .setValue(dev.gamblingitems.fabric.arcade.NeonBlock.COLOR, net.minecraft.world.item.DyeColor.values()[(x + 14) % 16]), 2);
            for (int z = -10; z <= -8; z++) {
                level.setBlock(o.offset(x, 0, z), ((x + z) & 1) == 0 ? ModContent.CASINO_CARPET.defaultBlockState()
                        : ModContent.CASINO_CARPET_BLUE.defaultBlockState(), 2);
            }
        }
        for (int i = 0; i < ARCADE.length; i++) place(level, o.offset(-12 + 4 * i, 0, -11), ARCADE[i], Direction.SOUTH, player);
        place(level, o.offset(5, 0, -11), ModContent.CASHIER, Direction.SOUTH, player);
        place(level, o.offset(8, 1, -11), ModContent.LEADERBOARD, Direction.SOUTH, player);
        place(level, o.offset(12, 0, -11), ModContent.CROUPIER, Direction.SOUTH, player);
        // The croupier stands at the dealer's side, the players' side facing the room.
        place(level, o.offset(12, 0, -9), ModContent.BLACKJACK_TABLE, Direction.SOUTH, player);
        // The table games with a stool in front of each, and the dyed slot machines behind them.
        for (int i = 0; i < TABLE_GAMES.length; i++) {
            place(level, o.offset(-14 + 2 * i, 0, TABLE_Z), TABLE_GAMES[i], Direction.SOUTH, player);
            level.setBlock(o.offset(-14 + 2 * i, 0, TABLE_Z + 1), ModContent.CASINO_STOOL.defaultBlockState(), 3);
        }
        for (int i = 0; i < THEMES.length; i++) {
            BlockPos pos = o.offset(-14 + 2 * i, 0, THEME_Z);
            place(level, pos, ModContent.SLOT_MACHINE, Direction.SOUTH, player);
            if (level.getBlockEntity(pos) instanceof dev.gamblingitems.fabric.block.GameStationEntity station) {
                if (THEMES[i] != null) station.theme = 1 + THEMES[i].getId();
                else station.vip = true;
                station.setChanged();
                level.sendBlockUpdated(pos, station.getBlockState(), station.getBlockState(), 3);
            }
            level.setBlock(o.offset(-13 + 2 * i, 0, THEME_Z), ModContent.NEON_TUBE.defaultBlockState()
                    .setValue(dev.gamblingitems.fabric.arcade.NeonBlock.COLOR, net.minecraft.world.item.DyeColor.values()[(i * 5) % 16]), 2);
        }
    }

    /** Plays a cabinet for real at a place, as playArcade does. */
    private static void playAt(ServerPlayer player, BlockPos pos, Block block, dev.gamblingitems.core.machines.CabinetControls.Control control,
                               int ahead) {
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 4));
        var mode = ((dev.gamblingitems.fabric.block.SlotMachineBlock) block).mode();
        boolean played = dev.gamblingitems.fabric.block.CabinetPlay.use(player, mode, pos, control);
        if (played && player.serverLevel().getBlockEntity(pos) instanceof dev.gamblingitems.fabric.block.GameStationEntity station) {
            station.startedAt -= ahead;
            player.serverLevel().sendBlockUpdated(pos, station.getBlockState(), station.getBlockState(), 3);
        }
    }

    /** A jackpot shown on a dyed slot machine, caught while it is celebrated: lit, beacon turning, confetti falling. */
    private static void bigWin(ServerLevel level, int index) {
        BlockPos pos = origin.offset(-14 + 2 * index, 0, THEME_Z);
        if (!(level.getBlockEntity(pos) instanceof dev.gamblingitems.fabric.block.GameStationEntity station)) return;
        station.reels = "6,6,6";
        station.animationTicks = 20;
        station.multiplier = 2500;
        station.show("Steve", "", "slot_won", "", true, 200);
    }

    /** Plays an arcade cabinet for real, a diamond in hand, as a player touching its control would. */
    private static void playArcade(ServerPlayer player, int index, int ahead) {
        BlockPos pos = origin.offset(-12 + 4 * index, 0, -11);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 4));
        var mode = ((dev.gamblingitems.fabric.block.SlotMachineBlock) ARCADE[index]).mode();
        boolean played = dev.gamblingitems.fabric.block.CabinetPlay.use(player, mode, pos, ARCADE_CONTROLS[index]);
        if (played && player.serverLevel().getBlockEntity(pos) instanceof dev.gamblingitems.fabric.block.GameStationEntity station) {
            station.startedAt -= ahead;
            player.serverLevel().sendBlockUpdated(pos, station.getBlockState(), station.getBlockState(), 3);
        }
    }

    /** Starts a play on a cabinet as the server would show it, long enough to be caught midway. */
    private static void play(ServerLevel level, int index, int ahead) {
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
        station.startedAt -= ahead;
        level.sendBlockUpdated(pos, station.getBlockState(), station.getBlockState(), 3);
    }

    private static void place(ServerLevel level, BlockPos pos, Block block, Direction facing, ServerPlayer player) {
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
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
            SHOTS.add(new Shot("cabinet_" + cabinets[i], x - 6 + 2 * i, y, z - 6 + 2.6, 180, 4, i, 0));
            // Caught later: the gacha capsule opening, then every machine celebrating its win.
            SHOTS.add(new Shot("cabinet_" + cabinets[i] + "_open", x - 6 + 2 * i, y, z - 6 + 2.6, 180, 4, i, 42));
            SHOTS.add(new Shot("cabinet_" + cabinets[i] + "_won", x - 6 + 2 * i, y, z - 6 + 2.8, 180, -4, i, 60));
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
        SHOTS.add(new Shot("arcade", x + 2, y + 2.5, z - 2, 180, 14));
        String[] arcade = {"plinko", "big_six", "video_poker", "horse_race"};
        for (int i = 0; i < arcade.length; i++) {
            double centre = x - 12 + 4 * i + .5;
            SHOTS.add(new Shot("arcade_" + arcade[i], centre, y, z - 11 + 4, 180, 2, ARCADE_SHOT + i, 0));
            SHOTS.add(new Shot("arcade_" + arcade[i] + "_later", centre, y, z - 11 + 4, 180, 2, ARCADE_SHOT + i, 90));
        }
        SHOTS.add(new Shot("arcade_decor", x + 8.5, y + .8, z - 6, 180, 6));
        SHOTS.add(new Shot("arcade_plinko_sign", x - 11, y + 1.6, z - 11 + 2.2, 180, -22));
        // Two falls caught early and late; a play takes 100 ticks, so other shots stand between them.
        SHOTS.add(new Shot("arcade_plinko_fall_early", x - 11, y + .9, z - 11 + 2.4, 180, -12, ARCADE_SHOT, -12));
        SHOTS.add(new Shot("arcade_cashier", x + 5, y + .9, z - 11 + 1.7, 180, 38));
        SHOTS.add(new Shot("arcade_croupier", x + 11.6, y + .6, z - 11 + 2.2, 200, 8));
        SHOTS.add(new Shot("arcade_plinko_fall_late", x - 11, y + .9, z - 11 + 2.4, 180, -12, ARCADE_SHOT, 32));
        SHOTS.add(new Shot("night_arcade", x + 2, y + 2.5, z - 2, 180, 14));
        SHOTS.add(new Shot("arcade_dealer", x + 12, y + 1.2, z - 4.6, 180, 24));
        for (int i = 0; i < tables.length; i++) {
            SHOTS.add(new Shot("table_close_" + tables[i], x + 13.6, y + 1.2, z - 6 + 5 * i + 1.4, -120, 34));
        }
        SHOTS.add(new Shot("floor_tables", x - 10.5, y + 2.2, z + TABLE_Z + 6, 180, 18));
        String[] games = {"craps", "baccarat", "keno", "scratch_card"};
        for (int i = 0; i < games.length; i++) {
            double centre = x - 14 + 2 * i;
            SHOTS.add(new Shot("floor_" + games[i] + "_mid", centre, y, z + TABLE_Z + 2.6, 180, 6, TABLE_SHOT + i, 20));
            SHOTS.add(new Shot("floor_" + games[i] + "_done", centre, y, z + TABLE_Z + 2.6, 180, 6, TABLE_SHOT + i, 100));
        }
        SHOTS.add(new Shot("floor_dice_close", x - 14, y + .3, z + TABLE_Z + 1.6, 180, 42, TABLE_SHOT, 30));
        SHOTS.add(new Shot("floor_stools", x - 11, y + 1.4, z + TABLE_Z + 3.4, 210, 30));
        SHOTS.add(new Shot("floor_themes", x - 10.5, y + 1.4, z + THEME_Z + 3.8, 180, 8));
        SHOTS.add(new Shot("floor_theme_bigwin", x - 14, y + 1.2, z + THEME_Z + 2.6, 180, -24, THEME_SHOT, 0));
        SHOTS.add(new Shot("floor_vip", x - 8, y + 1.5, z + THEME_Z + 3, 180, -18));
        SHOTS.add(new Shot("night_floor_themes", x - 10.5, y + 1.4, z + THEME_Z + 3.8, 180, 8));
        // -Dgamblingitems.clientsmoke.gallery.only=floor keeps only the shots whose names start with it.
        String only = System.getProperty("gamblingitems.clientsmoke.gallery.only", "");
        if (!only.isEmpty()) SHOTS.removeIf(shot -> !shot.name().startsWith(only) && !shot.name().startsWith("night_" + only));
    }
}
