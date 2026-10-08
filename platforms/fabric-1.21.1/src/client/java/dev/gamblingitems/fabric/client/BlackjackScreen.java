package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.blackjack.BlackjackRules;
import dev.gamblingitems.core.blackjack.BlackjackRules.Outcome;
import dev.gamblingitems.fabric.blackjack.BlackjackMenu;
import dev.gamblingitems.fabric.blackjack.BlackjackSettings;
import dev.gamblingitems.fabric.blackjack.BlackjackTable;
import dev.gamblingitems.fabric.menu.CasinoLayout;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** A hand against the house: the cards are drawn as cards, and only face up ones are known. */
public final class BlackjackScreen extends CasinoScreen<BlackjackMenu> {
    private static final int FELT = 0xff14472c, FELT_LINE = 0xff2d6b46;
    private static final int CARD = 0xfff3f6fb, CARD_BACK = 0xff7a2130, CARD_EDGE = 0xff20262f;
    private static final int CARD_WIDTH = 24, CARD_HEIGHT = 34, CARD_GAP = 6;
    /** A card takes this many ticks to land on the felt. */
    private static final int ANIMATION = 6;
    private static final int GX = CasinoLayout.GAME_X, GY = CasinoLayout.CONTENT_Y, GW = CasinoLayout.GAME_WIDTH;
    private static final int GH = CasinoLayout.CONTENT_HEIGHT;
    private static final int SX = CasinoLayout.SIDE_X, SW = CasinoLayout.SIDE_WIDTH;
    private static final int TABLE_X = GX + 10, DEALER_Y = GY + 15, PLAYER_Y = GY + 66;
    private Button deal, hit, stand, doubleDown, collect;
    /** How many cards each row held last tick, and how long ago the newest one was dealt. */
    private int seenHand, seenDealer, handDealt = ANIMATION, dealerDealt = ANIMATION;

    public BlackjackScreen(BlackjackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected GameMode mode() { return GameMode.BLACKJACK; }
    @Override protected boolean portable() { return menu.portable(); }
    @Override protected Component subtitle() {
        return Component.translatable("gui.gamblingitems.blackjack_subtitle", GameScreens.value(menu.settings().minimumStake()));
    }

    @Override protected boolean gameClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override protected void init() {
        super.init();
        int bx = leftPos + CasinoLayout.SLIP_X + 8, by = topPos + CasinoLayout.SLIP_Y + 52, bw = CasinoLayout.SLIP_WIDTH - 16;
        int third = (bw - 8) / 3;
        deal = addRenderableWidget(CasinoButton.primary(tr("deal"), button -> click(BlackjackMenu.DEAL_BUTTON))
                .bounds(bx, by, bw, 20).build());
        hit = addRenderableWidget(CasinoButton.primary(tr("hit"), button -> click(BlackjackMenu.HIT_BUTTON))
                .bounds(bx, by, third, 20).build());
        stand = addRenderableWidget(CasinoButton.builder(tr("stand"), button -> click(BlackjackMenu.STAND_BUTTON))
                .bounds(bx + third + 4, by, third, 20).build());
        doubleDown = addRenderableWidget(CasinoButton.builder(tr("double"), button -> click(BlackjackMenu.DOUBLE_BUTTON))
                .bounds(bx + 2 * (third + 4), by, bw - 2 * (third + 4), 20).build());
        doubleDown.setTooltip(Tooltip.create(tr("double_help")));
        collect = addRenderableWidget(CasinoButton.builder(tr("collect_short"),
                        button -> click(BlackjackMenu.COLLECT_BUTTON))
                .bounds(leftPos + SX + 6, topPos + GY + GH - 22, SW - 12, 16).build());
        collect.setTooltip(Tooltip.create(tr("collect_help")));
        refreshActions();
    }

    private void click(int button) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override protected void containerTick() {
        super.containerTick();
        // A row that grew has just been dealt a card; that card slides in from the shoe.
        int hand = menu.hand().size(), dealer = menu.dealer().size() + (menu.dealerHidden() ? 1 : 0);
        if (hand > seenHand) handDealt = 0;
        if (dealer > seenDealer) dealerDealt = 0;
        seenHand = hand;
        seenDealer = dealer;
        if (handDealt < ANIMATION) handDealt++;
        if (dealerDealt < ANIMATION) dealerDealt++;
        refreshActions();
    }

    /** Before a hand the slip offers to deal; during one it offers the three moves instead. */
    private void refreshActions() {
        boolean playing = menu.phase() == BlackjackTable.Phase.PLAYER;
        deal.visible = !playing;
        hit.visible = stand.visible = doubleDown.visible = playing;
        deal.active = menu.canDeal();
        deal.setMessage(menu.plannedStake() > 0
                ? Component.translatable("gui.gamblingitems.start_amount", GameScreens.value(menu.plannedStake()))
                : tr("start_hand"));
        if (menu.plannedStake() > 0 && !menu.isPayable()) deal.setTooltip(Tooltip.create(tr("bet_unpayable")));
        else deal.setTooltip(null);
        hit.active = menu.canAct();
        stand.active = menu.canAct();
        doubleDown.active = menu.canDouble();
        collect.active = menu.winnings() > 0;
        collect.setMessage(menu.winnings() > 0
                ? Component.translatable("gui.gamblingitems.collect_amount", GameScreens.value(menu.winnings()))
                : tr("collect_short"));
    }

    @Override protected void renderGame(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        GameScreens.rounded(g, x + GX, y + GY, GW, GH, FELT_LINE);
        GameScreens.rounded(g, x + GX + 1, y + GY + 1, GW - 2, GH - 2, FELT);
        renderHands(g, x, y, partialTick);

        GameScreens.card(g, x + SX, y + GY, SW, GH);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.bet_row", GameScreens.value(menu.plannedStake())),
                x + BlackjackMenu.STAKE_X, y + BlackjackMenu.INPUT_Y - 11, SW - 16, GameScreens.MUTED);
        GameScreens.label(g, font, tr("chips_on_table"), x + BlackjackMenu.STAKE_X, y + BlackjackMenu.ENGAGED_Y - 11, SW - 16);
        for (Slot slot : menu.slots)
            if (!(slot.container instanceof Inventory)) GameScreens.slot(g, x + slot.x, y + slot.y);

        slip(g);
        renderStatus(g, x + CasinoLayout.SLIP_X + 8, y + CasinoLayout.SLIP_Y + 8);
    }

    /** How far the newest card of a row still has to travel, from one to zero. */
    private float landing(int dealt, float partialTick) {
        float progress = Math.min(1, (dealt + partialTick) / ANIMATION);
        return 1 - progress * progress;
    }

    private void renderHands(GuiGraphics g, int x, int y, float partialTick) {
        g.drawString(font, tr("dealer"), x + TABLE_X, y + DEALER_Y - 10, GameScreens.MUTED, false);
        List<Integer> dealer = menu.dealer();
        int dealerCards = dealer.size() + (menu.dealerHidden() ? 1 : 0);
        float dealerLanding = landing(dealerDealt, partialTick);
        for (int index = 0; index < dealer.size(); index++) {
            int slide = index == dealerCards - 1 ? (int) (dealerLanding * 60) : 0;
            card(g, x + TABLE_X + index * (CARD_WIDTH + CARD_GAP) + slide, y + DEALER_Y, dealer.get(index));
        }
        if (menu.dealerHidden()) {
            int slide = (int) (dealerLanding * 60);
            hidden(g, x + TABLE_X + dealer.size() * (CARD_WIDTH + CARD_GAP) + slide, y + DEALER_Y);
        }
        String dealerTotal = menu.dealerHidden()
                ? menu.dealerTotal() + "+" : String.valueOf(menu.dealerTotal());
        g.drawString(font, dealerTotal, x + GX + GW - 10 - font.width(dealerTotal), y + DEALER_Y + 12, GameScreens.TEXT, false);

        // The seat is named, so a player reads their own score at a glance.
        String seat = minecraft.player == null ? tr("your_hand").getString()
                : minecraft.player.getGameProfile().getName();
        g.drawString(font, seat, x + TABLE_X, y + PLAYER_Y - 10, GameScreens.MUTED, false);
        List<Integer> hand = menu.hand();
        float handLanding = landing(handDealt, partialTick);
        for (int index = 0; index < hand.size(); index++) {
            int slide = index == hand.size() - 1 ? (int) (handLanding * 60) : 0;
            card(g, x + TABLE_X + index * (CARD_WIDTH + CARD_GAP) + slide, y + PLAYER_Y, hand.get(index));
        }
        int total = menu.handTotal();
        int colour = total > BlackjackRules.BLACKJACK ? GameScreens.RED
                : total == BlackjackRules.BLACKJACK ? GameScreens.GREEN : GameScreens.TEXT;
        String score = total == 0 ? "-" : String.valueOf(total);
        g.drawString(font, score, x + GX + GW - 10 - font.width(score), y + PLAYER_Y + 12, colour, false);
    }

    /** One card, face up: its rank and its suit, in the colour of that suit. */
    private void card(GuiGraphics g, int x, int y, int value) {
        g.fill(x - 1, y - 1, x + CARD_WIDTH + 1, y + CARD_HEIGHT + 1, CARD_EDGE);
        g.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, CARD);
        int suit = BlackjackRules.suitOf(value);
        int colour = suit == 0 || suit == 1 ? 0xffb3222e : 0xff161b22;
        String rank = rankName(BlackjackRules.rankOf(value));
        g.drawString(font, rank, x + 2, y + 2, colour, false);
        g.drawString(font, suitName(suit), x + CARD_WIDTH - 9, y + CARD_HEIGHT - 10, colour, false);
    }

    /** The hole card, drawn face down: nothing about it has been sent to this client. */
    private void hidden(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + CARD_WIDTH + 1, y + CARD_HEIGHT + 1, CARD_EDGE);
        g.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, CARD_BACK);
        g.fill(x + 3, y + 3, x + CARD_WIDTH - 3, y + CARD_HEIGHT - 3, 0xff5d1825);
    }

    private static String rankName(int rank) {
        return switch (rank) {
            case 0 -> "A";
            case 9 -> "10";
            case 10 -> "J";
            case 11 -> "Q";
            case 12 -> "K";
            default -> String.valueOf(rank + 1);
        };
    }

    private static String suitName(int suit) {
        return switch (suit) {
            case 0 -> "♥";
            case 1 -> "♦";
            case 2 -> "♣";
            default -> "♠";
        };
    }

    private void renderStatus(GuiGraphics g, int sx, int sy) {
        boolean inHand = menu.phase() != BlackjackTable.Phase.IDLE || menu.stake() > 0;
        long shown = inHand ? menu.stake() : menu.plannedStake();
        GameScreens.label(g, font, tr(inHand ? "engaged" : "stake"), sx, sy, 90);
        GameScreens.heading(g, font, Component.literal(GameScreens.value(shown)), sx, sy + 12, 2,
                !inHand && shown > 0 && !menu.isPayable() ? GameScreens.RED : GameScreens.TEXT);
        GameScreens.label(g, font, tr("winnings"), sx + 100, sy, 66);
        GameScreens.fitted(g, font, Component.literal(GameScreens.value(menu.winnings())), sx + 100, sy + 12, 66,
                menu.winnings() > 0 ? GameScreens.GOLD : GameScreens.MUTED);

        Component status = switch (menu.phase()) {
            case IDLE -> menu.outcome() == Outcome.PLAYING ? tr("blackjack_ready") : outcomeText();
            case PLAYER -> tr("blackjack_your_turn");
            case DEALER -> tr("blackjack_dealer_turn");
            case DONE -> outcomeText();
        };
        int colour = switch (menu.outcome()) {
            case NATURAL, WIN -> GameScreens.GREEN;
            case LOSS -> GameScreens.RED;
            case PUSH -> GameScreens.GOLD;
            case PLAYING -> GameScreens.MUTED;
        };
        if (menu.phase() == BlackjackTable.Phase.IDLE && menu.plannedStake() > 0 && !menu.isPayable()) {
            status = tr("bet_unpayable");
            colour = GameScreens.RED;
        }
        GameScreens.fitted(g, font, status, sx, sy + 32, CasinoLayout.SLIP_WIDTH - 16, colour);
    }

    private Component outcomeText() {
        return switch (menu.outcome()) {
            case NATURAL -> Component.translatable("gui.gamblingitems.blackjack_natural",
                    GameScreens.value(menu.paid()));
            case WIN -> Component.translatable("gui.gamblingitems.blackjack_won", GameScreens.value(menu.paid()));
            case PUSH -> Component.translatable("gui.gamblingitems.blackjack_push",
                    GameScreens.value(menu.paid()));
            case LOSS -> Component.translatable("gui.gamblingitems.blackjack_lost",
                    GameScreens.value(menu.stake()));
            case PLAYING -> tr("blackjack_ready");
        };
    }

    @Override protected void renderLabels(GuiGraphics g, int x, int y) {}
}
