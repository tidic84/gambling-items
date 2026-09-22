package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.slots.SlotRules;
import net.minecraft.network.chat.Component;

/** The face of each reel symbol, shared by the window of a cabinet and by the cabinet itself. */
public final class SlotSymbols {
    private static final String[] FACES = {"C", "L", "O", "B", "T", "D", "K", "7"};
    private static final int[] COLOURS = {0xffc0392b, 0xffe8d44d, 0xffe0873a, 0xffffce69,
            0xff5cc27a, 0xff6fd3ea, 0xffd9a441, 0xffe8687d};

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
