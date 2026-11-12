package dev.gamblingitems.core.machines;

/**
 * The fall of a Plinko ball, for show only: it drops from the chute onto the first peg, hops off each peg
 * on a real arc to the next one, falls into its pocket and bounces there twice before it settles.
 *
 * <p>Which side of each peg it passes is the drawn path, bit k for peg row k, so the pocket is always the
 * one the server settled. How high it hops off each peg is a fixed hash of that path: every client sees
 * the very same fall. Rows count from 0, a ball "on" row k resting on top of the peg of row k; columns are
 * half pin spacings from the middle. Time runs in units where gravity is 1, so the ball speeds up as it
 * falls and slows as it rises, as a ball does.
 */
public final class PlinkoFlight {
    /** How far above the first peg the ball is let go, in rows. */
    public static final double CHUTE = 1.5;
    private static final double[] SETTLE = {0.22, 0.07};

    /**
     * Where the ball is: its column and row, how far it has rolled (radians), and the peg it last struck:
     * its row (-1 before the first), its column, and the time since.
     */
    public record Point(double column, double row, double spin, int hitRow, double hitColumn, double sinceHit) {}

    private PlinkoFlight() {}

    /**
     * @param rows       the peg rows of the board
     * @param pocketDrop how far below the last peg row the ball rests in its pocket, in rows
     * @param progress   0 when let go, 1 once settled
     */
    public static Point at(int path, int rows, double pocketDrop, double progress) {
        double[] durations = new double[rows + 1 + SETTLE.length];
        durations[0] = Math.sqrt(2 * CHUTE);
        for (int peg = 0; peg < rows; peg++) durations[peg + 1] = flight(hop(path, peg), peg + 1 < rows ? 1 : pocketDrop);
        for (int bounce = 0; bounce < SETTLE.length; bounce++) durations[rows + 1 + bounce] = 2 * Math.sqrt(2 * SETTLE[bounce]);
        double total = 0;
        for (double duration : durations) total += duration;
        double time = Math.max(0, Math.min(1, progress)) * total;
        int segment = 0;
        while (segment < durations.length - 1 && time > durations[segment]) time -= durations[segment++];
        time = Math.min(time, durations[segment]);

        // The pegs already left behind: none in the chute, all of them once in the pocket.
        int passed = segment > rows ? rows : Math.max(0, segment - 1);
        double column = 0;
        for (int peg = 0; peg < passed; peg++) column += side(path, peg);
        if (segment == 0) return new Point(0, -CHUTE + time * time / 2, 0, -1, 0, 0);
        if (segment <= rows) {
            int peg = segment - 1;
            double from = column;
            double v = Math.sqrt(2 * hop(path, peg)), share = time / durations[segment];
            double here = from + side(path, peg) * share;
            return new Point(here, peg - v * time + time * time / 2, here * 1.4, peg, from, time);
        }
        // In the pocket: the last peg is passed, and two little bounces on the floor.
        double floor = rows - 1 + pocketDrop, v = Math.sqrt(2 * SETTLE[segment - rows - 1]);
        double row = floor - (v * time - time * time / 2);
        return new Point(column, Math.min(floor, row), column * 1.4, rows - 1, column - side(path, rows - 1), 99);
    }

    /** Time of a hop that rises off a peg to a height and comes down a drop below where it left. */
    private static double flight(double height, double drop) {
        double v = Math.sqrt(2 * height);
        return v + Math.sqrt(v * v + 2 * drop);
    }

    private static int side(int path, int peg) { return (path >> peg & 1) == 1 ? 1 : -1; }

    /** How high the ball hops off a peg, from a tenth of a row to four tenths: a hash of the path, never random. */
    private static double hop(int path, int peg) {
        int h = path * 0x9E3779B1 + (peg + 1) * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return 0.1 + 0.3 * (h & 0xffff) / 65535.0;
    }
}
