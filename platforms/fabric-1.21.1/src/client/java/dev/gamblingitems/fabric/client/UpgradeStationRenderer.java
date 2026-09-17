package dev.gamblingitems.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gamblingitems.fabric.block.UpgradeStationBlock;
import dev.gamblingitems.fabric.block.UpgradeStationEntity;
import dev.gamblingitems.fabric.upgrade.UpgradeMenu;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;

public final class UpgradeStationRenderer implements BlockEntityRenderer<UpgradeStationEntity> {
    private final Font font;
    public UpgradeStationRenderer(BlockEntityRendererProvider.Context context) { font = context.getFont(); }

    @Override public void render(UpgradeStationEntity station, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5, 0.66, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-station.getBlockState().getValue(UpgradeStationBlock.FACING).toYRot()));
        pose.translate(0, 0, 0.505);
        pose.scale(0.008f, -0.008f, 0.008f);
        line(pose, buffers, "UPGRADER", -21, 0xffffce69);
        if (station.target.isEmpty()) {
            line(pose, buffers, Component.translatable("gui.gamblingitems.station_ready").getString(), -3, 0xffe8eff6);
        } else {
            long elapsed = station.getLevel().getGameTime() - station.startedAt;
            line(pose, buffers, font.plainSubstrByWidth(station.playerName, 94), -8, 0xffe8eff6);
            boolean rolling = elapsed < UpgradeMenu.ANIMATION_TICKS;
            String status = rolling ? String.format(Locale.ROOT, "%.2f%%", station.chanceBasisPoints / 100.0)
                    : Component.translatable("gui.gamblingitems." + (station.won ? "won" : "lost")).getString();
            line(pose, buffers, status, 6, station.won ? 0xff6cdeb7 : 0xffffce69);
            if (rolling) {
                int step = (int) (elapsed / 5 % 4);
                line(pose, buffers, ".".repeat(step + 1), 19, 0xffe8eff6);
            }
        }
        pose.popPose();
    }

    private void line(PoseStack pose, MultiBufferSource buffers, String text, int y, int color) {
        font.drawInBatch(text, -font.width(text) / 2f, y, color, false,
                pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
    }
}
