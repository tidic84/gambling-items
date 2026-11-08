package dev.gamblingitems.core.machines;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlinkoFlightTest {
    private static final int ROWS = MachineTable.PLINKO_ROWS;
    private static final double DROP = 1.6;

    @Test
    void theBallIsLetGoAboveTheMiddleAndSettlesInTheDrawnPocket() {
        for (int path : new int[] {0, 0b101101, (1 << ROWS) - 1, 0b100000000001}) {
            PlinkoFlight.Point start = PlinkoFlight.at(path, ROWS, DROP, 0);
            assertEquals(0, start.column(), 1e-9);
            assertEquals(-PlinkoFlight.CHUTE, start.row(), 1e-9);
            PlinkoFlight.Point end = PlinkoFlight.at(path, ROWS, DROP, 1);
            assertEquals(2 * Integer.bitCount(path & ((1 << ROWS) - 1)) - ROWS, end.column(), 1e-9);
            assertEquals(ROWS - 1 + DROP, end.row(), 1e-9);
        }
    }

    @Test
    void theFallIsContinuousAndTouchesEveryPegOfThePath() {
        int path = 0b011010011101;
        PlinkoFlight.Point previous = PlinkoFlight.at(path, ROWS, DROP, 0);
        int lastHit = -1;
        for (int step = 1; step <= 4000; step++) {
            PlinkoFlight.Point point = PlinkoFlight.at(path, ROWS, DROP, step / 4000.0);
            assertTrue(Math.abs(point.column() - previous.column()) < 0.05, "no jump sideways at " + step);
            assertTrue(Math.abs(point.row() - previous.row()) < 0.05, "no jump down at " + step);
            // Never below the floor of the pocket, and every peg struck in turn.
            assertTrue(point.row() <= ROWS - 1 + DROP + 1e-9);
            assertTrue(point.hitRow() == lastHit || point.hitRow() == lastHit + 1, "pegs struck in order at " + step);
            lastHit = point.hitRow();
            previous = point;
        }
        assertEquals(ROWS - 1, lastHit);
    }

    @Test
    void everyClientSeesTheSameFall() {
        assertEquals(PlinkoFlight.at(1234, ROWS, DROP, 0.37), PlinkoFlight.at(1234, ROWS, DROP, 0.37));
    }
}
