package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.machines.MachineTable;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.arcade.VideoPoker;
import dev.gamblingitems.fabric.block.GameStationEntity;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Each cabinet's own sounds, heard by everyone around it from where it stands: its play starting, the
 * tick of what moves in it (reels, pegs, crank, hooves), then a win or a loss. They follow what the
 * block shows, so every player hears the same play at the same moment.
 */
final class CabinetSounds {
    private static final String[] KINDS = {"start", "tick", "win", "lose"};
    private static final Map<GameMode, SoundEvent[]> EVENTS = new EnumMap<>(GameMode.class);

    private CabinetSounds() {}

    private static SoundEvent event(GameMode mode, int kind) {
        return EVENTS.computeIfAbsent(mode, key -> {
            SoundEvent[] events = new SoundEvent[KINDS.length];
            for (int index = 0; index < KINDS.length; index++) {
                events[index] = SoundEvent.createVariableRangeEvent(ModContent.id("machine." + key.id() + "." + KINDS[index]));
            }
            return events;
        })[kind];
    }

    static void follow(GameStationEntity station, double now, double progress, boolean playing) {
        GameMode mode = station.mode();
        if (station.startedAt != station.soundedStart) {
            // Only a play running now is heard: a cabinet coming into view does not replay its last one.
            station.soundedStart = station.startedAt;
            station.soundedStep = -1;
            station.soundedReveal = !playing || station.startedAt == 0;
            if (playing && station.startedAt != 0 && !(mode == GameMode.VIDEO_POKER && station.phase == VideoPoker.DRAWN)) {
                play(station, mode, 0, 1, 0.8f);
            }
        }
        if (playing && progress < 1) {
            long step = step(mode, now - station.startedAt, progress);
            if (step >= 0 && step != station.soundedStep) {
                station.soundedStep = step;
                float pitch = mode == GameMode.LUCKY_WHEEL || mode == GameMode.BIG_SIX ? 1 + (float) progress * 0.3f
                        : 0.9f + (float) ((step * 7919) % 5) * 0.05f;
                play(station, mode, 1, pitch, 0.45f);
            }
        }
        // A video poker hand only ends at the draw; the deal leaves it waiting for the player.
        boolean ends = mode != GameMode.VIDEO_POKER || station.phase == VideoPoker.DRAWN;
        if (!station.soundedReveal && progress >= 1 && ends) {
            station.soundedReveal = true;
            if (!station.idle()) play(station, mode, station.highlight ? 2 : 3, 1, 0.9f);
        }
    }

    /** Which tick of the moving part the play is at; a new one sounds once. -1 when nothing ticks. */
    private static long step(GameMode mode, double age, double progress) {
        return switch (mode) {
            case SLOT_MACHINE -> (long) (age / 2);
            // A wheel slows down: the pegs go by further and further apart.
            case LUCKY_WHEEL, BIG_SIX -> (long) (Math.sqrt(Math.max(0, 1 - Math.pow(1 - progress, 2))) * 60);
            case PACHINKO -> (long) (progress * MachineTable.PACHINKO_ROWS);
            case PLINKO -> (long) (progress * MachineTable.PLINKO_ROWS);
            case COIN_PUSHER -> (long) (age / 6);
            case PRIZE_CLAW -> (long) (age / 4);
            case CAPSULE_GACHA -> progress < 0.45 ? (long) (age / 3) : -1;
            case HIGH_STAKES -> (long) (age / Math.max(2, 8 - progress * 6));
            case VIDEO_POKER -> age < 4 ? -1 : Math.min(4, (long) ((age - 4) / 3));
            case HORSE_RACE -> (long) (age / 3);
            default -> -1;
        };
    }

    private static void play(GameStationEntity station, GameMode mode, int kind, float pitch, float volume) {
        float scaled = volume * ClientOptions.soundVolume();
        if (scaled <= 0 || station.getLevel() == null) return;
        var pos = station.getBlockPos();
        station.getLevel().playLocalSound(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, event(mode, kind),
                SoundSource.BLOCKS, scaled, pitch, false);
    }
}
