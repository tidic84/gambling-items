package dev.gamblingitems.fabric.client;

import com.mojang.math.Axis;
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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A real roulette: the wheel turns on the left, the felt is on the right, and clicking an area
 * puts the prepared chips on it. The wheel only replays the pocket the server already drew.
 */
public final class RouletteScreen extends AbstractContainerScreen<RouletteMenu> {
    private static final int FELT = 0xff14472c, FELT_LINE = 0xff2d6b46;
    private static final int RED = 0xffc0392b, BLACK = 0xff1b1f27, GREEN = 0xff1e8f52;
    private static final int TABLE_X = 146, TABLE_Y = 46;
    private static final int WHEEL_X = 72, WHEEL_Y = 100, WHEEL_RADIUS = 50;
    private static final double WHEEL_TURNS = 3, BALL_TURNS = 6;
    private Button collect;
    private final List<RouletteTable.Area> areas = RouletteTable.areas();

    public RouletteScreen(RouletteMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 440;
        imageHeight = 296;
    }

    /** Every game explains itself, in the language of the player. */
    private final GameRules rules = new GameRules("roulette");

    @Override protected void init() {
        super.init();
        addRenderableWidget(rules.button(leftPos + imageWidth - 30, topPos + 6));
        collect = addRenderableWidget(Button.builder(tr("collect_winnings"),
                        button -> click(RouletteMenu.COLLECT_BUTTON))
                .bounds(leftPos + 296, topPos + 186, 124, 18).build());
        collect.setTooltip(Tooltip.create(tr("collect_help")));
    }

    private void click(int button) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    private static Component tr(String key) { return Component.translatable("gui.gamblingitems." + key); }

    @Override protected void containerTick() {
        super.containerTick();
        collect.active = menu.winnings() > 0;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // While the rules are up they take every click, so nothing is played by accident.
        if (rules.open()) {
            rules.close();
            return true;
        }
        if (button == 0 && menu.canBet()) {
            RouletteTable.Area area = RouletteTable.at(areas,
                    (int) mouseX - leftPos - TABLE_X, (int) mouseY - topPos - TABLE_Y);
            if (area != null) {
                click(RouletteMenu.BET_BUTTON + RouletteMenu.code(area.bet()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (rules.open()) {
            rules.render(graphics, font, width, height);
            return;
        }
        renderAreaTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, GameScreens.BORDER);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, GameScreens.INK);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + 3, GameScreens.GOLD);
        g.drawString(font, title, x + 12, y + 11, GameScreens.TEXT, false);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.roulette_subtitle",
                GameScreens.value(menu.settings().minimumStake())), x + 12, y + 22, imageWidth - 24,
                GameScreens.MUTED);
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
        g.drawString(font, tr("protected"), x + 12, y + 275, GameScreens.GREEN, false);
    }

    /** The wheel itself: pockets drawn as blades around a hub, and a ball falling into one of them. */
    private void renderWheel(GuiGraphics g, int x, int y, float partialTick) {
        int centreX = x + WHEEL_X, centreY = y + WHEEL_Y;
        double progress = spinProgress(partialTick);
        double eased = 1 - Math.pow(1 - progress, 3);
        double wheelAngle = eased * WHEEL_TURNS * 2 * Math.PI;
        g.fill(centreX - WHEEL_RADIUS - 3, centreY - WHEEL_RADIUS - 3,
                centreX + WHEEL_RADIUS + 3, centreY + WHEEL_RADIUS + 3, 0x00000000);
        for (int pocket = 0; pocket < RouletteWheel.POCKETS; pocket++) {
            int number = RouletteWheel.numberAtPocket(pocket);
            double angle = wheelAngle + pocket * 2 * Math.PI / RouletteWheel.POCKETS;
            blade(g, centreX, centreY, angle, colourOf(RouletteWheel.colourOf(number)));
            // Every pocket carries its number, as on a real wheel.
            number(g, centreX, centreY, angle + Math.PI / RouletteWheel.POCKETS, number);
        }
        ring(g, centreX, centreY, WHEEL_RADIUS, GameScreens.GOLD);
        ring(g, centreX, centreY, WHEEL_RADIUS / 2, 0xff3b2a17);
        int shown = menu.resultNumber() >= 0 ? menu.resultNumber() : menu.lastResultNumber();
        if (shown >= 0 && menu.phase() != RouletteGame.Phase.SPINNING) {
            String label = String.valueOf(shown);
            g.drawString(font, label, centreX - font.width(label) / 2, centreY - 4,
                    colourOf(RouletteWheel.colourOf(shown)) | 0xff000000, false);
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
        double radius = WHEEL_RADIUS * (0.95 - 0.22 * eased);
        int ballX = centreX + (int) Math.round(Math.cos(angle) * radius);
        int ballY = centreY + (int) Math.round(Math.sin(angle) * radius);
        g.fill(ballX - 2, ballY - 2, ballX + 2, ballY + 2, 0xfff3f6fb);
    }

    /** The number of a pocket, written upright in the middle of its blade. */
    private void number(GuiGraphics g, int centreX, int centreY, double angle, int number) {
        String text = String.valueOf(number);
        int x = centreX + (int) Math.round(Math.cos(angle) * (WHEEL_RADIUS * 0.78));
        int y = centreY + (int) Math.round(Math.sin(angle) * (WHEEL_RADIUS * 0.78));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.62f, 0.62f, 1f);
        g.drawString(font, text, -font.width(text) / 2, -4, GameScreens.TEXT, false);
        g.pose().popPose();
    }

    /** One pocket of the wheel, drawn as a rotated blade so the wheel really looks round. */
    private void blade(GuiGraphics g, int centreX, int centreY, double angle, int colour) {
        double half = Math.PI / RouletteWheel.POCKETS;
        int width = Math.max(2, (int) Math.round(2 * WHEEL_RADIUS * Math.sin(half)));
        g.pose().pushPose();
        g.pose().translate(centreX, centreY, 0);
        g.pose().mulPose(Axis.ZP.rotation((float) angle));
        g.fill(WHEEL_RADIUS / 2, -width / 2, WHEEL_RADIUS, width / 2 + 1, colour);
        g.pose().popPose();
    }

    private void ring(GuiGraphics g, int centreX, int centreY, int radius, int colour) {
        int steps = Math.max(24, radius * 4);
        for (int step = 0; step < steps; step++) {
            double angle = step * 2 * Math.PI / steps;
            int pointX = centreX + (int) Math.round(Math.cos(angle) * radius);
            int pointY = centreY + (int) Math.round(Math.sin(angle) * radius);
            g.fill(pointX, pointY, pointX + 1, pointY + 1, colour);
        }
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
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
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
