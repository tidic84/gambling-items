package dev.gamblingitems.fabric.vault;

import dev.gamblingitems.fabric.crash.CrashSettings;
import dev.gamblingitems.fabric.roulette.RouletteSettings;

/** One saved container per game and per player. Identifiers are stored, never ordinals. */
public enum VaultSection {
    UPGRADER("upgrader", 2),
    TRADE_UP("trade_up", 6),
    CASE_OPENING("case_opening", 2),
    CRASH("crash", CrashSettings.VAULT_SIZE),
    ROULETTE("roulette", RouletteSettings.VAULT_SIZE),
    BLACKJACK("blackjack", dev.gamblingitems.fabric.blackjack.BlackjackSettings.VAULT_SIZE),
    CASE_BATTLE("case_battle", dev.gamblingitems.fabric.battle.BattleSettings.VAULT_SIZE),
    BINGO("bingo", dev.gamblingitems.fabric.bingo.BingoSettings.VAULT_SIZE),
    SLOT_MACHINE("slot_machine", dev.gamblingitems.fabric.slots.SlotSettings.VAULT_SIZE);

    private final String id;
    private final int size;

    VaultSection(String id, int size) {
        this.id = id;
        this.size = size;
    }

    public String id() { return id; }
    public int size() { return size; }

    public static VaultSection fromId(String id) {
        for (VaultSection section : values()) {
            if (section.id.equals(id)) return section;
        }
        throw new IllegalArgumentException("Unknown vault section: " + id);
    }
}
