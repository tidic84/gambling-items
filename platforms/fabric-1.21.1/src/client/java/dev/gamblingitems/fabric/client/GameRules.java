package dev.gamblingitems.fabric.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

/**
 * The rules of a game, written out in the language of the player and shown over its window.
 *
 * <p>It is a panel drawn on top of the screen rather than a screen of its own: a game window is
 * a container, and leaving it to read the rules would close the round the player is sitting at.
 */
public final class GameRules {
    private static final int PANEL_WIDTH = 320, PADDING = 12, LINE = 11;
    private static final int SHADE = 0xd0060a11;
    private final String mode;
    private boolean open;

    public GameRules(String mode) { this.mode = mode; }

    public boolean open() { return open; }

    public void close() { open = false; }

    /** The button that opens the rules, meant for the top right corner of a window. */
    public Button button(int x, int y) {
        Button button = Button.builder(Component.translatable("gui.gamblingitems.rules_button"),
                ignored -> open = !open).bounds(x, y, 20, 16).build();
        button.setTooltip(Tooltip.create(Component.translatable("gui.gamblingitems.rules_help")));
        return button;
    }

    private List<FormattedCharSequence> lines(Font font) {
        return font.split(Component.translatable("gui.gamblingitems.rules." + mode),
                PANEL_WIDTH - 2 * PADDING);
    }

    /** Draws the rules over the whole screen; does nothing while they are closed. */
    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        if (!open) return;
        List<FormattedCharSequence> lines = lines(font);
        int height = PADDING * 2 + LINE * (lines.size() + 3);
        int left = (screenWidth - PANEL_WIDTH) / 2, top = Math.max(4, (screenHeight - height) / 2);
        graphics.fill(0, 0, screenWidth, screenHeight, SHADE);
        graphics.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + height + 1, GameScreens.BORDER);
        graphics.fill(left, top, left + PANEL_WIDTH, top + height, GameScreens.INK);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 2, GameScreens.GOLD);
        Component title = Component.translatable("gui.gamblingitems.rules_title",
                Component.translatable("gui.gamblingitems.mode." + mode));
        graphics.drawString(font, title, left + PADDING, top + PADDING, GameScreens.GOLD, false);
        for (int index = 0; index < lines.size(); index++) {
            graphics.drawString(font, lines.get(index), left + PADDING,
                    top + PADDING + LINE * (index + 2), GameScreens.TEXT, false);
        }
        graphics.drawString(font, Component.translatable("gui.gamblingitems.rules_close"),
                left + PADDING, top + height - PADDING - 8, GameScreens.MUTED, false);
    }
}
