package dev.gamblingitems.core.battle;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BattleRulesTest {
    @Test
    void aBattleHoldsTwoToFourSeatsAndAFewRounds() {
        assertFalse(BattleRules.acceptsPlayers(1), "A battle needs an opponent");
        assertTrue(BattleRules.acceptsPlayers(2));
        assertTrue(BattleRules.acceptsPlayers(4));
        assertFalse(BattleRules.acceptsPlayers(5));
        assertFalse(BattleRules.acceptsRounds(0));
        assertTrue(BattleRules.acceptsRounds(1));
        assertTrue(BattleRules.acceptsRounds(16));
        assertFalse(BattleRules.acceptsRounds(17));
    }

    @Test
    void theBestScoreTakesEverything() {
        assertEquals(List.of(1), BattleRules.leaders(List.of(10L, 40L, 25L)));
        assertEquals(1, BattleRules.winner(List.of(10L, 40L, 25L), 0, 10), "No draw is needed");
        assertEquals(1, BattleRules.winner(List.of(10L, 40L, 25L), 9, 10), "Whatever the draw says");
    }

    @Test
    void aTieIsSettledByAUniformDrawBetweenTheTiedSeats() {
        List<Long> tied = List.of(40L, 40L, 25L, 40L);
        assertEquals(List.of(0, 1, 3), BattleRules.leaders(tied));
        assertEquals(0, BattleRules.winner(tied, 0, 3));
        assertEquals(1, BattleRules.winner(tied, 1, 3));
        assertEquals(3, BattleRules.winner(tied, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> BattleRules.winner(tied, 3, 3));
        assertThrows(IllegalArgumentException.class, () -> BattleRules.winner(tied, -1, 3));
    }

    @Test
    void everySeatCanWinATieEqually() {
        List<Long> tied = List.of(7L, 7L);
        long bound = 1_000;
        int first = 0;
        for (long draw = 0; draw < bound; draw++) {
            if (BattleRules.winner(tied, draw, bound) == 0) first++;
        }
        assertEquals(bound / 2, first, "Both tied seats win as often as each other");
    }

    @Test
    void refusesAnEmptyBattle() {
        assertThrows(IllegalArgumentException.class, () -> BattleRules.leaders(List.of()));
    }
}
