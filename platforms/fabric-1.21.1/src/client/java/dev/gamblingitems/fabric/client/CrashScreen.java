package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.crash.CrashRules;
import dev.gamblingitems.fabric.crash.CrashGame;
import dev.gamblingitems.fabric.crash.CrashMenu;
import dev.gamblingitems.fabric.menu.CasinoLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * A shared round seen from one seat: the curve everybody watches, and this player's own bet.
 * The drawing only replays what the server already decided; it never advances the flight itself.
 */
public final class CrashScreen extends CasinoScreen<CrashMenu> {
    private static final int GX = CasinoLayout.GAME_X, GY = CasinoLayout.CONTENT_Y, GW = CasinoLayout.GAME_WIDTH;
    private static final int GH = CasinoLayout.CONTENT_HEIGHT;
    private static final int SX = CasinoLayout.SIDE_X, SW = CasinoLayout.SIDE_WIDTH;
    private static final int SHAKE_TICKS = 14, BURST_TICKS = 20, BURST_RAYS = 10;
    private Button bet, cashOut, collect;
    /** Flight tick of the previous client tick, so the curve can be drawn between two server ticks. */
    private int previousTick, currentTick;
    private int lastState = -1;

    public CrashScreen(CrashMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected GameMode mode() { return GameMode.CRASH; }
    @Override protected boolean portable() { return menu.portable(); }

    @Override protected void init() {
        super.init();
        int bx = leftPos + CasinoLayout.SLIP_X + 8, by = topPos + CasinoLayout.SLIP_Y + 52, bw = CasinoLayout.SLIP_WIDTH - 16;
        bet = addRenderableWidget(CasinoButton.primary(tr("bet"), button -> send(CrashMenu.BET_BUTTON))
                .bounds(bx, by, bw, 20).build());
        cashOut = addRenderableWidget(CasinoButton.primary(tr("cash_out"), button -> send(CrashMenu.CASH_OUT_BUTTON))
                .bounds(bx, by, bw, 20).build());
        cashOut.setTooltip(Tooltip.create(tr("cash_out_warning")));
        collect = addRenderableWidget(CasinoButton.builder(tr("collect_short"), button -> send(CrashMenu.COLLECT_BUTTON))
                .bounds(leftPos + SX + 6, topPos + GY + GH - 22, SW - 12, 16).build());
        collect.setTooltip(Tooltip.create(tr("collect_help")));
        refreshActions();
    }

    @Override protected void containerTick() {
        super.containerTick();
        previousTick = currentTick;
        currentTick = menu.flightTick();
        if (menu.phase() != CrashGame.Phase.FLYING && menu.phase() != CrashGame.Phase.CRASHED) {
            previousTick = 0;
            currentTick = 0;
        }
        if (lastState == CrashMenu.STATE_ENGAGED && menu.state() == CrashMenu.STATE_CASHED) CasinoSounds.win();
        if (lastState == CrashMenu.STATE_ENGAGED && menu.state() == CrashMenu.STATE_LOST) CasinoSounds.lose();
        lastState = menu.state();
        refreshActions();
        bet.setMessage(menu.plannedStake() > 0
                ? Component.translatable("gui.gamblingitems.bet_amount", GameScreens.value(menu.plannedStake()))
                : tr("bet"));
        bet.setTooltip(Tooltip.create(menu.plannedStake() > 0 && !menu.isPayable()
                ? tr("bet_unpayable")
                : Component.translatable("gui.gamblingitems.bet_warning", GameScreens.value(menu.settings().minimumStake()))));
    }

    private void refreshActions() {
        bet.active = menu.canBet();
        cashOut.active = menu.canCashOut();
        cashOut.visible = menu.isEngaged() && menu.phase() == CrashGame.Phase.FLYING;
        bet.visible = !cashOut.visible;
        cashOut.setMessage(Component.translatable("gui.gamblingitems.arena.cash_out_at",
                GameScreens.value(menu.stake() * menu.multiplier() / 100)));
        collect.active = menu.winnings() > 0;
        collect.setMessage(menu.winnings() > 0
                ? Component.translatable("gui.gamblingitems.collect_amount", GameScreens.value(menu.winnings()))
                : tr("collect_short"));
    }

    @Override protected void renderGame(GuiGraphics g, float tick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        GameScreens.card(g, x + GX, y + GY, GW, GH);
        renderFlight(g, x, y, tick);

        GameScreens.card(g, x + SX, y + GY, SW, GH);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.bet_row", GameScreens.value(menu.plannedStake())),
                x + CrashMenu.STAKE_X, y + CrashMenu.INPUT_Y - 11, SW - 16, GameScreens.MUTED);
        GameScreens.label(g, font, tr("engaged"), x + CrashMenu.STAKE_X, y + CrashMenu.ENGAGED_Y - 11, SW - 16);
        for (Slot slot : menu.slots)
            if (!(slot.container instanceof Inventory)) GameScreens.slot(g, x + slot.x, y + slot.y);

        slip(g);
        int sx = x + CasinoLayout.SLIP_X + 8, sy = y + CasinoLayout.SLIP_Y + 8;
        renderSeat(g, sx, sy);
        GameScreens.label(g, font, tr("crash_table_label"), sx + 100, sy, 70);
        g.drawString(font, Component.translatable("gui.gamblingitems.crash_players", menu.participants()), sx + 100, sy + 12, GameScreens.TEXT, false);
        g.drawString(font, Component.translatable("gui.gamblingitems.crash_pot", GameScreens.value(menu.pot())), sx + 100, sy + 24, GameScreens.GOLD, false);
    }

    /** Ticks elapsed since the crash, used by the shake and the burst only. */
    private int sinceCrash() {
        return menu.phase() == CrashGame.Phase.CRASHED ? menu.settings().resultTicks() - menu.phaseTicks() : -1;
    }

    private void renderFlight(GuiGraphics g, int x, int y, float tick) {
        CrashGame.Phase phase = menu.phase();
        int crashed = sinceCrash();
        int shake = crashed >= 0 && crashed < SHAKE_TICKS
                ? (int) Math.round(Math.sin(crashed * 1.7) * (SHAKE_TICKS - crashed) / 4.0) : 0;
        int left = x + GX + 6 + shake, right = x + GX + GW - 6 + shake;
        int top = y + GY + 6, bottom = y + GY + GH - 18;
        GameScreens.rounded(g, left, top, right - left, bottom - top, GameScreens.HOLE);
        for (int row = 1; row <= 3; row++) g.fill(left + 4, top + row * 21, right - 4, top + row * 21 + 1, 0xff12283a);
        boolean live = phase == CrashGame.Phase.FLYING || phase == CrashGame.Phase.CRASHED;
        int colour = switch (phase) {
            case FLYING -> GameScreens.GREEN;
            case CRASHED -> GameScreens.RED;
            default -> GameScreens.DIM;
        };
        g.enableScissor(left + 1, top + 1, right - 1, bottom - 1);
        if (live) renderCurve(g, left, right, top, bottom, colour, tick, crashed);
        g.disableScissor();
        String value = live ? GameScreens.multiplier(menu.multiplier()) : GameScreens.multiplier(CrashRules.START);
        float scale = 3;
        int width = Math.round(font.width(value) * scale);
        GameScreens.heading(g, font, Component.literal(value), (left + right - width) / 2, top + 22, scale,
                live ? colour : GameScreens.TEXT);
        if (phase == CrashGame.Phase.BETTING) {
            double share = (double) menu.phaseTicks() / Math.max(1, menu.settings().bettingTicks());
            GameScreens.bar(g, (left + right) / 2 - 50, top + 54, 100, 4, share, GameScreens.GOLD);
        }
        if (menu.lastCrashPoint() > 0) {
            String last = GameScreens.multiplier(menu.lastCrashPoint());
            int pill = font.width(last) + 8;
            GameScreens.rounded(g, right - pill - 4, top + 4, pill, 12, GameScreens.RAISED);
            g.drawString(font, last, right - pill, top + 6, GameScreens.RED, false);
        }
        Component status = switch (phase) {
            case WAITING -> tr("crash_waiting");
            case BETTING -> Component.translatable("gui.gamblingitems.crash_betting", (menu.phaseTicks() + 19) / 20);
            case FLYING -> tr("crash_flying");
            case CRASHED -> Component.translatable("gui.gamblingitems.crash_crashed_at", GameScreens.multiplier(menu.multiplier()));
        };
        GameScreens.fitted(g, font, status, x + GX + 8, y + GY + GH - 13, GW - 16, live ? colour : GameScreens.MUTED);
    }

    /**
     * The climb, drawn from the same rule the server used: one point per pixel column, on a
     * logarithmic scale that rescales itself so the head of the curve always stays visible.
     */
    private void renderCurve(GuiGraphics g, int left, int right, int top, int bottom, int colour, float tick, int crashed) {
        CrashRules rules = menu.settings().rules();
        double head = crashed >= 0 ? currentTick : previousTick + (currentTick - previousTick) * tick;
        double ceiling = Math.max(2 * CrashRules.START, menu.multiplier() * 1.15);
        double span = ceiling - CrashRules.START;
        int width = right - left - 8;
        int height = bottom - top - 16;
        int baseline = bottom - 4;
        float previousX = left + 4, previousY = baseline;
        for (int column = 0; column <= width; column += 2) {
            double at = head * column / Math.max(1, width);
            double value = CrashRules.START * StrictMath.pow(rules.growthPerTick().doubleValue(), at);
            double share = Math.min(1, (value - CrashRules.START) / span);
            float pointX = left + 4 + column;
            float pointY = baseline - (float) (share * height);
            g.fill((int) pointX, (int) pointY, (int) pointX + 2, baseline, (colour & 0x00ffffff) | 0x28000000);
            ArenaShapes.line(g, previousX, previousY, pointX, pointY, 2.5f, colour);
            previousX = pointX;
            previousY = pointY;
        }
        if (crashed < 0) {
            ArenaShapes.rounded(g, previousX - 3, previousY - 3, 6, 6, 3, GameScreens.TEXT);
            return;
        }
        // The flight ended here: a short burst marks the exact point, then only the curve remains.
        if (crashed >= BURST_TICKS) return;
        double radius = 3 + crashed * 1.6;
        int fade = (int) (0xff * (1 - (double) crashed / BURST_TICKS)) << 24;
        for (int ray = 0; ray < BURST_RAYS; ray++) {
            double angle = ray * 2 * Math.PI / BURST_RAYS;
            float sparkX = previousX + (float) (Math.cos(angle) * radius);
            float sparkY = previousY + (float) (Math.sin(angle) * radius);
            ArenaShapes.rounded(g, sparkX - 1.5f, sparkY - 1.5f, 3, 3, 1.5f, (GameScreens.RED & 0x00ffffff) | fade);
        }
    }

    private void renderSeat(GuiGraphics g, int sx, int sy) {
        switch (menu.state()) {
            case CrashMenu.STATE_ENGAGED -> {
                GameScreens.label(g, font, tr("engaged"), sx, sy, 90);
                GameScreens.heading(g, font, Component.literal(GameScreens.value(menu.stake())), sx, sy + 12, 2, GameScreens.TEXT);
            }
            case CrashMenu.STATE_CASHED -> {
                GameScreens.label(g, font, Component.translatable("gui.gamblingitems.crash_cashed_label",
                        GameScreens.multiplier(menu.settlement())), sx, sy, 90);
                GameScreens.heading(g, font, Component.literal("+" + GameScreens.value(menu.paid())), sx, sy + 12, 2, GameScreens.GOLD);
            }
            case CrashMenu.STATE_LOST -> {
                GameScreens.label(g, font, tr("crash_lost_label"), sx, sy, 90);
                GameScreens.heading(g, font, Component.literal("-" + GameScreens.value(menu.stake())), sx, sy + 12, 2, GameScreens.RED);
            }
            default -> {
                GameScreens.label(g, font, tr("stake"), sx, sy, 90);
                GameScreens.heading(g, font, Component.literal(GameScreens.value(menu.plannedStake())), sx, sy + 12, 2,
                        menu.plannedStake() > 0 && !menu.isPayable() ? GameScreens.RED : GameScreens.TEXT);
            }
        }
    }
}
