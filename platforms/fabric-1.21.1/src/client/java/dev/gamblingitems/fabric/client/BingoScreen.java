package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.bingo.BingoRules;
import dev.gamblingitems.fabric.bingo.BingoMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * One card and the drum everybody shares: the numbers mark themselves as they come out.
 *
 * <p>The card has the left of the window to itself and the money side has the right, so a board
 * never lands on the row where a player prepares what a card costs.
 */
public final class BingoScreen extends CasinoScreen<BingoMenu> {
    private static final int CARD_X = 24, CARD_Y = 52, SQUARE = 30;
    private static final int SQUARE_FREE = 0xff1f4d33, SQUARE_MARKED = 0xff2f855a, SQUARE_PLAIN = GameScreens.PANEL;
    private static final int BALL = 0xfff3f6fb;
    /** The money side of the window, where the drum, the rows and the buttons live. */
    private static final int SIDE_X = 198, SIDE_WIDTH = 160;
    private Button buy, collect;

    public BingoScreen(BingoMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 380, 316);
    }

    @Override protected GameMode mode() { return GameMode.BINGO; }
    @Override protected boolean portable() { return menu.portable(); }
    @Override protected Component subtitle() {
        return Component.translatable("gui.gamblingitems.bingo_subtitle", GameScreens.value(menu.settings().cardPrice()));
    }

    @Override protected void init() {
        super.init();
        buy = addRenderableWidget(CasinoButton.primary(tr("bingo_buy"), button -> click(BingoMenu.BUY_BUTTON))
                .bounds(leftPos + SIDE_X, topPos + 196, SIDE_WIDTH, 18).build());
        buy.setTooltip(Tooltip.create(Component.translatable("gui.gamblingitems.bingo_price",
                GameScreens.value(menu.settings().cardPrice()))));
        collect = addRenderableWidget(CasinoButton.builder(tr("collect_winnings"),
                        button -> click(BingoMenu.COLLECT_BUTTON))
                .bounds(leftPos + SIDE_X, topPos + 218, SIDE_WIDTH, 18).build());
        collect.setTooltip(Tooltip.create(tr("collect_help")));
    }

    private void click(int button) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override protected void containerTick() {
        super.containerTick();
        buy.active = menu.canBuy();
        collect.active = menu.winnings() > 0;
    }

    @Override protected boolean gameClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override protected void renderGame(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x + 8, y + 32, x + 188, y + 228, GameScreens.PANEL);
        g.fill(x + 192, y + 32, x + 372, y + 228, GameScreens.PANEL);
        renderCard(g, x, y);
        renderDrum(g, x, y);
        g.drawString(font, tr("bingo_paid"), x + BingoMenu.STAKE_X, y + BingoMenu.ENGAGED_Y - 9,
                GameScreens.MUTED, false);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.chips_ready",
                        GameScreens.value(menu.plannedStake())),
                x + BingoMenu.STAKE_X, y + BingoMenu.INPUT_Y - 9, SIDE_WIDTH, GameScreens.MUTED);
        GameScreens.slots(g, menu, x, y);
        g.drawString(font, tr("inventory"), x + 12, y + 241, GameScreens.MUTED, false);
    }

    /** The card: five columns headed B I N G O, with the drawn squares marked. */
    private void renderCard(GuiGraphics g, int x, int y) {
        String[] heads = {"B", "I", "N", "G", "O"};
        for (int column = 0; column < BingoRules.COLUMNS; column++) {
            int headX = x + CARD_X + column * SQUARE + SQUARE / 2;
            g.drawCenteredString(font, heads[column], headX, y + CARD_Y - 12, GameScreens.GOLD);
        }
        for (int square = 0; square < BingoRules.SIZE; square++) {
            int column = square % BingoRules.COLUMNS, row = square / BingoRules.COLUMNS;
            int left = x + CARD_X + column * SQUARE, top = y + CARD_Y + row * SQUARE;
            boolean free = square == BingoRules.FREE_SQUARE;
            boolean marked = menu.marked(square);
            g.fill(left, top, left + SQUARE - 2, top + SQUARE - 2,
                    free ? SQUARE_FREE : marked ? SQUARE_MARKED : SQUARE_PLAIN);
            String text = free ? "★" : menu.hasCard() ? String.valueOf(menu.numberAt(square)) : "-";
            g.drawCenteredString(font, text, left + (SQUARE - 2) / 2, top + (SQUARE - 2) / 2 - 4,
                    marked || free ? GameScreens.TEXT : GameScreens.MUTED);
        }
    }

    /** The drum: the number that just came out, the last ones before it, and the state of the round. */
    private void renderDrum(GuiGraphics g, int x, int y) {
        g.drawString(font, tr("bingo_drum"), x + SIDE_X, y + 38, GameScreens.MUTED, false);
        int number = menu.lastNumber();
        String shown = number > 0 ? String.valueOf(number) : "-";
        g.fill(x + SIDE_X, y + 48, x + SIDE_X + 48, y + 84, SQUARE_PLAIN);
        GuiPose.push(g);
        GuiPose.translate(g, x + SIDE_X + 24f, y + 56f);
        GuiPose.scale(g, 2f);
        g.drawString(font, shown, -font.width(shown) / 2, 0, BALL, false);
        GuiPose.pop(g);
        int[] draws = menu.recentDraws();
        for (int index = 0; index < draws.length; index++) {
            if (draws[index] <= 0) continue;
            int left = x + SIDE_X + 56 + (index % 6) * 17, top = y + 48 + (index / 6) * 17;
            g.fill(left, top, left + 15, top + 15, SQUARE_MARKED);
            g.drawCenteredString(font, String.valueOf(draws[index]), left + 7, top + 4, GameScreens.TEXT);
        }
        Component status = switch (menu.phase()) {
            case WAITING -> tr("bingo_waiting");
            case BETTING -> Component.translatable("gui.gamblingitems.bingo_betting",
                    (menu.phaseTicks() + 19) / 20);
            case DRAWING -> Component.translatable("gui.gamblingitems.bingo_missing", menu.missing());
            case RESULT -> switch (menu.state()) {
                case BingoMenu.STATE_WON -> Component.translatable("gui.gamblingitems.bingo_won",
                        GameScreens.value(menu.paid()));
                case BingoMenu.STATE_LOST -> tr("bingo_lost");
                default -> tr("bingo_over");
            };
        };
        int colour = menu.state() == BingoMenu.STATE_WON ? GameScreens.GREEN
                : menu.state() == BingoMenu.STATE_LOST ? GameScreens.RED : GameScreens.TEXT;
        GameScreens.fitted(g, font, status, x + SIDE_X, y + 92, SIDE_WIDTH, colour);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.bingo_table",
                        menu.players(), GameScreens.value(menu.pot())), x + SIDE_X, y + 104, SIDE_WIDTH,
                GameScreens.MUTED);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.winnings_amount",
                        GameScreens.value(menu.winnings())), x + SIDE_X, y + 116, SIDE_WIDTH,
                menu.winnings() > 0 ? GameScreens.GREEN : GameScreens.MUTED);
    }

    @Override protected void renderLabels(GuiGraphics g, int x, int y) {}
}
