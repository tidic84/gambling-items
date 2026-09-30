package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.slots.SlotRules;
import net.minecraft.network.chat.Component;

/** The face of each reel symbol, shared by the window of a cabinet and by the cabinet itself. */
public final class SlotSymbols {
    private static final String[] FACES = {"C", "L", "O", "B", "T", "D", "K", "7"};
    private static final int[] COLOURS = {0xffc0392b, 0xffe8d44d, 0xffe0873a, 0xffffce69,
            0xff5cc27a, 0xff6fd3ea, 0xffd9a441, 0xffe8687d};

    // Nine-pixel sprites shared by the GUI and the world renderer. h = glint, s = shade.
    private static final String[][] PIXELS = {
            {".....gg..", "...ggg...", "...g.g...", "..gg.g...", ".hhh..hh.", "hxxxsxxxs", "xxxxsxxxs", ".sss.sss.", "........."},
            {".........", ".....gg..", "...xxx...", "..hhxxx..", ".hhxxxxs.", ".xxxxxxs.", "..xxxxs..", "...sss...", "........."},
            {"....gg...", "...g.....", "..xxxxx..", ".hhxxxxs.", ".hxxxxxs.", ".xxxxxxs.", "..xxxxs..", "...sss...", "........."},
            {"....x....", "...hhh...", "..hxxxs..", "..xxxxs..", "..xxxxs..", ".hxxxxxs.", "xxxxxxxxx", "sssssssss", "...xxx..."},
            {"...hhh...", "...xxs...", ".hhxxshh.", "hxxxxxxxs", "xxxxxxxxs", ".ssxxsss.", "....x....", "....xs...", ".....ss.."},
            {".........", "..hhxxx..", ".hhhhxxs.", "hhhhxxxxs", ".hxxxxxs.", "..xxxxs..", "...xxs...", "....s....", "........."},
            {"h...h...h", "xx.xxx.xx", "xx.xxx.xs", "xxxxxxxxs", ".xxxxxxs.", ".hhhhhxs.", ".xxxxxxs.", ".sssssss.", "........."},
            {"hhhhhhhh.", "xxxxxxxs.", ".....xxs.", "....xxs..", "...xxs...", "..xxs....", "..xxs....", "..xxs....", "..sss...."}
    };

    @FunctionalInterface
    public interface Pixel {
        void draw(float x1, float y1, float x2, float y2, int colour);
    }

    /** Draws a crisp symbol centred at (x,y), without relying on the player's font. */
    public static void draw(int symbol, float x, float y, float size, Pixel pixel) {
        if (symbol < 0 || symbol >= PIXELS.length) {
            pixel.draw(x - size / 4, y - size / 18, x + size / 4, y + size / 18, 0xff78828c);
            return;
        }
        float unit = size / 9;
        String[] sprite = PIXELS[symbol];
        int base = colour(symbol);
        int shade = 0xff000000 | ((base & 0xfefefe) >> 1);
        for (int row = 0; row < sprite.length; row++) {
            for (int col = 0; col < sprite[row].length(); col++) {
                char ink = sprite[row].charAt(col);
                if (ink == '.') continue;
                int colour = switch (ink) {
                    case 'h' -> 0xfffff3cf;
                    case 's' -> shade;
                    case 'g' -> 0xff398450;
                    default -> base;
                };
                float left = x - size / 2 + col * unit, top = y - size / 2 + row * unit;
                pixel.draw(left, top, left + unit, top + unit, colour);
            }
        }
    }

    private SlotSymbols() {}

    public static String face(int symbol) {
        return symbol < 0 || symbol >= SlotRules.SYMBOLS ? "?" : FACES[symbol];
    }

    public static int colour(int symbol) {
        return symbol < 0 || symbol >= SlotRules.SYMBOLS ? GameScreens.MUTED : COLOURS[symbol];
    }

    /** The name of a symbol, in the language of the player. */
    public static Component name(int symbol) {
        return Component.translatable("gui.gamblingitems.slot_symbol."
                + (symbol < 0 || symbol >= SlotRules.SYMBOLS ? 0 : symbol));
    }
}
