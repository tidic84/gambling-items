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

import java.util.ArrayList;
import java.util.List;

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
        // The roulette is built of voxels, a sixty-fourth of a block each; the other tables are not yet.
        voxels = station.mode() == dev.gamblingitems.core.GameMode.ROULETTE;
        int mahogany = MAHOGANY, mahoganyLight = MAHOGANY_LIGHT, track = TRACK;
        try {
            if (voxels) {
                switch (DIRECTION) {
                    case 1 -> {
                        // Monte-Carlo: blond walnut, gold ink on a midnight cloth.
                        MAHOGANY = 0xff4a2c14;
                        MAHOGANY_LIGHT = 0xff8a6234;
                        TRACK = 0xffb08a52;
                        INK = 0xffe6c36a;
                    }
                    case 2 -> {
                        // The electronic table: black lacquer, steel, and light for ink.
                        MAHOGANY = 0xff101014;
                        MAHOGANY_LIGHT = 0xff24242c;
                        TRACK = 0xff34343e;
                        RIM = 0xff35e0ff;
                        METAL = 0xff8fa0b0;
                        INK = 0xff35e0ff;
                    }
                    case 3 -> {
                        // The saloon: oak, copper and iron, the layout painted in dark ink on a hide.
                        MAHOGANY = 0xff3e2a18;
                        MAHOGANY_LIGHT = 0xff6e5030;
                        TRACK = 0xff8e7046;
                        RIM = 0xffc8733a;
                        METAL = 0xff6a6a70;
                        INK = 0xff3a2414;
                    }
                    default -> { }
                }
            }
            if (voxels) ground(pose, buffers);
            else cloth(pose, buffers, station.mode() != dev.gamblingitems.core.GameMode.BINGO);
            switch (station.mode()) {
                case BLACKJACK -> blackjack(station, partialTick, pose, buffers, font);
                case ROULETTE -> roulette(station, partialTick, pose, buffers, font);
                case BINGO -> bingo(station, pose, buffers, font);
                default -> { }
            }
            stakes(station, pose, buffers);
            controls(station, pose, buffers, font);
        } finally {
            voxels = false;
            MAHOGANY = mahogany;
            MAHOGANY_LIGHT = mahoganyLight;
            TRACK = track;
            RIM = GOLD;
            METAL = BRASS;
            INK = 0xffe9e4d6;
        }
        pose.popPose();
    }

    /** What lies on the top of a roulette under its layout: the lamp's shade on a cloth, a line round it, or a hide. */
    private static void ground(PoseStack pose, WorldCanvas buffers) {
        if (DIRECTION == 3) {
            // A hide laid on the planks, stitched round its edge, the layout painted on it.
            rawQuad(pose, buffers, -186, -122, 38, 92, 0xff9a7a48, 0.6f);
            rawQuad(pose, buffers, -184, -120, 36, 90, 0xffc9a86a, 0.62f);
            for (float x = -180; x < 32; x += 8) {
                rawQuad(pose, buffers, x, -118, x + 4, -116, 0xff6a4a26, 0.64f);
                rawQuad(pose, buffers, x, 86, x + 4, 88, 0xff6a4a26, 0.64f);
            }
            for (float y = -116; y < 86; y += 8) {
                rawQuad(pose, buffers, -182, y, -180, y + 4, 0xff6a4a26, 0.64f);
                rawQuad(pose, buffers, 32, y, 34, y + 4, 0xff6a4a26, 0.64f);
            }
            return;
        }
        if (DIRECTION != 2) {
            for (int ring = 0; ring < 5; ring++) {
                float inset = ring * 6;
                rawQuad(pose, buffers, -HALF_WIDTH + inset, -HALF_DEPTH + inset, HALF_WIDTH - inset, HALF_DEPTH - inset,
                        0x14000000, 1 + ring * 0.01f);
            }
        }
        float pin = 4;
        int line = DIRECTION == 2 ? INK : GOLD;
        rawQuad(pose, buffers, -HALF_WIDTH + pin, -HALF_DEPTH + pin, HALF_WIDTH - pin, -HALF_DEPTH + pin + 2, line, 2.1f);
        rawQuad(pose, buffers, -HALF_WIDTH + pin, -HALF_DEPTH + pin, -HALF_WIDTH + pin + 2, HALF_DEPTH - 18, line, 2.1f);
        rawQuad(pose, buffers, HALF_WIDTH - pin - 2, -HALF_DEPTH + pin, HALF_WIDTH - pin, HALF_DEPTH - 18, line, 2.1f);
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

    private static final int BRASS = 0xffc9a04a;
    /** The woods of the wheel and the ink of the layout: a private room's table takes its own. */
    private static int MAHOGANY = 0xff3b1d12, MAHOGANY_LIGHT = 0xff6a3520, TRACK = 0xff8a5a34;
    /** The metals of the wheel: its rim and dome, and its frets and fittings. */
    private static int RIM = GOLD, METAL = BRASS;
    /** The roulette this table is: see TableLayout.DIRECTION. */
    private static final int DIRECTION = TableLayout.DIRECTION;
    /**
     * The wheel each table carries: the classic one at Vegas, the tall one at Monte-Carlo, the low steel
     * one on the electronic table, and the one sunk in a square cabinet at the saloon.
     */
    private static final int MODEL = new int[] {0, 2, 3, 1}[DIRECTION];
    /** How large the wheel is built, and where it stands. */
    private static final float WHEEL = DIRECTION == 2 ? 0.7f : 0.8f;
    private static final float WHEEL_X = new float[] {-134, 0, 0, 128}[DIRECTION];
    private static final float WHEEL_Y = new float[] {0, -6, -76, 0}[DIRECTION];
    /** How many voxels a chip is across. */
    private static final int CHIP = 7;
    /** The ink the layout is printed in. */
    private static int INK = 0xffe9e4d6;
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
        // The wheel head turns as one piece: its pockets with their frets, the numbers round them, the turret.
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotation((float) wheelAngle));
        head(pose, buffers, font);
        turret(pose, buffers);
        pose.popPose();
        if (result >= 0 && station.phase > 0) ball(pose, buffers, wheelAngle, result, spin, eased);
        pose.popPose();
        if (DIRECTION == 0) rake(pose, buffers);
        if (DIRECTION == 2) board(pose, buffers, font, result, station.phase == 3);
        layout(pose, buffers, font, result, station.phase == 3);
        layoutChips(station, pose, buffers);
    }

    /**
     * The fixed bowl the wheel turns in, built of voxels: a cabinet with a rim, the ball track stepping
     * down from the rim towards the wheel head, and the diamonds standing on it. The classic bowl is
     * round; the sunk one sits in a square cabinet with brass corners, the monumental one is deep
     * under a high rim, and the modern one barely rises from the cloth.
     */
    private static void bowl(PoseStack pose, WorldCanvas buffers) {
        float[][] diamonds = new float[8][];
        for (int diamond = 0; diamond < 8; diamond++) {
            double angle = diamond * Math.PI / 4 + Math.PI / 8;
            diamonds[diamond] = new float[] {(float) Math.cos(angle) * 58 * WHEEL, (float) Math.sin(angle) * 58 * WHEEL};
        }
        float reach = 70 * WHEEL;
        float rim = MODEL == 2 ? 22.5f : 16.5f, rise = MODEL == 2 ? 8 : MODEL == 3 ? 2 : 4;
        field(pose, buffers, -reach, -reach, reach, reach, 0.5f, MAHOGANY, (x, y) -> {
            float r = (float) Math.hypot(x, y) / WHEEL;
            if (r < 52) return NONE;
            if (MODEL == 1) {
                // The square cabinet: a gold line round its edge, a brass plate on each corner.
                float ax = Math.abs(x) / WHEEL, ay = Math.abs(y) / WHEEL;
                if (Math.max(ax, ay) >= 70) return NONE;
                if (r >= 66) {
                    if (ax > 56 && ay > 56) return cell(18.5f, METAL);
                    return cell(16.5f, Math.max(ax, ay) >= 67 ? RIM : MAHOGANY_LIGHT);
                }
            } else {
                if (r >= 68) return NONE;
                if (r >= 66) return cell(rim, RIM);
            }
            if (r >= 64) return cell(rim, MODEL == 1 ? RIM : MAHOGANY_LIGHT);
            float track = level(12.5f + (r - 52) / 12 * rise);
            for (float[] diamond : diamonds) {
                if (Math.abs(x - diamond[0]) < 2.5f && Math.abs(y - diamond[1]) < 2.5f) {
                    return cell(MODEL == 2 ? track + 4 : Math.max(rim, track + 2), METAL);
                }
            }
            return cell(track, TRACK);
        });
    }

    /**
     * The wheel head, at rest: the number ring in red and black, a metal ring inside it, the pockets
     * a step lower with a fret standing between each two, and the middle of it: a cone rising in steps
     * to a dome on the classic wheel, lower on the sunk one, higher on the monumental one, and on the
     * modern one a flat disc crossed by four steel spokes. The numbers are printed round the ring.
     */
    private static void head(PoseStack pose, WorldCanvas buffers, Font font) {
        double step = 2 * Math.PI / RouletteWheel.POCKETS;
        float reach = 52 * WHEEL;
        field(pose, buffers, -reach, -reach, reach, reach, 4.5f, MAHOGANY, (x, y) -> {
            float r = (float) Math.hypot(x, y) / WHEEL;
            if (r >= 52) return NONE;
            double at = Math.atan2(y, x) / step;
            if (at < 0) at += RouletteWheel.POCKETS;
            int pocket = (int) Math.round(at) % RouletteWheel.POCKETS;
            int colour = colourOf(RouletteWheel.numberAtPocket(pocket));
            if (r >= 41) return cell(10.5f, colour);
            if (r >= 39) return cell(10.5f, METAL);
            if (r >= 30) {
                // How far this column lies from the fret between its pocket and the next, along the ring.
                double fret = (0.5 - Math.abs(at - Math.round(at))) * step * r * WHEEL;
                return fret < 1.1 ? cell(12.5f, METAL) : cell(8.5f, shade(colour, .55f));
            }
            switch (MODEL) {
                case 1:
                    if (r >= 12) return cell(level(10.5f + (30 - r) / 18 * 4), MAHOGANY_LIGHT);
                    if (r >= 6) return cell(16.5f, METAL);
                    return cell(r >= 3 ? 18.5f : 20.5f, RIM);
                case 2:
                    if (r >= 15) return cell(level(10.5f + (30 - r) / 15 * 12), MAHOGANY_LIGHT);
                    if (r >= 8) return cell(level(22.5f + (15 - r) / 7 * 2), METAL);
                    return cell(24.5f, RIM);
                case 3:
                    if (r >= 27) return cell(12.5f, METAL);
                    if (r < 10) return cell(16.5f, METAL);
                    if (Math.abs(x) < 2 || Math.abs(y) < 2) return cell(14.5f, RIM);
                    return cell(12.5f, MAHOGANY_LIGHT);
                default:
                    if (r >= 15) return cell(level(10.5f + (30 - r) / 15 * 8), MAHOGANY_LIGHT);
                    if (r >= 8) return cell(level(18.5f + (15 - r) / 7 * 2), METAL);
                    return cell(20.5f, RIM);
            }
        });
        for (int pocket = 0; pocket < RouletteWheel.POCKETS; pocket++) {
            double angle = pocket * step;
            label(pose, buffers, font, String.valueOf(RouletteWheel.numberAtPocket(pocket)),
                    (float) (Math.cos(angle) * 46.5 * WHEEL), (float) (Math.sin(angle) * 46.5 * WHEEL), TEXT, 0.42f * WHEEL, 10.6f);
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
        // The track the ball runs on stands higher in a deep bowl, lower in a shallow one.
        double high = MODEL == 2 ? 20.5 : MODEL == 3 ? 14.5 : 16.5;
        if (spin < 0.62) {
            radius = 61;
            height = high;
        } else if (spin < 0.86) {
            double t = (spin - 0.62) / 0.24;
            radius = 61 - 26 * t * t;
            // Down the slope, then over the frets: one hop for each fret it crosses, smaller and smaller.
            height = high - (high - 11.5) * t + (t > 0.55 ? Math.abs(Math.sin((t - 0.55) * Math.PI * 4)) * 3.5 * (1 - t) : 0);
        } else {
            radius = 35;
            height = 11.5;
        }
        float x = (float) (Math.cos(angle) * radius) * WHEEL, y = (float) (Math.sin(angle) * radius) * WHEEL, h = (float) height;
        // A ball of voxels, three across: a full middle layer, a cross above it, a glint on top.
        rawBox(pose, buffers, x - 3, y - 3, x + 3, y + 3, h - 2, h, 0xfff4f4f0);
        rawBox(pose, buffers, x - 3, y - 1, x + 3, y + 1, h, h + 2, 0xffffffff);
        rawBox(pose, buffers, x - 1, y - 3, x + 1, y + 3, h, h + 2, 0xffffffff);
        rawQuad(pose, buffers, x - 1, y - 1, x + 1, y + 1, 0xffffffff, h + 2.02f);
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
        if (voxels) {
            float inner = Math.min(r1, r2), outer = Math.max(r1, r2), low = Math.min(h1, h2), high = Math.max(h1, h2);
            field(pose, buffers, x - outer - VOXEL, y - outer - VOXEL, x + outer + VOXEL, y + outer + VOXEL, low, 0, (cx, cy) -> {
                float r = (float) Math.hypot(cx - x, cy - y);
                if (r1 == r2) return r <= r1 && r > r1 - VOXEL ? cell(high, colour) : NONE;
                if (r < inner || r > outer) return NONE;
                float h = h1 + (r - r1) / (r2 - r1) * (h2 - h1);
                return cell(low + Math.round((h - low) / VOXEL) * VOXEL, colour);
            });
            return;
        }
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

    /**
     * The turret in the middle of the wheel. The classic one is a gold spindle under a brass cap with four
     * arms ending in knobs; the sunk wheel has only its dome; the monumental one stands tall, a collar
     * and four arms half way up it and a ball on top; the modern one is a plain steel post.
     */
    private static void turret(PoseStack pose, WorldCanvas buffers) {
        if (MODEL == 1) return;
        if (MODEL == 3) {
            field(pose, buffers, -6, -6, 6, 6, 16.5f, RIM, (x, y) -> Math.hypot(x, y) < 5 ? cell(22.5f, RIM) : NONE);
            field(pose, buffers, -4, -4, 4, 4, 22.5f, METAL, (x, y) -> Math.hypot(x, y) < 3 ? cell(24.5f, METAL) : NONE);
            return;
        }
        float foot = MODEL == 2 ? 24.5f : 20.5f, arms = MODEL == 2 ? 30.5f : 26.5f;
        field(pose, buffers, -4, -4, 4, 4, foot, RIM, (x, y) -> Math.hypot(x, y) < 3.5 ? cell(arms + (MODEL == 2 ? 6 : 2), RIM) : NONE);
        if (MODEL == 2) {
            field(pose, buffers, -6, -6, 6, 6, arms - 2, METAL, (x, y) -> Math.hypot(x, y) < 5 ? cell(arms, METAL) : NONE);
            // The ball that crowns it.
            field(pose, buffers, -6, -6, 6, 6, arms + 6, METAL, (x, y) -> {
                double r = Math.hypot(x, y);
                return r < 3 ? cell(arms + 12, RIM) : r < 5 ? cell(arms + 10, METAL) : NONE;
            });
        } else {
            field(pose, buffers, -6, -6, 6, 6, arms + 2, METAL, (x, y) -> Math.hypot(x, y) < 5 ? cell(arms + 4, METAL) : NONE);
        }
        float end = MODEL == 2 ? 18 : 16;
        for (int arm = 0; arm < 4; arm++) {
            pose.pushPose();
            // A quarter turn maps the grid onto itself, so every arm stays on the voxels of the head.
            pose.mulPose(Axis.ZP.rotation((float) (arm * Math.PI / 2)));
            rawBox(pose, buffers, 4, -2, end, 2, arms, arms + 2, RIM);
            rawBox(pose, buffers, end, -2, end + 4, 2, arms - 2, arms + 4, METAL);
            pose.popPose();
        }
    }

    /** The croupier's rake, lying on the far side of the felt: a long handle and the head chips are drawn in with. */
    private static void rake(PoseStack pose, WorldCanvas buffers) {
        rawBox(pose, buffers, 88, -104, 180, -102, 4.5f, 6.5f, MAHOGANY_LIGHT);
        rawBox(pose, buffers, 176, -104, 180, -102, 4.5f, 6.5f, BRASS);
        rawBox(pose, buffers, 84, -114, 88, -92, 3.5f, 10.5f, MAHOGANY);
        rawBox(pose, buffers, 84, -114, 88, -92, 10.5f, 10.6f, BRASS);
    }

    /**
     * The layout, drawn from the very boxes the server reads a click in: the zero, the numbers on their
     * red and black, the three "2 to 1", the dozens and the even money areas, and the dolly on the
     * number that came out. Each table prints it its own way, and Monte-Carlo prints it twice.
     */
    private static void layout(PoseStack pose, WorldCanvas buffers, Font font,
                               int result, boolean settled) {
        String[] outside = {"1-18", "EVEN", "RED", "BLACK", "ODD", "19-36"};
        for (int side = 0; side < (DIRECTION == 1 ? 2 : 1); side++) {
            for (int number = 1; number < RouletteWheel.POCKETS; number++) {
                cellOf(pose, buffers, font, side(TableLayout.numberBox(number), side), number, result, settled);
            }
            zero(pose, buffers, font, side(TableLayout.numberBox(0), side), result, settled);
            for (int index = 0; index < 3; index++) {
                float[] box = side(TableLayout.columnBox(index), side);
                frame(pose, buffers, box, INK);
                // At the saloon a row of the square is not a column of the wheel: the box names its numbers.
                boxLabel(pose, buffers, font, DIRECTION == 3 ? (index + 1) + " " + (index + 4) + " " + (index + 7) + "..."
                        : WorldCanvas.translate("gui.gamblingitems.layout.column"), box, 0.42f);
                box = side(TableLayout.dozenBox(index), side);
                frame(pose, buffers, box, INK);
                boxLabel(pose, buffers, font, WorldCanvas.translate("gui.gamblingitems.layout.dozen" + (index + 1)), box, 0.55f);
            }
            for (int index = 0; index < outside.length; index++) {
                float[] box = side(TableLayout.outsideBox(index), side);
                frame(pose, buffers, box, INK);
                float cx = box[0] + box[2] / 2, cy = box[1] + box[3] / 2;
                if (outside[index].equals("RED") || outside[index].equals("BLACK")) {
                    // The red and black diamonds of the layout, in place of the words.
                    int colour = colourOf(outside[index].equals("RED") ? 1 : 2);
                    diamond(pose, buffers, cx, cy, box[2] * 0.4f, box[3] * 0.36f, INK, 3.4f);
                    diamond(pose, buffers, cx, cy, box[2] * 0.4f - 2, box[3] * 0.36f - 2, colour, 3.5f);
                } else {
                    boxLabel(pose, buffers, font, outsideLabel(outside[index]), box, 0.5f);
                }
            }
        }
    }

    /** An area as it lies on this layout, or on the one across the wheel from it. */
    private static float[] side(float[] box, int side) {
        return side == 0 ? box : TableLayout.mirror(box);
    }

    /** The name of an area, in the ink of the layout: along the area when it stands taller than it is wide. */
    private static void boxLabel(PoseStack pose, WorldCanvas buffers, Font font, String text, float[] box, float size) {
        boolean upright = box[3] > box[2] * 1.3f;
        float room = (upright ? box[3] : box[2]) - 4;
        // The large areas of the electronic table and of the saloon take a larger hand.
        float scale = Math.min(DIRECTION >= 2 ? size * 1.5f : size, room / Math.max(1, WorldCanvas.width(font, text)));
        pose.pushPose();
        pose.translate(box[0] + box[2] / 2, box[1] + box[3] / 2, 0);
        if (upright) pose.mulPose(Axis.ZP.rotationDegrees(-90));
        label(pose, buffers, font, text, 0, 0, INK, scale, 3.5f);
        pose.popPose();
    }

    /** One area of the layout: a line of ink round it, the cloth showing through. */
    private static void frame(PoseStack pose, WorldCanvas buffers, float[] box, int ink) {
        float x1 = box[0] - 0.5f, y1 = box[1] - 0.5f, x2 = box[0] + box[2] + 0.5f, y2 = box[1] + box[3] + 0.5f;
        float line = 0.9f;
        quad(pose, buffers, x1, y1, x2, y1 + line, ink, 3);
        quad(pose, buffers, x1, y2 - line, x2, y2, ink, 3);
        quad(pose, buffers, x1, y1, x1 + line, y2, ink, 3);
        quad(pose, buffers, x2 - line, y1, x2, y2, ink, 3);
    }

    /** The zero: green, pointed towards the wheel at Vegas, a plain box on the other tables. */
    private static void zero(PoseStack pose, WorldCanvas buffers, Font font, float[] box, int result, boolean settled) {
        float x1 = box[0] - 0.5f, y1 = box[1] - 0.5f, x2 = box[0] + box[2] + 0.5f, y2 = box[1] + box[3] + 0.5f;
        float middle = (y1 + y2) / 2, cx = (x1 + x2) / 2;
        int green = colourOf(0);
        if (DIRECTION == 0) {
            float tip = 10;
            pointed(pose, buffers, x1, y1, x2, y2, tip, middle, INK, 3);
            pointed(pose, buffers, x1 + 1.4f, y1 + 0.9f, x2 - 0.9f, y2 - 0.9f, tip - 0.6f, middle, green, 3.1f);
            dyed(pose, buffers, x1 + tip + 0.8f, y1 + 0.9f, x2 - 0.9f, y2 - 0.9f, 3.2f, 0);
            cx = (x1 + tip + x2) / 2;
        } else {
            frame(pose, buffers, box, DIRECTION == 2 ? green : INK);
            quad(pose, buffers, box[0] + 2, box[1] + 2, box[0] + box[2] - 2, box[1] + box[3] - 2,
                    DIRECTION == 2 ? shade(green, .35f) : green, 3.1f);
        }
        label(pose, buffers, font, "0", cx, middle, 0xfff3eee2, 0.9f, 3.4f);
        if (settled && result == 0) dolly(pose, buffers, cx, middle);
    }

    /** A box whose left side comes to a point, as the zero of a layout does. */
    private static void pointed(PoseStack pose, WorldCanvas buffers, float x1, float y1, float x2, float y2,
                                float tip, float middle, int colour, float lift) {
        if (voxels) {
            float half = (y2 - y1) / 2;
            field(pose, buffers, x1, y1, x2, y2, lift, 0, (x, y) ->
                    x >= x1 + tip * Math.abs(y - middle) / half && x <= x2 && y >= y1 && y <= y2 ? cell(lift, colour) : NONE);
            return;
        }
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
        if (voxels) {
            field(pose, buffers, x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight, lift, 0, (cx, cy) ->
                    Math.abs(cx - x) / halfWidth + Math.abs(cy - y) / halfHeight <= 1 ? cell(lift, colour) : NONE);
            return;
        }
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

    /**
     * A number of the layout. Vegas dyes the whole area in its colour; Monte-Carlo prints the number on
     * a roundel and leaves the cloth round it; the electronic table lights a dim panel inside a bright
     * line; the saloon paints it roughly on the hide.
     */
    private static void cellOf(PoseStack pose, WorldCanvas buffers, Font font, float[] box,
                               int number, int result, boolean settled) {
        float x = box[0], y = box[1], width = box[2], height = box[3];
        float cx = x + width / 2, cy = y + height / 2;
        int colour = colourOf(number);
        boolean red = RouletteWheel.colourOf(number) == RouletteWheel.Colour.RED;
        float size = 0.62f * Math.min(width, height * 1.2f) / 14;
        switch (DIRECTION) {
            case 1 -> {
                frame(pose, buffers, box, INK);
                // An oval roundel: wider than it is deep, as the area is.
                field(pose, buffers, cx - 10, cy - 6, cx + 10, cy + 6, 3.1f, 0, (px, py) ->
                        Math.hypot((px - cx) / 9, (py - cy) / 6) <= 1 ? cell(3.1f, colour) : NONE);
                size = 0.6f;
            }
            case 2 -> {
                frame(pose, buffers, box, red ? colour : INK);
                quad(pose, buffers, x + 2, y + 2, x + width - 2, y + height - 2, red ? shade(colour, .32f) : colour, 3.1f);
                size = 0.95f;
            }
            case 3 -> {
                frame(pose, buffers, box, INK);
                quad(pose, buffers, x + 2, y + 2, x + width - 2, y + height - 2, colour, 3.1f);
                speckle(pose, buffers, x + 2, y + 2, x + width - 2, y + height - 2, 3.2f, number);
                size = 0.95f;
            }
            default -> {
                frame(pose, buffers, box, INK);
                quad(pose, buffers, x + 1.4f, y + 1.4f, x + width - 1.4f, y + height - 1.4f, colour, 3.1f);
                dyed(pose, buffers, x + 1.4f, y + 1.4f, x + width - 1.4f, y + height - 1.4f, 3.2f, number);
            }
        }
        label(pose, buffers, font, String.valueOf(number), cx, cy, 0xfff3eee2, size, 3.4f);
        if (settled && number == result) dolly(pose, buffers, cx, cy);
    }

    /**
     * The weave of the cloth showing through the dye of a printed area: the diagonal ribs of the
     * twill, on the same pitch as the baize round it so they run on from the felt, a soft patch where
     * the nap lies the other way, and a few loose fibres catching the light.
     */
    private static void dyed(PoseStack pose, WorldCanvas buffers, float x1, float y1, float x2, float y2,
                             float lift, int seed) {
        if (voxels) {
            // On voxels the twill is a pixel pattern: two voxels dark, two clear, a step along each row.
            float[] across = span(x1, x2), deep = span(y1, y2);
            if (across == null || deep == null) return;
            for (float y = deep[0]; y < deep[1] - 0.01f; y += VOXEL) {
                for (float x = across[0]; x < across[1] - 0.01f; x += VOXEL) {
                    int i = Math.round(x / VOXEL), j = Math.round(y / VOXEL);
                    if (Math.floorMod(i + j, 4) < 2) rawQuad(pose, buffers, x, y, x + VOXEL, y + VOXEL, 0x2a000000, lift);
                    else if (hash(seed * 59 + i * 7 + j * 131) % 9 == 0) rawQuad(pose, buffers, x, y, x + VOXEL, y + VOXEL, 0x26ffffff, lift + 0.01f);
                }
            }
            return;
        }
        float pitch = 3;
        int first = (int) Math.floor((x1 + y1) / pitch), last = (int) Math.ceil((x2 + y2) / pitch);
        for (int rib = first; rib <= last; rib++) {
            float from = rib * pitch;
            band(pose, buffers, x1, y1, x2, y2, from, from + pitch * 0.45f, 0x3a000000, lift);
            band(pose, buffers, x1, y1, x2, y2, from + pitch * 0.55f, from + pitch * 0.7f, 0x22ffffff, lift + 0.01f);
        }
        // The nap: the dye lies a shade deeper towards one end of the area, fading out across it.
        int h = hash(seed * 7919 + 11);
        int deep = 0x26000000, clear = 0x00000000;
        boolean down = (h & 1) == 0;
        buffers.quads(pose, (m, v) -> {
            WorldCanvas.vertex(v, m, x1, y1, lift + 0.02f, down ? deep : clear);
            WorldCanvas.vertex(v, m, x1, y2, lift + 0.02f, down ? clear : deep);
            WorldCanvas.vertex(v, m, x2, y2, lift + 0.02f, down ? clear : deep);
            WorldCanvas.vertex(v, m, x2, y1, lift + 0.02f, down ? deep : clear);
        });
        for (int fibre = 0; fibre < 3; fibre++) {
            int f = hash(seed * 31 + fibre);
            float fx = x1 + 0.5f + (f & 255) / 255f * (x2 - x1 - 2), fy = y1 + 0.5f + (f >> 8 & 255) / 255f * (y2 - y1 - 1);
            quad(pose, buffers, fx, fy, fx + 0.9f + (f >> 16 & 3) * 0.3f, fy + 0.22f, 0x40ffffff, lift + 0.03f);
        }
    }

    /** The part of a rectangle lying between two diagonals x + y = from and x + y = to, as one flat piece. */
    private static void band(PoseStack pose, WorldCanvas buffers, float x1, float y1, float x2, float y2,
                             float from, float to, int colour, float lift) {
        // Wound as quad() winds its corners, or the face would turn its back to the player.
        float[][] polygon = {{x1, y1}, {x1, y2}, {x2, y2}, {x2, y1}};
        polygon = clip(polygon, from, 1);
        polygon = clip(polygon, to, -1);
        if (polygon.length < 3) return;
        float[][] shape = polygon;
        buffers.quads(pose, (m, v) -> {
            // A fan of quads, each closing on itself, as the pointed zero is drawn.
            for (int corner = 1; corner + 1 < shape.length; corner++) {
                WorldCanvas.vertex(v, m, shape[0][0], shape[0][1], lift, colour);
                WorldCanvas.vertex(v, m, shape[corner][0], shape[corner][1], lift, colour);
                WorldCanvas.vertex(v, m, shape[corner + 1][0], shape[corner + 1][1], lift, colour);
                WorldCanvas.vertex(v, m, shape[corner + 1][0], shape[corner + 1][1], lift, colour);
            }
        });
    }

    /** What of a convex polygon lies on one side of the diagonal x + y = at: above it for side 1, below for -1. */
    private static float[][] clip(float[][] polygon, float at, int side) {
        List<float[]> kept = new ArrayList<>();
        for (int index = 0; index < polygon.length; index++) {
            float[] a = polygon[index], b = polygon[(index + 1) % polygon.length];
            float da = (a[0] + a[1] - at) * side, db = (b[0] + b[1] - at) * side;
            if (da >= 0) kept.add(a);
            if ((da >= 0) != (db >= 0)) {
                float t = da / (da - db);
                kept.add(new float[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t});
            }
        }
        return kept.toArray(new float[0][]);
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
            float dx = (seat % 3 - 1) * 4, dy = (seat % 2 == 0 ? -1 : 1) * 2 * Math.min(1, seat);
            for (String area : areas.split(", ")) {
                int space = area.lastIndexOf(' ');
                if (space <= 0) continue;
                RouletteWheel.Bet bet = betNamed(area.substring(0, space));
                if (bet == null) continue;
                float[] box = TableLayout.boxOf(bet);
                if (box == null) continue;
                // Where there are two layouts, the seats take them in turn.
                box = side(box, DIRECTION == 1 ? seat % 2 : 0);
                double value;
                try {
                    value = Double.parseDouble(area.substring(space + 1));
                } catch (NumberFormatException ignored) {
                    continue;
                }
                int chips = (int) Math.max(1, Math.min(8, Math.ceil(value)));
                voxelChips(pose, buffers, box[0] + box[2] / 2 + dx, box[1] + box[3] / 2 + dy, CHIP, chips, colour, false);
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

    /**
     * A stack of chips built of voxels, seven across, each one voxel thick, the top one showing its
     * face. Vegas plays round chips with eight cream inserts, striped down the stack; Monte-Carlo plays
     * oblong plaques framed in cream with a gold bar; the electronic table plays square markers that
     * light round their edge; the saloon plays coins, milled in gold round the colour of the player.
     */
    private static void voxelChips(PoseStack pose, WorldCanvas buffers, float x, float y, int size, int chips,
                                   int colour, boolean lit) {
        // Cream inserts, or blue ones on a chip that is itself pale.
        int cream = (colour >> 8 & 255) > 215 && (colour & 255) > 200 ? BLUE : 0xfff3eee2;
        float radius = size / 2f, middle = (size - 1) / 2f;
        float left = snap(x - size), near = snap(y - size), base = 4.5f;
        int[][] top = new int[size][size];
        boolean[][] solid = new boolean[size][size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                float i = Math.abs(col - middle), j = Math.abs(row - middle);
                solid[row][col] = switch (DIRECTION) {
                    case 1 -> j <= 2;
                    case 2 -> i < middle || j < middle;
                    default -> Math.hypot(i, j) <= radius;
                };
            }
        }
        // The shadow of the stack on the cloth, a voxel down and across from it.
        for (int row = 0; row < size; row++) {
            int from = 0, to = size;
            while (from < size && !solid[row][from]) from++;
            while (to > from && !solid[row][to - 1]) to--;
            if (to > from) rawQuad(pose, buffers, left + (from + 1) * VOXEL, near + (row + 1) * VOXEL,
                    left + (to + 1) * VOXEL, near + (row + 2) * VOXEL, 0x50000000, 3.55f);
        }
        float[] light = {.65f, .8f, .75f, .6f};
        int[][] sides = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int chip = 0; chip < chips; chip++) {
            boolean last = chip == chips - 1;
            // Counted from the top, so the top chip always shows the same edge.
            int down = chips - 1 - chip;
            float bottom = base + chip * VOXEL, height = bottom + VOXEL;
            int body = down % 2 == 0 ? colour : shade(colour, .84f);
            if (lit) body = shade(body, 1.25f);
            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; col++) {
                    if (!solid[row][col]) continue;
                    float i = col - middle, j = row - middle;
                    boolean edge = false;
                    for (int[] side : sides) {
                        int c = col + side[0], r = row + side[1];
                        if (c < 0 || r < 0 || c >= size || r >= size || !solid[r][c]) edge = true;
                    }
                    int voxel = body;
                    double from = Math.hypot(i, j), square = Math.max(Math.abs(i), Math.abs(j));
                    switch (DIRECTION) {
                        case 1 -> {
                            if (edge && down % 2 == 0) voxel = cream;
                            if (last && !edge && j == 0) voxel = cream;
                        }
                        case 2 -> {
                            voxel = edge ? (down % 2 == 0 ? shade(colour, 1.35f) : shade(colour, .6f)) : 0xff0e1218;
                            if (last && !edge && square <= 1) voxel = square == 0 ? 0xffffffff : shade(colour, 1.2f);
                        }
                        case 3 -> {
                            voxel = edge ? (Math.floorMod((int) Math.round(Math.atan2(j, i) / (Math.PI / 8)) + chip, 2) == 0
                                    ? 0xffe2b34a : 0xffa8792a) : 0xffd9a83a;
                            if (last && !edge && from <= radius * 0.62) voxel = from <= 0.5 ? 0xffe2b34a : body;
                        }
                        default -> {
                            int sector = (int) Math.round(Math.atan2(j, i) / (Math.PI / 8));
                            if (edge && Math.floorMod(sector + chip, 2) == 0) voxel = cream;
                            if (last && !edge) {
                                if (from <= radius * 0.2) voxel = shade(body, .72f);
                                else if (from <= radius * 0.47) voxel = cream;
                            }
                        }
                    }
                    top[row][col] = voxel;
                    if (!edge) continue;
                    float xa = left + col * VOXEL, ya = near + row * VOXEL, xb = xa + VOXEL, yb = ya + VOXEL;
                    for (int side = 0; side < 4; side++) {
                        int c = col + sides[side][0], r = row + sides[side][1];
                        if (c >= 0 && r >= 0 && c < size && r < size && solid[r][c]) continue;
                        // Wound as the walls of a field are: outwards, on a mirrored canvas.
                        float[] line = switch (side) {
                            case 0 -> new float[] {xb, ya, xa, ya};
                            case 1 -> new float[] {xb, yb, xb, ya};
                            case 2 -> new float[] {xa, yb, xb, yb};
                            default -> new float[] {xa, ya, xa, yb};
                        };
                        rawWall(pose, buffers, line[0], line[1], line[2], line[3], bottom, height, shade(voxel, light[side]));
                        // The dark line where the bottom chip meets the cloth.
                        if (chip == 0) rawWall(pose, buffers, line[0], line[1], line[2], line[3], 3.4f, bottom, 0xff0c0c0c);
                    }
                }
            }
            if (!last) continue;
            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; ) {
                    if (!solid[row][col]) {
                        col++;
                        continue;
                    }
                    int end = col + 1;
                    while (end < size && solid[row][end] && top[row][end] == top[row][col]) end++;
                    rawQuad(pose, buffers, left + col * VOXEL, near + row * VOXEL, left + end * VOXEL,
                            near + (row + 1) * VOXEL, top[row][col], height);
                    col = end;
                }
            }
        }
    }

    /**
     * The croupier's float: the chips of the table standing in rows beside the wheel, a column for each
     * colour the players are given and then the cash chips. Winnings are paid out of it, so it is where
     * a player collects them. The saloon keeps its own in a wooden crate.
     */
    private static void chipFloat(PoseStack pose, WorldCanvas buffers, float x1, float y1, float x2, float y2, boolean lit) {
        int[] cash = {0xffe8e4dc, 0xff1c1c22};
        int columns = Math.min(SEAT_COLOURS.length + cash.length, (int) ((x2 - x1) / 16)), rows = 2;
        float pitch = (x2 - x1) / columns, deep = (y2 - y1) / rows;
        if (DIRECTION == 3) {
            rawBox(pose, buffers, x1 - 4, y1 - 2, x2 + 4, y2 + 2, 0.5f, 4.5f, MAHOGANY);
            rawBox(pose, buffers, x1 - 4, y1 - 2, x2 + 4, y1, 4.5f, 8.5f, MAHOGANY_LIGHT);
            rawBox(pose, buffers, x1 - 4, y2, x2 + 4, y2 + 2, 4.5f, 8.5f, MAHOGANY_LIGHT);
            rawBox(pose, buffers, x1 - 4, y1, x1 - 2, y2, 4.5f, 8.5f, MAHOGANY_LIGHT);
            rawBox(pose, buffers, x2 + 2, y1, x2 + 4, y2, 4.5f, 8.5f, MAHOGANY_LIGHT);
            for (float cx : new float[] {x1 - 4, x2 + 2}) {
                for (float cy : new float[] {y1 - 2, y2}) rawBox(pose, buffers, cx, cy, cx + 2, cy + 2, 4.5f, 10.5f, METAL);
            }
        }
        for (int column = 0; column < columns; column++) {
            int colour = column < SEAT_COLOURS.length ? SEAT_COLOURS[column] : cash[column - SEAT_COLOURS.length];
            for (int row = 0; row < rows; row++) {
                // Fuller at the back, where the croupier has not drawn on them yet.
                int chips = 7 - row * 2 + hash(column * 31 + row * 7) % 2;
                voxelChips(pose, buffers, x1 + (column + 0.5f) * pitch, y1 + (row + 0.5f) * deep, CHIP, chips, colour, lit);
            }
        }
    }

    /**
     * The board of the electronic table: a low console at the back, its screen lit in the colour of the
     * number that came out and showing it large.
     */
    private static void board(PoseStack pose, WorldCanvas buffers, Font font, int result, boolean settled) {
        rawBox(pose, buffers, 72, -118, 180, -44, 0.5f, 6.5f, 0xff14161c);
        rawQuad(pose, buffers, 74, -116, 178, -46, INK, 6.52f);
        boolean shown = settled && result >= 0;
        rawQuad(pose, buffers, 76, -114, 176, -48, shown ? shade(colourOf(result), .45f) : 0xff0a0e14, 6.54f);
        label(pose, buffers, font, shown ? String.valueOf(result) : "--", 126, -80, shown ? 0xffffffff : INK, 4, 6.6f);
    }

    private static int colourOf(int number) {
        // Each table has its own red, black and green.
        return switch (RouletteWheel.colourOf(number)) {
            case RED -> new int[] {RED, 0xffb3202c, 0xffff2e63, 0xff9c2f22}[DIRECTION];
            case BLACK -> new int[] {BLACK, 0xff17171c, 0xff0c1620, 0xff2b2622}[DIRECTION];
            case GREEN -> new int[] {GREEN, 0xff1f8a4c, 0xff18e08a, 0xff4a6b2a}[DIRECTION];
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
            if (station.mode() == dev.gamblingitems.core.GameMode.ROULETTE) {
                // A roulette carries nothing a real one does not: its one control is the croupier's float.
                chipFloat(pose, buffers, x1, y1, x2, y2, lit);
                continue;
            }
            switch (control.action()) {
                case StationPanel.DEPOSIT -> dropBox(pose, buffers, x1 + 3, y1 + 2, x2 - 3, plate - 2, lit);
                case StationPanel.RETURN -> chipTray(pose, buffers, x1 + 2, y1 + 2, x2 - 2, plate - 2, lit);
                case StationPanel.COLLECT -> payoutCup(pose, buffers, (x1 + x2) / 2, (y1 + plate) / 2,
                        Math.min(x2 - x1, plate - y1) / 2 - 3, lit);
                case StationPanel.OPEN -> tableSign(pose, buffers, font, station.mode(), (x1 + x2) / 2, plate - 3,
                        x2 - x1 - 4, lit);
                default -> pushButton(pose, buffers, (x1 + x2) / 2, (y1 + plate) / 2, Math.min(x2 - x1, plate - y1) / 2 - 2,
                        buttonColour(control.label()), control.label(), lit);
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
        int face = lit ? 0xffffe08a : BRASS;
        quad(pose, buffers, cx - half + 0.7f, y1 + 0.7f, cx + half - 0.7f, y2 - 0.7f, face, 3.35f);
        brushed(pose, buffers, cx - half + 0.7f, y1 + 0.7f, cx + half - 0.7f, y2 - 0.7f, face, 3.36f, text.hashCode());
        // An engraved line just inside the edge, and a screw at either end.
        outline(pose, buffers, cx - half + 1.4f, y1 + 1.3f, cx + half - 1.4f, y2 - 1.3f, 0.3f, shade(face, .62f), 3.37f);
        for (float sx : new float[] {cx - half + 2.1f, cx + half - 2.1f}) screw(pose, buffers, sx, (y1 + y2) / 2, 0.75f, face, 3.38f);
        label(pose, buffers, font, text, cx, (y1 + y2) / 2 + 0.4f, lit ? 0xff2a1206 : 0xff3a2008, scale, 3.4f);
    }

    /** The drop box: a mahogany chest with a brass slot plate on its lid, the stake pushed through the slot. */
    private static void dropBox(PoseStack pose, WorldCanvas buffers, float x1, float y1, float x2, float y2, boolean lit) {
        float top = lit ? 12.5f : 11;
        solidBox(pose, buffers, x1, y1, x2, y2, 2.5f, top, lit ? MAHOGANY_LIGHT : MAHOGANY);
        // A gold band round the lid, and the lid itself a shade lighter than the chest.
        solidBox(pose, buffers, x1 - 0.4f, y1 - 0.4f, x2 + 0.4f, y2 + 0.4f, top - 1.6f, top - 0.6f, GOLD);
        int lid = shade(MAHOGANY_LIGHT, lit ? 1.2f : .85f);
        quad(pose, buffers, x1 + 1.2f, y1 + 1.2f, x2 - 1.2f, y2 - 1.2f, lid, top + 0.03f);
        grain(pose, buffers, x1 + 1.2f, y1 + 1.2f, x2 - 1.2f, y2 - 1.2f, lid, top + 0.04f, 11);
        float cx = (x1 + x2) / 2, cy = (y1 + y2) / 2;
        // A brass keyhole escutcheon on the front of the chest, facing the player.
        wall(pose, buffers, cx + 1.6f, y2 + 0.05f, cx - 1.6f, y2 + 0.05f, top - 6, top - 2.5f, BRASS);
        wall(pose, buffers, cx + 0.45f, y2 + 0.1f, cx - 0.45f, y2 + 0.1f, top - 5.4f, top - 3.4f, 0xff120806);
        // The brass plate round the slot, the slot, and a chip half way into it.
        int slotPlate = lit ? 0xffffe08a : BRASS;
        solidBox(pose, buffers, x1 + 3, cy - 3.2f, x2 - 3, cy + 3.2f, top, top + 0.6f, slotPlate);
        brushed(pose, buffers, x1 + 3, cy - 3.2f, x2 - 3, cy + 3.2f, slotPlate, top + 0.62f, 12);
        for (float sx : new float[] {x1 + 4, x2 - 4}) screw(pose, buffers, sx, cy, 0.7f, slotPlate, top + 0.64f);
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
        int wood = lit ? MAHOGANY_LIGHT : MAHOGANY;
        solidBox(pose, buffers, x1, y1, x2, y2, 2.5f, top, wood);
        grain(pose, buffers, x1, y1, x2, y2, wood, top + 0.02f, 21);
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
        if (voxels) {
            // A bowl of voxels: a gold lip, the brass inside stepping down to the bottom, the chips in it.
            int lip = lit ? 0xfffff4d0 : GOLD;
            field(pose, buffers, x - radius - VOXEL, y - radius - VOXEL, x + radius + VOXEL, y + radius + VOXEL, 2.5f, shade(brass, .85f),
                    (cx, cy) -> {
                        float r = (float) Math.hypot(cx - x, cy - y) / radius;
                        if (r > 1) return NONE;
                        if (r > 1 - VOXEL / radius) return cell(12.5f, lip);
                        return cell(level(4.5f + 8 * r * r), r < 0.6f ? 0xff3a2a12 : brass);
                    });
            for (int stack = 0; stack < 3; stack++) {
                double angle = stack * 2 * Math.PI / 3 + 0.5;
                float sx = x + (float) Math.cos(angle) * radius * 0.3f, sy = y + (float) Math.sin(angle) * radius * 0.3f;
                cylinder(pose, buffers, sx, sy, radius * 0.22f, 4.5f, 6.5f + stack * 2, stack % 2 == 0 ? GOLD : 0xffd9a83a);
            }
            return;
        }
        circle(pose, buffers, x + 1.5f, y + 1.5f, radius, 0x50000000, 2.55f);
        cylinder(pose, buffers, x, y, radius * 0.55f, 2.5f, 4.5f, shade(brass, .85f));
        cone(pose, buffers, x, y, radius * 0.6f, 4.5f, radius, 12, brass);
        // Spun brass: fine rings turned into the bowl, a shade darker or lighter, inside and out.
        for (int ring = 1; ring < 7 && !voxels; ring++) {
            float t = ring / 7f, t2 = t + 0.03f;
            float r1 = radius * (0.6f + 0.4f * t), r2 = radius * (0.6f + 0.4f * t2);
            cone(pose, buffers, x, y, r1 + 0.06f, 4.5f + 7.5f * t, r2 + 0.06f, 4.5f + 7.5f * t2,
                    shade(brass, ring % 2 == 0 ? .78f : 1.15f));
        }
        annulus(pose, buffers, x, y, radius - 1.1f, radius + 0.6f, lit ? 0xfffff4d0 : GOLD, 12.05f);
        annulus(pose, buffers, x, y, radius - 0.5f, radius - 0.2f, 0xfffff4d0, 12.08f);
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
    private static void pushButton(PoseStack pose, WorldCanvas buffers, float x, float y, float radius, int colour,
                                   String move, boolean lit) {
        if (lit) circle(pose, buffers, x, y, radius + 3.5f, (colour & 0x00ffffff) | 0x60000000, 2.55f);
        cylinder(pose, buffers, x, y, radius + 1.6f, 2.5f, 4.2f, BRASS);
        annulus(pose, buffers, x, y, radius, radius + 1.6f, GOLD, 4.25f);
        // The knurled edge of the bezel: little notches all round it.
        for (int notch = 0; notch < 24; notch++) {
            double a = notch * Math.PI / 12;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            quad(pose, buffers, x + c * (radius + 0.9f) - 0.25f, y + s * (radius + 0.9f) - 0.25f,
                    x + c * (radius + 0.9f) + 0.25f, y + s * (radius + 0.9f) + 0.25f, 0xff7a5a22, 4.27f);
        }
        int face = lit ? shade(colour, 1.45f) : colour;
        // Pressed in a little under the hand.
        float rise = lit ? 2.5f : 4;
        cylinder(pose, buffers, x, y, radius, 3.5f, 4.2f + rise * 0.4f, shade(face, .8f));
        cylinder(pose, buffers, x, y, radius * 0.85f, 4.2f + rise * 0.4f, 4.2f + rise * 0.75f, face);
        cylinder(pose, buffers, x, y, radius * 0.6f, 4.2f + rise * 0.75f, 4.2f + rise, shade(face, 1.12f));
        // The lamp inside a translucent dome: a brighter heart, then the move engraved over it.
        circle(pose, buffers, x, y, radius * 0.42f, shade(face, lit ? 1.4f : 1.22f), 4.22f + rise);
        float top = 4.26f + rise, size = radius * 0.32f;
        int ink = shade(face, .45f);
        switch (move) {
            case "hit", "bingo_buy" -> {
                quad(pose, buffers, x - size, y - size * 0.22f, x + size, y + size * 0.22f, ink, top);
                quad(pose, buffers, x - size * 0.22f, y - size, x + size * 0.22f, y + size, ink, top);
            }
            case "stand" -> quad(pose, buffers, x - size, y - size * 0.25f, x + size, y + size * 0.25f, ink, top);
            case "bingo_board" -> {
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        float px = x + i * size * 0.7f, py = y + j * size * 0.7f;
                        quad(pose, buffers, px - size * 0.25f, py - size * 0.25f, px + size * 0.25f, py + size * 0.25f, ink, top);
                    }
                }
            }
            case "info" -> {
                quad(pose, buffers, x - size * 0.2f, y - size * 0.3f, x + size * 0.2f, y + size, ink, top);
                quad(pose, buffers, x - size * 0.2f, y - size, x + size * 0.2f, y - size * 0.6f, ink, top);
            }
            default -> {
                // Deal or start: a card, its corner turned towards the player.
                outline(pose, buffers, x - size * 0.7f, y - size, x + size * 0.7f, y + size, size * 0.22f, ink, top);
            }
        }
        // The light on the dome, and the softer glint opposite it.
        circle(pose, buffers, x - radius * 0.3f, y - radius * 0.3f, radius * 0.16f, 0xc0ffffff, top + 0.02f);
        circle(pose, buffers, x + radius * 0.34f, y + radius * 0.34f, radius * 0.08f, 0x60ffffff, top + 0.02f);
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
        brushed(pose, buffers, -half, -height, half, -height + 1.4f, GOLD, 0.02f, 31);
        // The lacquer catches the light in a broad diagonal band.
        float band = -height * 0.35f;
        buffers.quads(pose, (m, v) -> {
            WorldCanvas.vertex(v, m, -half + 1.4f, band + 6, 0.07f, 0x14ffffff);
            WorldCanvas.vertex(v, m, -half + 1.4f, band + 12, 0.07f, 0x14ffffff);
            WorldCanvas.vertex(v, m, half - 1.4f, band - 4, 0.07f, 0x14ffffff);
            WorldCanvas.vertex(v, m, half - 1.4f, band - 10, 0.07f, 0x14ffffff);
        });
        for (float sx : new float[] {-half + 3.2f, half - 3.2f}) {
            for (float sy : new float[] {-height + 3.2f, -3.2f}) screw(pose, buffers, sx, sy, 0.8f, GOLD, 0.08f);
        }
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
            case ROULETTE -> new float[] {64, 0, -120, 110}[DIRECTION];
            case BINGO -> -40;
            default -> 0;
        };
        float centreY = switch (station.mode()) {
            case BINGO -> 34;
            // In front of the player, between the layout and the rail.
            case ROULETTE -> new float[] {108, 100, -50, 96}[DIRECTION];
            default -> TableLayout.STAKE_Y;
        };
        int shown = Math.min(parts.length, 6);
        for (int index = 0; index < shown; index++) {
            ItemStack stack = stackOf(parts[index]);
            if (stack.isEmpty()) continue;
            float x = centreX + (index - (shown - 1) / 2f) * (voxels && DIRECTION != 0 ? 20 : 26);
            int chip = chipColour(stack);
            if (chip != 0 && voxels) {
                voxelChips(pose, buffers, x, centreY, CHIP, Math.min(8, stack.getCount()), chip, false);
                continue;
            }
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

    /** A coplanar triangle fan, with no overlapping blades or depth drift. */
    private static void circle(PoseStack pose, WorldCanvas buffers, float centreX, float centreY,
                               float radius, int colour, float lift) {
        if (voxels) {
            field(pose, buffers, centreX - radius - VOXEL, centreY - radius - VOXEL, centreX + radius + VOXEL,
                    centreY + radius + VOXEL, lift, 0,
                    (x, y) -> Math.hypot(x - centreX, y - centreY) <= radius ? cell(lift, colour) : NONE);
            return;
        }
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
        // Clamped: a colour made lighter must not spill from one channel into the next.
        return 0xff000000 | Math.min(255, (int) (((colour >> 16) & 255) * amount)) << 16
                | Math.min(255, (int) (((colour >> 8) & 255) * amount)) << 8 | Math.min(255, (int) ((colour & 255) * amount));
    }

    private static void wall(PoseStack pose, WorldCanvas buffers, float x1, float y1,
                             float x2, float y2, float bottom, float top, int colour) {
        if (voxels) {
            float a = snap(x1), b = snap(y1), c = snap(x2), d = snap(y2);
            if (a != c || b != d) rawWall(pose, buffers, a, b, c, d, bottom, top, colour);
            return;
        }
        rawWall(pose, buffers, x1, y1, x2, y2, bottom, top, colour);
    }

    private static void rawWall(PoseStack pose, WorldCanvas buffers, float x1, float y1,
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
        if (voxels) {
            float[] across = span(x1, x2), deep = span(y1, y2);
            if (across != null && deep != null) rawBox(pose, buffers, across[0], deep[0], across[1], deep[1], bottom, top, colour);
            return;
        }
        rawBox(pose, buffers, x1, y1, x2, y2, bottom, top, colour);
    }

    private static void rawBox(PoseStack pose, WorldCanvas buffers, float x1, float y1,
                               float x2, float y2, float bottom, float top, int colour) {
        rawQuad(pose, buffers, x1, y1, x2, y2, colour, top);
        // Each side faces outwards; the canvas is mirrored, so it is wound clockwise seen from outside.
        rawWall(pose, buffers, x2, y1, x1, y1, bottom, top, shade(colour, .65f));
        rawWall(pose, buffers, x2, y2, x2, y1, bottom, top, shade(colour, .8f));
        rawWall(pose, buffers, x1, y2, x2, y2, bottom, top, shade(colour, .75f));
        rawWall(pose, buffers, x1, y1, x1, y2, bottom, top, shade(colour, .6f));
    }

    // ---------------------------------------------------------------- voxels

    /** The side of a voxel on a table built of them: a sixty-fourth of a block. */
    private static final float VOXEL = 2;
    /** Heights of the voxel layers are counted from here, so the first layer sits on the felt's things. */
    private static final float LAYER = 0.5f;
    /** True while a table built of voxels is drawn: every shape is then laid on the voxel grid. */
    private static boolean voxels;
    private static final long NONE = 0;

    /** What one column of a voxel field holds: its height and the colour of its top, or NONE. */
    @FunctionalInterface
    private interface Column { long at(float x, float y); }

    private static long cell(float height, int colour) {
        return (long) Float.floatToIntBits(height) << 32 | (colour & 0xffffffffL);
    }

    private static float snap(float value) {
        return Math.round(value / VOXEL) * VOXEL;
    }

    /** A height brought to the top of the voxel layer nearest it. */
    private static float level(float height) {
        return LAYER + Math.round((height - LAYER) / VOXEL) * VOXEL;
    }

    /**
     * Where a stretch from lo to hi lies on the grid. One thinner than a voxel still takes a whole
     * voxel when it is nearly one wide, as a line of ink does; anything finer is left out.
     */
    private static float[] span(float lo, float hi) {
        float a = snap(lo), b = snap(hi);
        if (a == b) {
            if (hi - lo < 0.75f) return null;
            a = (float) Math.floor(lo / VOXEL) * VOXEL;
            b = a + VOXEL;
        }
        return new float[] {a, b};
    }

    /**
     * A piece built of voxel columns over a rectangle of the grid: each column asked for its height and
     * colour, the tops of a row drawn as one strip where they match, and a wall wherever a column stands
     * above its neighbour, down to the base where there is none. The top voxel of a wall is in the
     * colour of the top; the rest of it is the body, when one is given.
     */
    private static void field(PoseStack pose, WorldCanvas buffers, float x1, float y1, float x2, float y2,
                              float base, int body, Column column) {
        int i1 = (int) Math.floor(x1 / VOXEL), i2 = (int) Math.ceil(x2 / VOXEL);
        int j1 = (int) Math.floor(y1 / VOXEL), j2 = (int) Math.ceil(y2 / VOXEL);
        int width = i2 - i1, depth = j2 - j1;
        if (width <= 0 || depth <= 0) return;
        float[] heights = new float[width * depth];
        int[] colours = new int[width * depth];
        for (int j = 0; j < depth; j++) {
            for (int i = 0; i < width; i++) {
                long value = column.at((i1 + i + 0.5f) * VOXEL, (j1 + j + 0.5f) * VOXEL);
                colours[j * width + i] = (int) value;
                heights[j * width + i] = Float.intBitsToFloat((int) (value >>> 32));
            }
        }
        for (int j = 0; j < depth; j++) {
            float y = (j1 + j) * VOXEL;
            for (int i = 0; i < width; ) {
                int at = j * width + i, colour = colours[at];
                if (colour == 0) {
                    i++;
                    continue;
                }
                int end = i + 1;
                while (end < width && colours[at + end - i] == colour && heights[at + end - i] == heights[at]) end++;
                rawQuad(pose, buffers, (i1 + i) * VOXEL, y, (i1 + end) * VOXEL, y + VOXEL, colour, heights[at]);
                i = end;
            }
        }
        int[][] sides = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        float[] light = {.65f, .8f, .75f, .6f};
        for (int j = 0; j < depth; j++) {
            for (int i = 0; i < width; i++) {
                int colour = colours[j * width + i];
                if (colour == 0) continue;
                float h = heights[j * width + i];
                float xa = (i1 + i) * VOXEL, ya = (j1 + j) * VOXEL, xb = xa + VOXEL, yb = ya + VOXEL;
                for (int side = 0; side < 4; side++) {
                    int ni = i + sides[side][0], nj = j + sides[side][1];
                    boolean inside = ni >= 0 && nj >= 0 && ni < width && nj < depth && colours[nj * width + ni] != 0;
                    float under = inside ? heights[nj * width + ni] : base;
                    if (under >= h) continue;
                    float split = body == 0 ? under : Math.max(under, h - VOXEL);
                    int topShade = shade(colour, light[side]), bodyShade = shade(body == 0 ? colour : body, light[side]);
                    // Wound so each wall faces out of its column: the canvas is mirrored, so outward is clockwise.
                    float[] edge = switch (side) {
                        case 0 -> new float[] {xb, ya, xa, ya};
                        case 1 -> new float[] {xb, yb, xb, ya};
                        case 2 -> new float[] {xa, yb, xb, yb};
                        default -> new float[] {xa, ya, xa, yb};
                    };
                    rawWall(pose, buffers, edge[0], edge[1], edge[2], edge[3], split, h, topShade);
                    if (split > under) rawWall(pose, buffers, edge[0], edge[1], edge[2], edge[3], under, split, bodyShade);
                }
            }
        }
    }

    /** The texture of a voxel face: one voxel in three a shade darker, one in six a shade lighter. */
    private static void speckle(PoseStack pose, WorldCanvas buffers, float x1, float y1, float x2, float y2,
                                float lift, int seed) {
        float[] across = span(x1, x2), deep = span(y1, y2);
        if (across == null || deep == null) return;
        for (float y = deep[0]; y < deep[1] - 0.01f; y += VOXEL) {
            for (float x = across[0]; x < across[1] - 0.01f; x += VOXEL) {
                int h = hash(seed * 7907 + Math.round(x) * 131 + Math.round(y) * 31337) % 6;
                if (h < 2) rawQuad(pose, buffers, x, y, x + VOXEL, y + VOXEL, 0x22000000, lift);
                else if (h == 2) rawQuad(pose, buffers, x, y, x + VOXEL, y + VOXEL, 0x18ffffff, lift);
            }
        }
    }

    private static void cylinder(PoseStack pose, WorldCanvas buffers, float x, float y,
                                 float radius, float bottom, float top, int colour) {
        if (voxels) {
            field(pose, buffers, x - radius - VOXEL, y - radius - VOXEL, x + radius + VOXEL, y + radius + VOXEL, bottom, 0,
                    (cx, cy) -> Math.hypot(cx - x, cy - y) <= radius ? cell(top, colour) : NONE);
            return;
        }
        circle(pose, buffers, x, y, radius, colour, top);
        int steps = WorldCanvas.segments(pose, radius, 40);
        for (int step = 0; step < steps; step++) {
            double a = step * 2 * Math.PI / steps, b = (step + 1) * 2 * Math.PI / steps;
            wall(pose, buffers, x + (float) Math.cos(a) * radius, y + (float) Math.sin(a) * radius,
                    x + (float) Math.cos(b) * radius, y + (float) Math.sin(b) * radius,
                    bottom, top, shade(colour, .68f + .13f * (float) Math.cos(a - .8)));
        }
    }

    /**
     * The grain of a piece of wood lying flat: thin streaks along its length, some darker, some
     * lighter, each starting and ending somewhere of its own. The same seed always grows the same wood.
     */
    private static void grain(PoseStack pose, WorldCanvas buffers, float x1, float y1, float x2, float y2,
                              int colour, float lift, int seed) {
        if (voxels) {
            speckle(pose, buffers, x1, y1, x2, y2, lift, seed);
            return;
        }
        float length = x2 - x1, depth = y2 - y1;
        int lines = Math.max(2, (int) (depth / 1.1f));
        for (int line = 0; line < lines; line++) {
            int h = hash(seed * 131 + line);
            float y = y1 + (line + 0.5f) * depth / lines + ((h & 7) - 3.5f) * 0.06f;
            float start = x1 + (h >> 3 & 15) / 15f * length * 0.35f;
            float end = x2 - (h >> 7 & 15) / 15f * length * 0.35f;
            float width = 0.18f + (h >> 11 & 3) * 0.1f;
            int tone = (h >> 13 & 3) == 0 ? shade(colour, 1.25f) : shade(colour, .74f);
            // A streak wavers a little half way along, as grain does round a knot.
            float mid = (start + end) / 2, sway = ((h >> 15 & 7) - 3.5f) * 0.07f;
            quad(pose, buffers, start, y - width / 2, mid, y + width / 2, tone, lift);
            quad(pose, buffers, mid, y + sway - width / 2, end, y + sway + width / 2, tone, lift);
        }
    }

    /** Brushed metal: fine scratches all one way, barely lighter or darker than the metal. */
    private static void brushed(PoseStack pose, WorldCanvas buffers, float x1, float y1, float x2, float y2,
                                int colour, float lift, int seed) {
        if (voxels) {
            speckle(pose, buffers, x1, y1, x2, y2, lift, seed);
            return;
        }
        int lines = Math.max(1, (int) ((y2 - y1) / 0.55f));
        for (int line = 0; line < lines; line++) {
            int h = hash(seed * 977 + line);
            if ((h & 1) == 0) continue;
            float y = y1 + (line + 0.5f) * (y2 - y1) / lines;
            float start = x1 + (h >> 2 & 7) / 7f * (x2 - x1) * 0.2f, end = x2 - (h >> 5 & 7) / 7f * (x2 - x1) * 0.2f;
            quad(pose, buffers, start, y - 0.07f, end, y + 0.07f, shade(colour, (h >> 8 & 1) == 0 ? 1.12f : .88f), lift);
        }
    }

    /** A line engraved round a rectangle, this thick. */
    private static void outline(PoseStack pose, WorldCanvas buffers, float x1, float y1, float x2, float y2,
                                float line, int colour, float lift) {
        quad(pose, buffers, x1, y1, x2, y1 + line, colour, lift);
        quad(pose, buffers, x1, y2 - line, x2, y2, colour, lift);
        quad(pose, buffers, x1, y1, x1 + line, y2, colour, lift);
        quad(pose, buffers, x2 - line, y1, x2, y2, colour, lift);
    }

    /** A slotted screw head set in a plate. */
    private static void screw(PoseStack pose, WorldCanvas buffers, float x, float y, float radius, int plate, float lift) {
        circle(pose, buffers, x, y, radius, shade(plate, .7f), lift);
        circle(pose, buffers, x - radius * 0.15f, y - radius * 0.15f, radius * 0.7f, shade(plate, 1.1f), lift + 0.01f);
        quad(pose, buffers, x - radius * 0.7f, y - radius * 0.14f, x + radius * 0.7f, y + radius * 0.14f, shade(plate, .5f), lift + 0.02f);
    }

    private static int hash(int value) {
        int h = value * 0x9E3779B9;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h & 0x7fffffff;
    }

    /** A flat coloured rectangle, wound the way the station screen winds its own. */
    private static void quad(PoseStack pose, WorldCanvas buffers, float x1, float y1,
                             float x2, float y2, int colour, float lift) {
        if (voxels) {
            float[] across = span(x1, x2), deep = span(y1, y2);
            if (across != null && deep != null) rawQuad(pose, buffers, across[0], deep[0], across[1], deep[1], colour, lift);
            return;
        }
        rawQuad(pose, buffers, x1, y1, x2, y2, colour, lift);
    }

    private static void rawQuad(PoseStack pose, WorldCanvas buffers, float x1, float y1,
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
