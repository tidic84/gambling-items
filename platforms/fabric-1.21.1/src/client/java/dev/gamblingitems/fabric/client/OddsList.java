package dev.gamblingitems.fabric.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A scrolling list of items with their odds, as the side card of a game shows them. */
final class OddsList {
    static final int ROW = 18;

    /** One line: the item, the figure on the right, and how it stands out. */
    record Row(ItemStack stack, String figure, int figureColour, Component tooltip, boolean enabled, boolean marked) {}

    private int scroll;

    void reset() { scroll = 0; }

    private int visible(int height) { return Math.max(1, height / ROW); }

    void scroll(int size, int height, double amount) {
        scroll = Math.max(0, Math.min(Math.max(0, size - visible(height)), scroll - (int) Math.signum(amount)));
    }

    /** Brings a row into view, such as the one a reel stopped on. */
    void reveal(int index, int size, int height) {
        int rows = visible(height);
        if (index < scroll) scroll = index;
        else if (index >= scroll + rows) scroll = index - rows + 1;
        scroll = Math.max(0, Math.min(Math.max(0, size - rows), scroll));
    }

    int at(int size, int x, int y, int width, int height, double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + width - 4 || mouseY < y || mouseY >= y + visible(height) * ROW) return -1;
        int index = scroll + (int) ((mouseY - y) / ROW);
        return index < size ? index : -1;
    }

    void render(GuiGraphics g, Font font, List<Row> rows, int x, int y, int width, int height, int mouseX, int mouseY) {
        int count = visible(height);
        int hovered = at(rows.size(), x, y, width, height, mouseX, mouseY);
        for (int line = 0; line < count; line++) {
            int index = scroll + line;
            if (index >= rows.size()) break;
            Row row = rows.get(index);
            int top = y + line * ROW;
            if (row.marked()) GameScreens.rounded(g, x, top, width - 4, ROW - 1, 0xff1f4a2a);
            else if (index == hovered && row.enabled()) GameScreens.rounded(g, x, top, width - 4, ROW - 1, GameScreens.RAISED);
            g.renderFakeItem(row.stack(), x + 1, top);
            int figureX = x + width - 7 - font.width(row.figure());
            g.drawString(font, row.figure(), figureX, top + 5, row.enabled() ? row.figureColour() : GameScreens.DIM, false);
            GameScreens.fitted(g, font, row.stack().getHoverName(), x + 20, top + 5, figureX - x - 23,
                    row.marked() ? GameScreens.GREEN : row.enabled() ? GameScreens.TEXT : GameScreens.DIM);
        }
        if (rows.size() > count) {
            int track = count * ROW - 2;
            int thumb = Math.max(8, track * count / rows.size());
            int offset = (track - thumb) * scroll / Math.max(1, rows.size() - count);
            g.fill(x + width - 2, y, x + width, y + track, GameScreens.HOLE);
            g.fill(x + width - 2, y + offset, x + width, y + offset + thumb, GameScreens.SELECTION);
        }
    }

    void tooltip(GuiGraphics g, Font font, List<Row> rows, int x, int y, int width, int height, int mouseX, int mouseY) {
        int index = at(rows.size(), x, y, width, height, mouseX, mouseY);
        if (index >= 0) GuiPose.tooltip(g, font, List.of(rows.get(index).stack().getHoverName(), rows.get(index).tooltip()), mouseX, mouseY);
    }
}
