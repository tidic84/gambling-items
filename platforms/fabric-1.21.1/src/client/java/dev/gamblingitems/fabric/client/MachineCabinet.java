package dev.gamblingitems.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.machines.MachineTable;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.block.SlotMachineBlock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import static dev.gamblingitems.fabric.client.SlotCabinet.*;

/**
 * The front of a prize machine cabinet, in the coordinates of {@link SlotCabinet}: a brass marquee,
 * the machine behind its window at eye height, a status band and its best odds on the belly.
 *
 * <p>Everything moving is rebuilt from what the block carries: the outcome, when it started and the
 * table of prizes, so every player around the machine sees the same play.
 */
final class MachineCabinet {
    private static final float CENTRE_Y = -121;
    private static final int GLASS = 0x3399c8e0, STEEL = 0xff8795a1, BRASS = TRIM, DARK = 0xff0a1019;

    private MachineCabinet() {}

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
        int count = station.mode() == GameMode.PACHINKO ? MachineTable.PACHINKO_POCKETS : 8;
        int[] multipliers = new int[count], weights = new int[count];
        java.util.Arrays.fill(weights, 1);
        if (station.mode() == GameMode.PACHINKO) weights = new int[] {1, 8, 28, 56, 70, 56, 28, 8, 1};
        if (station.mode() == GameMode.HIGH_STAKES) java.util.Arrays.fill(multipliers, 200);
        return new int[][] {multipliers, weights};
    }

    /** What the cabinet was told to show: the outcome, its detail and the choice it was played with. */
    private static int[] outcome(GameStationEntity station) {
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

    /** The few pieces that stand out of the cabinet: the gacha crank, the pachinko handle, the claw button. */
    static void parts(GameStationEntity station, PoseStack pose, WorldCanvas buffers, double progress, boolean playing) {
        GameMode mode = station.mode();
        if (mode != GameMode.CAPSULE_GACHA && mode != GameMode.PACHINKO && mode != GameMode.PRIZE_CLAW) return;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-station.getBlockState().getValue(SlotMachineBlock.FACING).toYRot()));
        switch (mode) {
            case CAPSULE_GACHA -> {
                // A crank on the right flank, turned twice for each capsule.
                double turn = playing ? MachineMotion.crank(progress) : 0;
                pose.translate(0.53, 0.35, 0.1);
                box(pose, buffers, 0.03f, 0.08f, 0.08f, TRIM_DARK);
                pose.translate(0.05, 0, 0);
                pose.mulPose(Axis.XP.rotation((float) (turn * 2 * Math.PI)));
                box(pose, buffers, 0.02f, 0.02f, 0.02f, STEEL);
                pose.translate(0, 0.1, 0);
                box(pose, buffers, 0.015f, 0.1f, 0.015f, STEEL);
                pose.translate(0.03, 0.09, 0);
                box(pose, buffers, 0.035f, 0.025f, 0.025f, BRASS);
            }
            case PACHINKO -> {
                // The launch handle on the right of the front, turned while the ball runs.
                pose.translate(0.36, -0.12, 0.53);
                box(pose, buffers, 0.07f, 0.07f, 0.025f, TRIM_DARK);
                pose.translate(0, 0, 0.04);
                pose.mulPose(Axis.ZP.rotation(playing && progress < 1 ? -0.9f : 0));
                box(pose, buffers, 0.02f, 0.06f, 0.02f, STEEL);
                pose.translate(0, 0.07, 0);
                box(pose, buffers, 0.03f, 0.02f, 0.03f, RED);
            }
            default -> {
                // The claw's drop button, lit while the claw is out.
                pose.translate(-0.3, -0.12, 0.53);
                box(pose, buffers, 0.06f, 0.06f, 0.02f, TRIM_DARK);
                pose.translate(0, 0, playing && progress < 0.3 ? 0.015 : 0.03);
                box(pose, buffers, 0.04f, 0.04f, 0.02f, playing && progress < 1 ? GREEN : RED);
            }
        }
        pose.popPose();
    }

    static void front(GameStationEntity station, PoseStack pose, WorldCanvas buffers, Font font, double now,
                      double progress, boolean playing) {
        GameMode mode = station.mode();
        quad(pose, buffers, -64, ROOF, 64, 64, BODY, 0);
        quad(pose, buffers, -60, ROOF + 4, 60, 58, BODY_LIGHT, 1);
        panel(pose, buffers, -57, ROOF + 4, 57, -184, TRIM_DARK, 2);
        quad(pose, buffers, -52, ROOF + 8, 52, -188, BRASS, 3);
        label(pose, buffers, font, Component.translatable("gui.gamblingitems.mode." + mode.id()).getString(), 0, -198, DARK, 0.8f, 4);
        boolean rolling = playing && progress < 1;
        boolean won = !station.idle() && !rolling && station.highlight;
        for (int index = 0; index < 12; index++) {
            int lamp = won ? GREEN : rolling && ((long) (now / 3) + index) % 3 == 0 ? TEXT : 0xffd35a61;
            float lampX = -53 + index * 9.5f;
            quad(pose, buffers, lampX, -183, lampX + 3, -180, lamp, 4);
        }
        panel(pose, buffers, -58, -178, 58, -62, WINDOW, 2);
        int[][] table = table(station);
        int[] outcome = outcome(station);
        boolean shown = outcome[0] >= 0 && !station.idle();
        double shownProgress = shown ? progress : 0;
        switch (mode) {
            case LUCKY_WHEEL -> wheel(pose, buffers, table, outcome, shown, shownProgress, rolling, now);
            case COIN_PUSHER -> pusher(pose, buffers, station, shown, shownProgress, now);
            case PRIZE_CLAW -> claw(pose, buffers, table, outcome, shown, shownProgress);
            case PACHINKO -> pachinko(pose, buffers, table, outcome, shown, shownProgress);
            case CAPSULE_GACHA -> gacha(pose, buffers, table, station, shown, shownProgress, rolling, now);
            default -> dial(pose, buffers, font, table, outcome, shown, shownProgress, rolling, now);
        }
        // The status band: what the machine is doing, who played and what it paid.
        panel(pose, buffers, -56, -58, 56, -26, WINDOW, 2);
        String status = rolling ? Component.translatable("gui.gamblingitems.machine_playing." + mode.id()).getString()
                : station.idle() ? Component.translatable("gui.gamblingitems.station_ready").getString()
                : Component.translatable("gui.gamblingitems." + station.resultKey).getString();
        int colour = rolling ? TEXT : station.idle() ? SILVER : station.highlight ? GREEN : RED;
        label(pose, buffers, font, status, 0, -50, colour, 0.7f, 4);
        String line = station.playerName;
        if (!station.idle() && !rolling && station.multiplier > 0) line += "  " + GameScreens.multiplier(station.multiplier);
        label(pose, buffers, font, line, 0, -36, SILVER, 0.6f, 4);
        odds(pose, buffers, font, mode, table, outcome[2]);
        quad(pose, buffers, -64, 47, 64, 64, WINDOW, 3);
        quad(pose, buffers, -60, 47, 60, 49, BRASS, 4);
    }

    /** The three best prizes of the machine, with their exact chance, printed on its belly. */
    private static void odds(PoseStack pose, WorldCanvas buffers, Font font, GameMode mode, int[][] table, int choice) {
        panel(pose, buffers, -52, -20, 52, 40, 0xff141b25, 2);
        int[] multipliers = table[0], weights = table[1];
        long total = 0;
        for (int weight : weights) total += weight;
        int lines = 0;
        boolean[] used = new boolean[multipliers.length];
        for (int line = 0; line < 3; line++) {
            int best = -1;
            for (int index = 0; index < multipliers.length; index++) {
                if (used[index] || multipliers[index] == 0) continue;
                if (best < 0 || multipliers[index] > multipliers[best]) best = index;
            }
            if (best < 0) break;
            used[best] = true;
            BigDecimal chance;
            if (mode == GameMode.PRIZE_CLAW) chance = BigDecimal.valueOf(weights[best], 2);
            else if (mode == GameMode.HIGH_STAKES) chance = BigDecimal.valueOf(10_000).divide(BigDecimal.valueOf(multipliers[best]), 2, RoundingMode.HALF_UP);
            else {
                long same = 0;
                for (int index = 0; index < multipliers.length; index++) {
                    if (multipliers[index] == multipliers[best]) {
                        same += weights[index];
                        used[index] = true;
                    }
                }
                chance = total == 0 ? BigDecimal.ZERO
                        : BigDecimal.valueOf(same * 100).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            }
            float top = -10 + line * 15;
            quad(pose, buffers, -44, top - 3, -38, top + 3, MachineMotion.tier(multipliers[best]), 4);
            label(pose, buffers, font, GameScreens.multiplier(multipliers[best]), -18, top, TEXT, 0.55f, 4);
            label(pose, buffers, font, chance.stripTrailingZeros().toPlainString() + "%", 26, top, SILVER, 0.55f, 4);
            lines++;
        }
        if (lines == 0) label(pose, buffers, font, Component.translatable("gui.gamblingitems.machine_odds").getString(), 0, 8, SILVER, 0.55f, 4);
    }

    private static void wheel(PoseStack pose, WorldCanvas buffers, int[][] table, int[] outcome, boolean shown,
                              double progress, boolean rolling, double now) {
        int[] multipliers = table[0], weights = table[1];
        double turn = shown ? MachineMotion.wheelTurn(weights, Math.min(outcome[0], weights.length - 1), outcome[1] % 1000, progress) : 0;
        float radius = 50;
        sector(pose, buffers, 0, CENTRE_Y, 0, radius + 5, 0, 2 * Math.PI, BRASS, 3);
        for (int sector = 0; sector < weights.length; sector++) {
            if (weights[sector] == 0) continue;
            double from = (MachineMotion.sectorStart(weights, sector) + turn) * 2 * Math.PI - Math.PI / 2;
            double to = from + MachineMotion.sectorWidth(weights, sector) * 2 * Math.PI;
            sector(pose, buffers, 0, CENTRE_Y, 9, radius, from, to, MachineMotion.tier(multipliers[sector]), 4);
            sector(pose, buffers, 0, CENTRE_Y, 9, radius, from, from + 0.025, DARK, 4.5f);
        }
        for (int lamp = 0; lamp < 20; lamp++) {
            double angle = lamp * Math.PI / 10;
            boolean lit = rolling ? ((long) (now / 2) + lamp) % 4 == 0 : lamp % 2 == 0;
            float lx = (float) Math.cos(angle) * (radius + 2.5f), ly = CENTRE_Y + (float) Math.sin(angle) * (radius + 2.5f);
            quad(pose, buffers, lx - 1.2f, ly - 1.2f, lx + 1.2f, ly + 1.2f, lit ? TEXT : TRIM_DARK, 5);
        }
        sector(pose, buffers, 0, CENTRE_Y, 0, 9, 0, 2 * Math.PI, BRASS, 5);
        sector(pose, buffers, 0, CENTRE_Y, 0, 4, 0, 2 * Math.PI, DARK, 5.5f);
        // The pointer: a brass tongue at the top, over the rim.
        sector(pose, buffers, 0, CENTRE_Y - radius - 9, 0, 12, Math.PI / 2 - 0.25, Math.PI / 2 + 0.25, TEXT, 6);
    }

    private static void pusher(PoseStack pose, WorldCanvas buffers, GameStationEntity station, boolean shown,
                               double progress, double now) {
        float left = -50, right = 50, shelf = -128, tray = -76;
        quad(pose, buffers, left, -170, right, -70, 0xff0d141d, 3);
        // Seen from the front, the plate comes forward and goes back: it grows and shrinks.
        double plate = MachineMotion.plate(now);
        quad(pose, buffers, left, -168, right, -150 + (float) (plate * 10), STEEL, 4);
        quad(pose, buffers, left, -150 + (float) (plate * 10), right, -148 + (float) (plate * 10), TEXT, 4.2f);
        quad(pose, buffers, left, shelf, right, shelf + 4, TRIM_DARK, 4);
        for (int row = 0; row < 2; row++) {
            for (int coin = 0; coin < 9; coin++) {
                float x = left + 6 + coin * 10 + row * 5, y = shelf - 8 - row * 7 + (float) (plate * 2);
                quad(pose, buffers, x, y, x + 8, y + 5, coin % 3 == 0 ? 0xffb8923f : BRASS, 4.5f);
            }
        }
        int falling = shown ? MachineMotion.coins(station.multiplier) : 0;
        for (int coin = 0; coin < falling; coin++) {
            double fall = MachineMotion.coinFall(coin, progress);
            if (fall <= 0) continue;
            float x = -30 + coin * 11, y = shelf + 4 + (float) (fall * (tray - shelf - 10));
            quad(pose, buffers, x, y, x + 8, y + 5, BRASS, 5);
        }
        quad(pose, buffers, left, tray - 6, right, tray, TRIM_DARK, 4);
        quad(pose, buffers, left, -170, right, -70, GLASS, 6);
    }

    private static void claw(PoseStack pose, WorldCanvas buffers, int[][] table, int[] outcome, boolean shown, double progress) {
        int[] multipliers = table[0];
        int count = multipliers.length;
        float left = -36, right = 50, floor = -76, rail = -168;
        quad(pose, buffers, -54, -172, 54, -66, 0xff0d141d, 3);
        quad(pose, buffers, -54, rail - 3, 54, rail, STEEL, 4);
        quad(pose, buffers, -54, floor - 22, -40, floor + 6, 0xff2a313d, 4);
        int playing = Math.max(0, Math.min(count - 1, shown ? outcome[0] : outcome[2]));
        float target = left + (playing + 0.5f) * (right - left) / count;
        boolean held = shown && outcome[1] == 1;
        MachineMotion.Claw claw = MachineMotion.claw(-47, target, held, progress);
        for (int prize = 0; prize < count; prize++) {
            boolean taken = shown && prize == playing && (claw.carrying() || held && progress >= 1);
            if (taken) continue;
            float px = left + (prize + 0.5f) * (right - left) / count, size = 4 + Math.min(5, multipliers[prize] / 400f);
            sector(pose, buffers, px, floor - size, 0, size, 0, 2 * Math.PI, MachineMotion.tier(multipliers[prize]), 4);
        }
        float clawX = (float) claw.rail(), bottom = rail + 6 + (float) (claw.depth() * (floor - rail - 20));
        quad(pose, buffers, clawX - 6, rail - 4, clawX + 6, rail + 2, BRASS, 5);
        quad(pose, buffers, clawX - 0.6f, rail + 2, clawX + 0.6f, bottom, STEEL, 5);
        float open = (float) (1 - claw.closed()) * 4;
        quad(pose, buffers, clawX - 4 - open, bottom, clawX - 2 - open, bottom + 8, TEXT, 5);
        quad(pose, buffers, clawX + 2 + open, bottom, clawX + 4 + open, bottom + 8, TEXT, 5);
        quad(pose, buffers, clawX - 4 - open, bottom - 1, clawX + 4 + open, bottom + 1, TEXT, 5);
        float size = 4 + Math.min(5, multipliers[playing] / 400f);
        if (claw.carrying()) {
            sector(pose, buffers, clawX, bottom + 6 + size, 0, size, 0, 2 * Math.PI, MachineMotion.tier(multipliers[playing]), 5.5f);
        } else if (shown && !held && claw.prizeFall() > 0 && claw.prizeFall() < 1) {
            sector(pose, buffers, target, (float) (bottom + 6 + (floor - size - bottom - 6) * claw.prizeFall()), 0, size, 0,
                    2 * Math.PI, MachineMotion.tier(multipliers[playing]), 5.5f);
        }
        quad(pose, buffers, -54, -172, 54, -66, GLASS, 6);
    }

    private static void pachinko(PoseStack pose, WorldCanvas buffers, int[][] table, int[] outcome, boolean shown, double progress) {
        int[] multipliers = table[0];
        float spacing = 11, top = -170, rowHeight = 9;
        quad(pose, buffers, -56, -174, 56, -66, 0xff121a26, 3);
        for (int row = 0; row < MachineTable.PACHINKO_ROWS; row++) {
            for (int pin = 0; pin <= row; pin++) {
                float px = (pin - row / 2f) * spacing, py = top + row * rowHeight + rowHeight;
                quad(pose, buffers, px - 1, py - 1, px + 1, py + 1, STEEL, 4);
            }
        }
        float pockets = top + MachineTable.PACHINKO_ROWS * rowHeight + 8;
        int landed = shown && progress >= 1 ? outcome[0] : -1;
        for (int pocket = 0; pocket < MachineTable.PACHINKO_POCKETS && pocket < multipliers.length; pocket++) {
            float px = (pocket - MachineTable.PACHINKO_ROWS / 2f) * spacing;
            quad(pose, buffers, px - 5, pockets, px + 5, pockets + 12, pocket == landed ? TEXT : MachineMotion.tier(multipliers[pocket]), 4);
        }
        float bx, by;
        if (shown) {
            double[] ball = MachineMotion.ball(outcome[1], progress);
            bx = (float) ball[0] * spacing / 2;
            by = progress >= 1 ? pockets + 4 : top + (float) ball[1] * rowHeight + rowHeight - 4;
        } else {
            bx = 0;
            by = top;
        }
        sector(pose, buffers, bx, by, 0, 3, 0, 2 * Math.PI, TEXT, 5);
        quad(pose, buffers, -56, -174, 56, -66, GLASS, 6);
    }

    private static void gacha(PoseStack pose, WorldCanvas buffers, int[][] table, GameStationEntity station, boolean shown,
                              double progress, boolean rolling, double now) {
        int[] multipliers = table[0];
        float cy = -136;
        sector(pose, buffers, 0, cy, 0, 38, 0, 2 * Math.PI, BRASS, 3);
        sector(pose, buffers, 0, cy, 0, 36, 0, 2 * Math.PI, DARK, 3.5f);
        double shake = rolling && progress < 0.45 ? Math.sin(now * 2.2) * 2 : 0;
        for (int capsule = 0; capsule < 18; capsule++) {
            double angle = capsule * 2.399, distance = 5 + capsule * 37 % 23;
            float px = (float) (Math.cos(angle) * distance + shake), py = cy + 8 + (float) (Math.sin(angle) * distance * 0.65);
            int colour = MachineMotion.tier(multipliers.length == 0 ? 0 : multipliers[capsule % multipliers.length]);
            sector(pose, buffers, px, py, 0, 5, Math.PI, 2 * Math.PI, colour, 4);
            sector(pose, buffers, px, py, 0, 5, 0, Math.PI, TEXT, 4);
        }
        sector(pose, buffers, -12, cy - 16, 0, 7, 0, 2 * Math.PI, 0x22ffffff, 5);
        quad(pose, buffers, -32, -96, 32, -66, RED, 3);
        quad(pose, buffers, -32, -96, 32, -93, BRASS, 3.5f);
        quad(pose, buffers, -24, -88, -6, -70, DARK, 4);
        if (shown) {
            double drop = MachineMotion.capsuleDrop(progress), open = MachineMotion.capsuleOpen(progress);
            if (drop > 0) {
                float x = -15 + (float) (drop * 34), y = -79, lift = (float) (open * 6);
                int colour = MachineMotion.tier(station.multiplier);
                sector(pose, buffers, x, y - lift, 0, 7, Math.PI, 2 * Math.PI, colour, 5);
                sector(pose, buffers, x, y + lift, 0, 7, 0, Math.PI, TEXT, 5);
            }
        }
    }

    private static void dial(PoseStack pose, WorldCanvas buffers, Font font, int[][] table, int[] outcome, boolean shown,
                             double progress, boolean rolling, double now) {
        int[] targets = table[0];
        int index = Math.max(0, Math.min(targets.length - 1, shown ? outcome[0] : outcome[2]));
        int target = Math.max(101, targets[index]);
        float cx = -22, cy = -128, radius = 30;
        double win = 100.0 / target;
        sector(pose, buffers, cx, cy, 0, radius + 4, 0, 2 * Math.PI, BRASS, 3);
        sector(pose, buffers, cx, cy, radius - 9, radius, -Math.PI / 2, 1.5 * Math.PI, 0xff3a1820, 4);
        sector(pose, buffers, cx, cy, radius - 9, radius, -Math.PI / 2, -Math.PI / 2 + win * 2 * Math.PI, GREEN, 4.2f);
        sector(pose, buffers, cx, cy, 0, radius - 10, 0, 2 * Math.PI, DARK, 4);
        double needle = shown ? MachineMotion.needle(target, outcome[1], progress) : 0;
        double angle = needle * 2 * Math.PI - Math.PI / 2;
        sector(pose, buffers, cx, cy, 0, radius + 1, angle - 0.04, angle + 0.04, TEXT, 5);
        sector(pose, buffers, cx, cy, 0, 3, 0, 2 * Math.PI, BRASS, 5.5f);
        label(pose, buffers, font, GameScreens.multiplier(target), 32, -150, TEXT, 0.8f, 4);
        label(pose, buffers, font, BigDecimal.valueOf(10_000).divide(BigDecimal.valueOf(target), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "%", 32, -134, GREEN, 0.7f, 4);
        for (int bar = 0; bar < 8; bar++) {
            double level = rolling ? 0.5 + 0.5 * Math.sin(now * 0.9 + bar * 1.7) : 0.25;
            float x = 14 + bar * 5, height = (float) (2 + level * 14);
            quad(pose, buffers, x, -84 - height, x + 3, -84, rolling ? 0xff4cc3d9 : 0xff2a313d, 4);
        }
    }

    /** A slice of a ring, drawn as a strip of quads; a disc is a slice from the centre round. */
    private static void sector(PoseStack pose, WorldCanvas buffers, float cx, float cy, float inner, float outer,
                               double from, double to, int colour, float lift) {
        int steps = Math.max(1, (int) Math.ceil(Math.abs(to - from) / (Math.PI / 24)));
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
