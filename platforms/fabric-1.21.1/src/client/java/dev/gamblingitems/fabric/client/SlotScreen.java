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
                .bounds(leftPos + PAY_X, topPos + 158, PAY_WIDTH, 18).build());
        spin.setTooltip(Tooltip.create(Component.translatable("gui.gamblingitems.slot_spin_help",
                GameScreens.value(menu.settings().minimumStake()))));
        collect = addRenderableWidget(Button.builder(tr("collect_winnings"),
                        button -> click(SlotMenu.COLLECT_BUTTON))
                .bounds(leftPos + PAY_X, topPos + 180, PAY_WIDTH, 18).build());
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
        g.fill(x, y, x + imageWidth, y + imageHeight, GameScreens.BORDER);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, GameScreens.INK);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + 3, GameScreens.GOLD);
        g.drawString(font, title, x + 12, y + 11, GameScreens.TEXT, false);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.slot_subtitle",
                        GameScreens.value(menu.settings().minimumStake())),
                x + 12, y + 22, imageWidth - 60, GameScreens.MUTED);
        g.fill(x + 12, y + 32, x + 188, y + 136, GameScreens.PANEL);
        g.fill(x + PAY_X, y + 32, x + PAY_X + PAY_WIDTH, y + 150, GameScreens.PANEL);
        renderReels(g, x, y, partialTick);
        renderPaytable(g, x, y);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.chips_ready",
                        GameScreens.value(menu.stake())),
                x + SlotMenu.STAKE_X, y + SlotMenu.INPUT_Y - 9, 150, GameScreens.MUTED);
        GameScreens.slots(g, menu, x, y);
        g.drawString(font, tr("inventory"), x + 12, y + 203, GameScreens.MUTED, false);
        g.drawString(font, tr("protected"), x + 12, y + 259, GameScreens.GREEN, false);
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
            g.fill(windowX - 2, windowY - 2, windowX + REEL_WIDTH + 2, windowY + REEL_HEIGHT + 2,
                    GameScreens.BORDER);
            g.fill(windowX, windowY, windowX + REEL_WIDTH, windowY + REEL_HEIGHT, WINDOW);
            // A reel that has not stopped yet shows whatever is passing in front of the window.
            boolean stopped = progress >= 1 - STAGGER * (SlotRules.REELS - 1 - reel);
            int symbol = stopped ? reels[reel]
                    : (int) Math.floorMod(ticks * 3 + reel * 5 + (long) (progress * 40), SlotRules.SYMBOLS);
            String face = SlotSymbols.face(symbol);
            g.pose().pushPose();
            g.pose().translate(windowX + REEL_WIDTH / 2f, windowY + 14f, 0);
            g.pose().scale(3f, 3f, 1f);
            g.drawString(font, face, -font.width(face) / 2, 0, SlotSymbols.colour(symbol), false);
            g.pose().popPose();
            String name = stopped && reels[reel] >= 0 ? SlotSymbols.name(reels[reel]).getString() : "";
            g.drawCenteredString(font, font.plainSubstrByWidth(name, REEL_WIDTH - 4),
                    windowX + REEL_WIDTH / 2, windowY + REEL_HEIGHT - 12, GameScreens.MUTED);
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
        g.drawString(font, tr("slot_paytable"), x + PAY_X + 8, y + 40, GameScreens.GOLD, false);
        for (int symbol = SlotRules.SYMBOLS - 1; symbol >= 0; symbol--) {
            int line = SlotRules.SYMBOLS - 1 - symbol;
            int top = y + 54 + line * 11;
            String face = SlotSymbols.face(symbol);
            g.drawString(font, face + face + face, x + PAY_X + 8, top, SlotSymbols.colour(symbol), false);
            String pays = GameScreens.multiplier(SlotRules.tripleOf(symbol));
            g.drawString(font, pays, x + PAY_X + PAY_WIDTH - 8 - font.width(pays), top,
                    GameScreens.TEXT, false);
        }
        int top = y + 54 + SlotRules.SYMBOLS * 11;
        g.drawString(font, tr("slot_pair"), x + PAY_X + 8, top, GameScreens.MUTED, false);
        String pays = GameScreens.multiplier(SlotRules.PAIR);
        g.drawString(font, pays, x + PAY_X + PAY_WIDTH - 8 - font.width(pays), top, GameScreens.TEXT, false);
    }

    @Override protected void renderLabels(GuiGraphics g, int x, int y) {}
}
