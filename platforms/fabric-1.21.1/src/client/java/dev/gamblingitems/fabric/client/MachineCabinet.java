package dev.gamblingitems.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.machines.MachineTable;
import dev.gamblingitems.fabric.block.GameStationEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import static dev.gamblingitems.fabric.client.SlotCabinet.*;

/**
 * The live parts of the prize machines, in the model and canvas spaces of {@link SlotCabinet}:
 * the wheel and its bulbs, the coins and the plate of the pusher, the claw and its prizes, the
 * pachinko board, the gacha globe and crank, the high stakes screen, and each machine's sign,
 * status and odds.
 *
 * <p>Everything moving is rebuilt from what the block carries: the outcome, when it started and the
 * table of prizes, so every player around the machine sees the same play.
 */
final class MachineCabinet {
    private static final int GLASS = 0xffa9d4e2, COIN = 0xffd9b25a, COIN_DARK = 0xffa98734;

    private MachineCabinet() {}

    static void render(GameStationEntity station, PoseStack pose, WorldCanvas buffers, Font font, double now,
                       double progress, boolean playing, float light) {
        GameMode mode = station.mode();
        boolean rolling = playing && progress < 1;
        boolean won = !station.idle() && !rolling && station.highlight;
        int[][] table = table(station);
        int[] outcome = outcome(station);
        boolean shown = outcome[0] >= 0 && !station.idle();
        double shownProgress = shown ? progress : 0;
        String name = Component.translatable("gui.gamblingitems.mode." + mode.id()).getString();
        String rollingKey = "machine_playing." + mode.id();
        switch (mode) {
            case LUCKY_WHEEL -> {
                // In front of the printed sectors the block model carries for the inventory.
                surface(pose, 1.1f);
                wheel(pose, buffers, font, table, outcome, shown, shownProgress);
                pose.popPose();
                surface(pose, 1.6f);
                bulbs(pose, buffers, now, rolling, won);
                pose.popPose();
                surface(pose, 4.8f);
                sign(pose, buffers, font, name, -53.6f, -249.6f, 53.6f, -223.2f, rolling, won, now);
                pose.popPose();
                surface(pose, 1.4f);
                status(pose, buffers, font, station, rolling, rollingKey, -36, -37.6f, 36, -21.6f);
                odds(pose, buffers, font, mode, table, outcome[2], -44, -8.8f, 44, 32.8f);
                pose.popPose();
            }
            case COIN_PUSHER -> {
                pusher(pose, buffers, station, shown, shownProgress, now, light);
                surface(pose, -0.2f);
                twoLineSign(pose, buffers, font, station, name, rolling, won, rollingKey, -59.2f, -211.2f, 59.2f, -180.8f, now);
                pose.popPose();
                surface(pose, 1.6f);
                odds(pose, buffers, font, mode, table, outcome[2], -44, -32.8f, 44, -0.8f);
                pose.popPose();
            }
            case PRIZE_CLAW -> {
                claw(pose, buffers, table, outcome, shown, shownProgress, light);
                surface(pose, -0.2f);
                sign(pose, buffers, font, name, -57.6f, -233.6f, 57.6f, -206.4f, rolling, won, now);
                pose.popPose();
                surface(pose, 0.9f);
                status(pose, buffers, font, station, rolling, rollingKey, 4, -18.4f, 48, 2);
                odds(pose, buffers, font, mode, table, outcome[2], 4, 4, 48, 40);
                pose.popPose();
            }
            case PACHINKO -> {
                tray(pose, buffers, light);
                handle(pose, buffers, rolling);
                surface(pose, 3.9f);
                pachinko(pose, buffers, font, station, table, outcome, shown, shownProgress, rolling, rollingKey);
                pose.popPose();
                surface(pose, 3.2f);
                for (int index = 0; index < 9; index++) {
                    float y = -196 + index * 19;
                    quad(pose, buffers, -54.4f, y - 1.6f, -51.2f, y + 1.6f, lamp(index, now, rolling, won), 0);
                    quad(pose, buffers, 51.2f, y - 1.6f, 54.4f, y + 1.6f, lamp(index + 1, now, rolling, won), 0);
                }
                pose.popPose();
                surface(pose, 4.8f);
                sign(pose, buffers, font, name, -52.8f, -252.8f, 52.8f, -235.2f, rolling, won, now);
                pose.popPose();
            }
            case CAPSULE_GACHA -> {
                gacha(pose, buffers, font, station, table, shown, shownProgress, rolling, playing, now, light);
                surface(pose, 5.8f);
                twoLineSign(pose, buffers, font, station, name, rolling, won, rollingKey, -32, -216, 32, -187.2f, now);
                pose.popPose();
            }
            case PLINKO, BIG_SIX, VIDEO_POKER, HORSE_RACE, CRAPS, BACCARAT, KENO, SCRATCH_CARD -> ArcadeCabinets.render(station, pose, buffers, font, table, outcome,
                    shown, shownProgress, rolling, won, now, light);
            default -> {
                // The screen leans back with its block model, about the same hinge.
                pose.pushPose();
                pose.translate(8, 13.5, 6.5);
                pose.mulPose(Axis.XP.rotationDegrees(22.5f));
                pose.translate(-8, -13.5, -6.5);
                surface(pose, 4.8f);
                terminal(pose, buffers, font, station, table, outcome, shown, shownProgress, rolling, won, now, name, rollingKey);
                pose.popPose();
                pose.popPose();
            }
        }
    }

    /** The prizes the server published on the block, or a neutral board until it has. */
    private static int[][] table(GameStationEntity station) {
        if (!station.wheelColours.isEmpty() && station.wheelColours.indexOf(':') > 0) {
            String[] entries = station.wheelColours.split(",");
            int[] multipliers = new int[entries.length], weights = new int[entries.length];
            try {
                for (int index = 0; index < entries.length; index++) {
                    String[] pair = entries[index].split(":");
                    multipliers[index] = Integer.parseInt(pair[0]);
                    weights[index] = Integer.parseInt(pair[1]);
                }
                return new int[][] {multipliers, weights};
            } catch (RuntimeException ignored) {
                // A cabinet never breaks on a table it cannot read; it shows the neutral board instead.
            }
        }
        int count = switch (station.mode()) {
            case PACHINKO -> MachineTable.PACHINKO_POCKETS;
            case PLINKO -> MachineTable.PLINKO_POCKETS;
            case BIG_SIX -> 7;
            case HORSE_RACE, KENO -> 6;
            case CRAPS -> 2;
            case BACCARAT -> 3;
            case VIDEO_POKER -> dev.gamblingitems.core.machines.VideoPokerRules.PAYTABLE.length;
            default -> 8;
        };
        int[] multipliers = new int[count], weights = new int[count];
        java.util.Arrays.fill(weights, 1);
        if (station.mode() == GameMode.PACHINKO) weights = new int[] {1, 8, 28, 56, 70, 56, 28, 8, 1};
        if (station.mode() == GameMode.VIDEO_POKER) multipliers = dev.gamblingitems.core.machines.VideoPokerRules.PAYTABLE.clone();
        if (station.mode() == GameMode.HIGH_STAKES) java.util.Arrays.fill(multipliers, 200);
        return new int[][] {multipliers, weights};
    }

    /** What the cabinet was told to show: the outcome, its detail and the choice it was played with. */
    static int[] outcome(GameStationEntity station) {
        int[] values = {-1, 0, 0};
        if (station.reels.isEmpty()) return values;
        String[] parts = station.reels.split(",");
        for (int index = 0; index < values.length && index < parts.length; index++) {
            try {
                values[index] = Integer.parseInt(parts[index]);
            } catch (NumberFormatException ignored) {
                return new int[] {-1, 0, 0};
            }
        }
        return values;
    }

    /** A sign with the machine's name, and under it what the machine is doing. */
    private static void twoLineSign(PoseStack pose, WorldCanvas buffers, Font font, GameStationEntity station, String name,
                                    boolean rolling, boolean won, String rollingKey, float x1, float y1, float x2, float y2,
                                    double now) {
        float split = y1 + (y2 - y1) * 0.58f;
        sign(pose, buffers, font, name, x1, y1, x2, split, rolling, won, now);
        status(pose, buffers, font, station, rolling, rollingKey, x1, split, x2, y2);
    }

    /** The best prizes with their exact chance, on a dark panel; the claw and terminal show the chosen one first. */
    static void odds(PoseStack pose, WorldCanvas buffers, Font font, GameMode mode, int[][] table, int choice,
                             float x1, float y1, float x2, float y2) {
        quad(pose, buffers, x1, y1, x2, y2, 0xff0b111a, 0);
        if (!near) {
            // From across the room the panel only calls players over with the best prize.
            int top = 0;
            for (int multiplier : table[0]) top = Math.max(top, multiplier);
            if (top > 0) {
                float middle = (y1 + y2) / 2, height = y2 - y1;
                label(pose, buffers, font, WorldCanvas.translate("gui.gamblingitems.machine_up_to"), (x1 + x2) / 2,
                        middle - height * 0.2f, SILVER, Math.min(0.9f, height / 40), x2 - x1 - 6, 0.2f);
                label(pose, buffers, font, GameScreens.multiplier(top), (x1 + x2) / 2, middle + height * 0.14f,
                        GOLD, Math.min(2.4f, height / 16), x2 - x1 - 6, 0.2f);
            }
            return;
        }
        List<String[]> lines = best(mode, table, choice);
        float row = (y2 - y1) / 4;
        float scale = Math.min(0.6f, row / 11);
        label(pose, buffers, font, WorldCanvas.translate("gui.gamblingitems.machine_odds"),
                (x1 + x2) / 2, y1 + row * 0.55f, GOLD, scale, x2 - x1 - 6, 0.2f);
        for (int line = 0; line < lines.size(); line++) {
            float y = y1 + row * (line + 1.5f);
            int multiplier = Integer.parseInt(lines.get(line)[0]);
            float chip = Math.min(3, row / 4);
            quad(pose, buffers, x1 + 4, y - chip, x1 + 4 + chip * 2, y + chip, MachineMotion.tier(multiplier), 0.2f);
            float column = (x2 - x1 - 14) / 2;
            label(pose, buffers, font, GameScreens.multiplier(multiplier), x1 + 12 + column / 2, y, TEXT, scale, column, 0.2f);
            label(pose, buffers, font, lines.get(line)[1], x2 - 4 - column / 2, y, SILVER, scale, column, 0.2f);
        }
    }

    /** Up to three prizes, best first, each as {multiplier, chance}. */
    private static List<String[]> best(GameMode mode, int[][] table, int choice) {
        int[] multipliers = table[0], weights = table[1];
        List<String[]> lines = new ArrayList<>();
        if (mode == GameMode.HIGH_STAKES || mode == GameMode.PRIZE_CLAW) {
            int index = Math.max(0, Math.min(multipliers.length - 1, choice));
            for (int step = 0; step < multipliers.length && lines.size() < 3; step++) {
                int at = (index + step) % multipliers.length;
                BigDecimal chance = mode == GameMode.PRIZE_CLAW ? BigDecimal.valueOf(weights[at], 2)
                        : BigDecimal.valueOf(10_000).divide(BigDecimal.valueOf(Math.max(1, multipliers[at])), 2, RoundingMode.HALF_UP);
                lines.add(new String[] {String.valueOf(multipliers[at]), chance.stripTrailingZeros().toPlainString() + "%"});
            }
            return lines;
        }
        long total = 0;
        for (int weight : weights) total += weight;
        boolean[] used = new boolean[multipliers.length];
        while (lines.size() < 3) {
            int top = -1;
            for (int index = 0; index < multipliers.length; index++) {
                if (used[index] || multipliers[index] == 0) continue;
                if (top < 0 || multipliers[index] > multipliers[top]) top = index;
            }
            if (top < 0) break;
            long same = 0;
            for (int index = 0; index < multipliers.length; index++) {
                if (multipliers[index] == multipliers[top]) {
                    same += weights[index];
                    used[index] = true;
                }
            }
            BigDecimal chance = total == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(same * 100).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            lines.add(new String[] {String.valueOf(multipliers[top]), chance.stripTrailingZeros().toPlainString() + "%"});
        }
        return lines;
    }

    // --- Lucky wheel ----------------------------------------------------------------------------

    private static final float WHEEL_Y = -140, WHEEL_RADIUS = 64;

    private static void wheel(PoseStack pose, WorldCanvas buffers, Font font, int[][] table, int[] outcome, boolean shown,
                              double progress) {
        int[] multipliers = table[0], weights = table[1];
        double turn = shown ? MachineMotion.wheelTurn(weights, Math.min(outcome[0], weights.length - 1), outcome[1] % 1000, progress) : 0;
        boolean landed = shown && progress >= 1;
        // How fast the wheel turns, from 0 at rest to 1 at full speed: past half speed it blurs and its prizes smear.
        double blur = 0;
        if (shown && progress < 1 && !ClientOptions.reducedMotion()) {
            double ahead = MachineMotion.wheelTurn(weights, Math.min(outcome[0], weights.length - 1), outcome[1] % 1000,
                    Math.min(1, progress + 0.01));
            blur = Math.min(1, Math.abs(ahead - turn) / 0.01 / 9);
        }
        sector(pose, buffers, 0, WHEEL_Y, 0, WHEEL_RADIUS + 3, 0, 2 * Math.PI, DARK, 0);
        // How close the nearest peg is to the flapper at the top, in radians.
        double nearestPeg = Math.PI;
        for (int sector = 0; sector < weights.length; sector++) {
            if (weights[sector] == 0) continue;
            double from = (MachineMotion.sectorStart(weights, sector) + turn) * 2 * Math.PI - Math.PI / 2;
            double width = MachineMotion.sectorWidth(weights, sector) * 2 * Math.PI, to = from + width;
            int colour = MachineMotion.tier(multipliers[sector]) | 0xff000000;
            boolean winner = landed && sector == outcome[0];
            sector(pose, buffers, 0, WHEEL_Y, 14, WHEEL_RADIUS, from, to, winner ? shade(colour, 1.35f) : colour, 0.1f);
            // A lighter band near the rim and a darker one near the hub give each sector some depth.
            sector(pose, buffers, 0, WHEEL_Y, WHEEL_RADIUS - 8, WHEEL_RADIUS, from, to, shade(colour, 1.25f), 0.15f);
            sector(pose, buffers, 0, WHEEL_Y, 14, 20, from, to, shade(colour, 0.75f), 0.15f);
            sector(pose, buffers, 0, WHEEL_Y, 14, WHEEL_RADIUS, from, from + 0.025, GOLD, 0.2f);
            // The peg between two sectors, which the flapper knocks against.
            float pegX = (float) Math.cos(from) * (WHEEL_RADIUS - 2.5f), pegY = WHEEL_Y + (float) Math.sin(from) * (WHEEL_RADIUS - 2.5f);
            quad(pose, buffers, pegX - 1.6f, pegY - 1.6f, pegX + 1.6f, pegY + 1.6f, CHROME, 0.3f);
            nearestPeg = Math.min(nearestPeg, Math.abs(Math.IEEEremainder(from + Math.PI / 2, 2 * Math.PI)));
            // Motion blur: the sector trails two fainter copies of itself behind the way it turns.
            if (blur > 0.25) {
                for (int ghost = 1; ghost <= 2; ghost++) {
                    double back = ghost * 0.09 * blur;
                    int alpha = (int) ((ghost == 1 ? 0x70 : 0x38) * blur);
                    sector(pose, buffers, 0, WHEEL_Y, 14, WHEEL_RADIUS, from - back, to - back, alpha << 24 | (colour & 0xffffff),
                            0.16f + ghost * 0.01f);
                }
            }
            // What the sector pays, written along its radius so it reads as the wheel turns; at speed it is a smear.
            if (width > 0.22 && blur < 0.55) {
                pose.pushPose();
                pose.translate(0, WHEEL_Y, 0);
                pose.mulPose(Axis.ZP.rotation((float) (from + width / 2)));
                label(pose, buffers, font, multipliers[sector] == 0 ? "-" : GameScreens.multiplier(multipliers[sector]),
                        WHEEL_RADIUS * 0.6f, 0, winner ? DARK : TEXT, 1.15f, WHEEL_RADIUS * 0.62f, 0.3f);
                pose.popPose();
            }
        }
        sector(pose, buffers, 0, WHEEL_Y, 0, 15, 0, 2 * Math.PI, BRASS, 0.3f);
        sector(pose, buffers, 0, WHEEL_Y, 0, 10, 0, 2 * Math.PI, GOLD, 0.35f);
        for (int spoke = 0; spoke < 8; spoke++) {
            double angle = spoke * Math.PI / 4 + turn * 2 * Math.PI;
            sector(pose, buffers, 0, WHEEL_Y, 5, 13.5f, angle - 0.08, angle + 0.08, BRASS_DARK, 0.38f);
        }
        sector(pose, buffers, 0, WHEEL_Y, 0, 4, 0, 2 * Math.PI, BRASS_DARK, 0.4f);
        // The flapper: a red tongue over the rim, kicked aside by each peg going by and falling back.
        float kick = shown && progress < 1 ? (float) Math.max(0, 1 - nearestPeg / 0.12) * 0.5f : 0;
        pose.pushPose();
        pose.translate(0, WHEEL_Y - WHEEL_RADIUS - 6, 0);
        pose.mulPose(Axis.ZP.rotation(kick));
        quad(pose, buffers, -2.4f, -3, 2.4f, 6, 0xffd2434f, 0.6f);
        quad(pose, buffers, -1.2f, 6, 1.2f, 10, 0xffd2434f, 0.6f);
        quad(pose, buffers, -1.2f, -2, 1.2f, 1, 0xffef8a92, 0.65f);
        pose.popPose();
    }

    /** The bulbs set in the brass rim, round the wheel. */
    private static void bulbs(PoseStack pose, WorldCanvas buffers, double now, boolean rolling, boolean won) {
        for (int bulb = 0; bulb < 24; bulb++) {
            double angle = bulb * Math.PI / 12;
            float x = (float) Math.cos(angle) * 70, y = WHEEL_Y + (float) Math.sin(angle) * 70;
            quad(pose, buffers, x - 1.8f, y - 1.8f, x + 1.8f, y + 1.8f, lamp(bulb, now, rolling, won), 0);
        }
    }

    // --- Coin pusher ----------------------------------------------------------------------------

    private static void pusher(PoseStack pose, WorldCanvas buffers, GameStationEntity station, boolean shown,
                               double progress, double now, float light) {
        double plate = MachineMotion.plate(now);
        float front = (float) (9 - 4 * plate);
        // The plate sweeps the lower shelf under the upper one.
        cube(pose, buffers, 1.2f, 17.6f, front, 14.8f, 20.8f, 14.6f, CHROME, light);
        cube(pose, buffers, 1.2f, 20.3f, front - 0.1f, 14.8f, 20.8f, front + 0.3f, GOLD, light);
        int falling = shown ? MachineMotion.coins(station.multiplier) : 0;
        // The bed of coins on the lower shelf lies still, between the edge and the furthest the plate reaches;
        // only the coins a win tips over leave it, the first ones of the front row.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 7; column++) {
                if (row == 0 && column < falling && MachineMotion.coinFall(column, progress) > 0) continue;
                if (row > 0 && (column * 5 + row * 3) % 7 == 0) continue;
                coin(pose, buffers, 3 + column * 1.9f + (row % 2) * 0.95f, 17.6f, 1.4f + row * 1.45f, light);
            }
        }
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 8; column++) {
                if ((column + row * 2) % 3 == 0) continue;
                coin(pose, buffers, 1.8f + column * 1.65f + (row % 2) * 0.8f, 21.6f, 6 + row * 1.6f, light);
            }
        }
        for (int coin = 0; coin < falling; coin++) {
            double fall = MachineMotion.coinFall(coin, progress);
            float u = 3 + coin * 1.9f;
            if (fall <= 0) {
                continue;
            } else if (fall < 1) {
                float y = 17.6f - (float) (fall * fall) * 2.6f;
                coin(pose, buffers, u, y, 0.9f, light);
            } else {
                // Paid: the coin waits in the tray at the bottom of the cabinet.
                coin(pose, buffers, 3.4f + coin * 1.6f, 2.9f, 0.2f + (coin % 2) * 0.6f, light);
            }
        }
        glassCase(pose, buffers, 14.6f, 29.4f, light);
    }

    private static void coin(PoseStack pose, WorldCanvas buffers, float u, float y, float z, float light) {
        cube(pose, buffers, u - 0.65f, y, z - 0.65f, u + 0.65f, y + 0.35f, z + 0.65f, COIN, light);
        cube(pose, buffers, u - 0.35f, y + 0.35f, z - 0.35f, u + 0.35f, y + 0.38f, z + 0.35f, COIN_DARK, light);
    }

    /**
     * The glass of a case: the front and both flanks, faintly tinted and as lit as the room. Panes are
     * drawn on both sides and seen through one another, so the tint stays light or the overlaps whiten.
     */
    private static void glassCase(PoseStack pose, WorldCanvas buffers, float bottom, float top, float light) {
        int glass = shade(GLASS, light) & 0x00ffffff | 0x12000000;
        pane(pose, buffers, 1, bottom, 0.5f, 15, top, 0.5f, glass);
        pane(pose, buffers, 0.5f, bottom, 1, 0.5f, top, 15, glass);
        pane(pose, buffers, 15.5f, bottom, 1, 15.5f, top, 15, glass);
    }

    // --- Prize claw -----------------------------------------------------------------------------

    private static final float CLAW_HOME_U = 2.8f, CLAW_HOME_Z = 2.8f, CLAW_FLOOR = 14.2f, CLAW_TOP = 30.6f;

    private static void claw(PoseStack pose, WorldCanvas buffers, int[][] table, int[] outcome, boolean shown,
                             double progress, float light) {
        int[] multipliers = table[0];
        int count = Math.max(1, multipliers.length);
        int chosen = Math.max(0, Math.min(count - 1, shown ? outcome[0] : outcome[2]));
        boolean held = shown && outcome[1] == 1;
        float targetU = prizeU(chosen, count), targetZ = prizeZ(chosen);
        MachineMotion.Claw claw = MachineMotion.claw(CLAW_HOME_U, targetU, held, progress);
        for (int prize = 0; prize < count; prize++) {
            boolean taken = shown && prize == chosen && (claw.carrying() || held && progress >= 1 || claw.prizeFall() > 0);
            if (!taken) prize(pose, buffers, prizeU(prize, count), CLAW_FLOOR, prizeZ(prize), multipliers[prize], light);
        }
        // The carriage runs on the rails; the claw goes along with it towards the chute.
        float u = (float) claw.rail();
        double along = Math.abs(targetU - CLAW_HOME_U) < 0.01 ? 0 : (u - targetU) / (CLAW_HOME_U - targetU);
        float z = (float) (targetZ + (CLAW_HOME_Z - targetZ) * along);
        float size = prizeSize(multipliers[chosen]);
        float lowest = CLAW_FLOOR + size * 1.6f + 2.1f;
        float y = (float) (CLAW_TOP - claw.depth() * (CLAW_TOP - lowest));
        cube(pose, buffers, u - 0.3f, 31.5f, 2.6f, u + 0.3f, 31.9f, 13.4f, CHROME, light);
        cube(pose, buffers, u - 0.8f, 30.8f, z - 0.8f, u + 0.8f, 31.5f, z + 0.8f, 0xff2a3038, light);
        cube(pose, buffers, u - 0.1f, y + 1.5f, z - 0.1f, u + 0.1f, 30.8f, z + 0.1f, 0xff9aa4ae, light);
        cube(pose, buffers, u - 0.9f, y, z - 0.9f, u + 0.9f, y + 1.1f, z + 0.9f, CHROME, light);
        cube(pose, buffers, u - 0.5f, y + 1.1f, z - 0.5f, u + 0.5f, y + 1.5f, z + 0.5f, GOLD, light);
        float open = (float) (1 - claw.closed());
        for (int prong = 0; prong < 3; prong++) {
            double angle = prong * 2 * Math.PI / 3 + Math.PI / 2;
            float reach = 0.7f + 1.1f * open;
            float pu = u + (float) Math.cos(angle) * reach, pz = z + (float) Math.sin(angle) * reach;
            float tipU = u + (float) Math.cos(angle) * (reach - 0.5f), tipZ = z + (float) Math.sin(angle) * (reach - 0.5f);
            cube(pose, buffers, pu - 0.2f, y - 1.6f, pz - 0.2f, pu + 0.2f, y, pz + 0.2f, CHROME, light);
            cube(pose, buffers, tipU - 0.2f, y - 2.3f, tipZ - 0.2f, tipU + 0.2f, y - 1.6f, tipZ + 0.2f, CHROME, light);
        }
        if (claw.carrying()) {
            prize(pose, buffers, u, y - 2.1f - size * 1.6f, z, multipliers[chosen], light);
        } else if (shown && claw.prizeFall() > 0 && claw.prizeFall() < 1) {
            // A prize let go falls back where it was; a prize carried home drops into the chute.
            boolean home = held;
            float fu = home ? CLAW_HOME_U : targetU, fz = home ? CLAW_HOME_Z : targetZ;
            float from = home ? CLAW_TOP - 2 - size : lowest - 1.2f - size;
            float fy = (float) (from + (CLAW_FLOOR - from) * claw.prizeFall());
            prize(pose, buffers, fu, fy, fz, multipliers[chosen], light);
        }
        glassCase(pose, buffers, 14, 32.4f, light);
    }

    private static float prizeU(int index, int count) { return 5.6f + (index + 0.5f) * 9f / count; }
    private static float prizeZ(int index) { return 8 + (index % 2 == 0 ? 2.2f : -2.2f); }
    private static float prizeSize(int multiplier) { return 1.1f + Math.min(1.1f, multiplier / 800f); }

    /** A plush prize: a block of its tier's colour with a lighter top. */
    private static void prize(PoseStack pose, WorldCanvas buffers, float u, float y, float z, int multiplier, float light) {
        float size = prizeSize(multiplier);
        int colour = MachineMotion.tier(multiplier) | 0xff000000;
        cube(pose, buffers, u - size, y, z - size, u + size, y + size * 2 * 0.8f, z + size, colour, light);
        cube(pose, buffers, u - size * 0.7f, y + size * 1.6f, z - size * 0.7f, u + size * 0.7f, y + size * 2,
                z + size * 0.7f, shade(colour, 1.2f) | 0xff000000, light);
    }

    // --- Pachinko -------------------------------------------------------------------------------

    private static void pachinko(PoseStack pose, WorldCanvas buffers, Font font, GameStationEntity station, int[][] table,
                                 int[] outcome, boolean shown, double progress, boolean rolling, String rollingKey) {
        int[] multipliers = table[0];
        float spacing = 10.5f, top = -190, rowHeight = 11;
        // The board: deep blue, lighter at the top, with two gold rings around the middle pins.
        for (int band = 0; band < 8; band++) {
            float y1 = -201.6f + band * 20, y2 = y1 + 20;
            quad(pose, buffers, -49.6f, y1, 49.6f, Math.min(-41.6f, y2), shade(0xff27407a, 1 - band * 0.07f), 0);
        }
        sector(pose, buffers, 0, -150, 30, 32, 0, 2 * Math.PI, 0x80d9b25a, 0.05f);
        sector(pose, buffers, 0, -150, 44, 45.5f, Math.PI * 1.1, Math.PI * 1.9, 0x60d9b25a, 0.05f);
        for (int row = 0; row < MachineTable.PACHINKO_ROWS; row++) {
            for (int pin = 0; pin <= row; pin++) {
                float px = (pin - row / 2f) * spacing, py = top + row * rowHeight + rowHeight;
                quad(pose, buffers, px - 1.3f, py - 1.3f, px + 1.3f, py + 1.3f, 0xffdfe6ee, 0.1f);
                quad(pose, buffers, px - 0.6f, py - 1.3f, px + 0.2f, py - 0.5f, 0xffffffff, 0.15f);
            }
        }
        float pockets = top + MachineTable.PACHINKO_ROWS * rowHeight + 8;
        int landed = shown && progress >= 1 ? outcome[0] : -1;
        for (int pocket = 0; pocket < MachineTable.PACHINKO_POCKETS && pocket < multipliers.length; pocket++) {
            float px = (pocket - MachineTable.PACHINKO_ROWS / 2f) * spacing;
            int colour = pocket == landed ? TEXT : MachineMotion.tier(multipliers[pocket]);
            quad(pose, buffers, px - 4.8f, pockets, px + 4.8f, pockets + 15, colour, 0.1f);
            quad(pose, buffers, px - 4.8f, pockets, px + 4.8f, pockets + 2, shade(colour, 1.3f) | 0xff000000, 0.12f);
            label(pose, buffers, font, BigDecimal.valueOf(multipliers[pocket], 2).stripTrailingZeros().toPlainString(),
                    px, pockets + 9, pocket == landed ? DARK : TEXT, 0.42f, 9.2f, 0.2f);
        }
        float bx, by;
        if (shown) {
            double[] ball = MachineMotion.ball(outcome[1], progress);
            bx = (float) ball[0] * spacing / 2;
            by = progress >= 1 ? pockets + 3 : top + (float) ball[1] * rowHeight + rowHeight - 4;
        } else {
            bx = 0;
            by = top - 2;
        }
        sector(pose, buffers, bx, by, 0, 3.2f, 0, 2 * Math.PI, 0xffe8edf2, 0.3f);
        sector(pose, buffers, bx - 1, by - 1, 0, 1.1f, 0, 2 * Math.PI, 0xffffffff, 0.35f);
        status(pose, buffers, font, station, rolling, rollingKey, -46, -70, 46, -44);
    }

    /** The ball tray along the front, a few silver balls waiting in it. */
    private static void tray(PoseStack pose, WorldCanvas buffers, float light) {
        for (int ball = 0; ball < 9; ball++) {
            float u = 2.8f + ball * 1.2f + (ball % 2) * 0.2f, z = -0.6f + (ball % 3) * 1.3f;
            cube(pose, buffers, u - 0.45f, 8.7f, z - 0.45f, u + 0.45f, 9.6f, z + 0.45f, 0xffdfe6ee, light);
        }
    }

    /** The launch handle on its housing, turned while the ball runs. */
    private static void handle(PoseStack pose, WorldCanvas buffers, boolean rolling) {
        pose.pushPose();
        pose.translate(16 - 15, 9.5, -0.4);
        pose.mulPose(Axis.ZP.rotationDegrees(rolling ? 50 : 0));
        cube(pose, buffers, 15, -1, -0.8f, 17, 1, 0, 0xff2a3038, 1);
        cube(pose, buffers, 15.75f, 0.6f, -1.3f, 16.25f, 2.6f, -0.6f, CHROME, 1);
        cube(pose, buffers, 15.5f, 2.6f, -1.5f, 16.5f, 3.2f, -0.4f, 0xffd2434f, 1);
        pose.popPose();
    }

    // --- Capsule gacha --------------------------------------------------------------------------

    private static final float GLOBE_U = 8, GLOBE_Y = 21.2f, GLOBE_Z = 8, GLOBE_RADIUS = 7.2f;

    private static void gacha(PoseStack pose, WorldCanvas buffers, Font font, GameStationEntity station, int[][] table,
                              boolean shown, double progress, boolean rolling, boolean playing, double now, float light) {
        int[] multipliers = table[0];
        double turns = playing ? MachineMotion.crank(progress) : 0;
        // While the crank turns the heap is churned; the capsules then tumble back down and settle.
        boolean stirring = rolling && progress < 0.5;
        CapsulePhysics heap = CapsulePhysics.of(station, GLOBE_U, GLOBE_Y, GLOBE_Z, GLOBE_RADIUS);
        long tick = (long) Math.floor(now);
        heap.advance(tick, stirring && !ClientOptions.reducedMotion() ? 1 : 0);
        float partial = (float) (now - tick);
        for (int capsule = 0; capsule < CapsulePhysics.COUNT; capsule++) {
            int colour = MachineMotion.tier(multipliers.length == 0 ? 0 : multipliers[capsule % multipliers.length]);
            capsule(pose, buffers, heap.x(capsule, partial), heap.y(capsule, partial) - CapsulePhysics.RADIUS,
                    heap.z(capsule, partial), CapsulePhysics.RADIUS, colour | 0xff000000, 0, heap.spin(capsule, partial), light);
        }
        globe(pose, buffers, light);
        crank(pose, buffers, turns);
        double drop = shown ? MachineMotion.capsuleDrop(progress) : 0, open = shown ? MachineMotion.capsuleOpen(progress) : 0;
        int colour = MachineMotion.tier(station.multiplier) | 0xff000000;
        if (drop > 0 && drop < 0.35) {
            // The capsule won sinks out of the heap, down through the collar into the machine.
            float y = GLOBE_Y - GLOBE_RADIUS + 1.2f - (float) (drop / 0.35) * 3.5f;
            capsule(pose, buffers, GLOBE_U, y, GLOBE_Z, 0.8f, colour, 0, 0, light);
        } else if (shown && drop >= 0.6) {
            // Then rolls out of the door, bouncing to a stop on the lip, and pops open.
            double out = (drop - 0.6) / 0.4;
            float z = 3.2f - (float) out * 2.8f;
            float bounce = (float) (Math.abs(Math.sin(out * Math.PI * 2.5)) * (1 - out) * 1.4);
            float spin = (float) (-out * 3 * Math.PI);
            capsule(pose, buffers, 8, 2.6f + bounce, z, 1.1f, colour, (float) open, spin, light);
            if (open > 0) prizeBurst(pose, buffers, font, station.multiplier, colour, open);
        }
        // The door flap swings up as the capsule pushes through it.
        float flap = drop > 0.55 && drop < 0.95 ? (float) Math.sin((drop - 0.55) / 0.4 * Math.PI) * 1.2f : 0;
        pose.pushPose();
        pose.translate(16 - 8, 5.5, 2.75);
        pose.mulPose(Axis.XP.rotation(-flap));
        cube(pose, buffers, 14, -3, -0.1f, 18, 0, 0.1f, 0x99c8d0d8, light);
        pose.popPose();
    }

    /** Once open, the capsule shows what it pays: the multiplier rising over it, and a ring of sparks. */
    private static void prizeBurst(PoseStack pose, WorldCanvas buffers, Font font, int multiplier, int colour, double open) {
        surface(pose, -0.8f);
        float rise = (float) open * 14;
        float y = (8 - 6.5f) * 8 - rise;
        for (int spark = 0; spark < 8; spark++) {
            double angle = spark * Math.PI / 4;
            float distance = 4 + (float) open * 16, size = 1.6f * (float) (1 - open * 0.6);
            float x = (float) Math.cos(angle) * distance, sy = y + 10 + (float) Math.sin(angle) * distance * 0.7f;
            quad(pose, buffers, x - size, sy - size, x + size, sy + size, spark % 2 == 0 ? colour : GOLD, 0.2f);
        }
        quad(pose, buffers, -22, y - 9, 22, y + 9, 0xe0101820, 0.3f);
        quad(pose, buffers, -22, y + 8, 22, y + 9, GOLD, 0.35f);
        label(pose, buffers, font, multiplier <= 0 ? "-" : GameScreens.multiplier(multiplier), 0, y,
                multiplier >= 100 ? GOLD : TEXT, 1.5f, 40, 0.4f);
        pose.popPose();
    }

    /**
     * A capsule standing on its base at (u, y, z): a coloured half under a cream lid, both eight-sided.
     * It turns about its axis by spin, and its lid lifts and tips back as it opens.
     */
    private static void capsule(PoseStack pose, WorldCanvas buffers, float u, float y, float z, float half, int colour,
                                float open, float spin, float light) {
        pose.pushPose();
        pose.translate(16 - u, y + half, z);
        if (spin != 0) pose.mulPose(Axis.XP.rotation(spin));
        shell(pose, buffers, -half, 0, half, colour, light);
        cube(pose, buffers, 16 - half * 1.02f, -0.12f * half, -half * 0.72f, 16 + half * 1.02f, 0.12f * half,
                half * 0.72f, 0xff2a3038, light);
        pose.pushPose();
        pose.translate(0, open * half * 1.4f, -open * half * 0.6f);
        pose.mulPose(Axis.XP.rotation(-open * 1.1f));
        // The lid is clear plastic of the same colour, lighter.
        shell(pose, buffers, 0, half, half, GameScreens.mix(colour, 0xfff3eee2, 0.55f), light);
        pose.popPose();
        pose.popPose();
    }

    /** One half of a capsule, about the axis: two crossed boxes read as an eight-sided shell. */
    private static void shell(PoseStack pose, WorldCanvas buffers, float y1, float y2, float half, int colour, float light) {
        cube(pose, buffers, 16 - half, y1, -half * 0.7f, 16 + half, y2, half * 0.7f, colour, light);
        cube(pose, buffers, 16 - half * 0.7f, y1, -half, 16 + half * 0.7f, y2, half, colour, light);
    }

    /** The glass globe: a sphere of faint quads, its upper half catching the light. */
    private static void globe(PoseStack pose, WorldCanvas buffers, float light) {
        int rings = 9, segments = 16;
        // Faint: the sphere is seen through itself, front and back, and its overlaps add up.
        int tint = shade(0xffa9d4e2, light) & 0x00ffffff | 0x16000000;
        buffers.quads(pose, (matrix, vertices) -> {
            for (int ring = 0; ring < rings; ring++) {
                double a = Math.PI * ring / rings - Math.PI / 2, b = Math.PI * (ring + 1) / rings - Math.PI / 2;
                for (int segment = 0; segment < segments; segment++) {
                    double c = 2 * Math.PI * segment / segments, d = 2 * Math.PI * (segment + 1) / segments;
                    int colour = tint;
                    sphere(vertices, matrix, a, c, colour);
                    sphere(vertices, matrix, b, c, colour);
                    sphere(vertices, matrix, b, d, colour);
                    sphere(vertices, matrix, a, d, colour);
                    // The far side shows through the near one: both windings.
                    sphere(vertices, matrix, a, d, colour);
                    sphere(vertices, matrix, b, d, colour);
                    sphere(vertices, matrix, b, c, colour);
                    sphere(vertices, matrix, a, c, colour);
                }
            }
        });
    }

    private static void sphere(com.mojang.blaze3d.vertex.VertexConsumer vertices, org.joml.Matrix4f matrix,
                               double latitude, double longitude, int colour) {
        float x = 16 - GLOBE_U + (float) (Math.cos(latitude) * Math.cos(longitude) * GLOBE_RADIUS);
        float y = GLOBE_Y + (float) (Math.sin(latitude) * GLOBE_RADIUS);
        float z = GLOBE_Z + (float) (Math.cos(latitude) * Math.sin(longitude) * GLOBE_RADIUS);
        WorldCanvas.vertex(vertices, matrix, x, y, z, colour);
    }

    /** The crank in the middle of its plate, turned twice for each capsule. */
    private static void crank(PoseStack pose, WorldCanvas buffers, double turns) {
        pose.pushPose();
        pose.translate(16 - 8, 9.4, 2.6);
        pose.mulPose(Axis.ZP.rotation((float) (turns * 2 * Math.PI)));
        cube(pose, buffers, 15, -1, -0.8f, 17, 1, 0, CHROME, 1);
        cube(pose, buffers, 15.6f, -0.4f, -1.4f, 16.4f, 2.6f, -0.8f, CHROME, 1);
        cube(pose, buffers, 15.4f, 2.2f, -2.4f, 16.6f, 3.2f, -1.2f, 0xffd2434f, 1);
        pose.popPose();
    }

    // --- High stakes terminal -------------------------------------------------------------------

    private static void terminal(PoseStack pose, WorldCanvas buffers, Font font, GameStationEntity station, int[][] table,
                                 int[] outcome, boolean shown, double progress, boolean rolling, boolean won, double now,
                                 String name, String rollingKey) {
        // A deep red-black glass, a gold title bar, and a thin red glow running round the edge.
        for (int band = 0; band < 6; band++) {
            float y1 = -197.6f + band * 24.6f;
            quad(pose, buffers, -53.6f, y1, 53.6f, Math.min(-50.4f, y1 + 24.6f), shade(0xff1a0a10, 1 - band * 0.12f), 0);
        }
        quad(pose, buffers, -53.6f, -197.6f, 53.6f, -184, 0xff2a1018, 0.05f);
        quad(pose, buffers, -53.6f, -184.6f, 53.6f, -184, GOLD, 0.06f);
        int glow = jackpotColour(now, rolling, won);
        quad(pose, buffers, -53.6f, -52, 53.6f, -50.4f, glow, 0.06f);
        quad(pose, buffers, -53.6f, -184, -52, -52, glow, 0.06f);
        quad(pose, buffers, 52, -184, 53.6f, -52, glow, 0.06f);
        label(pose, buffers, font, name, 0, -190.8f, GOLD, 0.8f, 100, 0.1f);
        int[] targets = table[0];
        int index = Math.max(0, Math.min(targets.length - 1, shown ? outcome[0] : outcome[2]));
        // Every target along the top of the screen, the chosen one lit: red key steps through them.
        int shownTargets = Math.min(8, targets.length);
        for (int chip = 0; chip < shownTargets; chip++) {
            float x = -48 + chip * (96f / shownTargets);
            boolean chosen = chip == index;
            quad(pose, buffers, x + 0.6f, -180, x + 96f / shownTargets - 0.6f, -170, chosen ? GOLD : 0xff2a1a22, 0.1f);
            label(pose, buffers, font, GameScreens.multiplier(Math.max(101, targets[chip])), x + 48f / shownTargets, -175,
                    chosen ? DARK : SILVER, 0.42f, 96f / shownTargets - 2, 0.15f);
        }
        int target = Math.max(101, targets[index]);
        float cx = -22, cy = -132, radius = 30;
        double win = 100.0 / target;
        sector(pose, buffers, cx, cy, 0, radius + 4, 0, 2 * Math.PI, BRASS_DARK, 0.1f);
        sector(pose, buffers, cx, cy, radius - 9, radius, -Math.PI / 2, 1.5 * Math.PI, 0xff3a1820, 0.15f);
        sector(pose, buffers, cx, cy, radius - 9, radius, -Math.PI / 2, -Math.PI / 2 + win * 2 * Math.PI, 0xff2fbf7a, 0.2f);
        sector(pose, buffers, cx, cy, 0, radius - 10, 0, 2 * Math.PI, DARK, 0.15f);
        double needle = shown ? MachineMotion.needle(target, outcome[1], progress) : 0;
        double angle = needle * 2 * Math.PI - Math.PI / 2;
        sector(pose, buffers, cx, cy, 0, radius + 1, angle - 0.04, angle + 0.04, TEXT, 0.3f);
        sector(pose, buffers, cx, cy, 0, 3, 0, 2 * Math.PI, GOLD, 0.35f);
        label(pose, buffers, font, GameScreens.multiplier(target), 28, -150, TEXT, 1.1f, 44, 0.1f);
        label(pose, buffers, font, BigDecimal.valueOf(10_000).divide(BigDecimal.valueOf(target), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "%", 28, -134, GREEN, 0.8f, 44, 0.1f);
        for (int bar = 0; bar < 8; bar++) {
            double level = rolling ? 0.5 + 0.5 * Math.sin(now * 0.9 + bar * 1.7) : 0.25;
            float x = 8 + bar * 5.5f, height = (float) (2 + level * 14);
            quad(pose, buffers, x, -104 - height, x + 3.5f, -104, rolling ? 0xff4cc3d9 : won ? GREEN : 0xff2a313d, 0.1f);
        }
        status(pose, buffers, font, station, rolling, rollingKey, -50, -92, 50, -62);
    }

    /** The glow round the high stakes screen: a slow red breath at rest, racing while it plays, green on a win. */
    private static int jackpotColour(double now, boolean rolling, boolean won) {
        if (won) return (long) (now / 4) % 2 == 0 ? GREEN : 0xff2f8a6c;
        float pulse = (float) (0.55 + 0.45 * Math.sin(now * (rolling ? 0.9 : 0.12)));
        return shade(0xffd2434f, pulse) | 0xff000000;
    }

    /** A slice of a ring, drawn as a strip of quads; a disc is a slice from the centre round. */
    static void sector(PoseStack pose, WorldCanvas buffers, float cx, float cy, float inner, float outer,
                       double from, double to, int colour, float lift) {
        int steps = Math.max(1, (int) Math.ceil(WorldCanvas.segments(pose, outer, 48) * Math.abs(to - from) / (2 * Math.PI)));
        buffers.quads(pose, (matrix, vertices) -> {
            for (int step = 0; step < steps; step++) {
                double a = from + (to - from) * step / steps, b = from + (to - from) * (step + 1) / steps;
                float ca = (float) Math.cos(a), sa = (float) Math.sin(a), cb = (float) Math.cos(b), sb = (float) Math.sin(b);
                WorldCanvas.vertex(vertices, matrix, cx + ca * inner, cy + sa * inner, lift, colour);
                WorldCanvas.vertex(vertices, matrix, cx + cb * inner, cy + sb * inner, lift, colour);
                WorldCanvas.vertex(vertices, matrix, cx + cb * outer, cy + sb * outer, lift, colour);
                WorldCanvas.vertex(vertices, matrix, cx + ca * outer, cy + sa * outer, lift, colour);
            }
        });
    }
}
