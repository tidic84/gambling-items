package dev.gamblingitems.core.machines;

import dev.gamblingitems.core.GameMode;

/**
 * The controls a player can touch on a cabinet itself: the lever, the crank, the buttons. Each is a box
 * in the space of the cabinet's model, in sixteenths of a block: u from the player's left (0) to right
 * (16), y from the floor up across the three blocks, z from the front (0) backwards.
 *
 * <p>Clicks land on the outer faces of the blocks, so every box reaches a little past the part it
 * covers, up to the face the player actually clicks.
 */
public final class CabinetControls {
    public enum Control { NONE, PLAY, NEXT }

    private static final double SLACK = 0.5;

    private CabinetControls() {}

    /** The control at a point of the cabinet, or {@link Control#NONE} where the cabinet is only cabinet. */
    public static Control at(GameMode mode, double u, double y, double z) {
        return switch (mode) {
            // The lever on the right flank, and the big spin button on the deck.
            case SLOT_MACHINE -> inside(u, y, z, 15, 13, 2, 19, 30, 10) || inside(u, y, z, 10.5, 14, -1, 14, 15.5, 1.5)
                    ? Control.PLAY : Control.NONE;
            // The wheel itself, and the red button in front of its column.
            case LUCKY_WHEEL -> inside(u, y, z, -1, 16.5, -1, 17, 34.5, 3) || inside(u, y, z, 6, 15, 3, 10, 16.5, 7)
                    ? Control.PLAY : Control.NONE;
            // The deck with the coin slot.
            case COIN_PUSHER -> inside(u, y, z, 0, 12.5, -1, 16, 15, 4) ? Control.PLAY : Control.NONE;
            // The drop button plays; the joystick moves the claw to the next prize.
            case PRIZE_CLAW -> inside(u, y, z, 10, 13, -2, 14, 15, 1.5) ? Control.PLAY
                    : inside(u, y, z, 2.3, 13, -2, 5.7, 17.5, 1.5) ? Control.NEXT : Control.NONE;
            // The launch handle on the right.
            case PACHINKO -> inside(u, y, z, 13.5, 7, -2.5, 17.5, 13, 4) ? Control.PLAY : Control.NONE;
            // The crank under the globe.
            case CAPSULE_GACHA -> inside(u, y, z, 4.5, 6.5, -3, 11.5, 12.5, 3.2) ? Control.PLAY : Control.NONE;
            // Green engages the credit; red moves to the next target.
            case HIGH_STAKES -> inside(u, y, z, 2, 13, -1, 5.5, 15, 2.6) ? Control.PLAY
                    : inside(u, y, z, 10.5, 13, -1, 14, 15, 2.6) ? Control.NEXT : Control.NONE;
            default -> Control.NONE;
        };
    }

    private static boolean inside(double u, double y, double z, double u1, double y1, double z1,
                                  double u2, double y2, double z2) {
        return u >= u1 - SLACK && u <= u2 + SLACK && y >= y1 - SLACK && y <= y2 + SLACK
                && z >= z1 - SLACK && z <= z2 + SLACK;
    }
}
