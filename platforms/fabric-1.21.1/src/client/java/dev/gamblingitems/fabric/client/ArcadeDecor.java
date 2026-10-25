package dev.gamblingitems.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gamblingitems.fabric.arcade.DecorBlock;
import dev.gamblingitems.fabric.block.GameStationEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import static dev.gamblingitems.fabric.client.SlotCabinet.*;

/**
 * The arcade's decor: the leaderboard, a lit board two blocks wide over its wall plate listing the
 * week's best wins, and the croupier, who looks at whoever comes near and deals while a table nearby plays.
 */
final class ArcadeDecor {
    private static final int SKIN = 0xffd9a27a, HAIR = 0xff3a2618, SHIRT = 0xfff3eee2, VEST = 0xff8a1e2c,
            TROUSERS = 0xff1c1c22, SHOE = 0xff0e0e10, VISOR = 0xc02f9a5c;

    private ArcadeDecor() {}

    static void render(GameStationEntity station, DecorBlock block, float partialTick, PoseStack pose, WorldCanvas buffers, Font font) {
        Direction facing = station.getBlockState().getValue(DecorBlock.FACING);
        double now = station.getLevel().getGameTime() + partialTick;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        pose.translate(-0.5, -0.5, -0.5);
        pose.scale(1f / 16, 1f / 16, 1f / 16);
        try {
            if (block.kind() == DecorBlock.Kind.LEADERBOARD) leaderboard(station, pose, buffers, font, now);
            else croupier(station, facing, pose, buffers, now);
        } finally {
            pose.popPose();
        }
    }

    // --- Leaderboard ----------------------------------------------------------------------------

    private static void leaderboard(GameStationEntity station, PoseStack pose, WorldCanvas buffers, Font font, double now) {
        // The board hangs in front of its plate: two blocks wide, two high, from u = -8 to 24 and y = -4 to 28.
        cube(pose, buffers, -8.6f, -4.6f, 13, 24.6f, 28.6f, 13.9f, BRASS_DARK, 1);
        cube(pose, buffers, -8.2f, -4.2f, 12.8f, 24.2f, 28.2f, 13, 0xff0a1019, 1);
        surface(pose, 12.8f);
        quad(pose, buffers, -128, -160, 128, 96, 0xff0a1019, 0);
        quad(pose, buffers, -128, -160, 128, -128, 0xff3a1418, 0.05f);
        int index = 0;
        for (int bulb = 0; bulb < 20; bulb++) {
            float x = -122 + bulb * 12.8f;
            int colour = lamp(bulb, now, false, false);
            quad(pose, buffers, x - 1.6f, -157, x + 1.6f, -154, colour, 0.1f);
            quad(pose, buffers, x - 1.6f, 92, x + 1.6f, 95, lamp(bulb + 1, now, false, false), 0.1f);
        }
        label(pose, buffers, font, Component.translatable("gui.gamblingitems.leaderboard_title").getString(), 0, -143, GOLD, 1.3f, 240, 0.2f);
        String[] rows = station.previewText.isEmpty() ? new String[0] : station.previewText.split("\n");
        if (rows.length == 0) {
            label(pose, buffers, font, Component.translatable("gui.gamblingitems.leaderboard_empty").getString(), 0, -20, SILVER, 0.9f, 240, 0.2f);
        }
        for (String row : rows) {
            String[] cells = row.split("\\|");
            if (cells.length < 4 || index >= 10) continue;
            float y = -114 + index * 20.5f;
            int colour = index == 0 ? GOLD : index < 3 ? TEXT : SILVER;
            if (index % 2 == 0) quad(pose, buffers, -124, y - 9, 124, y + 9, 0xff111a26, 0.05f);
            label(pose, buffers, font, String.valueOf(index + 1), -112, y, colour, 0.9f, 16, 0.2f);
            labelLeft(pose, buffers, font, cells[0], -98, y, colour, 0.85f, 80);
            labelLeft(pose, buffers, font, Component.translatable("gui.gamblingitems.mode." + cells[1]).getString(), -12, y, SILVER, 0.7f, 70);
            label(pose, buffers, font, times(cells[3]), 76, y, GREEN, 0.75f, 30, 0.2f);
            label(pose, buffers, font, cells[2] + " T", 108, y, colour, 0.8f, 32, 0.2f);
            index++;
        }
        pose.popPose();
    }

    private static String times(String hundredths) {
        try {
            return GameScreens.multiplier(Integer.parseInt(hundredths));
        } catch (NumberFormatException exception) {
            return "";
        }
    }

    private static void labelLeft(PoseStack pose, WorldCanvas buffers, Font font, String text, float x, float y, int colour,
                                  float scale, float width) {
        float fitted = Math.min(scale, width / Math.max(1, font.width(text)));
        label(pose, buffers, font, text, x + font.width(text) * fitted / 2, y, colour, scale, width, 0.2f);
    }

    // --- Croupier -------------------------------------------------------------------------------

    private static void croupier(GameStationEntity station, Direction facing, PoseStack pose, WorldCanvas buffers, double now) {
        boolean dealing = busy(station);
        float light = 0.35f + 0.65f * station.getLevel().getMaxLocalRawBrightness(station.getBlockPos().above()) / 15f;
        // Legs and shoes.
        cube(pose, buffers, 5.6f, 1, 7, 7.8f, 12, 9.2f, TROUSERS, light);
        cube(pose, buffers, 8.2f, 1, 7, 10.4f, 12, 9.2f, TROUSERS, light);
        cube(pose, buffers, 5.4f, 0, 6.2f, 7.9f, 1, 9.4f, SHOE, light);
        cube(pose, buffers, 8.1f, 0, 6.2f, 10.6f, 1, 9.4f, SHOE, light);
        // The body: a white shirt under a red waistcoat, a black bow tie.
        cube(pose, buffers, 4.6f, 12, 6.6f, 11.4f, 21.6f, 9.6f, SHIRT, light);
        cube(pose, buffers, 4.5f, 12, 6.5f, 11.5f, 19.8f, 9.7f, VEST, light);
        cube(pose, buffers, 7.3f, 15, 6.4f, 8.7f, 21.6f, 6.5f, SHIRT, light);
        cube(pose, buffers, 6.8f, 20.4f, 6.2f, 9.2f, 21.3f, 6.4f, TROUSERS, light);
        cube(pose, buffers, 7.6f, 16.5f, 6.35f, 8.4f, 17.2f, 6.45f, GOLD, light);
        // The arms: at rest they hang; while a table nearby plays they deal over it, one after the other.
        for (int side = 0; side < 2; side++) {
            float shoulder = side == 0 ? 3.5f : 12.5f;
            double swing = dealing ? -55 + 25 * Math.sin(now * 0.45 + side * Math.PI) : 3 * Math.sin(now * 0.05 + side);
            pose.pushPose();
            pose.rotateAround(Axis.XP.rotationDegrees((float) swing), 16 - shoulder, 21, 8);
            cube(pose, buffers, shoulder - 1.1f, 12.6f, 7, shoulder + 1.1f, 21.4f, 9.2f, SHIRT, light);
            cube(pose, buffers, shoulder - 1.15f, 17.5f, 6.95f, shoulder + 1.15f, 18.2f, 9.25f, VEST, light);
            cube(pose, buffers, shoulder - 1, 11.2f, 7.1f, shoulder + 1, 12.6f, 9.1f, SKIN, light);
            if (dealing && side == 1) cube(pose, buffers, shoulder - 0.9f, 10.4f, 6.4f, shoulder + 0.9f, 11.2f, 8, 0xfffbf8f0, light);
            pose.popPose();
        }
        // The head turns to the nearest player within eight blocks.
        pose.pushPose();
        pose.rotateAround(Axis.YP.rotation(headTurn(station, facing)), 8, 21.6f, 8);
        cube(pose, buffers, 5, 21.6f, 5, 11, 27.6f, 11, SKIN, light);
        cube(pose, buffers, 4.8f, 26, 5.2f, 11.2f, 28, 11.2f, HAIR, light);
        cube(pose, buffers, 4.8f, 22.5f, 9.5f, 11.2f, 26, 11.2f, HAIR, light);
        cube(pose, buffers, 6.2f, 24.4f, 4.95f, 7.4f, 25.4f, 5, 0xff1a1a24, light);
        cube(pose, buffers, 8.6f, 24.4f, 4.95f, 9.8f, 25.4f, 5, 0xff1a1a24, light);
        cube(pose, buffers, 6.8f, 22.6f, 4.95f, 9.2f, 23.1f, 5, 0xff9a5a4a, light);
        // The dealer's green visor.
        cube(pose, buffers, 4.7f, 26.6f, 3.2f, 11.3f, 27.1f, 8, VISOR, light);
        pose.popPose();
    }

    /** The turn of the head towards the nearest player, in radians about the vertical, at most a quarter each way. */
    private static float headTurn(GameStationEntity station, Direction facing) {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        BlockPos pos = station.getBlockPos();
        double dx = player.getX() - (pos.getX() + 0.5), dz = player.getZ() - (pos.getZ() + 0.5);
        if (dx * dx + dz * dz > 64) return 0;
        double turn = Math.atan2(dx, dz) - Math.atan2(facing.getStepX(), facing.getStepZ());
        turn = Math.IEEEremainder(turn, 2 * Math.PI);
        return (float) Math.max(-Math.PI / 2, Math.min(Math.PI / 2, turn));
    }

    /** Whether a casino table within three blocks is playing; looked up twice a second, not every frame. */
    private static boolean busy(GameStationEntity station) {
        var level = station.getLevel();
        long now = level.getGameTime();
        if (now - station.busyCheckedAt < 10 && now >= station.busyCheckedAt) return station.busyNearby;
        station.busyCheckedAt = now;
        boolean busy = false;
        BlockPos centre = station.getBlockPos();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-3, -1, -3), centre.offset(3, 1, 3))) {
            if (level.getBlockEntity(pos) instanceof GameStationEntity table && table != station
                    && StationTables.hasTable(table) && table.animating()) {
                busy = true;
                break;
            }
        }
        station.busyNearby = busy;
        return busy;
    }
}
