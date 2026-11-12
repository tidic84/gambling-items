package dev.gamblingitems.core.machines;

import dev.gamblingitems.core.GameMode;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TableGamesTest {
    @Test
    void theCrapsEventsAreTheExactOnes() {
        // The pass line: 8 naturals in 36, then each point p with ways w(p) wins w / (w + 6) of the time.
        double win = 8 / 36.0;
        for (int ways : new int[] {3, 4, 5, 5, 4, 3}) win += ways / 36.0 * ways / (ways + 6.0);
        assertEquals(win * 1980, CrapsRules.EVENTS[CrapsRules.PASS_WINS], 1e-9);
        assertEquals(1980 / 36, CrapsRules.EVENTS[CrapsRules.BAR_TWELVE]);
        assertEquals(1980, Arrays.stream(CrapsRules.EVENTS).sum());
    }

    @Test
    void theDiceShownLeadToTheDrawnEvent() {
        for (int event = 0; event < 3; event++) {
            for (int seed = 0; seed < 200; seed++) {
                List<Integer> rolls = CrapsRules.rolls(event, seed);
                assertEquals(event, CrapsRules.event(rolls));
                for (int roll : rolls) assertTrue(roll / 10 >= 1 && roll / 10 <= 6 && roll % 10 >= 1 && roll % 10 <= 6);
            }
        }
        assertEquals(CrapsRules.rolls(0, 77), CrapsRules.rolls(0, 77), "Every viewer sees the same dice");
    }

    @Test
    void crapsPaysEachLineAsPrinted() {
        MachineTable craps = new MachineTable(GameMode.CRAPS, new int[] {200, 200}, new int[0]);
        assertEquals(new BigDecimal("0.985859"), craps.returnToPlayer(0), "The pass line keeps 1.41%");
        assertEquals(new BigDecimal("0.986364"), craps.returnToPlayer(1), "The don't pass line keeps 1.36%");
        // Ticket 0 is a pass win; past 976 + 949 the come-out was a barred twelve.
        assertEquals(200, craps.play(0, draws(0, 5)).multiplier());
        assertEquals(0, craps.play(1, draws(0, 5)).multiplier());
        assertEquals(100, craps.play(1, draws(1925, 5)).multiplier(), "A barred twelve gives the stake back");
        assertArrayEquals(new int[] {CrapsRules.BAR_TWELVE, 5}, craps.play(0, draws(1979, 5)).shown());
    }

    @Test
    void baccaratHasItsKnownChances() {
        int[] events = BaccaratRules.EVENTS;
        assertEquals(0.4462466, events[BaccaratRules.PLAYER] / 1e9, 2e-7);
        assertEquals(0.4585974, events[BaccaratRules.BANKER] / 1e9, 2e-7);
        assertEquals(0.0951560, events[BaccaratRules.TIE] / 1e9, 2e-7);
        MachineTable baccarat = new MachineTable(GameMode.BACCARAT, new int[] {200, 195, 900}, new int[0]);
        assertEquals(0.98765, baccarat.returnToPlayer(0).doubleValue(), 1e-4, "The player bet keeps 1.24%");
        assertEquals(0.98942, baccarat.returnToPlayer(1).doubleValue(), 1e-4, "The banker bet keeps 1.06%");
        assertEquals(0.85641, baccarat.returnToPlayer(2).doubleValue(), 1e-4, "The tie keeps 14.36%");
    }

    @Test
    void theThirdCardRuleIsTheTableau() {
        // Player 5 draws a 4 (9); banker 5 draws on a 4.
        BaccaratRules.Coup coup = BaccaratRules.coup(new int[] {1, 1, 2, 2, 3, 6});
        assertEquals(3, coup.player().length);
        assertEquals(3, coup.banker().length);
        // A natural: nobody draws.
        coup = BaccaratRules.coup(new int[] {7, 0, 9, 0, 4, 4});
        assertEquals(2, coup.player().length);
        assertEquals(2, coup.banker().length);
        // Banker 6 stands when the player drew an 8... and draws on a 6.
        assertEquals(2, BaccaratRules.coup(new int[] {9, 2, 9, 2, 7, 0}).banker().length);
        assertEquals(3, BaccaratRules.coup(new int[] {9, 2, 9, 2, 5, 0}).banker().length);
        for (int result = 0; result < 3; result++) {
            for (int seed = 0; seed < 100; seed++) assertEquals(result, BaccaratRules.deal(result, seed).result());
        }
    }

    @Test
    void aKenoBoardHasExactlyItsHits() {
        assertEquals(24_040_016, Arrays.stream(KenoBoard.WEIGHTS).sum(), "C(80, 5)");
        for (int hits = 0; hits <= 5; hits++) {
            for (int seed = 0; seed < 100; seed++) {
                KenoBoard.Board board = KenoBoard.layout(hits, seed);
                long found = Arrays.stream(board.spots()).filter(spot -> Arrays.stream(board.drawn()).anyMatch(n -> n == spot)).count();
                assertEquals(hits, found);
                assertEquals(5, Arrays.stream(board.spots()).distinct().count());
                assertEquals(20, Arrays.stream(board.drawn()).distinct().filter(n -> n >= 1 && n <= 80).count());
            }
        }
        MachineTable keno = new MachineTable(GameMode.KENO, new int[] {0, 0, 50, 300, 2000, 50000}, new int[0]);
        assertEquals(new BigDecimal("0.951343"), keno.returnToPlayer(0));
    }

    @Test
    void aScratchCardShowsItsPrizeThreeTimesAndNoOtherTriple() {
        int[] multipliers = {0, 100, 200, 500, 2500};
        for (int prize = 0; prize < multipliers.length; prize++) {
            for (int seed = 0; seed < 100; seed++) {
                int[] cells = ScratchCard.layout(multipliers, prize, seed);
                assertEquals(ScratchCard.CELLS, cells.length);
                for (int symbol = 0; symbol < multipliers.length; symbol++) {
                    final int wanted = symbol;
                    long count = Arrays.stream(cells).filter(cell -> cell == wanted).count();
                    if (symbol == prize && multipliers[prize] > 0) assertEquals(3, count);
                    else assertTrue(count <= 2, "No other triple");
                    if (multipliers[symbol] == 0) assertEquals(0, count, "A blank prize is printed as a star");
                }
            }
        }
    }

    private static java.util.function.IntUnaryOperator draws(int... values) {
        java.util.Deque<Integer> queue = new java.util.ArrayDeque<>();
        for (int value : values) queue.add(value);
        return bound -> queue.remove();
    }
}
