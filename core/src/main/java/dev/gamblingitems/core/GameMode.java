package dev.gamblingitems.core;

/** Stable identifiers for configuration and future session records; never persist enum ordinals. */
public enum GameMode {
    UPGRADER("upgrader"),
    CRASH("crash"),
    TRADE_UP("trade_up"),
    CASE_OPENING("case_opening"),
    ROULETTE("roulette"),
    CASE_BATTLE("case_battle");

    private final String id;

    GameMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}

