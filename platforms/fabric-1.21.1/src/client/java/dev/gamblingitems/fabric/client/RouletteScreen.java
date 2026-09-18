package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.roulette.RouletteRules;
import dev.gamblingitems.core.roulette.RouletteRules.Colour;
import dev.gamblingitems.fabric.roulette.RouletteGame;
import dev.gamblingitems.fabric.roulette.RouletteMenu;
import java.math.RoundingMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A shared wheel seen from one seat. The strip only replays the slot the server already drew. */
public final class RouletteScreen extends AbstractContainerScreen<RouletteMenu> {
    private static final int INK = 0xff0d131c, PANEL = 0xff172231, BORDER = 0xff304358;
    private static final int TEXT = 0xffe8eff6, MUTED = 0xff91a6ba, GOLD = 0xffffce69;
    private static final int RED = 0xffe8687d, BLACK = 0xff2b3648, GREEN = 0xff6cdeb7;
    private static final int CELL = 20, LOOPS = 3;
    private final Button[] colours = new Button[Colour.values().length];
    private Button collect;

    public RouletteScreen(RouletteMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 320;
        imageHeight = 238;
    }

    @Override protected void init() {
        super.init();
        for (int index = 0; index < colours.length; index++) {
            Colour colour = Colour.values()[index];
            colours[index] = addRenderableWidget(Button.builder(
                    Component.translatable("gui.gamblingitems.colour." + colour.id()),
                    button -> click(RouletteMenu.BET_BUTTON + colour.ordinal()))
                    .bounds(leftPos + 182 + index * 42, topPos + 104, 40, 18).build());
            colours[index].setTooltip(Tooltip.create(Component.translatable("gui.gamblingitems.colour_bet",
                    menu.settings().rules().payoutOf(colour),
                    menu.settings().rules().chanceOf(colour).setScale(2, RoundingMode.HALF_UP).toPlainString())));
        }
        collect = addRenderableWidget(Button.builder(tr("collect_winnings"), button -> click(RouletteMenu.COLLECT_BUTTON))
                .bounds(leftPos + 182, topPos + 126, 124, 18).build());
    }

    private void click(int button) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    private static Component tr(String key) { return Component.translatable("gui.gamblingitems." + key); }

    private static int colourOf(Colour colour) {
        return switch (colour) {
            case RED -> RED;
            case BLACK -> BLACK;
            case GREEN -> GREEN;
        };
    }

    @Override protected void containerTick() {
        super.containerTick();
        boolean open = menu.canBet();
        for (Button button : colours) button.active = open;
        collect.active = menu.winnings() > 0;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, BORDER);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, INK);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + 3, GOLD);
        g.drawString(font, title, x + 12, y + 11, TEXT, false);
        RouletteRules rules = menu.settings().rules();
        g.drawString(font, Component.translatable("gui.gamblingitems.roulette_subtitle",
                rules.redSlots(), rules.blackSlots(), rules.greenSlots(),
                menu.settings().stakeStack(1).getHoverName()), x + 12, y + 23, MUTED, false);
        g.fill(x + 8, y + 34, x + 174, y + 150, PANEL);
        g.fill(x + 178, y + 32, x + 310, y + 150, PANEL);
        renderWheel(g, x, y, partialTick);
        g.drawString(font, tr("bet_slot"), x + 16, y + 118, MUTED, false);
        g.drawString(font, tr("engaged"), x + 46, y + 118, MUTED, false);
        slot(g, x + 20, y + 128);
        slot(g, x + 50, y + 128);
        renderSeat(g, x, y);
        renderTable(g, x, y);
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++) slot(g, x + 79 + col * 18, y + 155 + row * 18);
        for (int col = 0; col < 9; col++) slot(g, x + 79 + col * 18, y + 213);
        g.drawString(font, tr("inventory"), x + 12, y + 161, MUTED, false);
        g.drawString(font, tr("shift_click"), x + 12, y + 178, MUTED, false);
        g.drawString(font, tr("protected"), x + 12, y + 217, GREEN, false);
    }

    private void renderWheel(GuiGraphics g, int x, int y, float partialTick) {
        int left = x + 12, right = x + 170, top = y + 44, bottom = y + 84;
        RouletteRules rules = menu.settings().rules();
        g.fill(left, top, right, bottom, 0xff091018);
        double position = wheelPosition(rules, partialTick);
        int centre = (left + right) / 2 - CELL / 2;
        int cell = (int) Math.floor(position);
        double shift = position - cell;
        g.enableScissor(left + 1, top + 1, right - 1, bottom - 1);
        for (int offset = -5; offset <= 5; offset++) {
            int slot = Math.floorMod(cell + offset, rules.slots());
            int drawX = centre + (int) Math.round((offset - shift) * CELL);
            Colour colour = rules.colourAt(slot);
            g.fill(drawX + 1, top + 8, drawX + CELL - 1, bottom - 8, colourOf(colour));
            if (colour == Colour.BLACK) g.fill(drawX + 2, top + 9, drawX + CELL - 2, bottom - 9, 0xff141c28);
        }
        g.disableScissor();
        g.fill(centre - 1, top + 1, centre, bottom - 1, GOLD);
        g.fill(centre + CELL, top + 1, centre + CELL + 1, bottom - 1, GOLD);
        g.fill(left, top, right, top + 1, BORDER);
        g.fill(left, bottom - 1, right, bottom, BORDER);
        Component status = switch (menu.phase()) {
            case WAITING -> tr("roulette_waiting");
            case BETTING -> Component.translatable("gui.gamblingitems.roulette_betting",
                    (menu.phaseTicks() + 19) / 20);
            case SPINNING -> tr("roulette_spinning");
            case RESULT -> Component.translatable("gui.gamblingitems.roulette_result_is",
                    tr("colour." + rules.colourAt(Math.max(0, menu.resultSlot())).id()));
        };
        g.drawCenteredString(font, status, x + 91, y + 92, menu.phase() == RouletteGame.Phase.RESULT ? GOLD : MUTED);
    }

    /** The wheel slows down onto the drawn slot; it never chooses anything by itself. */
    private double wheelPosition(RouletteRules rules, float partialTick) {
        int slot = menu.resultSlot();
        if (slot < 0) {
            int previous = menu.lastResultSlot();
            return previous < 0 ? 0 : (double) LOOPS * rules.slots() + previous;
        }
        double stop = (double) LOOPS * rules.slots() + slot;
        if (menu.phase() != RouletteGame.Phase.SPINNING) return stop;
        int total = menu.settings().spinTicks();
        double progress = Math.min(1, (total - menu.phaseTicks() + partialTick) / total);
        return stop * (1 - Math.pow(1 - progress, 3));
    }

    private void renderTable(GuiGraphics g, int x, int y) {
        RouletteRules rules = menu.settings().rules();
        g.drawString(font, tr("roulette_table_title"), x + 183, y + 36, MUTED, false);
        g.drawString(font, Component.translatable("gui.gamblingitems.crash_table",
                menu.participants(), menu.pot()), x + 183, y + 48, TEXT, false);
        int row = y + 60;
        for (Colour colour : Colour.values()) {
            g.fill(x + 183, row, x + 189, row + 6, colourOf(colour));
            g.drawString(font, Component.translatable("gui.gamblingitems.colour_line",
                            tr("colour." + colour.id()), rules.payoutOf(colour),
                            rules.chanceOf(colour).setScale(2, RoundingMode.HALF_UP).toPlainString()),
                    x + 193, row, TEXT, false);
            row += 11;
        }
        g.renderFakeItem(menu.settings().stakeStack(1), x + 183, y + 90);
        g.drawString(font, Component.translatable("gui.gamblingitems.winnings_amount", menu.winnings()),
                x + 203, y + 94, menu.winnings() > 0 ? GREEN : MUTED, false);
    }

    private void renderSeat(GuiGraphics g, int x, int y) {
        Component seat;
        int colour = MUTED;
        Colour chosen = menu.colour();
        switch (menu.state()) {
            case RouletteMenu.STATE_ENGAGED -> {
                seat = Component.translatable("gui.gamblingitems.roulette_engaged", menu.stake(),
                        tr("colour." + chosen.id()));
                colour = GOLD;
            }
            case RouletteMenu.STATE_WON -> {
                seat = Component.translatable("gui.gamblingitems.roulette_won", menu.paid());
                colour = GREEN;
            }
            case RouletteMenu.STATE_LOST -> {
                seat = Component.translatable("gui.gamblingitems.crash_lost", menu.stake());
                colour = RED;
            }
            default -> seat = Component.translatable("gui.gamblingitems.roulette_ready", menu.plannedStake());
        }
        g.drawCenteredString(font, seat, x + 91, y + 143, colour);
    }

    private static void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, BORDER);
        g.fill(x, y, x + 16, y + 16, 0xff091018);
    }

    @Override protected void renderLabels(GuiGraphics g, int x, int y) {}
}
