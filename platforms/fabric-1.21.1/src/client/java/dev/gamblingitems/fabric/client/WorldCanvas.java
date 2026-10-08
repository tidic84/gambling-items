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
    public WorldCanvas(net.minecraft.client.renderer.MultiBufferSource buffers) { this.buffers = buffers; }
    //#endif

    public void quads(PoseStack pose, Quads draw) {
        //#if MC >= 1.21.9
        //$ collector.submitCustomGeometry(pose, ArenaShapes.worldQuads(), (entry, vertices) -> draw.draw(entry.pose(), vertices));
        //#else
        draw.draw(pose.last().pose(), buffers.getBuffer(ArenaShapes.worldQuads()));
        //#endif
    }

    public void text(PoseStack pose, Font font, String text, float x, float y, int colour, int light) {
        //#if MC >= 1.21.9
        //$ collector.submitText(pose, x, y, net.minecraft.util.FormattedCharSequence.forward(text, net.minecraft.network.chat.Style.EMPTY),
        //$         false, Font.DisplayMode.NORMAL, light, colour, 0, 0);
        //#else
        font.drawInBatch(text, x, y, colour, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
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
