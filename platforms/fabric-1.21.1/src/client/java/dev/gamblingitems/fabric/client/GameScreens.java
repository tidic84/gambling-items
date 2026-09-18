package dev.gamblingitems.fabric.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/** Drawing helpers shared by every screen of the mod, so they all look and align the same way. */
public final class GameScreens {
    public static final int INK = 0xff0d131c, PANEL = 0xff172231, BORDER = 0xff304358;
    public static final int TEXT = 0xffe8eff6, MUTED = 0xff91a6ba, GOLD = 0xffffce69;
    public static final int GREEN = 0xff6cdeb7, RED = 0xffe8687d, HOLE = 0xff091018;

    private GameScreens() {}

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
        graphics.fill(x - 1, y - 1, x + 17, y + 17, BORDER);
        graphics.fill(x, y, x + 16, y + 16, HOLE);
    }

    /** Draws a line of text, cut to the width it is given rather than running past the frame. */
    public static void fitted(GuiGraphics graphics, Font font, Component text, int x, int y, int width, int colour) {
        String plain = text.getString();
        String shown = font.width(plain) <= width ? plain : font.plainSubstrByWidth(plain, width - font.width("…")) + "…";
        graphics.drawString(font, shown, x, y, colour, false);
    }

    /** Item values are stored a thousand times larger than an iron ingot; show them as they read. */
    public static String value(long value) {
        return BigDecimal.valueOf(value, 3).setScale(1, RoundingMode.DOWN).stripTrailingZeros().toPlainString();
    }

    public static String multiplier(int hundredths) {
        return BigDecimal.valueOf(hundredths, 2).stripTrailingZeros().toPlainString() + "x";
    }
}
