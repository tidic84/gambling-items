package dev.gamblingitems.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gamblingitems.fabric.block.GameStationBlock;
import dev.gamblingitems.fabric.block.GameStationEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Draws the public screen of a station. It shows what happened, never anyone's inventory. */
public final class GameStationRenderer implements BlockEntityRenderer<GameStationEntity> {
    private static final int TEXT = 0xffe8eff6, GOLD = 0xffffce69, GREEN = 0xff6cdeb7;
    private final Font font;

    public GameStationRenderer(BlockEntityRendererProvider.Context context) { font = context.getFont(); }

    @Override public void render(GameStationEntity station, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5, 0.66, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-station.getBlockState().getValue(GameStationBlock.FACING).toYRot()));
        pose.translate(0, 0, 0.505);
        pose.scale(0.008f, -0.008f, 0.008f);
        line(pose, buffers, Component.translatable("gui.gamblingitems.station." + station.mode().id()).getString(),
                -21, GOLD);
        if (station.idle()) {
            line(pose, buffers, Component.translatable("gui.gamblingitems.station_ready").getString(), -3, TEXT);
            pose.popPose();
            return;
        }
        long elapsed = station.getLevel().getGameTime() - station.startedAt;
        boolean rolling = elapsed < station.durationTicks;
        line(pose, buffers, font.plainSubstrByWidth(station.playerName, 94), -8, TEXT);
        line(pose, buffers, rolling ? station.rollingText : result(station), 6,
                station.highlight && !rolling ? GREEN : GOLD);
        if (rolling) line(pose, buffers, ".".repeat((int) (elapsed / 5 % 4) + 1), 19, TEXT);
        pose.popPose();
    }

    private String result(GameStationEntity station) {
        if (station.resultItem.isEmpty()) {
            return Component.translatable("gui.gamblingitems." + station.resultKey).getString();
        }
        ResourceLocation id = ResourceLocation.tryParse(station.resultItem);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return station.resultItem;
        return font.plainSubstrByWidth(new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString(), 94);
    }

    private void line(PoseStack pose, MultiBufferSource buffers, String text, int y, int color) {
        font.drawInBatch(text, -font.width(text) / 2f, y, color, false,
                pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
    }
}
