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
    SLOT_MACHINE("slot_machine", dev.gamblingitems.fabric.slots.SlotSettings.VAULT_SIZE),
    LUCKY_WHEEL("lucky_wheel", dev.gamblingitems.fabric.machines.MachineSettings.VAULT_SIZE),
    COIN_PUSHER("coin_pusher", dev.gamblingitems.fabric.machines.MachineSettings.VAULT_SIZE),
    PRIZE_CLAW("prize_claw", dev.gamblingitems.fabric.machines.MachineSettings.VAULT_SIZE),
    PACHINKO("pachinko", dev.gamblingitems.fabric.machines.MachineSettings.VAULT_SIZE),
    CAPSULE_GACHA("capsule_gacha", dev.gamblingitems.fabric.machines.MachineSettings.VAULT_SIZE),
    HIGH_STAKES("high_stakes", dev.gamblingitems.fabric.machines.MachineSettings.VAULT_SIZE),
    PLINKO("plinko", dev.gamblingitems.fabric.machines.MachineSettings.VAULT_SIZE),
    BIG_SIX("big_six", dev.gamblingitems.fabric.machines.MachineSettings.VAULT_SIZE),
    VIDEO_POKER("video_poker", dev.gamblingitems.fabric.machines.MachineSettings.VAULT_SIZE),
    HORSE_RACE("horse_race", dev.gamblingitems.fabric.machines.MachineSettings.VAULT_SIZE),
    /** The cashier's exchange tray, where items wait to be changed. */
    CASHIER("cashier", dev.gamblingitems.fabric.arcade.CashierMenu.TRAY_SLOTS);

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
