package dev.gamblingitems.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.machines.CabinetControls;
import dev.gamblingitems.core.machines.CabinetTheme;
import dev.gamblingitems.fabric.arcade.NeonBlock;
import dev.gamblingitems.fabric.block.GameStationEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import static dev.gamblingitems.fabric.client.SlotCabinet.*;

/**
 * What a cabinet adds around itself, in the model space of {@link SlotCabinet}: the trim of its dyed theme,
 * the VIP badge over a reserved cabinet, and for a big win a rotating beacon on its roof and confetti
 * raining in front of it. Around a cabinet that plays the arcade's music, the neon tubes glow on the beat.
 */
final class CabinetAmbience {
    private static final int[] CONFETTI = {0xffe8463c, 0xfff6c33a, 0xff3cb4e6, 0xff5ad25a, 0xffd25ac8, 0xfff0ecdc};
    private static final int CONFETTI_COUNT = 48, NEON_RADIUS = 8, NEON_RESCAN = 40;

    private CabinetAmbience() {}

    /** The roof of each cabinet, in model pixels: the beacon and the badge stand on it. */
    private static float roof(GameMode mode) {
        return switch (mode) {
            case CAPSULE_GACHA -> 35.4f;
            case COIN_PUSHER, VIDEO_POKER -> 37.6f;
            case PRIZE_CLAW, CRAPS, BACCARAT, KENO, SCRATCH_CARD -> 38.2f;
            case HIGH_STAKES -> 38.8f;
            default -> 40;
        };
    }

    /** Drawn in model space, after the cabinet itself. */
    static void render(GameStationEntity station, PoseStack pose, WorldCanvas buffers, Font font, double now) {
        GameMode mode = station.mode();
        float width = 16 * CabinetControls.width(mode);
        trim(pose, buffers, CabinetTheme.of(station.theme), CabinetTheme.colour(station.theme), width, now);
        if (station.vip) badge(pose, buffers, font, width, roof(mode), now);
        if (station.celebrating(now)) {
            double age = now - station.startedAt - station.animationTicks;
            beacon(pose, buffers, width, roof(mode), now);
            if (!ClientOptions.reducedMotion()) confetti(pose, buffers, station.startedAt, width, age);
        }
    }

    // --- Themes ---------------------------------------------------------------------------------

    /** The trim along the plinth: Vegas chase bulbs, a neon strip with its glow on the floor, or retro chrome. */
    private static void trim(PoseStack pose, WorldCanvas buffers, CabinetTheme theme, int dye, float width, double now) {
        switch (theme) {
            case VEGAS -> {
                int bulbs = (int) (width / 1.6f);
                for (int index = 0; index < bulbs; index++) {
                    float u = 0.8f + index * (width - 1.6f) / (bulbs - 1);
                    boolean on = ((long) (now / 3) + index) % 3 != 0;
                    cube(pose, buffers, u - .3f, .45f, .25f, u + .3f, 1.05f, .5f, on ? 0xfffff0b8 : 0xff8a5a20, 1);
                }
            }
            case NEON -> {
                float glow = 0.75f + 0.25f * (float) Math.sin(now * 0.15);
                cube(pose, buffers, .5f, .5f, .3f, width - .5f, .9f, .5f, shade(dye, glow * 1.2f), 1);
                // The glow it throws on the floor around the cabinet.
                int alpha = (int) (0x50 * glow) << 24;
                cube(pose, buffers, -1.5f, .02f, -2.5f, width + 1.5f, .04f, 17, alpha | (dye & 0xffffff), 1);
            }
            case RETRO -> {
                cube(pose, buffers, .5f, .55f, .35f, width - .5f, .95f, .5f, CHROME, 1);
                for (float u = 1.5f; u < width - 1; u += 3) cube(pose, buffers, u, .6f, .3f, u + 1.2f, .9f, .35f, 0xff3aa8a0, 1);
            }
            default -> {}
        }
    }

    /** The colours a theme gives the lamps and signs of a cabinet; null for the cabinet's own. */
    static int[] palette(int stored, double now, int index) {
        CabinetTheme theme = CabinetTheme.of(stored);
        int dye = CabinetTheme.colour(stored);
        return switch (theme) {
            // Lamp lit, lamp off, sign background, sign band, sign middle, sign text.
            case VEGAS -> new int[] {((long) (now / 3) + index) % 3 == 0 ? 0xfffff4d0 : index % 2 == 0 ? GOLD : 0xffd2434f,
                    0xff5a1a10, 0xff2a0606, 0xff6a1010, 0xff8a1a1a, 0xffffe27a};
            case RETRO -> new int[] {index % 2 == 0 ? 0xfff0e6c8 : 0xff3aa8a0, 0xff1c3a38, 0xff0c2a2a, 0xff12403e, 0xff1c5a56, 0xfff0e6c8};
            case NEON -> new int[] {shade(dye, 0.75f + 0.35f * (float) Math.sin(now * 0.2 + index)), shade(dye, .25f), 0xff08080c,
                    shade(dye, .18f), shade(dye, .3f), shade(dye, 1.25f)};
            default -> null;
        };
    }

    // --- VIP ------------------------------------------------------------------------------------

    /** A gold badge over the roof, gently bobbing, with VIP on its face. */
    private static void badge(PoseStack pose, WorldCanvas buffers, Font font, float width, float roof, double now) {
        float centre = width / 2, y = roof + 2.2f + 0.3f * (float) Math.sin(now / 10);
        cube(pose, buffers, centre - .3f, roof, 7.2f, centre + .3f, y, 7.6f, BRASS, 1);
        cube(pose, buffers, centre - 4.6f, y, 7, centre + 4.6f, y + 3.8f, 7.8f, GOLD, 1);
        cube(pose, buffers, centre - 4.1f, y + .5f, 6.95f, centre + 4.1f, y + 3.3f, 7, 0xff141418, 1);
        surface(pose, 6.9f);
        label(pose, buffers, font, Component.translatable("gui.gamblingitems.vip").getString(), (centre - 8) * 8,
                (8 - (y + 1.9f)) * 8, GOLD, 2, 60, 0.3f);
        pose.popPose();
    }

    // --- Big wins -------------------------------------------------------------------------------

    /** A beacon on the roof, its two beams sweeping round while the win is celebrated. */
    private static void beacon(PoseStack pose, WorldCanvas buffers, float width, float roof, double now) {
        float centre = width / 2;
        boolean red = (long) (now / 4) % 2 == 0;
        cube(pose, buffers, centre - 1.6f, roof, 5.4f, centre + 1.6f, roof + .7f, 8.6f, CHROME, 1);
        cube(pose, buffers, centre - 1.2f, roof + .7f, 5.8f, centre + 1.2f, roof + 3, 8.2f, red ? 0xffff4a4a : 0xffffb030, 1.2f);
        pose.pushPose();
        // Model space mirrors u: the centre of the cabinet is at x = 16 - centre.
        pose.translate(16 - centre, roof + 1.9f, 7);
        pose.mulPose(Axis.YP.rotationDegrees((float) (now * 14 % 360)));
        pose.translate(-(16 - centre), -(roof + 1.9f), -7);
        int beam = 0x60000000 | (red ? 0xff4a4a : 0xffb030);
        cube(pose, buffers, centre - .5f, roof + 1.4f, -10, centre + .5f, roof + 2.4f, 5.8f, beam, 1);
        cube(pose, buffers, centre - .5f, roof + 1.4f, 8.2f, centre + .5f, roof + 2.4f, 24, beam, 1);
        pose.popPose();
    }

    /** Paper confetti fluttering down in front of the cabinet, each piece on its own path, the same for every viewer. */
    private static void confetti(PoseStack pose, WorldCanvas buffers, long seed, float width, double age) {
        for (int piece = 0; piece < CONFETTI_COUNT; piece++) {
            double a = hash(piece * 3 + seed * 31), b = hash(piece * 3 + 1 + seed * 31), c = hash(piece * 3 + 2 + seed * 31);
            double fall = (age * (0.35 + 0.25 * a) + c * 46) % 46;
            float y = (float) (46 - fall);
            float u = (float) (-3 + b * (width + 6) + Math.sin(age * 0.2 + piece) * 0.8);
            float z = (float) (-5 + a * 6);
            // Flat paper turning over as it falls.
            float turn = (float) Math.abs(Math.cos(age * 0.3 + piece)) * 1.1f + .1f;
            cube(pose, buffers, u, y, z, u + 1.3f, y + turn, z + .1f, CONFETTI[piece % CONFETTI.length], 1);
        }
    }

    private static double hash(long value) {
        long mixed = value * 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 31;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 29;
        return (mixed >>> 11) * 0x1.0p-53;
    }

    // --- Neons on the beat ----------------------------------------------------------------------

    /**
     * Around a cabinet heard playing the arcade's music, a halo on each neon tube swells on every beat.
     * Drawn in block space, before the cabinet's model space is pushed.
     */
    static void neons(GameStationEntity station, PoseStack pose, WorldCanvas buffers, double now) {
        float beat = AmbientMusic.beat(now);
        if (beat <= 0.02f || station.getLevel() == null) return;
        if (station.neonsScannedAt == 0 || now - station.neonsScannedAt > NEON_RESCAN) {
            station.neonsScannedAt = (long) now;
            List<BlockPos> found = new ArrayList<>();
            BlockPos origin = station.getBlockPos();
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-NEON_RADIUS, -2, -NEON_RADIUS), origin.offset(NEON_RADIUS, 5, NEON_RADIUS))) {
                if (station.getLevel().getBlockState(pos).getBlock() instanceof NeonBlock) found.add(pos.immutable());
            }
            station.neons = found;
        }
        BlockPos origin = station.getBlockPos();
        for (BlockPos pos : station.neons) {
            var state = station.getLevel().getBlockState(pos);
            if (!(state.getBlock() instanceof NeonBlock)) continue;
            int colour = CabinetTheme.colour(1 + state.getValue(NeonBlock.COLOR).getId());
            int halo = (int) (0x70 * beat) << 24 | (colour & 0xffffff);
            float grow = 1.2f + 1.3f * beat;
            pose.pushPose();
            pose.translate(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ());
            pose.scale(1f / 16, 1f / 16, 1f / 16);
            float low = 6.75f - grow, high = 9.25f + grow;
            switch (state.getValue(NeonBlock.FACING).getAxis()) {
                case Y -> cube(pose, buffers, low, 1.5f, low, high, 14.5f, high, halo, 1);
                case Z -> cube(pose, buffers, low, low, 1.5f, high, high, 14.5f, halo, 1);
                case X -> cube(pose, buffers, 1.5f, low, low, 14.5f, high, high, halo, 1);
            }
            pose.popPose();
        }
    }
}
