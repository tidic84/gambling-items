package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.roulette.RouletteWheel;
import dev.gamblingitems.core.roulette.RouletteWheel.Bet;
import dev.gamblingitems.core.roulette.RouletteWheel.BetType;
import dev.gamblingitems.core.roulette.RouletteWheel.Colour;
import dev.gamblingitems.fabric.roulette.RouletteGame;
import dev.gamblingitems.fabric.roulette.RouletteMenu;
import java.math.RoundingMode;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A real roulette: the wheel turns on the left, the felt is on the right, and clicking an area
 * puts the prepared chips on it. The wheel only replays the pocket the server already drew.
 */
public final class RouletteScreen extends CasinoScreen<RouletteMenu> {
    private static final int FELT = 0xff14472c, FELT_LINE = 0xff2d6b46;
    private static final int RED = 0xffc0392b, BLACK = 0xff17191e, GREEN = 0xff1e8f52;
    private static final int TABLE_X = 146, TABLE_Y = 46;
    private static final int WHEEL_X = 74, WHEEL_Y = 94, WHEEL_RADIUS = 56, HUB_RADIUS = 34;
    private static final double WHEEL_TURNS = 3, BALL_TURNS = 6;
    private Button collect;
    private final List<RouletteTable.Area> areas = RouletteTable.areas();

    public RouletteScreen(RouletteMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 440, 296);
    }

    @Override protected GameMode mode() { return GameMode.ROULETTE; }
    @Override protected boolean portable() { return menu.portable(); }
    @Override protected Component subtitle() {
        return Component.translatable("gui.gamblingitems.roulette_subtitle", GameScreens.value(menu.settings().minimumStake()));
    }

    @Override protected void init() {
        super.init();
        collect = addRenderableWidget(CasinoButton.builder(tr("collect_winnings"),
                        button -> click(RouletteMenu.COLLECT_BUTTON))
                .bounds(leftPos + 296, topPos + 186, 124, 18).build());
        collect.setTooltip(Tooltip.create(tr("collect_help")));
    }

    private void click(int button) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override protected void containerTick() {
        super.containerTick();
        collect.active = menu.winnings() > 0;
    }

    @Override protected boolean gameClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && menu.canBet()) {
            RouletteTable.Area area = RouletteTable.at(areas,
                    (int) mouseX - leftPos - TABLE_X, (int) mouseY - topPos - TABLE_Y);
            if (area != null) {
                click(RouletteMenu.BET_BUTTON + RouletteMenu.code(area.bet()));
                return true;
            }
        }
        return false;
    }

    @Override protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderAreaTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderGame(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        renderWheel(g, x, y, partialTick);
        renderFelt(g, x, y, mouseX, mouseY);
        renderStatus(g, x, y);
        g.drawString(font, tr("chips_on_table"), x + RouletteMenu.STAKE_X,
                y + RouletteMenu.ENGAGED_Y - 9, GameScreens.MUTED, false);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.chips_ready",
                        GameScreens.value(menu.plannedStake())),
                x + RouletteMenu.STAKE_X, y + RouletteMenu.INPUT_Y - 9, 96, GameScreens.MUTED);
        GameScreens.slots(g, menu, x, y);
        g.drawString(font, tr("inventory"), x + 12, y + 219, GameScreens.MUTED, false);
    }

    /** The wheel itself: pockets around a hub, and a ball falling into one of them. */
    private void renderWheel(GuiGraphics g, int x, int y, float partialTick) {
        int centreX = x + WHEEL_X, centreY = y + WHEEL_Y;
        double progress = spinProgress(partialTick);
        double eased = 1 - Math.pow(1 - progress, 3);
        double wheelAngle = eased * WHEEL_TURNS * 2 * Math.PI;
        // A gold rim and a wooden hub, so the pockets stand out from the room.
        ArenaShapes.disc(g, centreX, centreY, WHEEL_RADIUS + 3, GameScreens.GOLD);
        ArenaShapes.disc(g, centreX, centreY, WHEEL_RADIUS + 1.5f, 0xff3b2a17);
        double pocketAngle = 2 * Math.PI / RouletteWheel.POCKETS;
        for (int pocket = 0; pocket < RouletteWheel.POCKETS; pocket++) {
            int number = RouletteWheel.numberAtPocket(pocket);
            double angle = wheelAngle + pocket * pocketAngle;
            ArenaShapes.sector(g, centreX, centreY, HUB_RADIUS, WHEEL_RADIUS, angle, angle + pocketAngle,
                    colourOf(RouletteWheel.colourOf(number)));
        }
        // Thin gold frets between the pockets, as on a real wheel.
        for (int pocket = 0; pocket < RouletteWheel.POCKETS; pocket++) {
            double angle = wheelAngle + pocket * pocketAngle;
            ArenaShapes.line(g, centreX + (float) Math.cos(angle) * HUB_RADIUS, centreY + (float) Math.sin(angle) * HUB_RADIUS,
                    centreX + (float) Math.cos(angle) * WHEEL_RADIUS, centreY + (float) Math.sin(angle) * WHEEL_RADIUS,
                    .6f, 0xb0d9a441);
        }
        for (int pocket = 0; pocket < RouletteWheel.POCKETS; pocket++) {
            // Every pocket carries its number, written along the radius as on a real wheel.
            number(g, centreX, centreY, wheelAngle + (pocket + .5) * pocketAngle, RouletteWheel.numberAtPocket(pocket));
        }
        ArenaShapes.disc(g, centreX, centreY, HUB_RADIUS, 0xff3b2a17);
        ArenaShapes.disc(g, centreX, centreY, HUB_RADIUS - 3, GameScreens.HOLE);
        int shown = menu.resultNumber() >= 0 ? menu.resultNumber() : menu.lastResultNumber();
        if (shown >= 0 && menu.phase() != RouletteGame.Phase.SPINNING) {
            String label = String.valueOf(shown);
            int colour = RouletteWheel.colourOf(shown) == Colour.BLACK ? GameScreens.TEXT : colourOf(RouletteWheel.colourOf(shown));
            GameScreens.heading(g, font, Component.literal(label), centreX - font.width(label), centreY - 7, 2, colour);
        }
        if (shown >= 0) renderBall(g, centreX, centreY, wheelAngle, eased, shown);
    }

    /** Where the round is in its spin, between zero and one. */
    private double spinProgress(float partialTick) {
        if (menu.phase() == RouletteGame.Phase.SPINNING) {
            int total = menu.settings().spinTicks();
            return Math.min(1, (total - menu.phaseTicks() + partialTick) / total);
        }
        return menu.resultNumber() >= 0 || menu.lastResultNumber() >= 0 ? 1 : 0;
    }

    private void renderBall(GuiGraphics g, int centreX, int centreY, double wheelAngle,
                            double eased, int number) {
        double pocketAngle = RouletteWheel.pocketOf(number) * 2 * Math.PI / RouletteWheel.POCKETS;
        double angle = wheelAngle + pocketAngle - (1 - eased) * BALL_TURNS * 2 * Math.PI;
        // The ball runs on the rim, then drops into the inner end of its pocket.
        double radius = WHEEL_RADIUS + 1 - (WHEEL_RADIUS + 1 - HUB_RADIUS - 4) * eased;
        float ballX = centreX + (float) (Math.cos(angle + Math.PI / RouletteWheel.POCKETS) * radius);
        float ballY = centreY + (float) (Math.sin(angle + Math.PI / RouletteWheel.POCKETS) * radius);
        ArenaShapes.disc(g, ballX, ballY, 2.6f, 0xff000000);
        ArenaShapes.disc(g, ballX, ballY, 2.2f, 0xfff3f6fb);
    }

    /**
     * The number of a pocket, written along its radius. A pocket is about as wide as one line of
     * text, so the number runs along the radius at full size instead of being shrunk upright.
     */
    private void number(GuiGraphics g, int centreX, int centreY, double angle, int number) {
        String text = String.valueOf(number);
        float middle = (HUB_RADIUS + WHEEL_RADIUS) / 2f + 1;
        GuiPose.push(g);
        GuiPose.translate(g, centreX + (float) Math.cos(angle) * middle, centreY + (float) Math.sin(angle) * middle);
        // On the left half the text is turned over, so no number is ever read upside down.
        GuiPose.rotate(g, (float) (Math.cos(angle) < 0 ? angle + Math.PI : angle));
        // Slightly under full size: a pocket is barely wider than a line of text.
        GuiPose.scale(g, .8f);
        GuiPose.translate(g, -font.width(text) / 2f + .5f, -3.5f);
        g.drawString(font, text, 0, 0, GameScreens.TEXT, false);
        GuiPose.pop(g);
    }

    private static int colourOf(Colour colour) {
        return switch (colour) {
            case RED -> RED;
            case BLACK -> BLACK;
            case GREEN -> GREEN;
        };
    }

    private void renderFelt(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int left = x + TABLE_X, top = y + TABLE_Y;
        g.fill(left - 2, top - 2, left + RouletteTable.WIDTH + 2, top + RouletteTable.HEIGHT + 2, FELT_LINE);
        g.fill(left, top, left + RouletteTable.WIDTH, top + RouletteTable.HEIGHT, FELT);
        RouletteTable.Area hovered = RouletteTable.at(areas, mouseX - left, mouseY - top);
        int result = menu.phase() == RouletteGame.Phase.RESULT ? menu.resultNumber() : -1;
        for (RouletteTable.Area area : areas) {
            int areaX = left + area.x(), areaY = top + area.y();
            Bet bet = area.bet();
            int background = bet.type() == BetType.STRAIGHT
                    ? colourOf(RouletteWheel.colourOf(bet.choice())) : FELT;
            g.fill(areaX, areaY, areaX + area.width() - 1, areaY + area.height() - 1, background);
            if (bet.type() != BetType.STRAIGHT) {
                g.fill(areaX, areaY, areaX + area.width() - 1, areaY + 1, FELT_LINE);
            }
            if (result >= 0 && bet.wins(result)) {
                // The areas that just paid are outlined, so a table can be read at a glance.
                outline(g, areaX, areaY, area.width() - 1, area.height() - 1, GameScreens.GOLD);
            }
            if (area == hovered && menu.canBet()) {
                outline(g, areaX, areaY, area.width() - 1, area.height() - 1, GameScreens.TEXT);
            }
            String label = label(bet);
            g.drawString(font, label, areaX + (area.width() - 1 - font.width(label)) / 2,
                    areaY + (area.height() - 9) / 2, GameScreens.TEXT, false);
            long mine = menu.stakeOn(bet);
            if (mine > 0) chip(g, areaX + area.width() - 5, areaY + 2, mine);
        }
    }

    /** A chip marks what this player has on an area; its value is in the tooltip of that area. */
    private void chip(GuiGraphics g, int x, int y, long value) {
        g.fill(x - 3, y, x + 2, y + 5, GameScreens.GOLD);
        g.fill(x - 2, y + 1, x + 1, y + 4, 0xff7a5a12);
    }

    private void outline(GuiGraphics g, int x, int y, int width, int height, int colour) {
        g.fill(x, y, x + width, y + 1, colour);
        g.fill(x, y + height - 1, x + width, y + height, colour);
        g.fill(x, y, x + 1, y + height, colour);
        g.fill(x + width - 1, y, x + width, y + height, colour);
    }

    private String label(Bet bet) {
        return switch (bet.type()) {
            case STRAIGHT -> String.valueOf(bet.choice());
            case DOZEN -> (bet.choice() * 12 + 1) + "-" + (bet.choice() * 12 + 12);
            case COLUMN -> "2:1";
            case RED -> tr("colour.red").getString();
            case BLACK -> tr("colour.black").getString();
            case EVEN -> tr("bet.even").getString();
            case ODD -> tr("bet.odd").getString();
            case LOW -> "1-18";
            case HIGH -> "19-36";
        };
    }

    /** What an area is worth, what it pays and what this player already has on it. */
    private void renderAreaTooltip(GuiGraphics g, int mouseX, int mouseY) {
        RouletteTable.Area area = RouletteTable.at(areas,
                mouseX - leftPos - TABLE_X, mouseY - topPos - TABLE_Y);
        if (area == null) return;
        Bet bet = area.bet();
        long mine = menu.stakeOn(bet);
        var lines = new java.util.ArrayList<Component>();
        lines.add(Component.translatable("gui.gamblingitems.bet." + bet.type().id() + ".name"));
        lines.add(Component.translatable("gui.gamblingitems.bet_odds", bet.payout(),
                RouletteWheel.chanceOf(bet).setScale(2, RoundingMode.HALF_UP).toPlainString()));
        if (mine > 0) {
            lines.add(Component.translatable("gui.gamblingitems.bet_mine", GameScreens.value(mine)));
        }
        if (menu.canBet()) {
            lines.add(Component.translatable("gui.gamblingitems.bet_click",
                    GameScreens.value(menu.plannedStake())));
        }
        GuiPose.tooltip(g, font, lines, mouseX, mouseY);
    }

    private void renderStatus(GuiGraphics g, int x, int y) {
        Component status = switch (menu.phase()) {
            case WAITING -> tr("roulette_waiting");
            case BETTING -> Component.translatable("gui.gamblingitems.roulette_betting",
                    (menu.phaseTicks() + 19) / 20);
            case SPINNING -> tr("roulette_spinning");
            case RESULT -> Component.translatable("gui.gamblingitems.roulette_result_is",
                    menu.resultNumber() < 0 ? "?" : String.valueOf(menu.resultNumber()));
        };
        GameScreens.fitted(g, font, status, x + TABLE_X, y + TABLE_Y + RouletteTable.HEIGHT + 8, 140,
                menu.phase() == RouletteGame.Phase.RESULT ? GameScreens.GOLD : GameScreens.MUTED);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.crash_table",
                        menu.participants(), GameScreens.value(menu.pot())),
                x + TABLE_X, y + TABLE_Y + RouletteTable.HEIGHT + 20, 140, GameScreens.TEXT);
        Component seat = menu.settled() && menu.paid() > 0
                ? Component.translatable("gui.gamblingitems.roulette_won", GameScreens.value(menu.paid()))
                : menu.settled() && menu.staked() > 0
                        ? Component.translatable("gui.gamblingitems.crash_lost", GameScreens.value(menu.staked()))
                        : Component.translatable("gui.gamblingitems.roulette_engaged",
                                GameScreens.value(menu.staked()));
        GameScreens.fitted(g, font, seat, x + TABLE_X, y + TABLE_Y + RouletteTable.HEIGHT + 32, 140,
                menu.settled() && menu.paid() > 0 ? GameScreens.GREEN : GameScreens.GOLD);
        g.drawString(font, tr("winnings"), x + 296, y + 162, GameScreens.MUTED, false);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.winnings_amount",
                        GameScreens.value(menu.winnings())), x + 296, y + 174, 124,
                menu.winnings() > 0 ? GameScreens.GREEN : GameScreens.MUTED);
    }

    @Override protected void renderLabels(GuiGraphics g, int x, int y) {}
}
