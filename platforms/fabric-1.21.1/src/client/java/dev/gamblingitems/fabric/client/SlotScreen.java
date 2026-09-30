package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.slots.SlotRules;
import dev.gamblingitems.fabric.slots.SlotMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The window of a cabinet: three reels, the paytable next to them, and the credit prepared
 * underneath. The reels only replay the line the server has already drawn and paid.
 */
public final class SlotScreen extends AbstractContainerScreen<SlotMenu> {
    private static final int REEL_X = 20, REEL_Y = 44, REEL_WIDTH = 50, REEL_HEIGHT = 56, REEL_GAP = 6;
    private static final int WINDOW = 0xff091018, PAY_X = 196, PAY_WIDTH = 112;
    private static final int CRIMSON = 0xff942c3c, STEEL = 0xff8795a1, PAPER = 0xfff0eadb;
    /** The reels stop one after the other, the last one a third of a pull after the first. */
    private static final float STAGGER = 0.16f;
    private final GameRules rules = new GameRules("slot_machine");
    private Button spin, collect;

    public SlotScreen(SlotMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 320;
        imageHeight = 278;
    }

    @Override protected void init() {
        super.init();
        spin = addRenderableWidget(Button.builder(tr("slot_spin"), button -> click(SlotMenu.SPIN_BUTTON))
                .bounds(leftPos + PAY_X, topPos + 154, PAY_WIDTH, 18).build());
        spin.setTooltip(Tooltip.create(Component.translatable("gui.gamblingitems.slot_spin_help",
                GameScreens.value(menu.settings().minimumStake()))));
        collect = addRenderableWidget(Button.builder(tr("collect_winnings"),
                        button -> click(SlotMenu.COLLECT_BUTTON))
                .bounds(leftPos + PAY_X, topPos + 176, PAY_WIDTH, 18).build());
        collect.setTooltip(Tooltip.create(tr("collect_help")));
        addRenderableWidget(rules.button(leftPos + imageWidth - 30, topPos + 6));
    }

    private void click(int button) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    private static Component tr(String key) { return Component.translatable("gui.gamblingitems." + key); }

    @Override protected void containerTick() {
        super.containerTick();
        spin.active = menu.canSpin();
        collect.active = menu.winnings() > 0;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // While the rules are up they take every click, so nothing is played by accident.
        if (rules.open()) {
            rules.close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        g.fill(x + 4, y + 4, x + imageWidth + 4, y + imageHeight + 4, 0x88000000);
        panel(g, x, y, imageWidth, imageHeight, GameScreens.INK);
        g.fillGradient(x + 3, y + 3, x + imageWidth - 3, y + 31, 0xff702333, 0xff311b29);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + 5, CRIMSON);
        GameScreens.fitted(g, font, title, x + 12, y + 11, imageWidth - 54, GameScreens.TEXT);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.slot_subtitle",
                        GameScreens.value(menu.settings().minimumStake())),
                x + 12, y + 22, imageWidth - 60, GameScreens.MUTED);
        panel(g, x + 12, y + 34, 176, 101, WINDOW);
        panel(g, x + PAY_X, y + 34, PAY_WIDTH, 116, GameScreens.PANEL);
        renderReels(g, x, y, partialTick);
        renderPaytable(g, x, y);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.chips_ready",
                        GameScreens.value(menu.stake())),
                x + SlotMenu.STAKE_X, y + SlotMenu.INPUT_Y - 9, 150, GameScreens.MUTED);
        GameScreens.slots(g, menu, x, y);
        GameScreens.fitted(g, font, tr("inventory"), x + 12, y + 203, 62, GameScreens.MUTED);
        GameScreens.fitted(g, font, tr("protected"), x + 20, y + 178, 164, GameScreens.GREEN);
    }

    /** The three windows, still on a drawn line and rolling while the pull plays out. */
    private void renderReels(GuiGraphics g, int x, int y, float partialTick) {
        int[] reels = menu.reels();
        float left = menu.remainingTicks() - partialTick;
        float spinTicks = Math.max(1, menu.settings().spinTicks());
        float progress = menu.spinning() ? 1 - Math.max(0, left) / spinTicks : 1;
        long ticks = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        for (int reel = 0; reel < SlotRules.REELS; reel++) {
            int windowX = x + REEL_X + reel * (REEL_WIDTH + REEL_GAP), windowY = y + REEL_Y;
            panel(g, windowX - 2, windowY - 2, REEL_WIDTH + 4, REEL_HEIGHT + 4, STEEL);
            g.fill(windowX, windowY, windowX + REEL_WIDTH, windowY + REEL_HEIGHT, PAPER);
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
            String name = stopped && reels[reel] >= 0 ? SlotSymbols.name(reels[reel]).getString() : "";
            g.drawCenteredString(font, font.plainSubstrByWidth(name, REEL_WIDTH - 4),
                    windowX + REEL_WIDTH / 2, windowY + REEL_HEIGHT - 12, GameScreens.MUTED);
            int lamp = !stopped ? CRIMSON : menu.state() == SlotMenu.STATE_RESULT && menu.multiplier() > 0
                    ? GameScreens.GREEN : STEEL;
            g.fill(windowX + 17, windowY + REEL_HEIGHT + 4, windowX + 33, windowY + REEL_HEIGHT + 6, lamp);
        }
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
        GameScreens.fitted(g, font, tr("slot_paytable"), x + PAY_X + 8, y + 40, PAY_WIDTH - 16, GameScreens.TEXT);
        for (int symbol = SlotRules.SYMBOLS - 1; symbol >= 0; symbol--) {
            int line = SlotRules.SYMBOLS - 1 - symbol;
            int top = y + 54 + line * 11;
            if (line % 2 == 0) g.fill(x + PAY_X + 4, top - 1, x + PAY_X + PAY_WIDTH - 4, top + 10, 0xff1e2c3c);
            for (int reel = 0; reel < 3; reel++) {
                icon(g, symbol, x + PAY_X + 12 + reel * 12, top + 4, 9);
            }
            String pays = GameScreens.multiplier(SlotRules.tripleOf(symbol));
            g.drawString(font, pays, x + PAY_X + PAY_WIDTH - 8 - font.width(pays), top,
                    GameScreens.TEXT, false);
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

    private static void panel(GuiGraphics g, int x, int y, int width, int height, int colour) {
        g.fill(x, y, x + width, y + height, WINDOW);
        g.fill(x, y, x + width - 1, y + 1, STEEL);
        g.fill(x, y + 1, x + 1, y + height - 1, GameScreens.BORDER);
        g.fill(x + 2, y + 2, x + width - 2, y + height - 2, colour);
    }

    @Override protected void renderLabels(GuiGraphics g, int x, int y) {}
}
