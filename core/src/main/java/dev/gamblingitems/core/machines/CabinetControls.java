package dev.gamblingitems.core.machines;

import dev.gamblingitems.core.GameMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The controls a player can touch on a cabinet itself: the lever, the crank, the buttons. Each is a box
 * in the space of the cabinet's model, in sixteenths of a block: u from the player's left (0) to right
 * (16, or 32 for a cabinet two blocks wide), y from the floor up across the three blocks, z from the
 * front (0) backwards.
 *
 * <p>Clicks land on the outer faces of the blocks, so every box reaches a little past the part it
 * covers, up to the face the player actually clicks.
 */
public final class CabinetControls {
    /** PLAY plays, NEXT moves to the next choice, PICK_n plays or marks the n-th choice (a symbol, a card, a horse). */
    public enum Control {
        NONE, PLAY, NEXT, PICK_1, PICK_2, PICK_3, PICK_4, PICK_5, PICK_6, PICK_7, PICK_8;

        /** The choice a PICK control stands for, from 0; -1 for the others. */
        public int pick() { return ordinal() >= PICK_1.ordinal() ? ordinal() - PICK_1.ordinal() : -1; }

        public static Control pick(int index) { return values()[PICK_1.ordinal() + index]; }
    }

    /** One control and the box it fills, from (u1, y1, z1) to (u2, y2, z2). */
    public record Box(Control control, double u1, double y1, double z1, double u2, double y2, double z2) {
        boolean contains(double u, double y, double z) {
            return u >= u1 - SLACK && u <= u2 + SLACK && y >= y1 - SLACK && y <= y2 + SLACK
                    && z >= z1 - SLACK && z <= z2 + SLACK;
        }
    }

    private static final double SLACK = 0.5;
    /** Video poker: the five cards on the screen and the hold buttons under them. */
    public static final double CARD_U = 1.9, CARD_STEP = 2.5, CARD_WIDTH = 2.3, HOLD_U = 1, HOLD_STEP = 2.1, HOLD_WIDTH = 1.9;
    /** Big six: the seven betting buttons along the deck. */
    public static final double SYMBOL_U = 3, SYMBOL_STEP = 3.8, SYMBOL_WIDTH = 3;
    /** Horse race: one betting button in front of each lane. */
    public static final double LANE_U = 3, LANE_STEP = 4.4, LANE_WIDTH = 3.4;
    /** Craps and baccarat: the betting buttons along the deck, from the player's left; baccarat's tie is in the middle. */
    public static final double BET_U = 1, BET_STEP = 5, BET_WIDTH = 4;

    private CabinetControls() {}

    private static Box bet(Control control, int place) {
        double u = BET_U + place * BET_STEP;
        return new Box(control, u, 13, -1.5, u + BET_WIDTH, 14.8, 1.5);
    }

    /** How many blocks wide a cabinet stands: the giant machines take two. */
    public static int width(GameMode mode) {
        return mode == GameMode.PLINKO || mode == GameMode.BIG_SIX || mode == GameMode.HORSE_RACE ? 2 : 1;
    }

    /** The control at a point of the cabinet, or {@link Control#NONE} where the cabinet is only cabinet. */
    public static Control at(GameMode mode, double u, double y, double z) {
        for (Box box : boxes(mode)) {
            if (box.contains(u, y, z)) return box.control();
        }
        return Control.NONE;
    }

    /** Every control of a cabinet, so a click can be read and a hovered control lit. */
    public static List<Box> boxes(GameMode mode) {
        List<Box> boxes = new ArrayList<>();
        switch (mode) {
            case SLOT_MACHINE -> {
                // The lever on the right flank, and the big spin button on the deck.
                boxes.add(new Box(Control.PLAY, 15, 13, 2, 19, 30, 10));
                boxes.add(new Box(Control.PLAY, 10.5, 14, -1, 14, 15.5, 1.5));
            }
            case LUCKY_WHEEL -> {
                // The wheel itself, and the red button in front of its column.
                boxes.add(new Box(Control.PLAY, -1, 16.5, -1, 17, 34.5, 3));
                boxes.add(new Box(Control.PLAY, 6, 15, 3, 10, 16.5, 7));
            }
            // The deck with the coin slot.
            case COIN_PUSHER -> boxes.add(new Box(Control.PLAY, 0, 12.5, -1, 16, 15, 4));
            case PRIZE_CLAW -> {
                // The drop button plays; the joystick moves the claw to the next prize.
                boxes.add(new Box(Control.PLAY, 10, 13, -2, 14, 15, 1.5));
                boxes.add(new Box(Control.NEXT, 2.3, 13, -2, 5.7, 17.5, 1.5));
            }
            // The launch handle on the right.
            case PACHINKO -> boxes.add(new Box(Control.PLAY, 13.5, 7, -2.5, 17.5, 13, 4));
            // The crank under the globe.
            case CAPSULE_GACHA -> boxes.add(new Box(Control.PLAY, 4.5, 6.5, -3, 11.5, 12.5, 3.2));
            case HIGH_STAKES -> {
                // Green engages the credit; red moves to the next target.
                boxes.add(new Box(Control.PLAY, 2, 13, -1, 5.5, 15, 2.6));
                boxes.add(new Box(Control.NEXT, 10.5, 13, -1, 14, 15, 2.6));
            }
            case PLINKO -> {
                // The big drop button in the middle of the deck; the marquee above is only its name.
                boxes.add(new Box(Control.PLAY, 13.5, 13, -1.5, 18.5, 16, 2));
            }
            case BIG_SIX -> {
                // A betting button per symbol along the deck: the stake goes on that symbol.
                for (int symbol = 0; symbol < 7; symbol++) {
                    double u = SYMBOL_U + symbol * SYMBOL_STEP;
                    boxes.add(new Box(Control.pick(symbol), u, 10, -2, u + SYMBOL_WIDTH, 12.5, 1.5));
                }
            }
            case VIDEO_POKER -> {
                // Each card, on the screen or by its hold button, is kept or let go; the red key deals and draws.
                for (int card = 0; card < 5; card++) {
                    double screen = CARD_U + card * CARD_STEP, hold = HOLD_U + card * HOLD_STEP;
                    // The glass sits back from the front face of the block, which is where a click lands.
                    boxes.add(new Box(Control.pick(card), screen, 19.5, 0, screen + CARD_WIDTH, 27.5, 3));
                    boxes.add(new Box(Control.pick(card), hold, 13, -1.5, hold + HOLD_WIDTH, 14.8, 1.5));
                }
                boxes.add(new Box(Control.PLAY, 12, 13, -1.5, 15.2, 15.2, 1.5));
            }
            case HORSE_RACE -> {
                // One betting button in front of each lane, in the colour of its horse.
                for (int horse = 0; horse < 6; horse++) {
                    double u = LANE_U + horse * LANE_STEP;
                    boxes.add(new Box(Control.pick(horse), u, 12, -2, u + LANE_WIDTH, 14.5, 1.5));
                }
            }
            case CRAPS -> {
                // Pass on the left, don't pass on the right, the dice tray between them.
                boxes.add(bet(Control.PICK_1, 0));
                boxes.add(bet(Control.PICK_2, 2));
            }
            case BACCARAT -> {
                // Player, tie, banker, as on the felt: the tie is the third bet but sits in the middle.
                boxes.add(bet(Control.PICK_1, 0));
                boxes.add(bet(Control.PICK_3, 1));
                boxes.add(bet(Control.PICK_2, 2));
            }
            // The big play key on the right of the deck.
            case KENO, SCRATCH_CARD -> boxes.add(new Box(Control.PLAY, 10.5, 13, -1.5, 15, 15, 1.5));
            default -> {}
        }
        return boxes;
    }
}
