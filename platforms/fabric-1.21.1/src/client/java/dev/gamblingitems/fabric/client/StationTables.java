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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

/**
 * The felt of a table, drawn across the six blocks it occupies: the mat of a blackjack, the wheel
 * and layout of a roulette, or the board of a bingo. Raised pieces — the rail, the chip racks, the
 * shoe, the rim and turret of the wheel — are stacked quads, so a table reads as an object.
 *
 * <p>Nothing here decides anything: it draws what the block was told. Coordinates are the pixels
 * of the station panel, 128 to a block, so the surface runs from -192 to 192 across and -128 to
 * 128 deep, the player standing at the bottom edge.
 */
public final class StationTables {
    private static final int FELT = 0xff14472c, FELT_EDGE = 0xff0d2e1d, LINE = 0xff2d6b46;
    private static final int WOOD = 0xff4a3524, WOOD_LIGHT = 0xff6b4b32, RAIL = 0xff5a3b22;
    private static final int CARD = 0xfff3f6fb, CARD_BACK = 0xff7a2130, CARD_EDGE = 0xff20262f;
    private static final int RED = 0xffc0392b, BLACK = 0xff1b1f27, GREEN = 0xff1e8f52;
    private static final int TEXT = 0xffe8eff6, GOLD = 0xffffce69, SILVER = 0xffb9c4d0;
    private static final int BLUE = 0xff2b6cb0;
    /** The felt sits on the top face of the table. */
    private static final float HEIGHT = 0.83f;
    /** The surface: three blocks across, two deep, the anchor block at the front middle. */
    private static final float HALF_WIDTH = TableLayout.HALF_WIDTH, HALF_DEPTH = TableLayout.HALF_DEPTH;
    /** The hair of height between two pieces of the same fan, so none of them fight. */
    private static final float SLICE = 0.006f;
    /** How many ticks a card takes to land on the felt. */
    private static final float DEAL_TICKS = 6;

    private StationTables() {}

    /** True when this block is a table: the felt is drawn on its top, not on a wall. */
    public static boolean hasTable(GameStationEntity station) {
        return station.getBlockState().getBlock() instanceof GameTableBlock;
    }

    public static void render(GameStationEntity station, float partialTick, PoseStack pose,
                              MultiBufferSource buffers, Font font) {
        pose.pushPose();
        pose.translate(0.5, HEIGHT, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-station.getBlockState()
                .getValue(GameTableBlock.FACING).toYRot()));
        // The anchor is the front middle block, so the surface reaches half a block further back.
        pose.translate(0, 0, -0.5);
        // Lay the panel flat, keeping the handedness the station screen already uses.
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.scale(1f / 128, -1f / 128, 1f / 128);
        cloth(pose, buffers);
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

    /** The felt itself, with the padded rail a player leans on at the front. */
    private static void cloth(PoseStack pose, MultiBufferSource buffers) {
        quad(pose, buffers, -HALF_WIDTH, -HALF_DEPTH, HALF_WIDTH, HALF_DEPTH, FELT_EDGE, 1);
        quad(pose, buffers, -HALF_WIDTH + 3, -HALF_DEPTH + 3, HALF_WIDTH - 3, HALF_DEPTH - 14, FELT, 2);
        for (int layer = 0; layer < 3; layer++) {
            quad(pose, buffers, -HALF_WIDTH + 3 + layer, HALF_DEPTH - 14 + layer,
                    HALF_WIDTH - 3 - layer, HALF_DEPTH - 3 - layer,
                    layer % 2 == 0 ? RAIL : WOOD_LIGHT, 2 + layer);
        }
    }

    // ---------------------------------------------------------------- blackjack

    private static void blackjack(GameStationEntity station, float partialTick, PoseStack pose,
                                  MultiBufferSource buffers, Font font) {
        // The arc of a real mat, between the dealer and the seats.
        for (int step = 0; step <= 80; step++) {
            double angle = Math.PI * step / 80;
            float x = (float) (Math.cos(angle) * 150);
            float y = (float) (-30 - Math.sin(angle) * 48);
            quad(pose, buffers, x - 2, y - 2, x + 2, y + 2, LINE, 3);
        }
        label(pose, buffers, font, Component.translatable("gui.gamblingitems.blackjack_pays").getString(),
                0, -26, GOLD, 0.6f, 3);
        label(pose, buffers, font, Component.translatable("gui.gamblingitems.blackjack_stands").getString(),
                0, -14, SILVER, 0.5f, 3);
        shoe(pose, buffers);
        discardTray(pose, buffers);
        chipRack(pose, buffers, -176, 0);
        // The betting circles printed along the front of the mat.
        for (int seat = -2; seat <= 2; seat++) {
            circle(pose, buffers, seat * 62, TableLayout.STAKE_Y, 22, LINE, 3);
            circle(pose, buffers, seat * 62, TableLayout.STAKE_Y, 19, FELT, 4);
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
        String dealerLine = Component.translatable("gui.gamblingitems.dealer").getString()
                + score(dealer, dealer.contains("?"));
        label(pose, buffers, font, dealerLine, 0, -118, GOLD, 0.7f, 3);
        cards(pose, buffers, font, dealer, -104, landing);
        cards(pose, buffers, font, hand, 8, landing);
        String name = station.previewPlayer.isEmpty() ? station.playerName : station.previewPlayer;
        String seat = hand.isEmpty() && name.isEmpty()
                ? Component.translatable("gui.gamblingitems.blackjack_ready").getString()
                : (name.isEmpty() ? Component.translatable("gui.gamblingitems.your_hand").getString() : name)
                        + score(hand, false);
        label(pose, buffers, font, seat, 0, 50, TEXT, 0.8f, 3);
    }

    /** The shoe the cards come out of, in the far right corner. */
    private static void shoe(PoseStack pose, MultiBufferSource buffers) {
        for (int layer = 0; layer < 5; layer++) {
            float inset = layer * 2f;
            quad(pose, buffers, 120 + inset, -118 + inset, 176 - inset, -78 - inset,
                    layer % 2 == 0 ? WOOD : WOOD_LIGHT, 3 + layer);
        }
        quad(pose, buffers, 128, -108, 168, -100, CARD, 8);
        quad(pose, buffers, 128, -96, 168, -88, CARD, 8);
    }

    /** The tray the dealt cards are dropped into, on the far left. */
    private static void discardTray(PoseStack pose, MultiBufferSource buffers) {
        quad(pose, buffers, -176, -118, -120, -78, WOOD, 3);
        quad(pose, buffers, -172, -114, -124, -82, 0xff2a1c11, 4);
        quad(pose, buffers, -168, -110, -128, -96, CARD_BACK, 5);
    }

    /** A rack of chips: four colours, each a short stack. */
    private static void chipRack(PoseStack pose, MultiBufferSource buffers, float x, float y) {
        quad(pose, buffers, x - 8, y - 10, x + 8, y + 74, WOOD, 3);
        int[] colours = {RED, BLUE, GREEN, GOLD};
        for (int index = 0; index < colours.length; index++) {
            float top = y - 4 + index * 20;
            for (int layer = 0; layer < 4; layer++) {
                circle(pose, buffers, x, top + 8 - layer * 0.8f, 7 - layer * 0.4f, colours[index], 4 + layer);
            }
        }
    }

    /** One row of cards, centred on the felt. A hidden hole card is drawn face down. */
    private static void cards(PoseStack pose, MultiBufferSource buffers, Font font, String list,
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

    private static void roulette(GameStationEntity station, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, Font font) {
        double now = station.getLevel().getGameTime() + partialTick;
        double spin = station.phase == 2 && station.animationTicks > 0
                ? Math.max(0, Math.min(1, 1 - (station.phaseEnd - now) / station.animationTicks))
                : 1;
        double eased = 1 - Math.pow(1 - spin, 3);
        // A wheel only turns when a round is being played on it; at rest it stands still.
        double wheelAngle = station.phase <= 0 ? 0 : eased * 3 * Math.PI * 2;
        int result = station.targetSlot;
        pose.pushPose();
        pose.translate(-124, -18, 0);
        // The bowl: an apron, a wooden rim, the pockets, and the turret in the middle.
        circle(pose, buffers, 0, 0, 64, WOOD, 3);
        circle(pose, buffers, 0, 0, 58, WOOD_LIGHT, 4);
        circle(pose, buffers, 0, 0, 53, 0xff102b1c, 5);
        for (int pocket = 0; pocket < RouletteWheel.POCKETS; pocket++) {
            int number = RouletteWheel.numberAtPocket(pocket);
            double angle = wheelAngle + pocket * 2 * Math.PI / RouletteWheel.POCKETS;
            blade(pose, buffers, angle, colourOf(number), 62, 30, 6, pocket);
            // A blade is centred on its own angle, so its number is written on that same line.
            label(pose, buffers, font, String.valueOf(number),
                    (float) (Math.cos(angle) * 45), (float) (Math.sin(angle) * 45), TEXT, 0.5f, 7);
        }
        turret(pose, buffers, wheelAngle);
        if (result >= 0 && station.phase > 0) {
            double pocketAngle = RouletteWheel.pocketOf(result) * 2 * Math.PI / RouletteWheel.POCKETS;
            double ballAngle = wheelAngle + pocketAngle - (1 - eased) * 6 * Math.PI * 2;
            double radius = 56 - 14 * eased;
            circle(pose, buffers, (float) (Math.cos(ballAngle) * radius),
                    (float) (Math.sin(ballAngle) * radius), 4, 0xfff8fbff, 12);
        }
        pose.popPose();
        layout(pose, buffers, font, result, station.phase == 3);
    }

    /** The turret in the middle of the bowl: stacked discs, narrowing as they rise. */
    private static void turret(PoseStack pose, MultiBufferSource buffers, double angle) {
        circle(pose, buffers, 0, 0, 22, 0xff3b2a17, 8);
        circle(pose, buffers, 0, 0, 15, GOLD, 9);
        circle(pose, buffers, 0, 0, 9, 0xff8a6b1f, 10);
        circle(pose, buffers, 0, 0, 4, SILVER, 11);
        for (int arm = 0; arm < 4; arm++) {
            double armAngle = angle + arm * Math.PI / 2;
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotation((float) armAngle));
            quad(pose, buffers, 4, -2.5f, 20, 2.5f, SILVER, 11);
            pose.popPose();
        }
    }

    /** The layout on the right of the felt, with the marker standing on the number that came out. */
    private static void layout(PoseStack pose, MultiBufferSource buffers, Font font,
                               int result, boolean settled) {
        float left = TableLayout.LAYOUT_LEFT, top = TableLayout.LAYOUT_TOP;
        float width = TableLayout.ZERO_WIDTH + 12 * TableLayout.CELL_WIDTH;
        quad(pose, buffers, left - 5, top - 5, left + width + 5,
                TableLayout.OUTSIDE_TOP + TableLayout.OUTSIDE_HEIGHT + 5, WOOD, 3);
        // Every printed number is drawn from the same box the server reads a click in.
        for (int number = 0; number < RouletteWheel.POCKETS; number++) {
            float[] box = TableLayout.numberBox(number);
            cellOf(pose, buffers, font, box[0], box[1], box[2], box[3], number, result, settled);
        }
        String[] outside = {"1-18", "EVEN", "RED", "BLACK", "ODD", "19-36"};
        for (int index = 0; index < outside.length; index++) {
            float[] box = TableLayout.outsideBox(index);
            int colour = switch (outside[index]) {
                case "RED" -> RED;
                case "BLACK" -> BLACK;
                default -> LINE;
            };
            quad(pose, buffers, box[0], box[1], box[0] + box[2], box[1] + box[3], colour, 4);
            label(pose, buffers, font, outsideLabel(outside[index]), box[0] + box[2] / 2 - 0.5f,
                    box[1] + box[3] / 2, TEXT, 0.42f, 5);
        }
    }

    /** The even money bets read in the language of the player, not in the code. */
    private static String outsideLabel(String area) {
        return switch (area) {
            case "RED" -> Component.translatable("gui.gamblingitems.colour.red").getString();
            case "BLACK" -> Component.translatable("gui.gamblingitems.colour.black").getString();
            case "EVEN" -> Component.translatable("gui.gamblingitems.bet.even").getString();
            case "ODD" -> Component.translatable("gui.gamblingitems.bet.odd").getString();
            default -> area;
        };
    }

    private static void cellOf(PoseStack pose, MultiBufferSource buffers, Font font, float x, float y,
                               float width, float height, int number, int result, boolean settled) {
        quad(pose, buffers, x, y, x + width, y + height, colourOf(number), 4);
        label(pose, buffers, font, String.valueOf(number), x + width / 2, y + height / 2, TEXT, 0.5f, 5);
        if (!settled || number != result) return;
        // The marker a croupier stands on the winning number.
        circle(pose, buffers, x + width / 2, y + height / 2, Math.min(width, height) / 2 - 1, GOLD, 5);
        circle(pose, buffers, x + width / 2, y + height / 2, Math.min(width, height) / 2 - 3, 0xff8a6b1f, 6);
        circle(pose, buffers, x + width / 2, y + height / 2, Math.min(width, height) / 2 - 5, GOLD, 7);
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
                              MultiBufferSource buffers, Font font) {
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
        label(pose, buffers, font, Component.translatable("gui.gamblingitems.bingo_drum").getString(),
                cupX, cupY + 34, SILVER, 0.5f, 3);
        // One line per card, in front of the board rather than across it.
        String[] lines = station.publicBets.isEmpty()
                ? new String[] {Component.translatable("gui.gamblingitems.bingo_waiting").getString()}
                : station.publicBets.split("\n");
        for (int index = 0; index < lines.length && index < 3; index++) {
            label(pose, buffers, font, lines[index], 96, 18 + index * 18, TEXT, 0.55f, 3);
        }
    }

    // ---------------------------------------------------------------- playing on the felt

    /**
     * The controls printed along the edge the player leans on. They are the same actions as the
     * panel of a station, read from the same geometry the block reads a click in, so what is
     * drawn here is exactly what answers.
     */
    private static void controls(GameStationEntity station, PoseStack pose, MultiBufferSource buffers,
                                 Font font) {
        int hovered = hovered(station);
        for (StationPanel.Control control : TableLayout.controls(station.mode(), station.phase)) {
            int colour = switch (control.label()) {
                case "red" -> 0xff943d4b;
                case "black" -> 0xff303542;
                case "green", "play", "cash_out" -> 0xff216e5b;
                default -> 0xff293d52;
            };
            if (hovered == control.action()) {
                quad(pose, buffers, control.x() - 1, control.y() - 1,
                        control.x() + control.width() + 1, control.y() + control.height() + 1, GOLD, 5);
                colour = 0xff41607a;
            }
            quad(pose, buffers, control.x(), control.y(), control.x() + control.width(),
                    control.y() + control.height(), colour, 6);
            String text = Component.translatable("gui.gamblingitems.panel." + control.label()).getString();
            float scale = Math.min(0.55f, (control.width() - 6f) / Math.max(1, font.width(text)));
            label(pose, buffers, font, text, control.x() + control.width() / 2f,
                    control.y() + control.height() / 2f, TEXT, scale, 7);
        }
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
    private static void stakes(GameStationEntity station, PoseStack pose, MultiBufferSource buffers) {
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
            pose.pushPose();
            pose.translate(x, centreY, 8);
            pose.scale(22, -22, 4);
            net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(stack,
                    ItemDisplayContext.GUI, 0xf000f0,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers,
                    station.getLevel(), 0);
            pose.popPose();
        }
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
        return new ItemStack(BuiltInRegistries.ITEM.get(key), count);
    }

    // ---------------------------------------------------------------- drawing

    /**
     * One pocket of the wheel, drawn as a rotated blade so the wheel really looks round.
     * Neighbouring blades overlap near the middle, so each gets a hair of its own height:
     * two coloured surfaces sharing a plane are what makes a model flicker.
     */
    private static void blade(PoseStack pose, MultiBufferSource buffers, double angle, int colour,
                              float outer, float inner, float lift, int index) {
        double half = Math.PI / RouletteWheel.POCKETS;
        float width = (float) (2 * outer * Math.sin(half));
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotation((float) angle));
        quad(pose, buffers, inner, -width / 2, outer, width / 2, colour, lift + index * SLICE);
        pose.popPose();
    }

    /** A filled circle, drawn as a fan of blades around its middle, each on its own hair. */
    private static void circle(PoseStack pose, MultiBufferSource buffers, float centreX, float centreY,
                               float radius, int colour, float lift) {
        int steps = 40;
        pose.pushPose();
        pose.translate(centreX, centreY, 0);
        for (int step = 0; step < steps; step++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotation((float) (step * 2 * Math.PI / steps)));
            float width = (float) (2 * radius * Math.sin(Math.PI / steps)) + 0.4f;
            quad(pose, buffers, 0, -width / 2, radius, width / 2, colour, lift + step * SLICE);
            pose.popPose();
        }
        pose.popPose();
    }

    /** A flat coloured rectangle, wound the way the station screen winds its own. */
    private static void quad(PoseStack pose, MultiBufferSource buffers, float x1, float y1,
                             float x2, float y2, int colour, float lift) {
        var vertices = buffers.getBuffer(RenderType.gui());
        var matrix = pose.last().pose();
        vertices.addVertex(matrix, x1, y1, lift).setColor(colour);
        vertices.addVertex(matrix, x1, y2, lift).setColor(colour);
        vertices.addVertex(matrix, x2, y2, lift).setColor(colour);
        vertices.addVertex(matrix, x2, y1, lift).setColor(colour);
    }

    /**
     * Writes on the felt. The lift is the layer the text belongs to, plus a hair: printed on its
     * card or its pocket, not floating above the table.
     */
    private static void label(PoseStack pose, MultiBufferSource buffers, Font font, String text,
                              float x, float y, int colour, float scale, float lift) {
        if (text.isEmpty()) return;
        pose.pushPose();
        pose.translate(x, y, lift + 0.4f);
        pose.scale(scale, scale, scale);
        // Half a line above the middle, so a number sits in its cell whatever its size.
        font.drawInBatch(text, -font.width(text) / 2f, -4.5f, colour, false, pose.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, 0xF000F0);
        pose.popPose();
    }
}
