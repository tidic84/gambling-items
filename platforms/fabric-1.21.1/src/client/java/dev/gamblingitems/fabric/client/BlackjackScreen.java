package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.blackjack.BlackjackRules;
import dev.gamblingitems.core.blackjack.BlackjackRules.Outcome;
import dev.gamblingitems.fabric.blackjack.BlackjackMenu;
import dev.gamblingitems.fabric.blackjack.BlackjackSettings;
import dev.gamblingitems.fabric.blackjack.BlackjackTable;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A hand against the house: the cards are drawn as cards, and only face up ones are known. */
public final class BlackjackScreen extends AbstractContainerScreen<BlackjackMenu> {
    private static final int FELT = 0xff14472c, FELT_LINE = 0xff2d6b46;
    private static final int CARD = 0xfff3f6fb, CARD_BACK = 0xff7a2130, CARD_EDGE = 0xff20262f;
    private static final int CARD_WIDTH = 24, CARD_HEIGHT = 34, CARD_GAP = 6;
    /** A card takes this many ticks to land on the felt. */
    private static final int ANIMATION = 6;
    private static final int TABLE_X = 16, DEALER_Y = 44, PLAYER_Y = 86;
    private Button deal, hit, stand, doubleDown, collect;
    /** How many cards each row held last tick, and how long ago the newest one was dealt. */
    private int seenHand, seenDealer, handDealt = ANIMATION, dealerDealt = ANIMATION;

    public BlackjackScreen(BlackjackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 360;
        imageHeight = 260;
    }

    /** Every game explains itself, in the language of the player. */
    private final GameRules rules = new GameRules("blackjack");

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // While the rules are up they take every click, so nothing is played by accident.
        if (rules.open()) {
            rules.close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(rules.button(leftPos + imageWidth - 30, topPos + 6));
        deal = addRenderableWidget(Button.builder(tr("deal"), button -> click(BlackjackMenu.DEAL_BUTTON))
                .bounds(leftPos + 236, topPos + 44, 108, 18).build());
        hit = addRenderableWidget(Button.builder(tr("hit"), button -> click(BlackjackMenu.HIT_BUTTON))
                .bounds(leftPos + 236, topPos + 66, 52, 18).build());
        stand = addRenderableWidget(Button.builder(tr("stand"), button -> click(BlackjackMenu.STAND_BUTTON))
                .bounds(leftPos + 292, topPos + 66, 52, 18).build());
        doubleDown = addRenderableWidget(Button.builder(tr("double"), button -> click(BlackjackMenu.DOUBLE_BUTTON))
                .bounds(leftPos + 236, topPos + 88, 108, 18).build());
        doubleDown.setTooltip(Tooltip.create(tr("double_help")));
        collect = addRenderableWidget(Button.builder(tr("collect_winnings"),
                        button -> click(BlackjackMenu.COLLECT_BUTTON))
                .bounds(leftPos + 236, topPos + 132, 108, 18).build());
        collect.setTooltip(Tooltip.create(tr("collect_help")));
    }

    private void click(int button) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    private static Component tr(String key) { return Component.translatable("gui.gamblingitems." + key); }

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
        deal.active = menu.canDeal();
        deal.setMessage(menu.plannedStake() > 0
                ? Component.translatable("gui.gamblingitems.start_amount", GameScreens.value(menu.plannedStake()))
                : tr("start_hand"));
        hit.active = menu.canAct();
        stand.active = menu.canAct();
        doubleDown.active = menu.canDouble();
        collect.active = menu.winnings() > 0;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (rules.open()) {
            rules.render(graphics, font, width, height);
            return;
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, GameScreens.BORDER);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, GameScreens.INK);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + 3, GameScreens.GOLD);
        g.drawString(font, title, x + 12, y + 11, GameScreens.TEXT, false);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.blackjack_subtitle",
                        GameScreens.value(menu.settings().minimumStake())),
                x + 12, y + 22, imageWidth - 24, GameScreens.MUTED);
        g.fill(x + 8, y + 32, x + 228, y + 170, FELT);
        g.fill(x + 8, y + 32, x + 228, y + 34, FELT_LINE);
        g.fill(x + 232, y + 32, x + 352, y + 170, GameScreens.PANEL);
        renderHands(g, x, y, partialTick);
        renderStatus(g, x, y);
        g.drawString(font, tr("chips_on_table"), x + BlackjackMenu.STAKE_X,
                y + BlackjackMenu.ENGAGED_Y - 9, GameScreens.MUTED, false);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.chips_ready",
                        GameScreens.value(menu.plannedStake())),
                x + BlackjackMenu.STAKE_X, y + BlackjackMenu.INPUT_Y - 9, 96, GameScreens.MUTED);
        GameScreens.slots(g, menu, x, y);
        g.drawString(font, tr("inventory"), x + 12, y + 183, GameScreens.MUTED, false);
        g.drawString(font, tr("protected"), x + 12, y + 239, GameScreens.GREEN, false);
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
        g.drawString(font, dealerTotal, x + TABLE_X + 160, y + DEALER_Y + 12, GameScreens.TEXT, false);

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
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.blackjack_seat", seat,
                        total == 0 ? "-" : String.valueOf(total)),
                x + TABLE_X + 140, y + PLAYER_Y + 12, 80, colour);
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

    private void renderStatus(GuiGraphics g, int x, int y) {
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
        GameScreens.fitted(g, font, status, x + TABLE_X, y + 132, 200, colour);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.blackjack_stake",
                        GameScreens.value(menu.stake())), x + TABLE_X, y + 146, 200, GameScreens.MUTED);
        g.drawString(font, tr("winnings"), x + 236, y + 112, GameScreens.MUTED, false);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.winnings_amount",
                        GameScreens.value(menu.winnings())), x + 236, y + 122, 108,
                menu.winnings() > 0 ? GameScreens.GREEN : GameScreens.MUTED);
        if (menu.phase() == BlackjackTable.Phase.IDLE && menu.plannedStake() > 0 && !menu.isPayable()) {
            GameScreens.fitted(g, font, tr("bet_unpayable"), x + TABLE_X, y + 160, 200, GameScreens.RED);
        }
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
