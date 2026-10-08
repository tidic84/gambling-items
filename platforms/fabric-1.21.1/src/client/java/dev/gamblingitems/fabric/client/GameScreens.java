package dev.gamblingitems.fabric.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The casino look shared by every window of the mod: a night-blue room, raised cards, green for
 * the action that stakes something, gold for what is won.
 */
public final class GameScreens {
    /** Surfaces, from the room to the raised controls. */
    public static final int INK = 0xff0f212e, PANEL = 0xff1a2c38, RAISED = 0xff213743, HOVER = 0xff2f4553;
    public static final int BORDER = 0xff2f4553, HOLE = 0xff0a1822, HEADER = 0xff0b1923;
    public static final int TEXT = 0xffffffff, MUTED = 0xffb1bad3, DIM = 0xff6f8396;
    /** Green stakes, gold pays, red loses, blue marks what is selected. */
    public static final int GREEN = 0xff00e701, GREEN_HOVER = 0xff3dff3e, ON_GREEN = 0xff04210a;
    public static final int GOLD = 0xffffc83d, RED = 0xffed4163, ACCENT = 0xff1475e1;
    public static final int ACCENT_FILL = GREEN, ACCENT_HOVER = GREEN_HOVER, ON_ACCENT = ON_GREEN;
    public static final int SELECTION = 0xff557086;
    public static final int RADIUS = 4;

    private GameScreens() {}

    /** A game window: the room, and a darker band for its title or tabs. */
    public static void window(GuiGraphics g, int x, int y, int width, int height) {
        ArenaShapes.rounded(g, x - 1, y + 2, width + 2, height + 1, 7, 0x70000000);
        ArenaShapes.rounded(g, x, y, width, height, 6, INK);
        ArenaShapes.rounded(g, x, y, width, 28, 6, HEADER);
        g.fill(x, y + 22, x + width, y + 28, HEADER);
        g.fill(x, y + 28, x + width, y + 29, BORDER);
    }

    /** A raised card holding one part of a game. */
    public static void card(GuiGraphics g, int x, int y, int width, int height) {
        ArenaShapes.rounded(g, x, y, width, height, RADIUS, PANEL);
    }

    public static void rounded(GuiGraphics g, int x, int y, int width, int height, int colour) {
        ArenaShapes.rounded(g, x, y, width, height, RADIUS, colour);
    }

    public static void panel(GuiGraphics g, int x, int y, int width, int height) { card(g, x, y, width, height); }

    public static void surface(GuiGraphics g, int x, int y, int width, int height, int colour) {
        g.fill(x, y, x + width, y + height, colour);
    }

    public static void borderedSurface(GuiGraphics g, int x, int y, int width, int height, int colour) {
        rounded(g, x, y, width, height, colour);
    }

    public static void outline(GuiGraphics g, int x, int y, int width, int height, int colour) {
        g.fill(x, y, x + width, y + 1, colour);
        g.fill(x, y + height - 1, x + width, y + height, colour);
        g.fill(x, y + 1, x + 1, y + height - 1, colour);
        g.fill(x + width - 1, y + 1, x + width, y + height - 1, colour);
    }

    /** A rounded outline: the frame of something selected, or of a win. */
    public static void ring(GuiGraphics g, int x, int y, int width, int height, int colour) {
        ArenaShapes.rounded(g, x - 1, y - 1, width + 2, height + 2, RADIUS + 1, colour);
    }

    public static void panelHeader(GuiGraphics g, int x, int y, int width) {
        g.fill(x + 1, y + 1, x + width - 1, y + 27, PANEL);
        g.fill(x + 1, y + 27, x + width - 1, y + 28, BORDER);
    }

    /** A small capital label above a value, as on a betting slip. */
    public static void label(GuiGraphics g, Font font, Component text, int x, int y, int width) {
        fitted(g, font, Component.literal(text.getString().toUpperCase(java.util.Locale.ROOT)), x, y, width, DIM);
    }

    public static void heading(GuiGraphics g, Font font, Component text, int x, int y, float scale) {
        heading(g, font, text, x, y, scale, TEXT);
    }

    public static void heading(GuiGraphics g, Font font, Component text, int x, int y, float scale, int colour) {
        GuiPose.push(g);
        GuiPose.translate(g, x, y);
        GuiPose.scale(g, scale);
        g.drawString(font, text, 0, 0, colour, true);
        GuiPose.pop(g);
    }

    /** An item drawn larger than a slot, for the pieces a game is about. */
    public static void item(GuiGraphics g, ItemStack stack, float x, float y, float scale) {
        GuiPose.push(g);
        GuiPose.translate(g, x, y);
        GuiPose.scale(g, scale);
        g.renderFakeItem(stack, 0, 0);
        GuiPose.pop(g);
    }

    /**
     * Draws the frame of every slot the menu declares, at the position the menu declares.
     * The background can therefore never drift away from where the items actually are.
     */
    public static void slots(GuiGraphics graphics, AbstractContainerMenu menu, int left, int top) {
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) continue;
            slot(graphics, left + slot.x, top + slot.y);
        }
    }

    public static void slot(GuiGraphics graphics, int x, int y) {
        // One pixel of the room between two slots keeps every cell readable in a full grid.
        graphics.fill(x - 1, y - 1, x + 16, y + 16, HOLE);
        graphics.fill(x - 1, y + 15, x + 16, y + 16, 0xff15293a);
    }

    /** A larger well for the slot a game is played with; an empty one shows a plus. */
    public static void well(GuiGraphics g, Font font, int x, int y, boolean empty, int frame) {
        rounded(g, x - 4, y - 4, 24, 24, HOLE);
        if (frame != 0) ArenaShapes.rounded(g, x - 4, y + 18, 24, 2, 1, frame);
        if (empty) g.drawCenteredString(font, "+", x + 8, y + 4, 0xff2f4553);
    }

    /** Draws a line of text, cut to the width it is given rather than running past the frame. */
    public static void fitted(GuiGraphics graphics, Font font, Component text, int x, int y, int width, int colour) {
        String plain = text.getString();
        String shown = font.width(plain) <= width ? plain : font.plainSubstrByWidth(plain, Math.max(0, width - font.width("…"))) + "…";
        graphics.drawString(font, shown, x, y, colour, false);
    }

    public static void rightAligned(GuiGraphics g, Font font, String text, int right, int y, int colour) {
        g.drawString(font, text, right - font.width(text), y, colour, false);
    }

    /** Wraps a text into at most {@code lines} lines; returns the height used. */
    public static int paragraph(GuiGraphics g, Font font, Component text, int x, int y, int width, int lines, int colour) {
        List<net.minecraft.util.FormattedCharSequence> split = font.split(text, width);
        int shown = Math.min(lines, split.size());
        for (int i = 0; i < shown; i++) g.drawString(font, split.get(i), x, y + i * 10, colour, false);
        return shown * 10;
    }

    /**
     * A horizontal reel of items stopping under a gold marker, as on a case opening site.
     * {@code position} is the item under the marker, fractional while the reel moves.
     */
    public static void reel(GuiGraphics g, List<ItemStack> items, double position, int left, int top, int width, int height,
                            int cell, float scale) {
        rounded(g, left, top, width, height, HOLE);
        if (items.isEmpty()) return;
        int centre = left + width / 2;
        int index = (int) Math.floor(position);
        double shift = position - index;
        int visible = width / cell / 2 + 2;
        int size = Math.round(16 * scale);
        g.enableScissor(left + 1, top + 1, left + width - 1, top + height - 1);
        for (int offset = -visible; offset <= visible; offset++) {
            ItemStack stack = items.get(Math.floorMod(index + offset, items.size()));
            float cellX = (float) (centre + (offset - shift) * cell);
            g.fill((int) (cellX - cell / 2f + 1), top + 3, (int) (cellX + cell / 2f - 1), top + height - 3, PANEL);
            item(g, stack, cellX - size / 2f, top + (height - size) / 2f, scale);
        }
        // Both ends fade into the well, so the eye stays on the marker.
        for (int i = 0; i < 12; i++) {
            int alpha = (int) (0xd0 * (1 - i / 12f)) << 24;
            g.fill(left + 1 + i, top + 1, left + 2 + i, top + height - 1, alpha | (HOLE & 0xffffff));
            g.fill(left + width - 2 - i, top + 1, left + width - 1 - i, top + height - 1, alpha | (HOLE & 0xffffff));
        }
        g.disableScissor();
        g.fill(centre - 1, top, centre + 1, top + height, GOLD);
        ArenaShapes.rounded(g, centre - 4, top - 2, 8, 4, 2, GOLD);
        ArenaShapes.rounded(g, centre - 4, top + height - 2, 8, 4, 2, GOLD);
    }

    /** Eases a reel to the result the server already drew, slowing down as a real one does. */
    public static double eased(double progress) { return 1 - Math.pow(1 - Math.min(1, Math.max(0, progress)), 4); }

    /** A horizontal bar filled to {@code share}, for odds and timers. */
    public static void bar(GuiGraphics g, int x, int y, int width, int height, double share, int colour) {
        ArenaShapes.rounded(g, x, y, width, height, height / 2f, HOLE);
        int filled = (int) Math.round(width * Math.max(0, Math.min(1, share)));
        if (filled > 0) ArenaShapes.rounded(g, x, y, Math.max(height, filled), height, height / 2f, colour);
    }

    /** Item values are stored a thousand times larger than an iron ingot; show them as they read. */
    public static String value(long value) {
        // Below one iron ingot, two decimals keep cheap items from all reading as zero.
        return BigDecimal.valueOf(value, 3).setScale(Math.abs(value) < 1000 ? 2 : 1, RoundingMode.DOWN)
                .stripTrailingZeros().toPlainString();
    }

    public static String multiplier(int hundredths) {
        return BigDecimal.valueOf(hundredths, 2).stripTrailingZeros().toPlainString() + "x";
    }
}
