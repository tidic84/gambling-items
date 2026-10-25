package dev.gamblingitems.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

/**
 * What the furniture is drawn on in the world: flat quads, text and items. Until 1.21.8 they go
 * straight into the frame's buffers; since 1.21.9 they are submitted and drawn later.
 */
public final class WorldCanvas {
    /** Adds coloured quads, four vertices each, with the pose of the moment it was called. */
    @FunctionalInterface public interface Quads { void draw(Matrix4f matrix, VertexConsumer vertices); }

    //#if MC >= 1.21.9
    //$ private final net.minecraft.client.renderer.SubmitNodeCollector collector;
    //$ public WorldCanvas(net.minecraft.client.renderer.SubmitNodeCollector collector) { this.collector = collector; }
    //#else
    private final net.minecraft.client.renderer.MultiBufferSource buffers;
    /**
     * The quads have a buffer of their own. In the frame's shared buffer, every switch between a quad and
     * a line of text drew what came before: hundreds of draw calls for each piece of furniture.
     */
    //#if MC >= 1.20.5
    private static final net.minecraft.client.renderer.MultiBufferSource.BufferSource QUADS =
            net.minecraft.client.renderer.MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.ByteBufferBuilder(1 << 18));
    //#else
    //$ private static final net.minecraft.client.renderer.MultiBufferSource.BufferSource QUADS =
    //$         net.minecraft.client.renderer.MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(1 << 18));
    //#endif
    public WorldCanvas(net.minecraft.client.renderer.MultiBufferSource buffers) { this.buffers = buffers; }
    //#endif

    public void quads(PoseStack pose, Quads draw) {
        //#if MC >= 1.21.9
        //$ collector.submitCustomGeometry(pose, ArenaShapes.worldQuads(), (entry, vertices) -> draw.draw(entry.pose(), vertices));
        //#else
        draw.draw(pose.last().pose(), QUADS.getBuffer(ArenaShapes.worldQuads()));
        //#endif
    }

    /**
     * Draws what a piece of furniture queued: its text first, then its quads, so that glass and glows
     * blend over the writing behind them. Since 1.21.9 the frame sorts what was submitted itself.
     */
    public void flush() {
        //#if MC < 1.21.9
        if (buffers instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource source) source.endLastBatch();
        QUADS.endBatch();
        //#endif
    }

    /** Lines of text are measured, and translated, once: the same few are written on every frame. */
    private static final java.util.Map<String, Integer> WIDTHS = new java.util.HashMap<>();
    private static final java.util.Map<String, String> TRANSLATIONS = new java.util.HashMap<>();
    private static final int CACHED = 4096;
    private static Object language, measuredWith;
    /** A matrix lent to each line of text, instead of a pose pushed for it: every push is two new matrices. */
    private static final Matrix4f TEXT_MATRIX = new Matrix4f();
    private static long frame;

    /** A frame is starting; called from Minecraft.runTick. */
    public static void frame() {
        frame++;
        if (SmokeBench.ENABLED) SmokeBench.frame();
    }

    /** Counts the frames, so that what several pieces of furniture share is drawn once in each. */
    static long frameNumber() {
        return frame;
    }

    /** The width of a line of text in the font. */
    static int width(Font font, String text) {
        if (font != measuredWith || WIDTHS.size() > CACHED) {
            measuredWith = font;
            WIDTHS.clear();
        }
        Integer width = WIDTHS.get(text);
        if (width == null) {
            width = font.width(text);
            WIDTHS.put(text, width);
        }
        return width;
    }

    /** A translated line without arguments, read again whenever the language changes. */
    static String translate(String key) {
        Object current = net.minecraft.locale.Language.getInstance();
        if (current != language || TRANSLATIONS.size() > CACHED) {
            language = current;
            TRANSLATIONS.clear();
        }
        String text = TRANSLATIONS.get(key);
        if (text == null) {
            text = net.minecraft.network.chat.Component.translatable(key).getString();
            TRANSLATIONS.put(key, text);
        }
        return text;
    }

    /** Screen pixels a block covers where the piece of furniture being drawn stands. */
    private static float pixelsPerBlock = Float.MAX_VALUE;
    /** Text lower than this on screen cannot be read; it is not drawn. */
    private static final float SMALLEST_TEXT = 2;

    /** Sets how large the next piece of furniture appears: it stands this many blocks from the camera. */
    static void focus(double distance) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        // A spyglass narrows the view far below the field of view set in the options.
        if (distance <= 1 || minecraft.player != null && minecraft.player.isScoping()) {
            pixelsPerBlock = Float.MAX_VALUE;
            return;
        }
        double fov = Math.toRadians(minecraft.options.fov().get());
        pixelsPerBlock = (float) (minecraft.getWindow().getHeight() / (2 * Math.tan(fov / 2) * distance));
    }

    /** How many screen pixels a length in the units of the pose covers, at the furniture being drawn. */
    static float pixels(PoseStack pose, float length) {
        Matrix4f matrix = pose.last().pose();
        float scale = (float) Math.sqrt(matrix.m10() * matrix.m10() + matrix.m11() * matrix.m11() + matrix.m12() * matrix.m12());
        return length * scale * pixelsPerBlock;
    }

    /** Whether a shape this long and wide, in the units of the pose, would cover less than a pixel either way. */
    static boolean tiny(PoseStack pose, float length, float width) {
        return pixels(pose, Math.max(Math.abs(length), Math.abs(width))) < 1;
    }

    /** How many segments a round shape of this radius needs: one for every three pixels of its rim, from 8 to most. */
    static int segments(PoseStack pose, float radius, int most) {
        float rim = (float) (2 * Math.PI) * pixels(pose, radius);
        return Math.max(8, Math.min(most, (int) Math.ceil(rim / 3)));
    }

    public void text(PoseStack pose, Font font, String text, float x, float y, int colour, int light) {
        if (pixels(pose, font.lineHeight) < SMALLEST_TEXT) return;
        //#if MC >= 1.21.9
        //$ collector.submitText(pose, x, y, net.minecraft.util.FormattedCharSequence.forward(text, net.minecraft.network.chat.Style.EMPTY),
        //$         false, Font.DisplayMode.NORMAL, light, colour, 0, 0);
        //#else
        font.drawInBatch(text, x, y, colour, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
        //#endif
    }

    /**
     * Writes text at (textX, textY), in a frame moved to (x, y, z) of the pose and scaled; the same as
     * pushing that pose, without the push.
     */
    public void text(PoseStack pose, Font font, String text, float x, float y, float z, float scale,
                     float textX, float textY, int colour, int light) {
        //#if MC >= 1.21.9
        //$ pose.pushPose();
        //$ pose.translate(x, y, z);
        //$ pose.scale(scale, scale, scale);
        //$ text(pose, font, text, textX, textY, colour, light);
        //$ pose.popPose();
        //#else
        if (pixels(pose, font.lineHeight * scale) < SMALLEST_TEXT) return;
        TEXT_MATRIX.set(pose.last().pose()).translate(x, y, z).scale(scale);
        font.drawInBatch(text, textX, textY, colour, false, TEXT_MATRIX, buffers, Font.DisplayMode.NORMAL, 0, light);
        //#endif
    }

    public void item(PoseStack pose, ItemStack stack, int light, Level level) {
        //#if MC >= 1.21.9
        //$ var state = new net.minecraft.client.renderer.item.ItemStackRenderState();
        //$ net.minecraft.client.Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, level, null, 0);
        //$ state.submit(pose, collector, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
        //#else
        net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GUI, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
        //#endif
    }

    /** One coloured vertex; the builder calls were renamed in 1.21. */
    public static void vertex(VertexConsumer vertices, Matrix4f matrix, float x, float y, float z, int colour) {
        //#if MC >= 1.21
        vertices.addVertex(matrix, x, y, z).setColor(colour);
        //#else
        //$ vertices.vertex(matrix, x, y, z).color(colour).endVertex();
        //#endif
    }
}
