package dev.gamblingitems.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gamblingitems.fabric.prediction.MarketView;
import dev.gamblingitems.fabric.prediction.OddsBoardBlock;
import dev.gamblingitems.fabric.prediction.OddsBoardEntity;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;

/** Draws the odds board: the busiest open questions and the probability the traders give them. */
//#if MC >= 1.21.9
//$ public final class OddsBoardRenderer implements BlockEntityRenderer<OddsBoardEntity, OddsBoardRenderer.State> {
//#else
public final class OddsBoardRenderer implements BlockEntityRenderer<OddsBoardEntity> {
//#endif
    private static final int TEXT = 0xffe8eff6, GOLD = 0xffffce69, YES = 0xff2fbf71, TRACK = 0xff304358, MUTED = 0xffb1bad3;
    private static final int LIGHT = 0xF000F0;
    private final Font font;

    public OddsBoardRenderer(BlockEntityRendererProvider.Context context) {
        //#if MC >= 1.21.9
        //$ font = context.font();
        //#else
        font = context.getFont();
        //#endif
    }

    //#if MC >= 1.21.9
    //$ public static final class State extends net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState {
    //$     OddsBoardEntity board;
    //$ }
    //$
    //$ @Override public State createRenderState() { return new State(); }
    //$
    //$ @Override public void extractRenderState(OddsBoardEntity board, State state, float partialTick,
    //$         net.minecraft.world.phys.Vec3 camera, net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
    //$     BlockEntityRenderer.super.extractRenderState(board, state, partialTick, camera, crumbling);
    //$     state.board = board;
    //$ }
    //$
    //$ @Override public void submit(State state, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector,
    //$         net.minecraft.client.renderer.state.CameraRenderState camera) {
    //$     draw(state.board, pose, new WorldCanvas(collector));
    //$ }
    //#elif MC >= 1.21.5
    //$ @Override public void render(OddsBoardEntity board, float partialTick, PoseStack pose,
    //$         net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay, net.minecraft.world.phys.Vec3 camera) {
    //$     draw(board, pose, new WorldCanvas(buffers));
    //$ }
    //#else
    @Override public void render(OddsBoardEntity board, float partialTick, PoseStack pose,
                                 net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay) {
        draw(board, pose, new WorldCanvas(buffers));
    }
    //#endif

    private void draw(OddsBoardEntity board, PoseStack pose, WorldCanvas canvas) {
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-board.getBlockState().getValue(OddsBoardBlock.FACING).toYRot()));
        // The panel stands against the back of its block; the text floats just in front of it.
        pose.translate(0, 0, -0.37);
        pose.scale(1f / 128, -1f / 128, 1f / 128);
        rectangle(pose, canvas, -62, -62, 62, 62, 0xff0d131c, 1);
        rectangle(pose, canvas, -62, -62, 62, -59, GOLD, 2);
        centred(pose, canvas, Component.translatable("gui.gamblingitems.market.board_title").getString(), -54, 0.8f, GOLD);
        List<MarketView> views = board.views();
        if (views.isEmpty()) {
            centred(pose, canvas, Component.translatable("gui.gamblingitems.market.board_empty").getString(), -4, 0.6f, MUTED);
        }
        for (int index = 0; index < views.size(); index++) {
            MarketView view = views.get(index);
            int top = -38 + index * 33;
            rectangle(pose, canvas, -58, top - 2, 58, top + 28, 0xff172231, 2);
            text(pose, canvas, view.text().getString(), -55, top, 0.5f, TEXT, 108);
            String price = view.price() / 100 + "%";
            text(pose, canvas, price, -55, top + 9, 1f, YES, 40);
            rectangle(pose, canvas, -20, top + 12, 55, top + 16, TRACK, 3);
            int filled = -20 + Math.round(75 * view.price() / 10_000f);
            if (filled > -20) rectangle(pose, canvas, -20, top + 12, filled, top + 16, YES, 4);
            text(pose, canvas, Component.translatable("gui.gamblingitems.market.board_volume",
                    GameScreens.value(view.volume())).getString(), -20, top + 19, 0.45f, MUTED, 75);
        }
        pose.popPose();
    }

    //#if MC >= 1.21.6
    //$ @Override public boolean shouldRenderOffScreen() { return false; }
    //#else
    @Override public boolean shouldRenderOffScreen(OddsBoardEntity board) { return false; }
    //#endif

    private void text(PoseStack pose, WorldCanvas canvas, String text, float x, float y, float scale, int colour, int width) {
        String fitted = font.plainSubstrByWidth(text, (int) (width / scale));
        if (fitted.length() < text.length() && fitted.length() > 1) fitted = fitted.substring(0, fitted.length() - 1) + "…";
        pose.pushPose();
        pose.translate(x, y, 8);
        pose.scale(scale, scale, scale);
        canvas.text(pose, font, fitted, 0, 0, colour, LIGHT);
        pose.popPose();
    }

    private void centred(PoseStack pose, WorldCanvas canvas, String text, float y, float scale, int colour) {
        String fitted = font.plainSubstrByWidth(text, (int) (116 / scale));
        text(pose, canvas, fitted, -font.width(fitted) * scale / 2, y, scale, colour, 116);
    }

    private void rectangle(PoseStack pose, WorldCanvas canvas, int x1, int y1, int x2, int y2, int colour, float depth) {
        canvas.quads(pose, (matrix, vertices) -> {
            WorldCanvas.vertex(vertices, matrix, x1, y1, depth, colour);
            WorldCanvas.vertex(vertices, matrix, x1, y2, depth, colour);
            WorldCanvas.vertex(vertices, matrix, x2, y2, depth, colour);
            WorldCanvas.vertex(vertices, matrix, x2, y1, depth, colour);
        });
    }
}
