package dev.gamblingitems.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gamblingitems.core.bingo.BingoRules;
import dev.gamblingitems.core.blackjack.BlackjackRules;
import dev.gamblingitems.core.roulette.RouletteWheel;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.block.GameTableBlock;
import dev.gamblingitems.fabric.block.StationPanel;
import dev.gamblingitems.fabric.block.TableLayout;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

/**
 * The felt of a table, drawn across the six blocks it occupies: the mat of a blackjack, the wheel
 * and layout of a roulette, or the board of a bingo. Raised pieces — the rail, the chip racks, the
 * shoe, the rim and turret of the wheel — have closed sides and shaded edges.
 *
 * <p>Nothing here decides anything: it draws what the block was told. Coordinates are the pixels
 * of the station panel, 128 to a block, so the surface runs from -192 to 192 across and -128 to
 * 128 deep, the player standing at the bottom edge.
 */
public final class StationTables {
    private static final int WOOD = 0xff4a3524, WOOD_LIGHT = 0xff6b4b32;
    private static final int CARD = 0xfff3f6fb, CARD_BACK = 0xff7a2130, CARD_EDGE = 0xff20262f;
    private static final int RED = 0xffc0392b, BLACK = 0xff1b1f27, GREEN = 0xff1e8f52;
    private static final int TEXT = 0xffe8eff6, GOLD = 0xffffce69, SILVER = 0xffb9c4d0;
    private static final int BLUE = 0xff2b6cb0;
    /** The felt sits on the top face of the table. */
    private static final float HEIGHT = 0.83f;
    /** The surface: three blocks across, two deep, the anchor block at the front middle. */
    private static final float HALF_WIDTH = TableLayout.HALF_WIDTH, HALF_DEPTH = TableLayout.HALF_DEPTH;
    /** How many ticks a card takes to land on the felt. */
    private static final float DEAL_TICKS = 6;

    private StationTables() {}

    /** True when this block is a table: the felt is drawn on its top, not on a wall. */
    public static boolean hasTable(GameStationEntity station) {
        return station.getBlockState().getBlock() instanceof GameTableBlock;
    }

    public static void render(GameStationEntity station, float partialTick, PoseStack pose,
                              WorldCanvas buffers, Font font) {
        pose.pushPose();
        pose.translate(0.5, HEIGHT, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-station.getBlockState()
                .getValue(GameTableBlock.FACING).toYRot()));
        // The anchor is the front middle block, so the surface reaches half a block further back.
        pose.translate(0, 0, -0.5);
        // Lay the panel flat, keeping the handedness the station screen already uses.
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.scale(1f / 128, -1f / 128, 1f / 128);
        cloth(pose, buffers, station.mode() != dev.gamblingitems.core.GameMode.BINGO);
        switch (station.mode()) {
            case BLACKJACK -> blackjack(station, partialTick, pose, buffers, font);
            case ROULETTE -> roulette(station, partialTick, pose, buffers, font);
            case BINGO -> bingo(station, pose, buffers, font);
            default -> { }
        }
        stakes(station, pose, buffers);
        controls(station, pose, buffers, font);
        pose.popPose();
    }

    /**
     * The shading of the felt. The baize itself is the table's model, woven in its texture; this only
     * darkens it towards the edges, as under the lamp over a real table, inside a gold pinstripe.
     */
    private static void cloth(PoseStack pose, WorldCanvas buffers, boolean detailed) {
        for (int ring = 0; ring < 5; ring++) {
            float inset = ring * 6;
            quad(pose, buffers, -HALF_WIDTH + inset, -HALF_DEPTH + inset, HALF_WIDTH - inset, HALF_DEPTH - inset,
                    0x14000000, 1 + ring * 0.01f);
        }
        if (!detailed) return;
        float pin = 4.5f;
        quad(pose, buffers, -HALF_WIDTH + pin, -HALF_DEPTH + pin, HALF_WIDTH - pin, -HALF_DEPTH + pin + 0.8f, GOLD, 2.1f);
        quad(pose, buffers, -HALF_WIDTH + pin, -HALF_DEPTH + pin, -HALF_WIDTH + pin + 0.8f, HALF_DEPTH - 18, GOLD, 2.1f);
        quad(pose, buffers, HALF_WIDTH - pin - 0.8f, -HALF_DEPTH + pin, HALF_WIDTH - pin, HALF_DEPTH - 18, GOLD, 2.1f);
    }

    // ---------------------------------------------------------------- blackjack

    private static void blackjack(GameStationEntity station, float partialTick, PoseStack pose,
                                  WorldCanvas buffers, Font font) {
        // The arc of a real mat, between the dealer and the seats.
        // Two gold arcs printed between the dealer and the seats, the rules written between them.
        for (int step = 0; step <= 120; step++) {
            double angle = Math.PI * step / 120;
            for (float radius : new float[] {150, 138}) {
                float x = (float) (Math.cos(angle) * radius);
                float y = (float) (-30 - Math.sin(angle) * radius * 0.32f);
                quad(pose, buffers, x - 1.3f, y - 0.7f, x + 1.3f, y + 0.7f, GOLD, 3);
            }
        }
        label(pose, buffers, font, WorldCanvas.translate("gui.gamblingitems.blackjack_pays").toUpperCase(java.util.Locale.ROOT),
                0, -26, GOLD, 0.6f, 3);
        label(pose, buffers, font, WorldCanvas.translate("gui.gamblingitems.blackjack_stands"),
                0, -14, 0xffe9e4d6, 0.5f, 3);
        shoe(pose, buffers);
        discardTray(pose, buffers);
        chipRack(pose, buffers, -176, 0);
        dealerTray(pose, buffers);
        // The betting boxes printed along the front of the mat: a gold ring round each.
        for (int seat = -2; seat <= 2; seat++) {
            annulus(pose, buffers, seat * 62, TableLayout.STAKE_Y, 19.6f, 21, GOLD, 3);
            annulus(pose, buffers, seat * 62, TableLayout.STAKE_Y, 15.2f, 16, 0x50000000, 3);
        }
        // A card that has just appeared slides in, the same way the screen deals it.
        if (!station.cards.equals(station.seenCards)) {
            station.seenCards = station.cards;
            station.cardsChangedAt = station.getLevel().getGameTime();
        }
        float since = (float) (station.getLevel().getGameTime() + partialTick - station.cardsChangedAt);
        float landing = Math.max(0, 1 - Math.min(1, since / DEAL_TICKS));
        landing = landing * landing;
        String[] sides = station.cards.split("\\|", -1);
        String hand = sides.length > 0 ? sides[0] : "";
        String dealer = sides.length > 1 ? sides[1] : "";
        // Both hands are counted on the felt, so nobody has to add up the cards themselves.
        String dealerLine = WorldCanvas.translate("gui.gamblingitems.dealer")
                + score(dealer, dealer.contains("?"));
        // Under the dealer's cards, the chip tray taking the edge behind them.
        label(pose, buffers, font, dealerLine, 0, -52, GOLD, 0.7f, 3);
        cards(pose, buffers, font, dealer, -104, landing);
        cards(pose, buffers, font, hand, 8, landing);
        String name = station.previewPlayer.isEmpty() ? station.playerName : station.previewPlayer;
        String seat = hand.isEmpty() && name.isEmpty()
                ? WorldCanvas.translate("gui.gamblingitems.blackjack_ready")
                : (name.isEmpty() ? WorldCanvas.translate("gui.gamblingitems.your_hand") : name)
                        + score(hand, false);
        label(pose, buffers, font, seat, 0, 50, TEXT, 0.8f, 3);
    }

    /** The shoe the cards come out of, in the far right corner. */
    private static void shoe(PoseStack pose, WorldCanvas buffers) {
        solidBox(pose, buffers, 122, -117, 177, -78, 2, 5, WOOD);
        solidBox(pose, buffers, 124, -115, 129, -80, 5, 15, WOOD_LIGHT);
        solidBox(pose, buffers, 170, -115, 175, -80, 5, 15, WOOD_LIGHT);
        solidBox(pose, buffers, 129, -115, 170, -110, 5, 15, WOOD);
        solidBox(pose, buffers, 131, -108, 168, -84, 5, 12, CARD);
        for (int layer = 6; layer < 12; layer += 2) {
            solidBox(pose, buffers, 131, -84.5f, 168, -84, layer, layer + .35f, SILVER);
        }
        quad(pose, buffers, 134, -105, 165, -87, CARD_BACK, 12.1f);
        solidBox(pose, buffers, 139, -90, 160, -72, 5, 5.7f, CARD);
    }

    /** The tray the dealt cards are dropped into, on the far left. */
    private static void discardTray(PoseStack pose, WorldCanvas buffers) {
        solidBox(pose, buffers, -176, -118, -120, -78, 2, 5, WOOD);
        solidBox(pose, buffers, -176, -118, -172, -78, 5, 10, WOOD_LIGHT);
        solidBox(pose, buffers, -124, -118, -120, -78, 5, 10, WOOD_LIGHT);
        solidBox(pose, buffers, -172, -118, -124, -114, 5, 10, WOOD_LIGHT);
        solidBox(pose, buffers, -166, -109, -130, -84, 5, 7, CARD);
        quad(pose, buffers, -164, -107, -132, -86, CARD_BACK, 7.1f);
    }

    /** The dealer's chip tray, set into the far edge of the table: rows of chips lying in their grooves. */
    private static void dealerTray(PoseStack pose, WorldCanvas buffers) {
        solidBox(pose, buffers, -70, -126, 70, -106, 1, 5, MAHOGANY);
        quad(pose, buffers, -68, -124, 68, -108, 0xff1a0f0b, 5.05f);
        int[] colours = {0xffe8e4dc, RED, BLUE, GREEN, 0xff1c1c22, GOLD, RED, 0xffe8e4dc};
        for (int column = 0; column < colours.length; column++) {
            float x = -60 + column * 17;
            // Chips lying on their side down the groove: a row of short cylinders seen edge on.
            solidBox(pose, buffers, x - 6.5f, -122, x + 6.5f, -110, 5, 9, colours[column]);
            for (float y = -121; y < -110; y += 2.2f) {
                quad(pose, buffers, x - 6.5f, y, x + 6.5f, y + 0.5f, shade(colours[column], .6f), 9.05f);
            }
            quad(pose, buffers, x - 1.5f, -121, x + 1.5f, -111, 0xfff3eee2, 9.06f);
        }
    }

    /** A rack of chips: four colours, each a short stack. */
    private static void chipRack(PoseStack pose, WorldCanvas buffers, float x, float y) {
        solidBox(pose, buffers, x - 9, y - 10, x + 9, y + 74, 2, 4, WOOD);
        int[] colours = {RED, BLUE, GREEN, GOLD};
        for (int index = 0; index < colours.length; index++) {
            float top = y - 4 + index * 20;
            for (int layer = 0; layer < 4; layer++) {
                cylinder(pose, buffers, x, top + 8, 7, 4 + layer * 2, 6 + layer * 2, colours[index]);
                circle(pose, buffers, x, top + 8, 4.5f, CARD, 6.1f + layer * 2);
            }
        }
    }

    /** One row of cards, centred on the felt. A hidden hole card is drawn face down. */
    private static void cards(PoseStack pose, WorldCanvas buffers, Font font, String list,
                              int y, float landing) {
        if (list.isEmpty()) return;
        String[] parts = list.split(",");
        int width = 26, gap = 5;
        int total = parts.length * width + (parts.length - 1) * gap;
        int x = -total / 2;
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            // Only the newest card of the row is still travelling.
            int slide = index == parts.length - 1 ? (int) (landing * 70) : 0;
            int left = x + slide;
            // The shadow a card casts on the cloth.
            quad(pose, buffers, left + 2, y + 3, left + width + 3, y + 39, 0x55000000, 4.5f);
            if (part.equals("?")) {
                quad(pose, buffers, left - 2, y - 2, left + width + 2, y + 38, CARD_EDGE, 5);
                quad(pose, buffers, left, y, left + width, y + 36, CARD_BACK, 6);
                quad(pose, buffers, left + 4, y + 4, left + width - 4, y + 32, 0xff5d1825, 7);
            } else {
                int card;
                try {
                    card = Integer.parseInt(part);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                if (card < 0 || card >= BlackjackRules.CARDS) continue;
                quad(pose, buffers, left - 2, y - 2, left + width + 2, y + 38, CARD_EDGE, 5);
                quad(pose, buffers, left, y, left + width, y + 36, CARD, 6);
                int suit = BlackjackRules.suitOf(card);
                int colour = suit == 0 || suit == 1 ? 0xffb3222e : 0xff161b22;
                label(pose, buffers, font, rank(BlackjackRules.rankOf(card)), left + width / 2f, y + 11,
                        colour, 0.55f, 6);
                label(pose, buffers, font, suit(suit), left + width / 2f, y + 26, colour, 0.55f, 6);
            }
            x += width + gap;
        }
    }

    /**
     * What a row of cards counts, written as " : 18". A hidden hole card is not counted, and is
     * shown as a plus: the felt never tells anyone what the dealer is holding face down.
     */
    private static String score(String list, boolean hidden) {
        java.util.List<Integer> cards = new java.util.ArrayList<>();
        for (String part : list.split(",")) {
            if (part.isEmpty() || part.equals("?")) continue;
            try {
                int card = Integer.parseInt(part);
                if (card >= 0 && card < BlackjackRules.CARDS) cards.add(card);
            } catch (NumberFormatException ignored) {
                // A felt never breaks on a card it cannot read.
            }
        }
        if (cards.isEmpty()) return "";
        return " : " + BlackjackRules.total(cards) + (hidden ? "+" : "");
    }

    private static String rank(int rank) {
        return switch (rank) {
            case 0 -> "A";
            case 9 -> "10";
            case 10 -> "J";
            case 11 -> "Q";
            case 12 -> "K";
            default -> String.valueOf(rank + 1);
        };
    }

    private static String suit(int suit) {
        return switch (suit) {
            case 0 -> "♥";
            case 1 -> "♦";
            case 2 -> "♣";
            default -> "♠";
        };
    }

    // ---------------------------------------------------------------- roulette

    private static final int MAHOGANY = 0xff3b1d12, MAHOGANY_LIGHT = 0xff6a3520, TRACK = 0xff8a5a34, BRASS = 0xffc9a04a;
    /** The ink the layout is printed in, and where the wheel stands: at the end of the layout, as on a real table. */
    private static final int INK = 0xffe9e4d6;
    private static final float WHEEL_X = -122, WHEEL_Y = -42;
    /** The colours of the players' chips on the layout: every seat at a roulette plays its own colour. */
    private static final int[] SEAT_COLOURS = {0xff2f7fd8, 0xffe8b830, 0xff9a4fd0, 0xffe07a2a, 0xff3cc0a0, 0xffe86aa8};

    private static void roulette(GameStationEntity station, float partialTick, PoseStack pose,
                                 WorldCanvas buffers, Font font) {
        double now = station.getLevel().getGameTime() + partialTick;
        double spin = station.phase == 2 && station.animationTicks > 0
                ? Math.max(0, Math.min(1, 1 - (station.phaseEnd - now) / station.animationTicks))
                : 1;
        double eased = 1 - Math.pow(1 - spin, 3);
        // A wheel only turns when a round is being played on it; at rest it stands still.
        double wheelAngle = station.phase <= 0 ? 0 : eased * 3 * Math.PI * 2;
        int result = station.targetSlot;
        pose.pushPose();
        pose.translate(WHEEL_X, WHEEL_Y, 0);
        bowl(pose, buffers);
        // The wheel head turns: its pockets with their frets, the numbers round them and the cone in the middle.
        double step = 2 * Math.PI / RouletteWheel.POCKETS;
        cylinder(pose, buffers, 0, 0, 51, 4, 9.5f, MAHOGANY);
        for (int pocket = 0; pocket < RouletteWheel.POCKETS; pocket++) {
            int number = RouletteWheel.numberAtPocket(pocket);
            double angle = wheelAngle + pocket * step;
            // The number ring, sloping up to the track, and the pocket the ball falls into below it.
            blade(pose, buffers, angle, colourOf(number), 51, 40, 9.6f);
            blade(pose, buffers, angle, shade(colourOf(number), .55f), 40, 30, 9.55f);
            label(pose, buffers, font, String.valueOf(number),
                    (float) (Math.cos(angle) * 45.5), (float) (Math.sin(angle) * 45.5), TEXT, 0.42f, 9.7f);
            // The brass fret between two pockets, standing proud so the ball can rattle over it.
            double edge = angle + step / 2;
            float c = (float) Math.cos(edge), s = (float) Math.sin(edge);
            wall(pose, buffers, c * 30, s * 30, c * 40, s * 40, 9.5f, 12.5f, BRASS);
            quad(pose, buffers, c * 35 - 0.8f, s * 35 - 0.8f, c * 35 + 0.8f, s * 35 + 0.8f, GOLD, 12.6f);
        }
        circle(pose, buffers, 0, 0, 40.4f, BRASS, 9.58f);
        circle(pose, buffers, 0, 0, 39.6f, 0xff1a1210, 9.59f);
        cone(pose, buffers, 0, 0, 30, 9.6f, 18, 15, MAHOGANY_LIGHT);
        turret(pose, buffers, wheelAngle);
        if (result >= 0 && station.phase > 0) ball(pose, buffers, wheelAngle, result, spin, eased);
        pose.popPose();
        layout(pose, buffers, font, result, station.phase == 3);
        layoutChips(station, pose, buffers);
    }

    /**
     * The fixed bowl the wheel turns in: a mahogany cabinet with a gold rim, and the ball track sloping
     * down from the rim towards the wheel head, with the brass diamonds that knock the ball about.
     */
    private static void bowl(PoseStack pose, WorldCanvas buffers) {
        // The outside of the bowl, open at the top, and its rim: a gold band, then the mahogany lip.
        tube(pose, buffers, 0, 0, 68, 1, 17, MAHOGANY);
        annulus(pose, buffers, 0, 0, 66.5f, 68, GOLD, 17.05f);
        annulus(pose, buffers, 0, 0, 65, 66.5f, MAHOGANY_LIGHT, 17.05f);
        cone(pose, buffers, 0, 0, 65, 17, 52, 12.5f, TRACK);
        tube(pose, buffers, 0, 0, 52, 4, 12.5f, 0xff2a1a12);
        circle(pose, buffers, 0, 0, 52.2f, 0xff241612, 4.1f);
        for (int diamond = 0; diamond < 8; diamond++) {
            double angle = diamond * Math.PI / 4 + Math.PI / 8;
            pose.pushPose();
            pose.translate((float) (Math.cos(angle) * 58), (float) (Math.sin(angle) * 58), 0);
            pose.mulPose(Axis.ZP.rotation((float) angle + (float) (Math.PI / 4)));
            solidBox(pose, buffers, -2.2f, -2.2f, 2.2f, 2.2f, 14, 16.5f, BRASS);
            pose.popPose();
        }
    }

    /**
     * The ball: it runs round the track against the wheel, loses speed and drops off the track, rattles over
     * a few frets and settles in the pocket of the number that came out, then turns with the wheel.
     */
    private static void ball(PoseStack pose, WorldCanvas buffers, double wheelAngle, int result, double spin, double eased) {
        double pocketAngle = RouletteWheel.pocketOf(result) * 2 * Math.PI / RouletteWheel.POCKETS;
        double angle = wheelAngle + pocketAngle - (1 - eased) * 6 * Math.PI * 2;
        double radius, height;
        if (spin < 0.62) {
            radius = 61;
            height = 15.5;
        } else if (spin < 0.86) {
            double t = (spin - 0.62) / 0.24;
            radius = 61 - 26 * t * t;
            // Down the slope, then over the frets: one hop for each fret it crosses, smaller and smaller.
            height = 15.5 - 4 * t + (t > 0.55 ? Math.abs(Math.sin((t - 0.55) * Math.PI * 4)) * 3.5 * (1 - t) : 0);
        } else {
            radius = 35;
            height = 11.4;
        }
        float x = (float) (Math.cos(angle) * radius), y = (float) (Math.sin(angle) * radius), h = (float) height;
        // A white sphere: a short cylinder, a rounder top and a glint where the light catches it.
        circle(pose, buffers, x, y, 3.3f, 0x50000000, (float) Math.min(h - 3, 9.62));
        cylinder(pose, buffers, x, y, 3, h - 2.6f, h + 0.4f, 0xfff4f4f0);
        cylinder(pose, buffers, x, y, 2.2f, h + 0.4f, h + 1.4f, 0xffffffff);
        circle(pose, buffers, x - 0.8f, y - 0.8f, 0.8f, 0xffffffff, h + 1.45f);
    }

    /** The upright wall of a cylinder without its top, seen from both sides. */
    private static void tube(PoseStack pose, WorldCanvas buffers, float x, float y, float radius, float bottom, float top, int colour) {
        cone(pose, buffers, x, y, radius, bottom, radius, top, colour);
    }

    /** A flat ring between two radii. */
    private static void annulus(PoseStack pose, WorldCanvas buffers, float x, float y, float inner, float outer, int colour, float lift) {
        cone(pose, buffers, x, y, outer, lift, inner, lift, colour);
    }

    /** A sloping ring, as a cone from radius r1 at height h1 to radius r2 at height h2. */
    private static void cone(PoseStack pose, WorldCanvas buffers, float x, float y, float r1, float h1, float r2, float h2,
                             int colour) {
        int steps = WorldCanvas.segments(pose, Math.max(r1, r2), 48);
        for (int step = 0; step < steps; step++) {
            double a = step * 2 * Math.PI / steps, b = (step + 1) * 2 * Math.PI / steps;
            int lit = shade(colour, .82f + .18f * (float) Math.cos(a - .8));
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a), cb = (float) Math.cos(b), sb = (float) Math.sin(b);
            buffers.quads(pose, (m, v) -> {
                WorldCanvas.vertex(v, m, x + ca * r1, y + sa * r1, h1, lit);
                WorldCanvas.vertex(v, m, x + cb * r1, y + sb * r1, h1, lit);
                WorldCanvas.vertex(v, m, x + cb * r2, y + sb * r2, h2, lit);
                WorldCanvas.vertex(v, m, x + ca * r2, y + sa * r2, h2, lit);
                WorldCanvas.vertex(v, m, x + ca * r2, y + sa * r2, h2, lit);
                WorldCanvas.vertex(v, m, x + cb * r2, y + sb * r2, h2, lit);
                WorldCanvas.vertex(v, m, x + cb * r1, y + sb * r1, h1, lit);
                WorldCanvas.vertex(v, m, x + ca * r1, y + sa * r1, h1, lit);
            });
        }
    }

    /** The turret in the middle of the wheel: a gold dome, a spindle and four arms ending in knobs. */
    private static void turret(PoseStack pose, WorldCanvas buffers, double angle) {
        cylinder(pose, buffers, 0, 0, 18, 15, 16.5f, GOLD);
        cone(pose, buffers, 0, 0, 16, 16.5f, 8, 20, BRASS);
        cylinder(pose, buffers, 0, 0, 3.5f, 20, 30, GOLD);
        cylinder(pose, buffers, 0, 0, 5, 29, 31, BRASS);
        for (int arm = 0; arm < 4; arm++) {
            double armAngle = angle + arm * Math.PI / 2;
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotation((float) armAngle));
            solidBox(pose, buffers, 3, -1.4f, 17, 1.4f, 26, 28, GOLD);
            cylinder(pose, buffers, 18, 0, 2.6f, 25.5f, 29, BRASS);
            pose.popPose();
        }
    }

    /**
     * The layout on the right of the felt, printed in white ink on the cloth as on a real table: the
     * pointed zero against the wheel, the numbers on their red and black, the three "2 to 1" at the end
     * of the rows, the dozens and the even money areas underneath, and the dolly on the number that
     * came out. Every area is drawn from the same box the server reads a click in.
     */
    private static void layout(PoseStack pose, WorldCanvas buffers, Font font,
                               int result, boolean settled) {
        for (int number = 1; number < RouletteWheel.POCKETS; number++) {
            float[] box = TableLayout.numberBox(number);
            cellOf(pose, buffers, font, box, number, result, settled);
        }
        zero(pose, buffers, font, TableLayout.numberBox(0), result, settled);
        String column = WorldCanvas.translate("gui.gamblingitems.layout.column");
        for (int index = 0; index < 3; index++) {
            float[] box = TableLayout.columnBox(index);
            frame(pose, buffers, box);
            pose.pushPose();
            pose.translate(box[0] + box[2] / 2, box[1] + box[3] / 2, 0);
            // Printed across the narrow box, the way the dealer reads it.
            pose.mulPose(Axis.ZP.rotationDegrees(-90));
            label(pose, buffers, font, column, 0, 0, INK, 0.42f, 3.5f);
            pose.popPose();
            box = TableLayout.dozenBox(index);
            frame(pose, buffers, box);
            label(pose, buffers, font, WorldCanvas.translate("gui.gamblingitems.layout.dozen" + (index + 1)),
                    box[0] + box[2] / 2, box[1] + box[3] / 2, INK, 0.55f, 3.5f);
        }
        String[] outside = {"1-18", "EVEN", "RED", "BLACK", "ODD", "19-36"};
        for (int index = 0; index < outside.length; index++) {
            float[] box = TableLayout.outsideBox(index);
            frame(pose, buffers, box);
            float cx = box[0] + box[2] / 2, cy = box[1] + box[3] / 2;
            if (outside[index].equals("RED") || outside[index].equals("BLACK")) {
                // The red and black diamonds of the layout, in place of the words.
                int colour = outside[index].equals("RED") ? RED : BLACK;
                diamond(pose, buffers, cx, cy, box[2] * 0.4f, box[3] * 0.36f, INK, 3.4f);
                diamond(pose, buffers, cx, cy, box[2] * 0.4f - 1.4f, box[3] * 0.36f - 1.4f, colour, 3.5f);
            } else {
                label(pose, buffers, font, outsideLabel(outside[index]), cx, cy, INK, 0.5f, 3.5f);
            }
        }
    }

    /** One area of the layout: a line of white ink round it, the cloth showing through. */
    private static void frame(PoseStack pose, WorldCanvas buffers, float[] box) {
        float x1 = box[0] - 0.5f, y1 = box[1] - 0.5f, x2 = box[0] + box[2] + 0.5f, y2 = box[1] + box[3] + 0.5f;
        float line = 0.9f;
        quad(pose, buffers, x1, y1, x2, y1 + line, INK, 3);
        quad(pose, buffers, x1, y2 - line, x2, y2, INK, 3);
        quad(pose, buffers, x1, y1, x1 + line, y2, INK, 3);
        quad(pose, buffers, x2 - line, y1, x2, y2, INK, 3);
    }

    /** The zero: a box pointed towards the wheel, green, the number upright in it. */
    private static void zero(PoseStack pose, WorldCanvas buffers, Font font, float[] box, int result, boolean settled) {
        float x1 = box[0] - 0.5f, y1 = box[1] - 0.5f, x2 = box[0] + box[2] + 0.5f, y2 = box[1] + box[3] + 0.5f;
        float tip = 10, middle = (y1 + y2) / 2;
        pointed(pose, buffers, x1, y1, x2, y2, tip, middle, INK, 3);
        pointed(pose, buffers, x1 + 1.4f, y1 + 0.9f, x2 - 0.9f, y2 - 0.9f, tip - 0.6f, middle, GREEN, 3.1f);
        float cx = (x1 + tip + x2) / 2;
        label(pose, buffers, font, "0", cx, middle, 0xfff3eee2, 0.9f, 3.4f);
        if (settled && result == 0) dolly(pose, buffers, cx, middle);
    }

    /** A box whose left side comes to a point, as the zero of a layout does. */
    private static void pointed(PoseStack pose, WorldCanvas buffers, float x1, float y1, float x2, float y2,
                                float tip, float middle, int colour, float lift) {
        quad(pose, buffers, x1 + tip, y1, x2, y2, colour, lift);
        buffers.quads(pose, (m, v) -> {
            WorldCanvas.vertex(v, m, x1 + tip, y1, lift, colour);
            WorldCanvas.vertex(v, m, x1, middle, lift, colour);
            WorldCanvas.vertex(v, m, x1 + tip, y2, lift, colour);
            WorldCanvas.vertex(v, m, x1 + tip, y2, lift, colour);
        });
    }

    /** A lozenge lying on the felt, its half width and half height given. */
    private static void diamond(PoseStack pose, WorldCanvas buffers, float x, float y, float halfWidth, float halfHeight,
                                int colour, float lift) {
        buffers.quads(pose, (m, v) -> {
            WorldCanvas.vertex(v, m, x - halfWidth, y, lift, colour);
            WorldCanvas.vertex(v, m, x, y + halfHeight, lift, colour);
            WorldCanvas.vertex(v, m, x + halfWidth, y, lift, colour);
            WorldCanvas.vertex(v, m, x, y - halfHeight, lift, colour);
        });
    }

    /** The even money bets read in the language of the player, not in the code. */
    private static String outsideLabel(String area) {
        return switch (area) {
            case "RED" -> WorldCanvas.translate("gui.gamblingitems.colour.red");
            case "BLACK" -> WorldCanvas.translate("gui.gamblingitems.colour.black");
            case "EVEN" -> WorldCanvas.translate("gui.gamblingitems.bet.even");
            case "ODD" -> WorldCanvas.translate("gui.gamblingitems.bet.odd");
            default -> area;
        };
    }

    /** A number of the layout: its colour painted inside the white line, the number in cream on it. */
    private static void cellOf(PoseStack pose, WorldCanvas buffers, Font font, float[] box,
                               int number, int result, boolean settled) {
        frame(pose, buffers, box);
        float x = box[0], y = box[1], width = box[2], height = box[3];
        quad(pose, buffers, x + 1.4f, y + 1.4f, x + width - 1.4f, y + height - 1.4f, colourOf(number), 3.1f);
        float cx = x + width / 2, cy = y + height / 2;
        label(pose, buffers, font, String.valueOf(number), cx, cy, 0xfff3eee2, 0.62f, 3.4f);
        if (settled && number == result) dolly(pose, buffers, cx, cy);
    }

    /** The dolly a croupier stands on the winning number: a white base, a gold crown. */
    private static void dolly(PoseStack pose, WorldCanvas buffers, float x, float y) {
        cylinder(pose, buffers, x, y, 6, 4, 9, 0xfff3eee2);
        cylinder(pose, buffers, x, y, 4.2f, 9, 11, GOLD);
        cylinder(pose, buffers, x, y, 2.2f, 11, 18, 0xfff3eee2);
        cylinder(pose, buffers, x, y, 3.4f, 18, 21, GOLD);
    }

    /**
     * The chips every player has on the layout, read from the bets the table shows everyone: a stack on
     * each area that was backed, in the colour of the seat, a little apart from the stacks of the others.
     */
    private static void layoutChips(GameStationEntity station, PoseStack pose, WorldCanvas buffers) {
        if (station.publicBets.isEmpty()) return;
        String[] seats = station.publicBets.split("\n");
        for (int seat = 0; seat < seats.length; seat++) {
            String line = seats[seat];
            int colon = line.indexOf(" : ");
            if (colon < 0) continue;
            String areas = line.substring(colon + 3);
            int paid = areas.indexOf(" -> ");
            if (paid >= 0) areas = areas.substring(0, paid);
            int colour = SEAT_COLOURS[seat % SEAT_COLOURS.length];
            float dx = (seat % 3 - 1) * 3.5f, dy = (seat % 2 == 0 ? -1 : 1) * 2.5f * Math.min(1, seat);
            for (String area : areas.split(", ")) {
                int space = area.lastIndexOf(' ');
                if (space <= 0) continue;
                RouletteWheel.Bet bet = betNamed(area.substring(0, space));
                if (bet == null) continue;
                float[] box = TableLayout.boxOf(bet);
                if (box == null) continue;
                double value;
                try {
                    value = Double.parseDouble(area.substring(space + 1));
                } catch (NumberFormatException ignored) {
                    continue;
                }
                int chips = (int) Math.max(1, Math.min(8, Math.ceil(value)));
                chipStack(pose, buffers, box[0] + box[2] / 2 + dx, box[1] + box[3] / 2 + dy,
                        Math.min(6.2f, box[2] * 0.42f), chips, colour);
            }
        }
    }

    /** The bet an area is called by on the public line of a table, or null for a name it does not know. */
    private static RouletteWheel.Bet betNamed(String name) {
        try {
            if (name.startsWith("dozen")) return new RouletteWheel.Bet(RouletteWheel.BetType.DOZEN,
                    Integer.parseInt(name.substring(5)) - 1);
            if (name.startsWith("column")) return new RouletteWheel.Bet(RouletteWheel.BetType.COLUMN,
                    Integer.parseInt(name.substring(6)) - 1);
            if (Character.isDigit(name.charAt(0))) {
                return new RouletteWheel.Bet(RouletteWheel.BetType.STRAIGHT, Integer.parseInt(name));
            }
            for (RouletteWheel.BetType type : RouletteWheel.BetType.values()) {
                if (!type.needsChoice() && type.id().equals(name)) return new RouletteWheel.Bet(type, 0);
            }
        } catch (RuntimeException ignored) {
            // A felt never breaks on a bet it cannot read.
        }
        return null;
    }

    /** A stack of casino chips: each one edged with white inserts, the top one showing its inlay. */
    private static void chipStack(PoseStack pose, WorldCanvas buffers, float x, float y, float radius, int chips,
                                  int colour) {
        float base = 3.6f, thickness = 1.5f;
        // The shadow of the stack on the cloth.
        circle(pose, buffers, x + 0.8f, y + 0.8f, radius + 0.4f, 0x50000000, 3.55f);
        for (int chip = 0; chip < chips; chip++) {
            float bottom = base + chip * thickness, top = bottom + thickness - 0.15f;
            // Stacked by hand: each chip a hair off the one under it.
            float ox = (float) Math.sin(chip * 2.3) * 0.35f, oy = (float) Math.cos(chip * 1.7) * 0.35f;
            cylinder(pose, buffers, x + ox, y + oy, radius, bottom, top, colour);
            for (int insert = 0; insert < 6; insert++) {
                double a = insert * Math.PI / 3 + chip * 0.4, b = a + 0.28;
                wall(pose, buffers, x + ox + (float) Math.cos(a) * (radius + 0.05f), y + oy + (float) Math.sin(a) * (radius + 0.05f),
                        x + ox + (float) Math.cos(b) * (radius + 0.05f), y + oy + (float) Math.sin(b) * (radius + 0.05f),
                        bottom + 0.1f, top - 0.1f, 0xfff3eee2);
            }
            if (chip == chips - 1) {
                for (int insert = 0; insert < 6; insert++) {
                    double a = insert * Math.PI / 3 + chip * 0.4;
                    float cx = x + ox + (float) Math.cos(a + 0.14) * (radius - 1.2f);
                    float cy = y + oy + (float) Math.sin(a + 0.14) * (radius - 1.2f);
                    quad(pose, buffers, cx - 0.7f, cy - 0.7f, cx + 0.7f, cy + 0.7f, 0xfff3eee2, top + 0.03f);
                }
                circle(pose, buffers, x + ox, y + oy, radius * 0.55f, 0xfff3eee2, top + 0.05f);
                circle(pose, buffers, x + ox, y + oy, radius * 0.45f, shade(colour, 1.15f), top + 0.08f);
            }
        }
    }

    private static int colourOf(int number) {
        return switch (RouletteWheel.colourOf(number)) {
            case RED -> RED;
            case BLACK -> BLACK;
            case GREEN -> GREEN;
        };
    }

    // ---------------------------------------------------------------- bingo

    private static void bingo(GameStationEntity station, PoseStack pose,
                              WorldCanvas buffers, Font font) {
        boolean[] called = new boolean[BingoRules.NUMBERS + 1];
        if (!station.drawn.isEmpty()) {
            for (String part : station.drawn.split(",")) {
                try {
                    int number = Integer.parseInt(part);
                    if (number >= 1 && number <= BingoRules.NUMBERS) called[number] = true;
                } catch (NumberFormatException ignored) {
                    // A board never breaks on a number it cannot read.
                }
            }
        }
        // The board: the whole drum, five rows of fifteen, lit as the numbers come out.
        String[] heads = {"B", "I", "N", "G", "O"};
        // The board takes the back of the table; the money side stays in front of it.
        float cell = 23, left = -166, top = -116;
        quad(pose, buffers, left - 10, top - 8, left + 15 * cell + 6, top + 5 * cell + 8, WOOD, 3);
        for (int row = 0; row < BingoRules.COLUMNS; row++) {
            label(pose, buffers, font, heads[row], left - 17, top + row * cell + (cell - 2) / 2, GOLD, 0.55f, 4);
            for (int index = 0; index < BingoRules.PER_COLUMN; index++) {
                int number = row * BingoRules.PER_COLUMN + index + 1;
                float x = left + index * cell, y = top + row * cell;
                quad(pose, buffers, x, y, x + cell - 2, y + cell - 2,
                        called[number] ? GREEN : 0xff16202c, 4);
                label(pose, buffers, font, String.valueOf(number), x + (cell - 2) / 2, y + (cell - 2) / 2,
                        called[number] ? TEXT : SILVER, 0.42f, 5);
            }
        }
        // The ball that just came out, in its cup at the front of the table.
        int last = station.targetSlot;
        float cupX = -140, cupY = 34;
        circle(pose, buffers, cupX, cupY, 30, WOOD, 3);
        circle(pose, buffers, cupX, cupY, 24, 0xff16202c, 4);
        circle(pose, buffers, cupX, cupY, 20, last > 0 ? GREEN : 0xff1d2a3c, 5);
        label(pose, buffers, font, last > 0 ? String.valueOf(last) : "-", cupX, cupY, TEXT, 0.9f, 6);
        label(pose, buffers, font, WorldCanvas.translate("gui.gamblingitems.bingo_drum"),
                cupX, cupY + 34, SILVER, 0.5f, 3);
        // One line per card, in front of the board rather than across it.
        String[] lines = station.publicBets.isEmpty()
                ? new String[] {WorldCanvas.translate("gui.gamblingitems.bingo_waiting")}
                : station.publicBets.split("\n");
        for (int index = 0; index < lines.length && index < 3; index++) {
            label(pose, buffers, font, lines[index], 96, 18 + index * 18, TEXT, 0.55f, 3);
        }
    }

    // ---------------------------------------------------------------- playing on the felt

    /**
     * What stands along the edge the player leans on. Each control is a thing of the table rather than a
     * printed button: the drop box a stake is put into, the tray holding the player's own chips, the
     * cup the winnings are paid into, the sign that gives the table's rules, and on a blackjack or a
     * bingo the domed buttons of the game set into the cloth. Each sits on the very box the block reads
     * a click in, with a small engraved plate in front of it saying what it does.
     */
    private static void controls(GameStationEntity station, PoseStack pose, WorldCanvas buffers,
                                 Font font) {
        int hovered = hovered(station);
        for (StationPanel.Control control : TableLayout.controls(station.mode(), station.phase)) {
            boolean lit = hovered == control.action();
            float x1 = control.x(), y1 = control.y(), x2 = control.x() + control.width();
            float y2 = control.y() + control.height();
            // The plate takes the front of the box; the thing itself stands behind it.
            float plate = y2 - 9;
            switch (control.action()) {
                case StationPanel.DEPOSIT -> dropBox(pose, buffers, x1 + 3, y1 + 2, x2 - 3, plate - 2, lit);
                case StationPanel.RETURN -> chipTray(pose, buffers, x1 + 2, y1 + 2, x2 - 2, plate - 2, lit);
                case StationPanel.COLLECT -> payoutCup(pose, buffers, (x1 + x2) / 2, (y1 + plate) / 2,
                        Math.min(x2 - x1, plate - y1) / 2 - 3, lit);
                case StationPanel.OPEN -> tableSign(pose, buffers, font, station.mode(), (x1 + x2) / 2, plate - 3,
                        x2 - x1 - 4, lit);
                default -> pushButton(pose, buffers, (x1 + x2) / 2, (y1 + plate) / 2, Math.min(x2 - x1, plate - y1) / 2 - 2,
                        buttonColour(control.label()), lit);
            }
            nameplate(pose, buffers, font, WorldCanvas.translate("gui.gamblingitems.panel." + control.label()),
                    x1 + 2, plate, x2 - 2, y2 - 1, lit);
        }
    }

    /** A small brass plate lying on the cloth, the name of what stands behind it engraved in it. */
    private static void nameplate(PoseStack pose, WorldCanvas buffers, Font font, String text,
                                  float x1, float y1, float x2, float y2, boolean lit) {
        // As wide as its name, no wider: a plate, not a button.
        float scale = Math.min(0.45f, (x2 - x1 - 4) / Math.max(1, WorldCanvas.width(font, text)));
        float half = Math.min((x2 - x1) / 2, WorldCanvas.width(font, text) * scale / 2 + 3), cx = (x1 + x2) / 2;
        solidBox(pose, buffers, cx - half, y1, cx + half, y2, 2.5f, 3.3f, lit ? 0xffe8c36a : 0xff9c7a32);
        quad(pose, buffers, cx - half + 0.7f, y1 + 0.7f, cx + half - 0.7f, y2 - 0.7f, lit ? 0xffffe08a : BRASS, 3.35f);
        label(pose, buffers, font, text, cx, (y1 + y2) / 2 + 0.4f, lit ? 0xff2a1206 : 0xff3a2008, scale, 3.4f);
    }

    /** The drop box: a mahogany chest with a brass slot plate on its lid, the stake pushed through the slot. */
    private static void dropBox(PoseStack pose, WorldCanvas buffers, float x1, float y1, float x2, float y2, boolean lit) {
        float top = lit ? 12.5f : 11;
        solidBox(pose, buffers, x1, y1, x2, y2, 2.5f, top, lit ? MAHOGANY_LIGHT : MAHOGANY);
        // A gold band round the lid, and the lid itself a shade lighter than the chest.
        solidBox(pose, buffers, x1 - 0.4f, y1 - 0.4f, x2 + 0.4f, y2 + 0.4f, top - 1.6f, top - 0.6f, GOLD);
        quad(pose, buffers, x1 + 1.2f, y1 + 1.2f, x2 - 1.2f, y2 - 1.2f, shade(MAHOGANY_LIGHT, lit ? 1.2f : .85f), top + 0.03f);
        float cx = (x1 + x2) / 2, cy = (y1 + y2) / 2;
        // The brass plate round the slot, the slot, and a chip half way into it.
        solidBox(pose, buffers, x1 + 3, cy - 3.2f, x2 - 3, cy + 3.2f, top, top + 0.6f, lit ? 0xffffe08a : BRASS);
        quad(pose, buffers, x1 + 5, cy - 1.2f, x2 - 5, cy + 1.2f, 0xff120806, top + 0.65f);
        solidBox(pose, buffers, cx - 5, cy - 0.6f, cx + 5, cy + 0.6f, top, top + 4.5f, lit ? 0xffff5a5a : 0xffc8303c);
        quad(pose, buffers, cx - 3, cy - 0.65f, cx + 3, cy + 0.65f, 0xfff3eee2, top + 4.55f);
        // Brass corners on the chest.
        for (float x : new float[] {x1, x2 - 2}) {
            for (float y : new float[] {y1, y2 - 2}) solidBox(pose, buffers, x - 0.2f, y - 0.2f, x + 2.2f, y + 2.2f, 2.5f, top - 1.4f, BRASS);
        }
    }

    /** The player's chip tray: a mahogany tray, its grooves holding rows of chips lying on their edge. */
    private static void chipTray(PoseStack pose, WorldCanvas buffers, float x1, float y1, float x2, float y2, boolean lit) {
        float top = lit ? 7.5f : 6.5f;
        solidBox(pose, buffers, x1, y1, x2, y2, 2.5f, top, lit ? MAHOGANY_LIGHT : MAHOGANY);
        quad(pose, buffers, x1, y1, x2, y1 + 0.8f, GOLD, top + 0.04f);
        int[] colours = {0xffc8303c, 0xff2f7fd8, 0xff1f8a4c, 0xff1c1c22, 0xffe8e4dc};
        int grooves = Math.max(2, Math.min(colours.length, (int) ((x2 - x1 - 4) / 13)));
        float width = (x2 - x1 - 4) / grooves;
        for (int groove = 0; groove < grooves; groove++) {
            float gx1 = x1 + 2 + groove * width + 0.8f, gx2 = gx1 + width - 1.6f;
            quad(pose, buffers, gx1, y1 + 2.5f, gx2, y2 - 2, 0xff1a0f0b, top + 0.05f);
            int colour = colours[groove % colours.length];
            // Chips lying on their side down the groove: a run of short cylinders seen edge on.
            float length = (y2 - 2) - (y1 + 3) - (groove % 2) * 3;
            solidBox(pose, buffers, gx1 + 0.6f, y1 + 3, gx2 - 0.6f, y1 + 3 + length, top - 1, top + 2.2f, colour);
            for (float y = y1 + 3.6f; y < y1 + 3 + length; y += 1.8f) {
                quad(pose, buffers, gx1 + 0.6f, y, gx2 - 0.6f, y + 0.45f, shade(colour, .6f), top + 2.25f);
            }
            float mid = (gx1 + gx2) / 2;
            quad(pose, buffers, mid - 1, y1 + 3.4f, mid + 1, y1 + 2.6f + length, 0xfff3eee2, top + 2.26f);
        }
    }

    /** The payout cup: a brass bowl on a short foot, the winnings stacked in gold chips inside it. */
    private static void payoutCup(PoseStack pose, WorldCanvas buffers, float x, float y, float radius, boolean lit) {
        int brass = lit ? 0xffffe08a : BRASS;
        circle(pose, buffers, x + 1.5f, y + 1.5f, radius, 0x50000000, 2.55f);
        cylinder(pose, buffers, x, y, radius * 0.55f, 2.5f, 4.5f, shade(brass, .85f));
        cone(pose, buffers, x, y, radius * 0.6f, 4.5f, radius, 12, brass);
        annulus(pose, buffers, x, y, radius - 1.1f, radius + 0.6f, lit ? 0xfffff4d0 : GOLD, 12.05f);
        circle(pose, buffers, x, y, radius * 0.6f, 0xff3a2a12, 4.6f);
        // Three short stacks of gold chips in the bottom of the cup.
        for (int stack = 0; stack < 3; stack++) {
            double angle = stack * 2 * Math.PI / 3 + 0.5;
            float sx = x + (float) Math.cos(angle) * radius * 0.28f, sy = y + (float) Math.sin(angle) * radius * 0.28f;
            for (int chip = 0; chip < 2 + stack; chip++) {
                cylinder(pose, buffers, sx, sy, radius * 0.27f, 4.7f + chip * 1.4f, 5.9f + chip * 1.4f, chip % 2 == 0 ? GOLD : 0xffd9a83a);
            }
        }
    }

    /** A domed push button set into a brass bezel, glowing when the hand is on it. */
    private static void pushButton(PoseStack pose, WorldCanvas buffers, float x, float y, float radius, int colour, boolean lit) {
        if (lit) circle(pose, buffers, x, y, radius + 3.5f, (colour & 0x00ffffff) | 0x60000000, 2.55f);
        cylinder(pose, buffers, x, y, radius + 1.6f, 2.5f, 4.2f, BRASS);
        annulus(pose, buffers, x, y, radius, radius + 1.6f, GOLD, 4.25f);
        int face = lit ? shade(colour, 1.45f) : colour;
        // Pressed in a little under the hand.
        float rise = lit ? 2.5f : 4;
        cylinder(pose, buffers, x, y, radius, 3.5f, 4.2f + rise * 0.4f, shade(face, .8f));
        cylinder(pose, buffers, x, y, radius * 0.85f, 4.2f + rise * 0.4f, 4.2f + rise * 0.75f, face);
        cylinder(pose, buffers, x, y, radius * 0.6f, 4.2f + rise * 0.75f, 4.2f + rise, shade(face, 1.12f));
        circle(pose, buffers, x - radius * 0.25f, y - radius * 0.25f, radius * 0.18f, 0xc0ffffff, 4.25f + rise);
    }

    /** The colour of the button a move is played with. */
    private static int buttonColour(String move) {
        return switch (move) {
            case "start", "deal", "bingo_buy" -> 0xff1f9a52;
            case "hit", "bingo_board" -> 0xffe0a020;
            case "stand" -> 0xffc8303c;
            default -> 0xff2f7fd8;
        };
    }

    /**
     * The sign every casino table carries: a black plate in a gold frame on a brass foot, leaning back
     * towards the player, naming the game.
     */
    private static void tableSign(PoseStack pose, WorldCanvas buffers, Font font, dev.gamblingitems.core.GameMode mode,
                                  float x, float y, float width, boolean lit) {
        float height = 30;
        solidBox(pose, buffers, x - width / 2 + 3, y - 6, x + width / 2 - 3, y, 2.5f, 4.5f, GOLD);
        solidBox(pose, buffers, x - 2, y - 4.5f, x + 2, y - 2, 4.5f, 9, BRASS);
        pose.pushPose();
        pose.translate(x, y - 3, 6);
        // Lean the plate back a little from upright, its face turned to the player.
        pose.mulPose(Axis.XP.rotationDegrees(-72));
        float half = width / 2;
        solidBox(pose, buffers, -half, -height, half, 0, -1.4f, 0, lit ? 0xffffe08a : GOLD);
        back(pose, buffers, -half, -height, half, 0, -1.45f, GOLD);
        quad(pose, buffers, -half + 1.4f, -height + 1.4f, half - 1.4f, -1.4f, lit ? 0xff2a2018 : 0xff121014, 0.05f);
        String game = WorldCanvas.translate("gui.gamblingitems.mode." + mode.id()).toUpperCase(java.util.Locale.ROOT);
        float scale = Math.min(0.5f, (width - 8) / Math.max(1, WorldCanvas.width(font, game)));
        label(pose, buffers, font, game, 0, -height + 9, GOLD, scale, 0.1f);
        quad(pose, buffers, -half + 5, -height + 14, half - 5, -height + 14.6f, GOLD, 0.1f);
        label(pose, buffers, font, "?", 0, -height + 21, lit ? 0xfffff4d0 : INK, 0.8f, 0.1f);
        pose.popPose();
    }

    /** A rectangle seen from below, so a plate standing up is solid from behind as well. */
    private static void back(PoseStack pose, WorldCanvas buffers, float x1, float y1, float x2, float y2, float lift, int colour) {
        int dark = shade(colour, .55f);
        buffers.quads(pose, (m, v) -> {
            WorldCanvas.vertex(v, m, x1, y1, lift, dark);
            WorldCanvas.vertex(v, m, x2, y1, lift, dark);
            WorldCanvas.vertex(v, m, x2, y2, lift, dark);
            WorldCanvas.vertex(v, m, x1, y2, lift, dark);
        });
    }

    /** The control the player is pointing at, or -1 when they are not pointing at this table. */
    private static int hovered(GameStationEntity station) {
        var hit = net.minecraft.client.Minecraft.getInstance().hitResult;
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)) return -1;
        var state = station.getLevel().getBlockState(blockHit.getBlockPos());
        if (!(state.getBlock() instanceof GameTableBlock)) return -1;
        if (!GameTableBlock.anchor(blockHit.getBlockPos(), state).equals(station.getBlockPos())) return -1;
        return TableLayout.button(station.mode(),
                station.getBlockState().getValue(GameTableBlock.FACING), blockHit, station.getBlockPos());
    }

    /** The very items staked on this table, laid out on the felt where the chips belong. */
    private static void stakes(GameStationEntity station, PoseStack pose, WorldCanvas buffers) {
        if (station.stakeItems.isEmpty()) return;
        String[] parts = station.stakeItems.split(",");
        float centreX = switch (station.mode()) {
            case ROULETTE -> -124;
            case BINGO -> -40;
            default -> 0;
        };
        float centreY = station.mode() == dev.gamblingitems.core.GameMode.BINGO ? 34 : TableLayout.STAKE_Y;
        int shown = Math.min(parts.length, 6);
        for (int index = 0; index < shown; index++) {
            ItemStack stack = stackOf(parts[index]);
            if (stack.isEmpty()) continue;
            float x = centreX + (index - (shown - 1) / 2f) * 26;
            int chip = chipColour(stack);
            if (chip != 0) {
                // Casino chips stand in a real stack, one disc per chip up to twenty, edged in white.
                float top = 8 + Math.min(20, stack.getCount()) * 2.2f;
                cylinder(pose, buffers, x, centreY, 10, 8, top, chip);
                circle(pose, buffers, x, centreY, 7.5f, 0xfff3eee2, top + 0.05f);
                circle(pose, buffers, x, centreY, 6, chip, top + 0.1f);
                continue;
            }
            pose.pushPose();
            pose.translate(x, centreY, 8);
            pose.scale(22, -22, 4);
            buffers.item(pose, stack, 0xf000f0, station.getLevel());
            pose.popPose();
        }
    }

    /** The colour of a casino chip by its face, as at a real table; 0 for anything else. */
    private static int chipColour(ItemStack stack) {
        int[] faces = dev.gamblingitems.fabric.arcade.ArcadeSettings.CHIPS;
        int[] colours = {0xffe8e4dc, 0xffc8303c, 0xff1f8a4c, 0xff1c1c22};
        for (int index = 0; index < faces.length; index++) {
            if (stack.is(dev.gamblingitems.fabric.ModContent.chip(faces[index]))) return colours[index];
        }
        return 0;
    }

    /** One staked stack, written as "item*count"; anything unreadable is simply not drawn. */
    private static ItemStack stackOf(String part) {
        int star = part.indexOf('*');
        String id = star < 0 ? part : part.substring(0, star);
        int count = 1;
        if (star >= 0) {
            try {
                count = Math.max(1, Math.min(64, Integer.parseInt(part.substring(star + 1))));
            } catch (NumberFormatException ignored) {
                // A felt never breaks on a stack it cannot read.
            }
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return ItemStack.EMPTY;
        return new ItemStack(dev.gamblingitems.fabric.Compat.item(key), count);
    }

    // ---------------------------------------------------------------- drawing

    /** A true annular sector; the narrow gap reveals the brass pocket separators. */
    private static void blade(PoseStack pose, WorldCanvas buffers, double angle, int colour,
                              float outer, float inner, float lift) {
        double half = Math.PI / RouletteWheel.POCKETS * .92;
        buffers.quads(pose, (m, v) -> {
        WorldCanvas.vertex(v, m, (float) (inner * Math.cos(angle-half)), (float) (inner * Math.sin(angle-half)), lift, colour);
        WorldCanvas.vertex(v, m, (float) (inner * Math.cos(angle+half)), (float) (inner * Math.sin(angle+half)), lift, colour);
        WorldCanvas.vertex(v, m, (float) (outer * Math.cos(angle+half)), (float) (outer * Math.sin(angle+half)), lift, colour);
        WorldCanvas.vertex(v, m, (float) (outer * Math.cos(angle-half)), (float) (outer * Math.sin(angle-half)), lift, colour);
        });
    }

    /** A coplanar triangle fan, with no overlapping blades or depth drift. */
    private static void circle(PoseStack pose, WorldCanvas buffers, float centreX, float centreY,
                               float radius, int colour, float lift) {
        int steps = WorldCanvas.segments(pose, radius, 40);
        buffers.quads(pose, (matrix, vertices) -> {
        for (int step = 0; step < steps; step++) {
            double a = step * 2 * Math.PI / steps, b = (step + 1) * 2 * Math.PI / steps;
            WorldCanvas.vertex(vertices, matrix, centreX, centreY, lift, colour);
            WorldCanvas.vertex(vertices, matrix, centreX + (float) Math.cos(b) * radius, centreY + (float) Math.sin(b) * radius, lift, colour);
            WorldCanvas.vertex(vertices, matrix, centreX + (float) Math.cos(a) * radius, centreY + (float) Math.sin(a) * radius, lift, colour);
            WorldCanvas.vertex(vertices, matrix, centreX, centreY, lift, colour);
        }
        });
    }

    private static int shade(int colour, float amount) {
        return 0xff000000 | (int) (((colour >> 16) & 255) * amount) << 16
                | (int) (((colour >> 8) & 255) * amount) << 8 | (int) ((colour & 255) * amount);
    }

    private static void wall(PoseStack pose, WorldCanvas buffers, float x1, float y1,
                             float x2, float y2, float bottom, float top, int colour) {
        buffers.quads(pose, (m, v) -> {
        WorldCanvas.vertex(v, m, x1, y1, bottom, colour);
        WorldCanvas.vertex(v, m, x2, y2, bottom, colour);
        WorldCanvas.vertex(v, m, x2, y2, top, colour);
        WorldCanvas.vertex(v, m, x1, y1, top, colour);
        });
    }

    private static void solidBox(PoseStack pose, WorldCanvas buffers, float x1, float y1,
                                 float x2, float y2, float bottom, float top, int colour) {
        quad(pose, buffers, x1, y1, x2, y2, colour, top);
        wall(pose, buffers, x1, y1, x2, y1, bottom, top, shade(colour, .65f));
        wall(pose, buffers, x2, y1, x2, y2, bottom, top, shade(colour, .8f));
        wall(pose, buffers, x2, y2, x1, y2, bottom, top, shade(colour, .75f));
        wall(pose, buffers, x1, y2, x1, y1, bottom, top, shade(colour, .6f));
    }

    private static void cylinder(PoseStack pose, WorldCanvas buffers, float x, float y,
                                 float radius, float bottom, float top, int colour) {
        circle(pose, buffers, x, y, radius, colour, top);
        int steps = WorldCanvas.segments(pose, radius, 40);
        for (int step = 0; step < steps; step++) {
            double a = step * 2 * Math.PI / steps, b = (step + 1) * 2 * Math.PI / steps;
            wall(pose, buffers, x + (float) Math.cos(a) * radius, y + (float) Math.sin(a) * radius,
                    x + (float) Math.cos(b) * radius, y + (float) Math.sin(b) * radius,
                    bottom, top, shade(colour, .68f + .13f * (float) Math.cos(a - .8)));
        }
    }

    /** A flat coloured rectangle, wound the way the station screen winds its own. */
    private static void quad(PoseStack pose, WorldCanvas buffers, float x1, float y1,
                             float x2, float y2, int colour, float lift) {
        if (WorldCanvas.tiny(pose, x2 - x1, y2 - y1)) return;
        buffers.quads(pose, (matrix, vertices) -> {
        WorldCanvas.vertex(vertices, matrix, x1, y1, lift, colour);
        WorldCanvas.vertex(vertices, matrix, x1, y2, lift, colour);
        WorldCanvas.vertex(vertices, matrix, x2, y2, lift, colour);
        WorldCanvas.vertex(vertices, matrix, x2, y1, lift, colour);
        });
    }

    /**
     * Writes on the felt. The lift is the layer the text belongs to, plus a hair: printed on its
     * card or its pocket, not floating above the table.
     */
    private static void label(PoseStack pose, WorldCanvas buffers, Font font, String text,
                              float x, float y, int colour, float scale, float lift) {
        if (text.isEmpty()) return;
        // Half a line above the middle, so a number sits in its cell whatever its size.
        buffers.text(pose, font, text, x, y, lift + 0.4f, scale, -WorldCanvas.width(font, text) / 2f, -4.5f, colour, 0xF000F0);
    }
}
