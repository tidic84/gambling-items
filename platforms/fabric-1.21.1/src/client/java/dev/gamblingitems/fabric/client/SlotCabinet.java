package dev.gamblingitems.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gamblingitems.core.slots.SlotRules;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.block.SlotMachineBlock;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

/**
 * The four sides of a slot machine, drawn across the two blocks and a half of its cabinet: the
 * reels at the front, at the height of the eyes of whoever stands at it, trims and vents along the
 * flanks, the service hatch at the back. The arched roof is part of the block model, and the lever
 * is a piece of geometry of its own, so it can be pulled.
 *
 * <p>Coordinates are the pixels of a block face, 128 to a block, counted from the middle of the
 * block a player stands at: the cabinet runs from -64 to 64 across and from 64 at the floor up to
 * -214, where the roof begins to arch away and nothing is written any more.
 */
public final class SlotCabinet {
    private static final int BODY = 0xff1b2430, BODY_LIGHT = 0xff2b3a4c, WINDOW = 0xff070b12;
    /** The cabinet is trimmed in dark red; nothing on it is gold. */
    private static final int TRIM = 0xff8e1c1c, TRIM_DARK = 0xff5a1010, TEXT = 0xffe8eff6;
    private static final int SILVER = 0xffb9c4d0, GREEN = 0xff6cdeb7, RED = 0xffe8687d;
    private static final int REEL_WIDTH = 36, REEL_GAP = 4;
    /** The reels stop one after the other, the last one a third of a pull after the first. */
    private static final double STAGGER = 0.16;
    /** The highest line of the cabinet that is still a flat face rather than roof. */
    private static final float ROOF = -214;

    private SlotCabinet() {}

    public static boolean isCabinet(GameStationEntity station) {
        return station.getBlockState().getBlock() instanceof SlotMachineBlock;
    }

    public static void render(GameStationEntity station, float partialTick, PoseStack pose,
                              MultiBufferSource buffers, Font font) {
        double now = station.getLevel().getGameTime() + partialTick;
        int ticks = Math.max(1, station.animationTicks);
        double progress = station.startedAt == 0 ? 1
                : Math.max(0, Math.min(1, (now - station.startedAt) / ticks));
        boolean playing = station.animating();
        lever(station, pose, buffers, progress, playing);
        for (int quarter = 0; quarter < 4; quarter++) {
            face(pose, station, quarter);
            switch (quarter) {
                case 0 -> front(station, pose, buffers, font, now, progress, playing);
                case 2 -> back(pose, buffers, font);
                default -> flank(pose, buffers, playing && progress < 1, now);
            }
            pose.popPose();
        }
    }

    /** Puts the pose on one of the four faces of the cabinet, a hair outside the block. */
    private static void face(PoseStack pose, GameStationEntity station, int quarter) {
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-station.getBlockState()
                .getValue(SlotMachineBlock.FACING).toYRot() + quarter * 90));
        pose.translate(0, 0, 0.515);
        pose.scale(1f / 128, -1f / 128, 1f / 128);
    }

    /** The playing side: the marquee above, the three reels, the payline and the lever. */
    private static void front(GameStationEntity station, PoseStack pose, MultiBufferSource buffers,
                              Font font, double now, double progress, boolean playing) {
        quad(pose, buffers, -64, ROOF, 64, 64, BODY, 0);
        quad(pose, buffers, -60, ROOF + 4, 60, 58, BODY_LIGHT, 1);
        // The marquee crowns the cabinet; the reels sit under it, at eye height.
        panel(pose, buffers, -57, ROOF + 4, 57, -184, TRIM_DARK, 2);
        quad(pose, buffers, -52, ROOF + 8, 52, -188, TRIM, 3);
        label(pose, buffers, font, Component.translatable("gui.gamblingitems.mode.slot_machine").getString(),
                0, -198, TEXT, 0.8f, 4);

        boolean rolling = playing && progress < 1;
        boolean won = !station.idle() && !rolling && station.highlight;
        for (int index = 0; index < 12; index++) {
            int lamp = won ? GREEN : rolling && ((long) (now / 3) + index) % 3 == 0
                    ? TEXT : 0xffd35a61;
            float lampX = -53 + index * 9.5f;
            quad(pose, buffers, lampX, -183, lampX + 3, -180, lamp, 4);
        }

        int[] reels = line(station.reels);
        for (int reel = 0; reel < SlotRules.REELS; reel++) {
            float left = -58 + reel * (REEL_WIDTH + REEL_GAP);
            panel(pose, buffers, left - 2, -178, left + REEL_WIDTH + 2, -112,
                    won ? GREEN : SILVER, 4);
            quad(pose, buffers, left, -174, left + REEL_WIDTH, -116, 0xfff0eadb, 5);
            quad(pose, buffers, left, -174, left + REEL_WIDTH, -166, 0xffa9ada9, 6);
            quad(pose, buffers, left, -124, left + REEL_WIDTH, -116, 0xffb9bab1, 6);
            boolean stopped = progress >= 1 - STAGGER * (SlotRules.REELS - 1 - reel);
            double travel = now * 0.7 + reel * 2.3;
            int symbol = stopped ? reels[reel]
                    : Math.floorMod((long) Math.floor(travel), SlotRules.SYMBOLS);
            float offset = stopped ? 0 : (float) (travel - Math.floor(travel)) * 36;
            reelSymbol(pose, buffers, symbol, left + REEL_WIDTH / 2f, -145 + offset);
            if (!stopped) {
                reelSymbol(pose, buffers, (symbol + 1) % SlotRules.SYMBOLS,
                        left + REEL_WIDTH / 2f, -181 + offset);
            }
        }
        // Mark the payline at the edges so it never crosses the symbols.
        quad(pose, buffers, -63, -147, -56, -143, TRIM, 8);
        quad(pose, buffers, 56, -147, 63, -143, TRIM, 8);
        panel(pose, buffers, -56, -106, 56, -54, WINDOW, 2);

        String status = playing && progress < 1
                ? Component.translatable("gui.gamblingitems.slot_spinning").getString()
                : station.idle() ? Component.translatable("gui.gamblingitems.station_ready").getString()
                : Component.translatable("gui.gamblingitems." + station.resultKey).getString();
        int colour = playing && progress < 1 ? TEXT
                : station.idle() ? SILVER : station.highlight ? GREEN : RED;
        label(pose, buffers, font, status, 0, -96, colour, 0.8f, 4);
        label(pose, buffers, font, station.playerName, 0, -78, SILVER, 0.7f, 4);
        if (!station.idle() && station.multiplier > 0) {
            label(pose, buffers, font, GameScreens.multiplier(station.multiplier), 0, -60, GREEN, 0.9f, 4);
        }
        paytable(pose, buffers, font);
    }

    /** The best lines of the paytable, printed on the belly of the cabinet. */
    private static void paytable(PoseStack pose, MultiBufferSource buffers, Font font) {
        // Above the tray, which stands out of the cabinet itself.
        panel(pose, buffers, -52, -50, 52, 14, 0xff141b25, 2);
        label(pose, buffers, font, Component.translatable("gui.gamblingitems.slot_paytable").getString(),
                0, -42, SILVER, 0.5f, 4);
        for (int line = 0; line < 3; line++) {
            int symbol = SlotRules.SYMBOLS - 1 - line;
            for (int reel = 0; reel < 3; reel++) {
                symbol(pose, buffers, symbol, -36 + reel * 12, -27 + line * 13, 9, 4);
            }
            label(pose, buffers, font, GameScreens.multiplier(SlotRules.tripleOf(symbol)), 26,
                    -27 + line * 13, TEXT, 0.5f, 4);
        }
        panel(pose, buffers, -43, 22, 43, 40, SILVER, 3);
        quad(pose, buffers, -39, 24, 39, 35, WINDOW, 5);
        quad(pose, buffers, -64, 47, 64, 64, WINDOW, 3);
        quad(pose, buffers, -60, 47, 60, 49, TRIM, 4);
    }

    /** A flank: trims from top to bottom, a grille of vents, and the slot the coins go in. */
    private static void flank(PoseStack pose, MultiBufferSource buffers, boolean running, double now) {
        quad(pose, buffers, -64, ROOF, 64, 64, BODY, 0);
        quad(pose, buffers, -58, ROOF + 4, 58, 58, BODY_LIGHT, 1);
        // Two dark red rails running the height of the cabinet.
        quad(pose, buffers, -62, ROOF + 2, -58, 60, TRIM_DARK, 2);
        quad(pose, buffers, 58, ROOF + 2, 62, 60, TRIM_DARK, 2);
        // The grille: a bank of vents alongside the reels.
        quad(pose, buffers, -40, -180, 40, -112, 0xff141b25, 2);
        for (int row = 0; row < 9; row++) {
            float top = -176 + row * 7;
            quad(pose, buffers, -36, top, 36, top + 4, 0xff0a0f16, 3);
        }
        // A lamp on the flank, lit while the reels turn, so the machine reads from the side.
        int lamp = running && ((long) (now / 5) % 2 == 0) ? TRIM : TRIM_DARK;
        for (int index = 0; index < 3; index++) {
            quad(pose, buffers, -10 + index * 10 - 4, -100, -10 + index * 10 + 4, -92, lamp, 3);
        }
        // The coin slot and its plate.
        quad(pose, buffers, -22, -20, 22, 6, 0xff141b25, 2);
        quad(pose, buffers, -14, -12, 14, -6, WINDOW, 3);
        quad(pose, buffers, -18, 0, 18, 2, TRIM_DARK, 3);
        // The plinth the cabinet stands on.
        quad(pose, buffers, -64, 40, 64, 64, 0xff10161f, 2);
    }

    /** The back: a bolted service hatch, plus the vents the cabinet breathes through. */
    private static void back(PoseStack pose, MultiBufferSource buffers, Font font) {
        quad(pose, buffers, -64, ROOF, 64, 64, BODY, 0);
        quad(pose, buffers, -58, ROOF + 4, 58, 58, 0xff222d3b, 1);
        quad(pose, buffers, -44, -180, 44, -40, 0xff192230, 2);
        quad(pose, buffers, -40, -176, 40, -44, BODY_LIGHT, 3);
        for (int corner = 0; corner < 4; corner++) {
            float x = corner % 2 == 0 ? -34 : 34, y = corner / 2 == 0 ? -170 : -50;
            quad(pose, buffers, x - 3, y - 3, x + 3, y + 3, SILVER, 4);
        }
        label(pose, buffers, font, Component.translatable("gui.gamblingitems.slot_service").getString(),
                0, -110, SILVER, 0.6f, 4);
        for (int row = 0; row < 5; row++) {
            float top = 4 + row * 7;
            quad(pose, buffers, -30, top, 30, top + 4, 0xff0a0f16, 2);
        }
        quad(pose, buffers, -64, 40, 64, 64, 0xff10161f, 2);
    }

    /**
     * The lever, standing out of the right flank of the cabinet: an arm and a ball, on a pivot.
     * It is pulled down as a round starts and rises back on its own once the reels have stopped.
     */
    private static void lever(GameStationEntity station, PoseStack pose, MultiBufferSource buffers,
                              double progress, boolean playing) {
        // Down in the first third of a pull, then back up: a hand lets go of a lever.
        double pulled = !playing ? 0
                : progress < 0.3 ? progress / 0.3 : Math.max(0, 1 - (progress - 0.3) / 0.7);
        float angle = (float) (-0.35 + 1.55 * pulled);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-station.getBlockState()
                .getValue(SlotMachineBlock.FACING).toYRot()));
        // The base plate the lever turns in, then the arm itself.
        pose.translate(0.58, 0.55, 0.26);
        box(pose, buffers, 0.08f, 0.07f, 0.07f, TRIM_DARK);
        pose.translate(0.08, 0, 0);
        box(pose, buffers, 0.025f, 0.045f, 0.045f, SILVER);
        pose.mulPose(Axis.XP.rotation(angle));
        pose.translate(0, 0.17, 0);
        box(pose, buffers, 0.022f, 0.17f, 0.022f, SILVER);
        pose.translate(0, 0.19, 0);
        box(pose, buffers, 0.05f, 0.05f, 0.05f, TRIM);
        pose.translate(0, 0.03, 0.01);
        box(pose, buffers, 0.035f, 0.013f, 0.035f, 0xffd35a61);
        pose.popPose();
    }

    /** A box centred on the current origin, drawn as its six faces. */
    private static void box(PoseStack pose, MultiBufferSource buffers, float halfX, float halfY,
                            float halfZ, int colour) {
        var vertices = buffers.getBuffer(RenderType.gui());
        var matrix = pose.last().pose();
        float[][] corners = {
                {-halfX, -halfY, -halfZ}, {halfX, -halfY, -halfZ},
                {halfX, halfY, -halfZ}, {-halfX, halfY, -halfZ},
                {-halfX, -halfY, halfZ}, {halfX, -halfY, halfZ},
                {halfX, halfY, halfZ}, {-halfX, halfY, halfZ}};
        int[][] faces = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2},
                {3, 2, 6, 7}, {4, 5, 1, 0}};
        int faceIndex = 0;
        for (int[] face : faces) {
            float brightness = switch (faceIndex++) {
                case 0, 1 -> 0.85f;
                case 2, 3 -> 0.7f;
                case 5 -> 0.55f;
                default -> 1f;
            };
            int shaded = 0xff000000 | (int) (((colour >> 16) & 255) * brightness) << 16
                    | (int) (((colour >> 8) & 255) * brightness) << 8
                    | (int) ((colour & 255) * brightness);
            for (int index : face) {
                vertices.addVertex(matrix, corners[index][0], corners[index][1], corners[index][2])
                        .setColor(shaded);
            }
        }
    }

    /** The line a cabinet was told to show, as "0,4,7"; every unknown reel stays blank. */
    private static int[] line(String reels) {
        int[] line = {-1, -1, -1};
        if (reels.isEmpty()) return line;
        String[] parts = reels.split(",");
        for (int index = 0; index < line.length && index < parts.length; index++) {
            try {
                int symbol = Integer.parseInt(parts[index]);
                if (symbol >= 0 && symbol < SlotRules.SYMBOLS) line[index] = symbol;
            } catch (NumberFormatException ignored) {
                // A cabinet never breaks on a line it cannot read.
            }
        }
        return line;
    }

    private static void quad(PoseStack pose, MultiBufferSource buffers, float x1, float y1,
                             float x2, float y2, int colour, float lift) {
        var vertices = buffers.getBuffer(RenderType.gui());
        var matrix = pose.last().pose();
        vertices.addVertex(matrix, x1, y1, lift).setColor(colour);
        vertices.addVertex(matrix, x1, y2, lift).setColor(colour);
        vertices.addVertex(matrix, x2, y2, lift).setColor(colour);
        vertices.addVertex(matrix, x2, y1, lift).setColor(colour);
    }

    private static void symbol(PoseStack pose, MultiBufferSource buffers, int symbol,
                               float x, float y, float size, float lift) {
        SlotSymbols.draw(symbol, x, y, size,
                (x1, y1, x2, y2, colour) -> quad(pose, buffers, x1, y1, x2, y2, colour, lift));
    }

    private static void reelSymbol(PoseStack pose, MultiBufferSource buffers, int symbol, float x, float y) {
        SlotSymbols.draw(symbol, x, y, 27, (x1, y1, x2, y2, colour) -> {
            float top = Math.max(-165, y1), bottom = Math.min(-125, y2);
            if (top < bottom) quad(pose, buffers, x1, top, x2, bottom, colour, 7);
        });
    }

    private static void panel(PoseStack pose, MultiBufferSource buffers, float x1, float y1,
                              float x2, float y2, int colour, float lift) {
        quad(pose, buffers, x1, y1, x2, y2, WINDOW, lift);
        quad(pose, buffers, x1, y1, x2, y1 + 2, SILVER, lift + 0.2f);
        quad(pose, buffers, x1, y1, x1 + 2, y2, BODY_LIGHT, lift + 0.3f);
        quad(pose, buffers, x1 + 2, y1 + 2, x2 - 2, y2 - 2, colour, lift + 0.4f);
    }

    /** Writes on the cabinet, the given point being the middle of the line at any size. */
    private static void label(PoseStack pose, MultiBufferSource buffers, Font font, String text,
                              float x, float y, int colour, float scale, float lift) {
        if (text.isEmpty()) return;
        pose.pushPose();
        pose.translate(x, y, lift + 0.4f);
        float fitted = Math.min(scale, 104f / Math.max(1, font.width(text)));
        pose.scale(fitted, fitted, fitted);
        font.drawInBatch(text, -font.width(text) / 2f, -4.5f, colour, false, pose.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, 0xF000F0);
        pose.popPose();
    }
}
