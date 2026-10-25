package dev.gamblingitems.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.slots.SlotRules;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.block.SlotMachineBlock;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * What a cabinet shows on top of its block model: lit screens, lamps and the parts that move.
 * The cabinet itself (its body, trims, deck and glass case) is the block model drawn by
 * tools/generate_cabinet_models.py; everything here sits on the faces that script names "screen".
 *
 * <p>Two coordinate systems are used. In model space, a unit is a pixel of the block model:
 * u runs from the player's left (0) to right (16), y from the floor (0) to the top of the cabinet
 * (40), z from the front (0) to the back (16), exactly as in the script. A screen is drawn in
 * canvas space, eight canvas pixels to a model pixel, centred on u = 8 and y = 8, x to the
 * player's right and y downwards, so a point of the model is at ((u - 8) * 8, (8 - y) * 8).
 */
public final class SlotCabinet {
    static final int TEXT = 0xfff3eee2, SILVER = 0xffb9c4d0, GREEN = 0xff6cdeb7, RED = 0xffe8687d;
    static final int GOLD = 0xffd9b25a, BRASS = 0xffb08d43, BRASS_DARK = 0xff6b5631, DARK = 0xff0a1019;
    static final int STEEL = 0xff8795a1, CHROME = 0xffd5dde6, LAMP_OFF = 0xff5a2a2e;
    /** The reels stop one after the other, the last one a third of a pull after the first. */
    private static final double STAGGER = 0.16;
    private static final float REEL_WIDTH = 27, REEL_GAP = 4, REEL_LEFT = -44.5f, REEL_TOP = -164, REEL_BOTTOM = -97;

    private SlotCabinet() {}

    public static boolean isCabinet(GameStationEntity station) {
        return station.getBlockState().getBlock() instanceof SlotMachineBlock;
    }

    public static void render(GameStationEntity station, float partialTick, PoseStack pose,
                              WorldCanvas buffers, Font font) {
        double now = station.getLevel().getGameTime() + partialTick;
        int ticks = Math.max(1, station.animationTicks);
        double progress = station.startedAt == 0 ? 1
                : MachineMotion.progress((now - station.startedAt) / ticks);
        boolean playing = station.animating();
        // Props inside a glass case follow the light around the cabinet; screens and lamps glow.
        int raw = station.getLevel().getMaxLocalRawBrightness(station.getBlockPos().above());
        float light = 0.3f + 0.7f * raw / 15f;
        model(pose, station);
        if (station.mode() == GameMode.SLOT_MACHINE) slots(station, pose, buffers, font, now, progress, playing);
        else MachineCabinet.render(station, pose, buffers, font, now, progress, playing, light);
        pose.popPose();
    }

    /** Pushes the pose into model space: one unit per pixel of the block model, origin at its corner. */
    static void model(PoseStack pose, GameStationEntity station) {
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - station.getBlockState().getValue(SlotMachineBlock.FACING).toYRot()));
        pose.translate(-0.5, -0.5, -0.5);
        pose.scale(1f / 16, 1f / 16, 1f / 16);
    }

    /** From model space, pushes a canvas lying on the face at depth z, a hair in front of it. */
    static void surface(PoseStack pose, float z) {
        pose.pushPose();
        pose.translate(8, 8, z - 0.03f);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(1f / 8, -1f / 8, 1f / 8);
    }

    private static void slots(GameStationEntity station, PoseStack pose, WorldCanvas buffers, Font font,
                              double now, double progress, boolean playing) {
        boolean rolling = playing && progress < 1;
        boolean won = !station.idle() && !rolling && station.highlight;
        lever(pose, buffers, progress, playing);

        surface(pose, 0.3f);
        sign(pose, buffers, font, Component.translatable("gui.gamblingitems.mode.slot_machine").getString(),
                -59, -227, 59, -197, rolling, won, now);
        pose.popPose();

        // Chasing lamps along the brass bar over the reels.
        surface(pose, 1.6f);
        for (int index = 0; index < 14; index++) {
            float x = -50 + index * 7.7f;
            quad(pose, buffers, x - 1.6f, -178.5f, x + 1.6f, -175.5f, lamp(index, now, rolling, won), 0);
        }
        pose.popPose();

        surface(pose, 1.9f);
        reels(pose, buffers, station, now, progress, playing, won);
        status(pose, buffers, font, station, rolling, "slot_spinning", -36, -79, 36, -63);
        pose.popPose();

        surface(pose, 1.4f);
        paytable(pose, buffers, font);
        pose.popPose();
    }

    /** A lamp of a chase: steady red at rest, running while the game plays, all green on a win. */
    static int lamp(int index, double now, boolean rolling, boolean won) {
        if (won) return ((long) (now / 4) + index) % 2 == 0 ? GREEN : 0xff2f8a6c;
        if (rolling) return ((long) (now / 2) + index) % 3 == 0 ? TEXT : LAMP_OFF;
        return index % 2 == 0 ? 0xffe0a24a : LAMP_OFF;
    }

    /** A lit sign: a glowing band, the name in gold, and bulbs above and below it. */
    static void sign(PoseStack pose, WorldCanvas buffers, Font font, String name, float x1, float y1, float x2, float y2,
                     boolean rolling, boolean won, double now) {
        float height = y2 - y1;
        quad(pose, buffers, x1, y1, x2, y2, 0xff1a0d10, 0);
        for (int band = 0; band < 6; band++) {
            float t = band / 6f;
            int glow = band < 3 ? 0xff3a1418 : 0xff2a1014;
            quad(pose, buffers, x1, y1 + height * t, x2, y1 + height * (t + 1 / 6f), glow, 0.05f);
        }
        quad(pose, buffers, x1 + 3, y1 + height * 0.25f, x2 - 3, y2 - height * 0.25f, 0xff4a1a1e, 0.1f);
        label(pose, buffers, font, name, (x1 + x2) / 2, (y1 + y2) / 2, GOLD, height / 14f, x2 - x1 - 16, 0.3f);
        int bulbs = Math.max(4, (int) ((x2 - x1) / 9));
        for (int index = 0; index < bulbs; index++) {
            float x = x1 + 4 + index * (x2 - x1 - 8) / (bulbs - 1);
            int colour = lamp(index, now, rolling, won);
            quad(pose, buffers, x - 1.4f, y1 + 1.5f, x + 1.4f, y1 + 4.3f, colour, 0.2f);
            quad(pose, buffers, x - 1.4f, y2 - 4.3f, x + 1.4f, y2 - 1.5f, lamp(index + 1, now, rolling, won), 0.2f);
        }
    }

    /** What the cabinet is doing in one line; between plays, the last player and what they won. */
    static void status(PoseStack pose, WorldCanvas buffers, Font font, GameStationEntity station, boolean rolling,
                       String rollingKey, float x1, float y1, float x2, float y2) {
        quad(pose, buffers, x1, y1, x2, y2, DARK, 0);
        String text;
        int colour;
        if (rolling) {
            text = Component.translatable("gui.gamblingitems." + rollingKey).getString();
            colour = TEXT;
        } else if (station.idle()) {
            text = Component.translatable("gui.gamblingitems.station_ready").getString();
            colour = SILVER;
        } else {
            text = Component.translatable("gui.gamblingitems." + station.resultKey).getString();
            if (station.multiplier > 0) text += "  " + GameScreens.multiplier(station.multiplier);
            colour = station.highlight ? GREEN : RED;
        }
        boolean twoLines = y2 - y1 >= 26 && !station.playerName.isEmpty() && !station.idle();
        float middle = (y1 + y2) / 2;
        float scale = Math.min(0.9f, (y2 - y1) / (twoLines ? 24f : 13f));
        label(pose, buffers, font, text, (x1 + x2) / 2, twoLines ? middle - 6 : middle, colour, scale, x2 - x1 - 6, 0.2f);
        if (twoLines) label(pose, buffers, font, station.playerName, (x1 + x2) / 2, middle + 7, SILVER, scale * 0.85f, x2 - x1 - 6, 0.2f);
    }

    /** The three reels behind their glass, and the payline across them. */
    private static void reels(PoseStack pose, WorldCanvas buffers, GameStationEntity station, double now,
                              double progress, boolean playing, boolean won) {
        int[] line = line(station.reels);
        float middle = (REEL_TOP + REEL_BOTTOM) / 2;
        quad(pose, buffers, -45.6f, -167.2f, 45.6f, -93.6f, 0xff05080d, 0);
        for (int reel = 0; reel < SlotRules.REELS; reel++) {
            float left = REEL_LEFT + reel * (REEL_WIDTH + REEL_GAP), right = left + REEL_WIDTH;
            // The drum: cream in the middle, shaded where it curves away top and bottom.
            quad(pose, buffers, left, REEL_TOP, right, REEL_BOTTOM, 0xfff0eadb, 0.1f);
            quad(pose, buffers, left, REEL_TOP, right, REEL_TOP + 9, 0xffb8b2a4, 0.15f);
            quad(pose, buffers, left, REEL_TOP + 9, right, REEL_TOP + 14, 0xffd8d2c3, 0.15f);
            quad(pose, buffers, left, REEL_BOTTOM - 14, right, REEL_BOTTOM - 9, 0xffd8d2c3, 0.15f);
            quad(pose, buffers, left, REEL_BOTTOM - 9, right, REEL_BOTTOM, 0xffb8b2a4, 0.15f);
            boolean stopped = !playing || progress >= 1 - STAGGER * (SlotRules.REELS - 1 - reel);
            double travel = now * 0.7 + reel * 2.3;
            int symbol = stopped ? line[reel] : Math.floorMod((long) Math.floor(travel), SlotRules.SYMBOLS);
            float offset = stopped ? 0 : (float) (travel - Math.floor(travel)) * 30;
            reelSymbol(pose, buffers, symbol, left + REEL_WIDTH / 2, middle + offset);
            if (!stopped) reelSymbol(pose, buffers, (symbol + 1) % SlotRules.SYMBOLS, left + REEL_WIDTH / 2, middle - 30 + offset);
        }
        int payline = won ? GREEN : 0xccd2434f;
        quad(pose, buffers, -45, middle - 0.6f, 45, middle + 0.6f, payline, 0.5f);
        quad(pose, buffers, -45.6f, middle - 3, -42, middle + 3, GOLD, 0.6f);
        quad(pose, buffers, 42, middle - 3, 45.6f, middle + 3, GOLD, 0.6f);
    }

    private static void reelSymbol(PoseStack pose, WorldCanvas buffers, int symbol, float x, float y) {
        SlotSymbols.draw(symbol, x, y, 22, (x1, y1, x2, y2, colour) -> {
            float top = Math.max(REEL_TOP + 2, y1), bottom = Math.min(REEL_BOTTOM - 2, y2);
            if (top < bottom) quad(pose, buffers, x1, top, x2, bottom, colour, 0.3f);
        });
    }

    /** The best lines of the paytable, printed on the belly of the cabinet. */
    private static void paytable(PoseStack pose, WorldCanvas buffers, Font font) {
        quad(pose, buffers, -44, -20, 44, 28, 0xff0b111a, 0);
        label(pose, buffers, font, Component.translatable("gui.gamblingitems.slot_paytable").getString(),
                0, -13, GOLD, 0.55f, 80, 0.2f);
        for (int line = 0; line < 3; line++) {
            int symbol = SlotRules.SYMBOLS - 1 - line;
            float y = -1 + line * 11;
            for (int reel = 0; reel < 3; reel++) {
                SlotSymbols.draw(symbol, -30 + reel * 11, y, 8,
                        (x1, y1, x2, y2, colour) -> quad(pose, buffers, x1, y1, x2, y2, colour, 0.2f));
            }
            label(pose, buffers, font, GameScreens.multiplier(SlotRules.tripleOf(symbol)), 22, y, TEXT, 0.5f, 36, 0.2f);
        }
    }

    /**
     * The lever on the right flank: pulled forward as a round starts, back up once the reels stop.
     * It turns about a pivot level with the deck, in model space.
     */
    private static void lever(PoseStack pose, WorldCanvas buffers, double progress, boolean playing) {
        double pulled = !playing ? 0
                : progress < 0.3 ? progress / 0.3 : Math.max(0, 1 - (progress - 0.3) / 0.7);
        cube(pose, buffers, 15.5f, 16.6f, 4.6f, 16.6f, 19.4f, 7.4f, BRASS_DARK, 1);
        cube(pose, buffers, 16.6f, 17.4f, 5.4f, 17.2f, 18.6f, 6.6f, CHROME, 1);
        pose.pushPose();
        pose.translate(16 - 16.9f, 18, 6);
        // Rest leans back a little; a pull brings the ball forward and down.
        pose.mulPose(Axis.XP.rotationDegrees((float) (15 - 95 * pulled)));
        // cube() takes u, the mirror of x: around the pivot, u = 16 is x = 0.
        cube(pose, buffers, 15.75f, 0, -0.25f, 16.25f, 7, 0.25f, CHROME, 1);
        cube(pose, buffers, 15.1f, 7, -0.9f, 16.9f, 8.8f, 0.9f, 0xffd2434f, 1);
        cube(pose, buffers, 15.4f, 8.8f, -0.6f, 16.6f, 9.1f, 0.6f, 0xffef8a92, 1);
        pose.popPose();
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

    /** A flat rectangle of a canvas; lift brings it towards the player, in canvas pixels. */
    static void quad(PoseStack pose, WorldCanvas buffers, float x1, float y1,
                     float x2, float y2, int colour, float lift) {
        buffers.quads(pose, (matrix, vertices) -> {
            WorldCanvas.vertex(vertices, matrix, x1, y1, lift, colour);
            WorldCanvas.vertex(vertices, matrix, x1, y2, lift, colour);
            WorldCanvas.vertex(vertices, matrix, x2, y2, lift, colour);
            WorldCanvas.vertex(vertices, matrix, x2, y1, lift, colour);
        });
    }

    /**
     * A box of model space, from (u1, y1, z1) to (u2, y2, z2), shaded like a block: the top full,
     * the front and back a little darker, the flanks darker still. light dims it all.
     */
    static void cube(PoseStack pose, WorldCanvas buffers, float u1, float y1, float z1,
                     float u2, float y2, float z2, int colour, float light) {
        float x1 = 16 - u2, x2 = 16 - u1;
        int top = shade(colour, light), front = shade(colour, 0.8f * light), side = shade(colour, 0.62f * light),
                bottom = shade(colour, 0.5f * light);
        buffers.quads(pose, (matrix, vertices) -> {
            face(vertices, matrix, top, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
            face(vertices, matrix, bottom, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
            face(vertices, matrix, front, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
            face(vertices, matrix, front, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
            face(vertices, matrix, side, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
            face(vertices, matrix, side, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
        });
    }

    static void face(com.mojang.blaze3d.vertex.VertexConsumer vertices, org.joml.Matrix4f matrix, int colour,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        // Both windings, so the face shows whichever way culling faces it.
        WorldCanvas.vertex(vertices, matrix, ax, ay, az, colour);
        WorldCanvas.vertex(vertices, matrix, bx, by, bz, colour);
        WorldCanvas.vertex(vertices, matrix, cx, cy, cz, colour);
        WorldCanvas.vertex(vertices, matrix, dx, dy, dz, colour);
        WorldCanvas.vertex(vertices, matrix, dx, dy, dz, colour);
        WorldCanvas.vertex(vertices, matrix, cx, cy, cz, colour);
        WorldCanvas.vertex(vertices, matrix, bx, by, bz, colour);
        WorldCanvas.vertex(vertices, matrix, ax, ay, az, colour);
    }

    /** A pane of glass in model space, given by two opposite corners of an upright rectangle. */
    static void pane(PoseStack pose, WorldCanvas buffers, float u1, float y1, float z1, float u2, float y2, float z2, int colour) {
        float x1 = 16 - u1, x2 = 16 - u2;
        buffers.quads(pose, (matrix, vertices) -> face(vertices, matrix, colour, x1, y1, z1, x2, y1, z2, x2, y2, z2, x1, y2, z1));
    }

    /** A colour scaled by a factor, darker below 1 and lighter above, its alpha kept. */
    static int shade(int colour, float factor) {
        float f = Math.max(0, factor);
        return colour & 0xff000000 | Math.min(255, (int) (((colour >> 16) & 255) * f)) << 16
                | Math.min(255, (int) (((colour >> 8) & 255) * f)) << 8 | Math.min(255, (int) ((colour & 255) * f));
    }

    /** Writes on a canvas, centred on (x, y), shrunk so that it never runs wider than width. */
    static void label(PoseStack pose, WorldCanvas buffers, Font font, String text,
                      float x, float y, int colour, float scale, float width, float lift) {
        if (text.isEmpty()) return;
        pose.pushPose();
        pose.translate(x, y, lift + 0.4f);
        float fitted = Math.min(scale, width / Math.max(1, font.width(text)));
        pose.scale(fitted, fitted, fitted);
        buffers.text(pose, font, text, -font.width(text) / 2f, -4, colour, 0xF000F0);
        pose.popPose();
    }
}
