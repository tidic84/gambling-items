package dev.gamblingitems.fabric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Vanilla button behaviour and narration, drawn as a casino control. */
public final class CasinoButton extends Button {
    /** Green stakes something; secondary is any other action; quiet is for small tools such as arrows. */
    public enum Style { PRIMARY, SECONDARY, QUIET }
    private final Style style;

    private CasinoButton(int x, int y, int width, int height, Component label, OnPress action, Style style) {
        super(x, y, width, height, label, action, DEFAULT_NARRATION);
        this.style = style;
    }

    public static Builder builder(Component label, OnPress action) { return new Builder(label, action, Style.SECONDARY); }
    public static Builder primary(Component label, OnPress action) { return new Builder(label, action, Style.PRIMARY); }
    public static Builder quiet(Component label, OnPress action) { return new Builder(label, action, Style.QUIET); }

    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hot = active && isHoveredOrFocused();
        int fill, text;
        switch (style) {
            case PRIMARY -> {
                fill = !active ? GameScreens.RAISED : hot ? GameScreens.GREEN_HOVER : GameScreens.GREEN;
                text = !active ? GameScreens.DIM : GameScreens.ON_GREEN;
            }
            case SECONDARY -> {
                fill = !active ? 0xff1a222d : hot ? GameScreens.HOVER : GameScreens.RAISED;
                text = !active ? GameScreens.DIM : GameScreens.TEXT;
            }
            default -> {
                fill = hot ? GameScreens.HOVER : 0;
                text = !active ? GameScreens.BORDER : hot ? GameScreens.TEXT : GameScreens.MUTED;
            }
        }
        if (style == Style.PRIMARY && active) {
            // The button that stakes wears a brass rim, brighter under the cursor.
            ArenaShapes.rounded(g, getX() - 1, getY() - 1, width + 2, height + 3, GameScreens.RADIUS + 1, hot ? GameScreens.GOLD : 0xff8a6d36);
            ArenaShapes.rounded(g, getX(), getY() + 1, width, height, GameScreens.RADIUS, 0xff0f5534);
        } else if (style == Style.SECONDARY && active) {
            ArenaShapes.rounded(g, getX(), getY() + 1, width, height, GameScreens.RADIUS, 0xff070b11);
        }
        if (fill != 0) {
            ArenaShapes.rounded(g, getX(), getY(), width, height, GameScreens.RADIUS, fill);
            if (style != Style.QUIET && active && height > 8) {
                // Light falls on the upper half of a key; the lower half stays its colour.
                GameScreens.gradient(g, getX() + 2, getY() + 1, getX() + width - 2, getY() + height / 2,
                        SlotCabinet.shade(fill, 1.25f), fill);
                g.fill(getX() + 3, getY() + 1, getX() + width - 3, getY() + 2, 0x40ffffff);
            }
        }
        Font font = Minecraft.getInstance().font;
        String label = getMessage().getString();
        if (font.width(label) > width - 6) label = font.plainSubstrByWidth(label, Math.max(0, width - 6 - font.width("…"))) + "…";
        g.drawString(font, label, getX() + (width - font.width(label) + 1) / 2, getY() + (height - 7) / 2, text, style == Style.PRIMARY && active);
    }

    public static final class Builder extends Button.Builder {
        private final Component label;
        private final OnPress action;
        private final Style style;
        private int x, y, width = 150, height = 20;

        private Builder(Component label, OnPress action, Style style) {
            super(label, action);
            this.label = label;
            this.action = action;
            this.style = style;
        }

        @Override public Builder bounds(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            return this;
        }

        @Override public Button build() { return new CasinoButton(x, y, width, height, label, action, style); }
    }
}
