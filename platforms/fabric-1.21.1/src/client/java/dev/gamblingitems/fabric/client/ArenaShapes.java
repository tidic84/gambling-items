package dev.gamblingitems.fabric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

/** Fractional geometry with a one-physical-pixel alpha fringe, independent of GUI scale. */
final class ArenaShapes {
    private static final int ARC_STEPS = 12;
    private ArenaShapes() {}

    static void rounded(GuiGraphics g, float x, float y, float width, float height, float radius, int colour) {
        radius = Math.min(radius, Math.min(width, height) / 2);
        float aa = fringe(g);
        int count = 4 * (ARC_STEPS + 1);
        float[] inner = new float[count * 2], outer = new float[count * 2];
        for (int corner = 0; corner < 4; corner++) {
            float cx = corner < 2 ? x + width - radius : x + radius;
            float cy = corner == 0 || corner == 3 ? y + radius : y + height - radius;
            for (int step = 0; step <= ARC_STEPS; step++) {
                double angle = (corner - 1) * Math.PI / 2 + step * Math.PI / (2 * ARC_STEPS);
                int index = (corner * (ARC_STEPS + 1) + step) * 2;
                float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
                inner[index] = cx + cos * radius; inner[index + 1] = cy + sin * radius;
                outer[index] = cx + cos * (radius + aa); outer[index + 1] = cy + sin * (radius + aa);
            }
        }
        polygon(g, inner, outer, x + width / 2, y + height / 2, colour);
    }

    static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float thickness, int colour) {
        float dx = x2 - x1, dy = y2 - y1, length = (float) Math.hypot(dx, dy);
        if (length == 0) return;
        dx /= length; dy /= length;
        float nx = -dy * thickness / 2, ny = dx * thickness / 2, aa = fringe(g);
        float[] inner = {x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, x1 - nx, y1 - ny};
        float ox = -dy * (thickness / 2 + aa), oy = dx * (thickness / 2 + aa);
        float[] outer = {x1 + ox - dx * aa, y1 + oy - dy * aa, x2 + ox + dx * aa, y2 + oy + dy * aa,
                x2 - ox + dx * aa, y2 - oy + dy * aa, x1 - ox - dx * aa, y1 - oy - dy * aa};
        polygon(g, inner, outer, (x1 + x2) / 2, (y1 + y2) / 2, colour);
    }

    /** A slice of a ring between two angles, such as one pocket of a roulette wheel. */
    static void sector(GuiGraphics g, float cx, float cy, float inner, float outer, double from, double to, int colour) {
        int steps = Math.max(2, (int) Math.ceil(Math.abs(to - from) / (Math.PI / 36)));
        float[] points = new float[(steps + 1) * 4];
        for (int i = 0; i <= steps; i++) {
            double angle = from + (to - from) * i / steps;
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            points[i * 2] = cx + cos * outer; points[i * 2 + 1] = cy + sin * outer;
            int back = (steps + 1) * 2 + (steps - i) * 2;
            points[back] = cx + cos * inner; points[back + 1] = cy + sin * inner;
        }
        double middle = (from + to) / 2;
        float mx = cx + (float) Math.cos(middle) * (inner + outer) / 2, my = cy + (float) Math.sin(middle) * (inner + outer) / 2;
        float aa = fringe(g);
        float[] fringe = new float[points.length];
        for (int i = 0; i < points.length; i += 2) {
            float dx = points[i] - mx, dy = points[i + 1] - my, length = (float) Math.max(1e-3, Math.hypot(dx, dy));
            fringe[i] = points[i] + dx / length * aa; fringe[i + 1] = points[i + 1] + dy / length * aa;
        }
        polygon(g, points, fringe, mx, my, colour);
    }

    static void disc(GuiGraphics g, float cx, float cy, float radius, int colour) {
        rounded(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, colour);
    }

    private static float fringe(GuiGraphics g) {
        float scale = GuiPose.scaleFactor(g);
        return (float) (1 / (Minecraft.getInstance().getWindow().getGuiScale() * Math.max(.01f, scale)));
    }

    private static void polygon(GuiGraphics g, float[] inner, float[] outer, float cx, float cy, int colour) {
        //#if MC >= 1.21.6
        //$ // Custom geometry is submitted as a GUI element since 1.21.6, sorted with the rest of the frame.
        //$ var pose = new org.joml.Matrix3x2f(g.pose());
        //$ float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        //$ for (int i = 0; i < outer.length; i += 2) {
        //$     minX = Math.min(minX, outer[i]); maxX = Math.max(maxX, outer[i]);
        //$     minY = Math.min(minY, outer[i + 1]); maxY = Math.max(maxY, outer[i + 1]);
        //$ }
        //$ var bounds = new net.minecraft.client.gui.navigation.ScreenRectangle((int) Math.floor(minX), (int) Math.floor(minY),
        //$         (int) Math.ceil(maxX - minX) + 1, (int) Math.ceil(maxY - minY) + 1).transformMaxBounds(pose);
        //$ ((dev.gamblingitems.fabric.client.mixin.GuiGraphicsAccessor) g).gamblingitems$state().submitGuiElement(new Shape(pose, inner, outer, cx, cy, colour, bounds));
        //#else
        var pose = g.pose().last().pose();
        //#endif
        //#if MC >= 1.21.6
        //#elif MC >= 1.21.2
        //$ g.drawSpecial(buffers -> {
        //$     var vertices = buffers.getBuffer(RenderType.gui());
        //$     ArenaMesh.polygon(inner, outer, cx, cy, colour, (x, y, argb) -> WorldCanvas.vertex(vertices, pose, x, y, 0, argb));
        //$ });
        //#else
        var vertices = g.bufferSource().getBuffer(RenderType.gui());
        ArenaMesh.polygon(inner, outer, cx, cy, colour,
                (x, y, argb) -> WorldCanvas.vertex(vertices, pose, x, y, 0, argb));
        //#endif
    }

    /** Flat coloured quads drawn in the world, on the monitors and tables. */
    static RenderType worldQuads() {
        //#if MC >= 1.21.6
        //$ return RenderType.debugQuads();
        //#else
        return RenderType.gui();
        //#endif
    }

    //#if MC >= 1.21.6
    //$ private record Shape(org.joml.Matrix3x2f pose, float[] inner, float[] outer, float cx, float cy, int colour,
    //$                      net.minecraft.client.gui.navigation.ScreenRectangle bounds)
    //$         implements net.minecraft.client.gui.render.state.GuiElementRenderState {
    //#if MC >= 1.21.9
    //$     @Override public void buildVertices(com.mojang.blaze3d.vertex.VertexConsumer consumer) {
    //$         ArenaMesh.polygon(inner, outer, cx, cy, colour, (x, y, argb) -> consumer.addVertexWith2DPose(pose, x, y).setColor(argb));
    //$     }
    //#else
    //$     @Override public void buildVertices(com.mojang.blaze3d.vertex.VertexConsumer consumer, float depth) {
    //$         ArenaMesh.polygon(inner, outer, cx, cy, colour, (x, y, argb) -> consumer.addVertexWith2DPose(pose, x, y, depth).setColor(argb));
    //$     }
    //#endif
    //$     @Override public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() { return net.minecraft.client.renderer.RenderPipelines.GUI; }
    //$     @Override public net.minecraft.client.gui.render.TextureSetup textureSetup() { return net.minecraft.client.gui.render.TextureSetup.noTexture(); }
    //$     @Override public net.minecraft.client.gui.navigation.ScreenRectangle scissorArea() { return null; }
    //$ }
    //#endif
}
