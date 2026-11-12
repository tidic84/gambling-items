package dev.gamblingitems.core.prediction;

import dev.gamblingitems.core.prediction.PredictionMarket.Purchase;
import dev.gamblingitems.core.prediction.PredictionMarket.Side;
import dev.gamblingitems.core.prediction.PredictionMarket.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PredictionMarketTest {
    private static final long SHARE = 1_000;

    @Test
    void theMakerQuotesFiftyFiftyAndBoundsItsLoss() {
        Lmsr maker = Lmsr.funded(10);
        assertEquals(0.5, maker.priceYes(0, 0), 1e-12);
        assertEquals(10, maker.worstLoss(), 1e-9);
        double bought = maker.sharesFor(true, 0, 0, 3);
        assertEquals(3, maker.buyCost(true, 0, 0, bought), 1e-9, "Spending buys exactly what it costs");
        assertEquals(3, maker.sellValue(true, bought, 0, bought), 1e-9, "Selling straight back returns it");
        assertTrue(maker.priceYes(bought, 0) > 0.5, "Buying YES raises its price");
    }

    @Test
    void largePositionsNeverOverflow() {
        Lmsr maker = new Lmsr(5);
        double cost = maker.cost(1e6, 0);
        assertTrue(Double.isFinite(cost));
        assertEquals(1e6, cost, 1e-3);
        assertTrue(Double.isFinite(maker.sharesFor(false, 1e6, 0, 10)));
    }

    @Test
    void aPurchaseTakesItsFeeAndMovesThePrice() {
        PredictionMarket market = PredictionMarket.open(10_000, SHARE, 200);
        assertEquals(5_000, market.priceBasisPoints());
        Purchase purchase = market.buy(Side.YES, 5_000);
        assertNotNull(purchase);
        assertEquals(100, purchase.fee(), "Two percent of the spend");
        assertEquals(4_900, purchase.net());
        assertEquals(14_900, market.pool());
        assertEquals(100, market.fees());
        assertTrue(market.priceBasisPoints() > 5_000);
        assertTrue(purchase.shares() > 4_900, "Below one a share costs less than it pays");
        assertEquals(100, market.takeFees());
        assertEquals(0, market.fees(), "Fees are handed over once");
    }

    @Test
    void aSpendTooSmallToBuyAThousandthIsRefused() {
        PredictionMarket market = PredictionMarket.open(10_000, SHARE, 0);
        assertNull(market.buy(Side.NO, 0));
        assertEquals(10_000, market.pool());
    }

    @Test
    void sellingBackNeverReturnsMoreThanWasPaid() {
        PredictionMarket market = PredictionMarket.open(10_000, SHARE, 0);
        Purchase purchase = market.buy(Side.NO, 3_000);
        long back = market.sell(Side.NO, purchase.shares());
        assertTrue(back <= 3_000 && back >= 2_990, "Only the rounding is lost: " + back);
        assertEquals(0, market.noShares());
        assertEquals(5_000, market.priceBasisPoints());
    }

    @Test
    void aResolvedMarketPaysTheWinnersInFullAndReturnsTheRestToItsCreator() {
        for (Side answer : Side.values()) {
            PredictionMarket market = PredictionMarket.open(5_000, SHARE, 200);
            Purchase yes = market.buy(Side.YES, 7_000);
            Purchase no = market.buy(Side.NO, 2_000);
            Purchase more = market.buy(Side.YES, 4_000);
            long left = market.resolve(answer);
            assertTrue(left >= 0);
            assertEquals(Status.RESOLVED, market.status());
            long paid = 0;
            paid += market.redeem(Side.YES, yes.shares());
            paid += market.redeem(Side.NO, no.shares());
            paid += market.redeem(Side.YES, more.shares());
            long winners = answer.yes() ? yes.shares() + more.shares() : no.shares();
            assertEquals(winners * SHARE / PredictionMarket.MILLI, paid, 1, "Every winning share pays one unit");
            assertTrue(market.pool() >= 0 && market.pool() <= 2, "Only the rounding stays behind: " + market.pool());
        }
    }

    @Test
    void theCreatorKeepsWhatTheMakerDidNotLose() {
        PredictionMarket market = PredictionMarket.open(5_000, SHARE, 0);
        // Nobody traded: the whole subsidy comes back.
        assertEquals(5_000, market.resolve(Side.NO));
    }

    @Test
    void aCancelledMarketRepaysEveryTicketAtTheLastPrice() {
        PredictionMarket market = PredictionMarket.open(5_000, SHARE, 0);
        Purchase yes = market.buy(Side.YES, 6_000);
        Purchase no = market.buy(Side.NO, 1_000);
        int price = market.priceBasisPoints();
        market.cancel();
        assertEquals(price, market.priceBasisPoints(), "The last price is frozen");
        long first = market.redeem(Side.YES, yes.shares());
        long second = market.redeem(Side.NO, no.shares());
        assertEquals(yes.shares() * SHARE * price / (PredictionMarket.MILLI * PredictionMarket.BASIS), first, 1);
        assertTrue(second > 0);
        assertTrue(market.pool() >= 0);
    }

    @Test
    void randomTradingNeverLeavesAMarketUnableToPay() {
        Random random = new Random(42);
        for (int round = 0; round < 200; round++) {
            PredictionMarket market = PredictionMarket.open(1_000 + random.nextInt(20_000), SHARE, random.nextInt(500));
            List<long[]> tickets = new ArrayList<>();
            for (int trade = 0; trade < 30; trade++) {
                Side side = random.nextBoolean() ? Side.YES : Side.NO;
                if (!tickets.isEmpty() && random.nextInt(4) == 0) {
                    long[] ticket = tickets.remove(random.nextInt(tickets.size()));
                    market.sell(Side.values()[(int) ticket[0]], ticket[1]);
                    continue;
                }
                Purchase purchase = market.buy(side, 1 + random.nextInt(30_000));
                if (purchase != null) tickets.add(new long[] {side.ordinal(), purchase.shares()});
            }
            boolean cancel = random.nextInt(5) == 0;
            Side answer = random.nextBoolean() ? Side.YES : Side.NO;
            long left = cancel ? market.cancel() : market.resolve(answer);
            assertTrue(left >= 0);
            for (long[] ticket : tickets) {
                Side side = Side.values()[(int) ticket[0]];
                long owed = cancel
                        ? (long) Math.floor((double) ticket[1] * SHARE * market.priceBasisPoints(side) / (PredictionMarket.MILLI * PredictionMarket.BASIS))
                        : side == answer ? ticket[1] * SHARE / PredictionMarket.MILLI : 0;
                assertEquals(owed, market.redeem(side, ticket[1]), "Round " + round + " pays every ticket in full");
            }
            assertTrue(market.pool() >= 0);
        }
    }

    @Test
    void aProposedAnswerStandsUnlessItIsDisputed() {
        PredictionMarket market = PredictionMarket.open(5_000, SHARE, 0);
        market.close();
        assertFalse(market.trading());
        assertNull(market.buy(Side.YES, 1_000), "A closed market does not sell");
        market.propose(Side.YES, 1_000, 500);
        assertEquals(-1, market.finishChallenge(1_499), "The window is still open");
        assertEquals(5_000, market.finishChallenge(1_500), "Nobody traded: the subsidy goes back");
        assertEquals(Status.RESOLVED, market.status());
        assertEquals(Side.YES, market.outcome());

        PredictionMarket disputed = PredictionMarket.open(5_000, SHARE, 0);
        disputed.propose(Side.NO, 0, 500);
        disputed.dispute();
        assertEquals(-1, disputed.finishChallenge(10_000), "A disputed answer waits for a judge");
        assertEquals(Status.DISPUTED, disputed.status());
        disputed.resolve(Side.YES);
        assertEquals(Side.YES, disputed.outcome());
        assertThrows(IllegalStateException.class, () -> disputed.resolve(Side.NO));
    }

    @Test
    void aSavedMarketComesBackExactly() {
        PredictionMarket market = PredictionMarket.open(8_000, SHARE, 150);
        market.buy(Side.YES, 3_000);
        market.buy(Side.NO, 1_234);
        PredictionMarket copy = PredictionMarket.restore(market.shareValue(), market.feeBasisPoints(),
                market.liquidity(), market.yesShares(), market.noShares(), market.pool(), market.fees(),
                market.volume(), market.status(), market.outcome(), market.proposal(), market.challengeEnds(),
                market.cancelPrice());
        assertEquals(market.priceBasisPoints(), copy.priceBasisPoints());
        assertEquals(market.quote(Side.YES, 999).shares(), copy.quote(Side.YES, 999).shares());
        assertEquals(market.quoteSell(Side.NO, 100), copy.quoteSell(Side.NO, 100));
    }
}
