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
                fill = !active ? 0xff1d3341 : hot ? GameScreens.HOVER : GameScreens.RAISED;
                text = !active ? GameScreens.DIM : GameScreens.TEXT;
            }
            default -> {
                fill = hot ? GameScreens.HOVER : 0;
                text = !active ? GameScreens.BORDER : hot ? GameScreens.TEXT : GameScreens.MUTED;
            }
        }
        if (style == Style.PRIMARY && active)
            ArenaShapes.rounded(g, getX(), getY() + 1, width, height, GameScreens.RADIUS, 0xff00a301);
        if (fill != 0) ArenaShapes.rounded(g, getX(), getY(), width, height, GameScreens.RADIUS, fill);
        Font font = Minecraft.getInstance().font;
        String label = getMessage().getString();
        if (font.width(label) > width - 6) label = font.plainSubstrByWidth(label, Math.max(0, width - 6 - font.width("…"))) + "…";
        g.drawString(font, label, getX() + (width - font.width(label) + 1) / 2, getY() + (height - 7) / 2, text, false);
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
