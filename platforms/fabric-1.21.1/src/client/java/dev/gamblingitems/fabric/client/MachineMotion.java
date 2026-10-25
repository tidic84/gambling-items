package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.machines.MachineTable;

/**
 * Where the moving parts of a prize machine are at a moment of a play, the same in its window and
 * on its cabinet. Everything is rebuilt from the settled outcome and the progress of the play, so a
 * player who walks up halfway sees the same thing as the one who pulled.
 */
public final class MachineMotion {
    /** From ten times the stake, a win is celebrated: by the cabinet for everyone around it, and in the window. */
    public static final int BIG_WIN = 1000;
    /** How long the celebration lasts once the result shows, in ticks. */
    public static final int CELEBRATION = 70;
    /** Full turns a wheel or a needle makes before it settles. */
    private static final int TURNS = 4;

    private MachineMotion() {}

    /** The progress of a play, or the end of it at once for a player who asked for less motion. */
    public static double progress(double raw) {
        double clamped = Math.max(0, Math.min(1, raw));
        return ClientOptions.reducedMotion() && clamped > 0 ? 1 : clamped;
    }

    /** Where a sector starts on the wheel, as a share of a turn: each takes the share of its weight. */
    public static double sectorStart(int[] weights, int sector) {
        long before = 0, total = 0;
        for (int index = 0; index < weights.length; index++) {
            if (index < sector) before += weights[index];
            total += weights[index];
        }
        return total == 0 ? 0 : (double) before / total;
    }

    public static double sectorWidth(int[] weights, int sector) {
        long total = 0;
        for (int weight : weights) total += weight;
        return total == 0 ? 0 : (double) weights[sector] / total;
    }

    /**
     * How far the wheel has turned, in turns, so that once settled the pointer at the top reads the
     * drawn sector, at the place inside it the server chose. Before any play the wheel rests at zero.
     */
    public static double wheelTurn(int[] weights, int sector, int stop, double progress) {
        if (sector < 0 || sector >= weights.length) return 0;
        double inside = 0.1 + 0.8 * Math.max(0, Math.min(999, stop)) / 1000.0;
        double settled = -(sectorStart(weights, sector) + sectorWidth(weights, sector) * inside);
        return settled - TURNS * (1 - GameScreens.eased(progress));
    }

    /**
     * The ball of a pachinko board: its column, counted in half pin spacings from the middle, and its
     * row, from 0 at the top to {@link MachineTable#PACHINKO_ROWS} in the pockets, with a hop between rows.
     */
    public static double[] ball(int path, double progress) { return ball(path, progress, MachineTable.PACHINKO_ROWS); }

    /** The same ball on a board of any number of rows: Plinko's has twelve. */
    public static double[] ball(int path, double progress, int rows) {
        double row = progress * rows;
        int passed = (int) Math.min(rows, Math.floor(row));
        double column = 0;
        for (int index = 0; index < passed; index++) column += (path >> index & 1) == 1 ? 1 : -1;
        if (passed < rows) {
            double within = row - passed;
            column += ((path >> passed & 1) == 1 ? 1 : -1) * within;
            // A ball bounces off each pin before it falls on.
            row = passed + within - Math.sin(within * Math.PI) * 0.35;
        }
        return new double[] {column, row};
    }

    /** The claw: where it is along the rail, from 0 to 1, how far down, how closed, and whether it carries the prize. */
    public record Claw(double rail, double depth, double closed, boolean carrying, double prizeFall) {}

    /** {@code home} is the rail position of the chute, {@code target} that of the chosen prize. */
    public static Claw claw(double home, double target, boolean held, double progress) {
        if (progress <= 0) return new Claw(target, 0, 0, false, 0);
        if (progress < 0.2) return new Claw(target, 0, 0, false, 0);
        if (progress < 0.4) return new Claw(target, smooth((progress - 0.2) / 0.2), 0, false, 0);
        if (progress < 0.5) return new Claw(target, 1, smooth((progress - 0.4) / 0.1), false, 0);
        if (progress < 0.7) {
            double up = smooth((progress - 0.5) / 0.2);
            // A loose grip lets go halfway up.
            boolean carrying = held || up < 0.5;
            double fall = held || up < 0.5 ? 0 : Math.min(1, (up - 0.5) * 4);
            return new Claw(target, 1 - up, held || up < 0.5 ? 1 : 0.6, carrying, fall);
        }
        double back = smooth((progress - 0.7) / 0.25);
        return new Claw(target + (home - target) * back, 0, held && progress < 0.95 ? 1 : 0, held && progress < 0.95,
                held ? 0 : 1);
    }

    /** The coins a push tips over: none for nothing, more the more it pays, at most six. */
    public static int coins(int multiplier) {
        if (multiplier <= 0) return 0;
        int coins = 1;
        for (int share = 50; share * 2 <= multiplier && coins < 6; share *= 2) coins++;
        return coins;
    }

    /** The pusher plate, from 0 back to 1 forward, sweeping steadily whatever happens. */
    public static double plate(double ticks) {
        return 0.5 - 0.5 * Math.cos(ticks * Math.PI / 20);
    }

    /** How far a falling coin has gone, from 0 on the shelf to 1 in the tray, the n-th one a little later. */
    public static double coinFall(int coin, double progress) {
        double start = 0.45 + coin * 0.06;
        return Math.max(0, Math.min(1, (progress - start) / 0.25));
    }

    /** The gacha crank, in turns; the capsule's way down the chute; how open it is. */
    public static double crank(double progress) { return 2 * smooth(Math.min(1, progress / 0.45)); }
    public static double capsuleDrop(double progress) { return smooth((progress - 0.45) / 0.25); }
    public static double capsuleOpen(double progress) { return smooth((progress - 0.75) / 0.2); }

    /** The needle of the high stakes dial, as a share of a turn: it settles where the server stopped it. */
    public static double needle(int target, int stop, double progress) {
        if (target <= 0) return 0;
        double settled = (stop + 0.5) / target;
        return settled + TURNS * (1 - GameScreens.eased(progress));
    }

    private static double smooth(double value) {
        double clamped = Math.max(0, Math.min(1, value));
        return clamped * clamped * (3 - 2 * clamped);
    }

    /**
     * The colour of a prize, read from how much it pays: blank, part back, even, small, good,
     * great, jackpot. Every prize is also written out, so no result rests on its colour alone.
     */
    public static int tier(int multiplier) {
        if (multiplier <= 0) return 0xff2a313d;
        if (multiplier < 100) return 0xff4a5f78;
        if (multiplier < 150) return 0xff3d7d8c;
        if (multiplier < 250) return 0xff17784a;
        if (multiplier < 600) return 0xff4cc3d9;
        if (multiplier < 1200) return 0xffd9b25a;
        return 0xffd2434f;
    }
}
