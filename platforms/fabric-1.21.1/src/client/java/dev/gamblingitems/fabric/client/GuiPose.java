package dev.gamblingitems.fabric.client;

import net.minecraft.client.gui.GuiGraphics;

/** The 2D transforms of the interface, the only place that knows how the target version stores them. */
public final class GuiPose {
    private GuiPose() {}

    //#if MC >= 1.21.6
    //$ public static void push(GuiGraphics g) { g.pose().pushMatrix(); }
    //$ public static void pop(GuiGraphics g) { g.pose().popMatrix(); }
    //$ public static void translate(GuiGraphics g, float x, float y) { g.pose().translate(x, y); }
    //$ public static void scale(GuiGraphics g, float scale) { g.pose().scale(scale, scale); }
    //$ public static void rotate(GuiGraphics g, float radians) { g.pose().rotate(radians); }
    //$ /** Lifts what follows above the items and widgets already drawn, for overlays. */
    //$ public static void front(GuiGraphics g) { g.nextStratum(); }
    //$ /** The current horizontal scale, used to size anti-aliasing to one physical pixel. */
    //$ public static float scaleFactor(GuiGraphics g) { return Math.abs(g.pose().m00()); }
    //#else
    public static void push(GuiGraphics g) { g.pose().pushPose(); }
    public static void pop(GuiGraphics g) { g.pose().popPose(); }
    public static void translate(GuiGraphics g, float x, float y) { g.pose().translate(x, y, 0); }
    public static void scale(GuiGraphics g, float scale) { g.pose().scale(scale, scale, 1); }
    public static void rotate(GuiGraphics g, float radians) { g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(radians)); }
    /** Lifts what follows above the items and widgets already drawn, for overlays. */
    public static void front(GuiGraphics g) { g.pose().translate(0, 0, 400); }
    /** The current horizontal scale, used to size anti-aliasing to one physical pixel. */
    public static float scaleFactor(GuiGraphics g) { return Math.abs(g.pose().last().pose().m00()); }
    //#endif

    /** A tooltip made of several lines, drawn above everything else. */
    public static void tooltip(GuiGraphics g, net.minecraft.client.gui.Font font, java.util.List<net.minecraft.network.chat.Component> lines, int x, int y) {
        //#if MC >= 1.21.6
        //$ g.setComponentTooltipForNextFrame(font, lines, x, y);
        //#else
        g.renderComponentTooltip(font, lines, x, y);
        //#endif
    }
}
