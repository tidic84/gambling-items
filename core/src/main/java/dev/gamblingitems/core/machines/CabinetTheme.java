package dev.gamblingitems.core.machines;

/**
 * The look a dye gives a cabinet. A cabinet stores 0 for its own look, or 1 + the id of the dye it was
 * painted with: the warm dyes make it Vegas, the bright ones neon in their own colour, the neutral ones retro.
 */
public enum CabinetTheme {
    CLASSIC, RETRO, VEGAS, NEON;

    /** The sixteen dye colours by id, from white to black, as the game numbers them. */
    private static final int[] DYES = {0xfff9fffe, 0xfff9801d, 0xffc74ebd, 0xff3ab3da, 0xfffed83d, 0xff80c71f, 0xfff38baa,
            0xff474f52, 0xff9d9d97, 0xff169c9c, 0xff8932b8, 0xff3c44aa, 0xff835432, 0xff5e7c16, 0xffb02e26, 0xff1d1d21};

    public static CabinetTheme of(int stored) {
        if (stored <= 0 || stored > DYES.length) return CLASSIC;
        return switch (stored - 1) {
            case 1, 4, 14 -> VEGAS;                 // orange, yellow, red
            case 0, 7, 8, 12, 15 -> RETRO;          // white, grays, brown, black
            default -> NEON;
        };
    }

    /** The colour of the dye a cabinet was painted with, opaque; white for an unpainted one. */
    public static int colour(int stored) { return stored <= 0 || stored > DYES.length ? 0xffffffff : DYES[stored - 1]; }

    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
}
