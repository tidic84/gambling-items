package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.slots.SlotRules;
import dev.gamblingitems.fabric.slots.SlotMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The window of a cabinet: three reels, the paytable next to them, and the credit prepared
 * underneath. The reels only replay the line the server has already drawn and paid.
 */
public final class SlotScreen extends CasinoScreen<SlotMenu> {
    private static final int REEL_X = 20, REEL_Y = 44, REEL_WIDTH = 50, REEL_HEIGHT = 56, REEL_GAP = 6;
    private static final int WINDOW = GameScreens.HOLE, PAY_X = 196, PAY_WIDTH = 112;
    private static final int CRIMSON = 0xff942c3c, STEEL = 0xff8795a1, PAPER = 0xfff0eadb;
    /** The reels stop one after the other, the last one a third of a pull after the first. */
    private static final float STAGGER = 0.16f;
    private final CasinoSounds.Reel ticker = new CasinoSounds.Reel();
    private Button spin, collect;
    private boolean wasSpinning;

    public SlotScreen(SlotMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 320, 278);
    }

    @Override protected GameMode mode() { return GameMode.SLOT_MACHINE; }
    @Override protected boolean portable() { return menu.portable(); }
    @Override protected Component subtitle() {
        return Component.translatable("gui.gamblingitems.slot_subtitle", GameScreens.value(menu.settings().minimumStake()));
    }

    @Override protected void init() {
        super.init();
        spin = addRenderableWidget(CasinoButton.primary(tr("slot_spin"), button -> click(SlotMenu.SPIN_BUTTON))
                .bounds(leftPos + PAY_X, topPos + 154, PAY_WIDTH, 18).build());
        spin.setTooltip(Tooltip.create(Component.translatable("gui.gamblingitems.slot_spin_help",
                GameScreens.value(menu.settings().minimumStake()))));
        collect = addRenderableWidget(CasinoButton.builder(tr("collect_winnings"),
                        button -> click(SlotMenu.COLLECT_BUTTON))
                .bounds(leftPos + PAY_X, topPos + 176, PAY_WIDTH, 18).build());
        collect.setTooltip(Tooltip.create(tr("collect_help")));
    }

    private void click(int button) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override protected void containerTick() {
        super.containerTick();
        spin.active = menu.canSpin();
        collect.active = menu.winnings() > 0;
        boolean spinning = menu.spinning();
        if (!wasSpinning && spinning) {
            ticker.reset();
            CasinoSounds.start();
        }
        if (wasSpinning && !spinning && menu.state() == SlotMenu.STATE_RESULT) {
            if (menu.multiplier() > 0) CasinoSounds.win();
            else CasinoSounds.lose();
        }
        wasSpinning = spinning;
    }

    @Override protected boolean gameClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override protected void renderGame(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        housing(g, x + 12, y + 34, 176, 101, partialTick);
        GameScreens.card(g, x + PAY_X, y + 34, PAY_WIDTH, 116);
        GameScreens.cardHeader(g, x + PAY_X, y + 34, PAY_WIDTH);
        renderReels(g, x, y, partialTick);
        renderPaytable(g, x, y);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.chips_ready",
                        GameScreens.value(menu.stake())),
                x + SlotMenu.STAKE_X, y + SlotMenu.INPUT_Y - 9, 150, GameScreens.MUTED);
        GameScreens.slots(g, menu, x, y);
        GameScreens.fitted(g, font, tr("inventory"), x + 12, y + 203, 62, GameScreens.MUTED);
    }

    /** The three windows, still on a drawn line and rolling while the pull plays out. */
    private void renderReels(GuiGraphics g, int x, int y, float partialTick) {
        int[] reels = menu.reels();
        float left = menu.remainingTicks() - partialTick;
        float spinTicks = Math.max(1, menu.settings().spinTicks());
        float progress = menu.spinning() ? (float) MachineMotion.progress(1 - Math.max(0, left) / spinTicks) : 1;
        long ticks = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        // A click each time a symbol passes the window, until the last reel stops.
        if (menu.spinning()) ticker.follow((ticks + partialTick) * 0.7, progress);
        for (int reel = 0; reel < SlotRules.REELS; reel++) {
            int windowX = x + REEL_X + reel * (REEL_WIDTH + REEL_GAP), windowY = y + REEL_Y;
            ArenaShapes.rounded(g, windowX - 3, windowY - 3, REEL_WIDTH + 6, REEL_HEIGHT + 6, 3, GameScreens.GOLD);
            ArenaShapes.rounded(g, windowX - 2, windowY - 2, REEL_WIDTH + 4, REEL_HEIGHT + 4, 2, 0xff3a2e18);
            // The drum curves away from the glass: paper in the middle, shadow above and below.
            GameScreens.gradient(g, windowX, windowY, windowX + REEL_WIDTH, windowY + 20, 0xffa49d8e, PAPER);
            GameScreens.gradient(g, windowX, windowY + 20, windowX + REEL_WIDTH, windowY + 41, PAPER, 0xffa49d8e);
            // A reel that has not stopped yet shows whatever is passing in front of the window.
            boolean stopped = progress >= 1 - STAGGER * (SlotRules.REELS - 1 - reel);
            double travel = (ticks + partialTick) * 0.7 + reel * 2.3;
            int symbol = stopped ? reels[reel] : Math.floorMod((long) Math.floor(travel), SlotRules.SYMBOLS);
            g.enableScissor(windowX, windowY, windowX + REEL_WIDTH, windowY + 40);
            float offset = stopped ? 0 : (float) (travel - Math.floor(travel)) * 36;
            icon(g, symbol, windowX + REEL_WIDTH / 2f, windowY + 21 + offset, 27);
            if (!stopped) {
                icon(g, (symbol + 1) % SlotRules.SYMBOLS, windowX + REEL_WIDTH / 2f,
                        windowY + 21 + offset - 36, 27);
            }
            g.disableScissor();
            g.fillGradient(windowX, windowY, windowX + REEL_WIDTH, windowY + 10, 0x770c1520, 0x000c1520);
            g.fillGradient(windowX, windowY + 32, windowX + REEL_WIDTH, windowY + 41, 0x000c1520, 0x550c1520);
            g.fill(windowX, windowY + 41, windowX + REEL_WIDTH, windowY + REEL_HEIGHT, WINDOW);
            g.fill(windowX, windowY + 41, windowX + REEL_WIDTH, windowY + 42, GameScreens.GOLD);
            String name = stopped && reels[reel] >= 0 ? SlotSymbols.name(reels[reel]).getString() : "";
            g.drawCenteredString(font, font.plainSubstrByWidth(name, REEL_WIDTH - 4),
                    windowX + REEL_WIDTH / 2, windowY + REEL_HEIGHT - 12, GameScreens.MUTED);
            int lamp = !stopped ? CRIMSON : menu.state() == SlotMenu.STATE_RESULT && menu.multiplier() > 0
                    ? GameScreens.GREEN : STEEL;
            ArenaShapes.rounded(g, windowX + 15, windowY + REEL_HEIGHT + 4, 20, 3, 1.5f, lamp);
        }
        // The pay line runs across all three drums, between two brass pointers.
        int line = y + REEL_Y + 20, from = x + REEL_X - 4, to = x + REEL_X + 3 * REEL_WIDTH + 2 * REEL_GAP + 4;
        g.fill(from, line, to, line + 1, 0xccd2434f);
        ArenaShapes.line(g, from - 4, line - 3, from, line + .5f, 1.5f, GameScreens.GOLD);
        ArenaShapes.line(g, from - 4, line + 4, from, line + .5f, 1.5f, GameScreens.GOLD);
        ArenaShapes.line(g, to + 4, line - 3, to, line + .5f, 1.5f, GameScreens.GOLD);
        ArenaShapes.line(g, to + 4, line + 4, to, line + .5f, 1.5f, GameScreens.GOLD);
        Component status = menu.spinning() ? tr("slot_spinning")
                : menu.state() == SlotMenu.STATE_IDLE ? tr("slot_ready")
                : menu.multiplier() == 0 ? tr("slot_lost")
                : Component.translatable("gui.gamblingitems.slot_won",
                        GameScreens.multiplier(menu.multiplier()), GameScreens.value(menu.lastWin()));
        int colour = menu.spinning() ? GameScreens.TEXT
                : menu.state() == SlotMenu.STATE_IDLE ? GameScreens.MUTED
                : menu.multiplier() == 0 ? GameScreens.RED : GameScreens.GREEN;
        GameScreens.fitted(g, font, status, x + REEL_X, y + 112, 160, colour);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.winnings_amount",
                        GameScreens.value(menu.winnings())), x + REEL_X, y + 124, 160,
                menu.winnings() > 0 ? GameScreens.GREEN : GameScreens.MUTED);
    }

    /** What every line pays, read off the same table the machine is paid from. */
    private void renderPaytable(GuiGraphics g, int x, int y) {
        GameScreens.fitted(g, font, tr("slot_paytable"), x + PAY_X + 8, y + 38, PAY_WIDTH - 16, GameScreens.GOLD);
        for (int symbol = SlotRules.SYMBOLS - 1; symbol >= 0; symbol--) {
            int line = SlotRules.SYMBOLS - 1 - symbol;
            int top = y + 54 + line * 11;
            if (line % 2 == 0) g.fill(x + PAY_X + 4, top - 1, x + PAY_X + PAY_WIDTH - 4, top + 10, 0xff223143);
            for (int reel = 0; reel < 3; reel++) {
                icon(g, symbol, x + PAY_X + 12 + reel * 12, top + 4, 9);
            }
            String pays = GameScreens.multiplier(SlotRules.tripleOf(symbol));
            g.drawString(font, pays, x + PAY_X + PAY_WIDTH - 8 - font.width(pays), top,
                    line == 0 ? GameScreens.GOLD : GameScreens.TEXT, false);
        }
        int top = y + 54 + SlotRules.SYMBOLS * 11;
        g.drawString(font, tr("slot_pair"), x + PAY_X + 8, top, GameScreens.MUTED, false);
        String pays = GameScreens.multiplier(SlotRules.PAIR);
        g.drawString(font, pays, x + PAY_X + PAY_WIDTH - 8 - font.width(pays), top, GameScreens.TEXT, false);
    }

    private static void icon(GuiGraphics g, int symbol, float x, float y, float size) {
        SlotSymbols.draw(symbol, x, y, size, (x1, y1, x2, y2, colour) ->
                g.fill(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2), colour));
    }

    /** The cabinet round the reels: crimson lacquer under a brass rim, a row of chasing lamps along the top. */
    private void housing(GuiGraphics g, int x, int y, int width, int height, float partialTick) {
        GameScreens.cabinet(g, x, y, width, height, CRIMSON);
        long ticks = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        int lit = (int) ((ticks + partialTick) / (menu.spinning() ? 2 : 6));
        for (int lamp = 0; lamp < 14; lamp++) {
            boolean on = (lamp + lit) % 3 == 0;
            ArenaShapes.disc(g, x + 10 + lamp * (width - 20) / 13f, y + 3, 1.6f, on ? 0xffffd27a : 0xff6b3a2a);
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int x, int y) {}
}
