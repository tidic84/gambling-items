package dev.gamblingitems.core;

/** Stable identifiers for configuration and future session records; never persist enum ordinals. */
public enum GameMode {
    UPGRADER("upgrader"),
    CRASH("crash"),
    TRADE_UP("trade_up"),
    CASE_OPENING("case_opening"),
    ROULETTE("roulette"),
    CASE_BATTLE("case_battle"),
    BLACKJACK("blackjack"),
    BINGO("bingo"),
    SLOT_MACHINE("slot_machine"),
    // The prize machines; new modes are only ever appended, the tabs of a window count on the order.
    LUCKY_WHEEL("lucky_wheel"),
    COIN_PUSHER("coin_pusher"),
    PRIZE_CLAW("prize_claw"),
    PACHINKO("pachinko"),
    CAPSULE_GACHA("capsule_gacha"),
    HIGH_STAKES("high_stakes"),
    // The arcade: a giant plinko board, a big six wheel, video poker and a horse race.
    PLINKO("plinko"),
    BIG_SIX("big_six"),
    VIDEO_POKER("video_poker"),
    HORSE_RACE("horse_race"),
    // The table games played on a cabinet: craps with real dice, punto banco, keno and scratch cards.
    CRAPS("craps"),
    BACCARAT("baccarat"),
    KENO("keno"),
    SCRATCH_CARD("scratch_card");

    private final String id;

    GameMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Reads a stored identifier. Never read a mode from an enum ordinal. */
    public static GameMode fromId(String id) {
        for (GameMode mode : values()) {
            if (mode.id.equals(id)) return mode;
        }
        throw new IllegalArgumentException("Unknown game mode: " + id);
    }
}

