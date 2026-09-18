package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.crash.CrashRules;
import dev.gamblingitems.fabric.crash.CrashGame;
import dev.gamblingitems.fabric.crash.CrashMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A shared round seen from one seat: the curve everybody watches, and this player's own bet.
 * The drawing only replays what the server already decided; it never advances the flight itself.
 */
public final class CrashScreen extends AbstractContainerScreen<CrashMenu> {
    private static final int GRAPH_LEFT = 12, GRAPH_RIGHT = 170, GRAPH_TOP = 36, GRAPH_BOTTOM = 96;
    private static final int SHAKE_TICKS = 14, BURST_TICKS = 20, BURST_RAYS = 10;
    private Button bet, cashOut, collect;
    /** Flight tick of the previous client tick, so the curve can be drawn between two server ticks. */
    private int previousTick, currentTick;

    public CrashScreen(CrashMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 320;
        imageHeight = 238;
    }

    @Override protected void init() {
        super.init();
        bet = addRenderableWidget(Button.builder(tr("bet"), button -> click(CrashMenu.BET_BUTTON))
                .bounds(leftPos + 182, topPos + 104, 60, 18).build());
        cashOut = addRenderableWidget(Button.builder(tr("cash_out"), button -> click(CrashMenu.CASH_OUT_BUTTON))
                .bounds(leftPos + 246, topPos + 104, 60, 18).build());
        cashOut.setTooltip(Tooltip.create(tr("cash_out_warning")));
        collect = addRenderableWidget(Button.builder(tr("collect_winnings"), button -> click(CrashMenu.COLLECT_BUTTON))
                .bounds(leftPos + 182, topPos + 126, 124, 18).build());
        collect.setTooltip(Tooltip.create(tr("collect_help")));
    }

    private void click(int button) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    private static Component tr(String key) { return Component.translatable("gui.gamblingitems." + key); }

    @Override protected void containerTick() {
        super.containerTick();
        previousTick = currentTick;
        currentTick = menu.flightTick();
        if (menu.phase() != CrashGame.Phase.FLYING && menu.phase() != CrashGame.Phase.CRASHED) {
            previousTick = 0;
            currentTick = 0;
        }
        bet.active = menu.canBet();
        bet.setMessage(menu.plannedStake() > 0
                ? Component.translatable("gui.gamblingitems.bet_amount", GameScreens.value(menu.plannedStake()))
                : tr("bet"));
        bet.setTooltip(Tooltip.create(menu.plannedStake() > 0 && !menu.isPayable()
                ? tr("bet_unpayable")
                : Component.translatable("gui.gamblingitems.bet_warning",
                        GameScreens.value(menu.settings().minimumStake()))));
        cashOut.active = menu.canCashOut();
        collect.active = menu.winnings() > 0;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, GameScreens.BORDER);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, GameScreens.INK);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + 3, GameScreens.GOLD);
        g.drawString(font, title, x + 12, y + 11, GameScreens.TEXT, false);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.crash_subtitle",
                GameScreens.value(menu.settings().minimumStake())), x + 12, y + 22, imageWidth - 24,
                GameScreens.MUTED);
        g.fill(x + 8, y + 32, x + 174, y + 150, GameScreens.PANEL);
        g.fill(x + 178, y + 32, x + 310, y + 150, GameScreens.PANEL);
        renderFlight(g, x, y, partialTick);
        g.drawString(font, tr("engaged"), x + CrashMenu.STAKE_X, y + CrashMenu.ENGAGED_Y - 9,
                GameScreens.MUTED, false);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.bet_row",
                        GameScreens.value(menu.plannedStake())),
                x + CrashMenu.STAKE_X, y + CrashMenu.INPUT_Y - 9, 150, GameScreens.MUTED);
        // Every slot frame is drawn where the menu says the slot is.
        GameScreens.slots(g, menu, x, y);
        renderSeat(g, x, y);
        renderTable(g, x, y);
        g.drawString(font, tr("inventory"), x + 12, y + 161, GameScreens.MUTED, false);
        g.drawString(font, tr("shift_click"), x + 12, y + 178, GameScreens.MUTED, false);
        g.drawString(font, tr("protected"), x + 12, y + 217, GameScreens.GREEN, false);
    }

    /** Ticks elapsed since the crash, used by the shake and the burst only. */
    private int sinceCrash() {
        return menu.phase() == CrashGame.Phase.CRASHED
                ? menu.settings().resultTicks() - menu.phaseTicks() : -1;
    }

    private void renderFlight(GuiGraphics g, int x, int y, float partialTick) {
        CrashGame.Phase phase = menu.phase();
        int crashed = sinceCrash();
        int shake = crashed >= 0 && crashed < SHAKE_TICKS
                ? (int) Math.round(Math.sin(crashed * 1.7) * (SHAKE_TICKS - crashed) / 4.0) : 0;
        int left = x + GRAPH_LEFT + shake, right = x + GRAPH_RIGHT + shake;
        int top = y + GRAPH_TOP, bottom = y + GRAPH_BOTTOM;
        g.fill(left, top, right, bottom, GameScreens.HOLE);
        g.enableScissor(left + 1, top + 1, right - 1, bottom - 1);
        int colour = switch (phase) {
            case FLYING -> GameScreens.GREEN;
            case CRASHED -> GameScreens.RED;
            default -> GameScreens.MUTED;
        };
        if (phase == CrashGame.Phase.FLYING || phase == CrashGame.Phase.CRASHED) {
            renderCurve(g, left, right, top, bottom, colour, partialTick, crashed);
        } else {
            g.drawCenteredString(font, tr("crash_idle_curve"), (left + right) / 2, (top + bottom) / 2 - 4,
                    GameScreens.MUTED);
        }
        g.disableScissor();
        String value = phase == CrashGame.Phase.FLYING || phase == CrashGame.Phase.CRASHED
                ? GameScreens.multiplier(menu.multiplier()) : GameScreens.multiplier(CrashRules.START);
        g.pose().pushPose();
        g.pose().translate((left + right) / 2f, top + 5f, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, value, -font.width(value) / 2, 0, colour, false);
        g.pose().popPose();
        g.fill(left, top, right, top + 1, GameScreens.BORDER);
        g.fill(left, bottom - 1, right, bottom, GameScreens.BORDER);
        Component status = switch (phase) {
            case WAITING -> tr("crash_waiting");
            case BETTING -> Component.translatable("gui.gamblingitems.crash_betting", (menu.phaseTicks() + 19) / 20);
            case FLYING -> tr("crash_flying");
            case CRASHED -> Component.translatable("gui.gamblingitems.crash_crashed_at",
                    GameScreens.multiplier(menu.multiplier()));
        };
        g.drawCenteredString(font, status, x + 91, y + GRAPH_BOTTOM + 3, colour);
    }

    /**
     * The climb, drawn from the same rule the server used: one point per pixel column, on a
     * logarithmic scale that rescales itself so the head of the curve always stays visible.
     */
    private void renderCurve(GuiGraphics g, int left, int right, int top, int bottom,
                             int colour, float partialTick, int crashed) {
        CrashRules rules = menu.settings().rules();
        double head = crashed >= 0 ? currentTick : previousTick + (currentTick - previousTick) * partialTick;
        double ceiling = Math.max(2 * CrashRules.START, menu.multiplier() * 1.15);
        double span = StrictMath.log(ceiling / CrashRules.START);
        int width = right - left - 2;
        int height = bottom - top - 16;
        int baseline = bottom - 2;
        int previousY = baseline;
        for (int column = 0; column <= width; column++) {
            double tick = head * column / Math.max(1, width);
            double value = CrashRules.START * StrictMath.pow(rules.growthPerTick().doubleValue(), tick);
            double share = Math.min(1, StrictMath.log(value / CrashRules.START) / span);
            int pointX = left + 1 + column;
            int pointY = baseline - (int) Math.round(share * height);
            // The area under the curve, then the curve itself joined to the previous column.
            g.fill(pointX, pointY, pointX + 1, baseline, (colour & 0x00ffffff) | 0x30000000);
            g.fill(pointX, Math.min(pointY, previousY), pointX + 1, Math.max(pointY, previousY) + 2, colour);
            previousY = pointY;
        }
        int headX = left + 1 + width;
        int headY = previousY;
        if (crashed < 0) {
            g.fill(headX - 2, headY - 2, headX + 3, headY + 3, colour);
            return;
        }
        // The flight ended here: a short burst marks the exact point, then only the curve remains.
        if (crashed >= BURST_TICKS) return;
        double radius = 3 + crashed * 1.6;
        for (int ray = 0; ray < BURST_RAYS; ray++) {
            double angle = ray * 2 * Math.PI / BURST_RAYS;
            int sparkX = headX + (int) Math.round(Math.cos(angle) * radius);
            int sparkY = headY + (int) Math.round(Math.sin(angle) * radius);
            int fade = (int) (0xff * (1 - (double) crashed / BURST_TICKS)) << 24;
            g.fill(sparkX - 1, sparkY - 1, sparkX + 2, sparkY + 2, (GameScreens.RED & 0x00ffffff) | fade);
        }
    }

    private void renderTable(GuiGraphics g, int x, int y) {
        g.drawString(font, tr("crash_table_title"), x + 183, y + 36, GameScreens.MUTED, false);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.crash_table",
                menu.participants(), GameScreens.value(menu.pot())), x + 183, y + 50, 124, GameScreens.TEXT);
        if (menu.lastCrashPoint() > 0) {
            GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.crash_previous",
                            GameScreens.multiplier(menu.lastCrashPoint())),
                    x + 183, y + 62, 124, GameScreens.MUTED);
        }
        g.drawString(font, tr("winnings"), x + 183, y + 80, GameScreens.MUTED, false);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.winnings_amount",
                        GameScreens.value(menu.winnings())), x + 183, y + 92, 124,
                menu.winnings() > 0 ? GameScreens.GREEN : GameScreens.MUTED);
    }

    private void renderSeat(GuiGraphics g, int x, int y) {
        Component seat;
        int colour = GameScreens.MUTED;
        switch (menu.state()) {
            case CrashMenu.STATE_ENGAGED -> {
                seat = Component.translatable("gui.gamblingitems.crash_engaged", GameScreens.value(menu.stake()));
                colour = GameScreens.GOLD;
            }
            case CrashMenu.STATE_CASHED -> {
                seat = Component.translatable("gui.gamblingitems.crash_cashed",
                        GameScreens.multiplier(menu.settlement()), GameScreens.value(menu.paid()));
                colour = GameScreens.GREEN;
            }
            case CrashMenu.STATE_LOST -> {
                seat = Component.translatable("gui.gamblingitems.crash_lost", GameScreens.value(menu.stake()));
                colour = GameScreens.RED;
            }
            default -> seat = menu.plannedStake() > 0 && !menu.isPayable() ? tr("bet_unpayable")
                    : Component.translatable("gui.gamblingitems.crash_ready", GameScreens.value(menu.plannedStake()));
        }
        GameScreens.fitted(g, font, seat, x + 12, y + 146, 160, colour);
    }

    @Override protected void renderLabels(GuiGraphics g, int x, int y) {}
}
