package dev.gamblingitems.fabric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/**
 * The few sounds of the casino, played for this player only: a reel ticking, a play starting, a win,
 * a loss. They are the mod's own sound events (assets/gamblingitems/sounds.json), which a resource
 * pack can replace; by default they use vanilla sounds.
 */
public final class CasinoSounds {
    private static final SoundEvent TICK = event("ui.tick"), START = event("ui.start"), WIN = event("ui.win"),
            LOSE = event("ui.lose");

    private CasinoSounds() {}

    private static SoundEvent event(String path) {
        return SoundEvent.createVariableRangeEvent(dev.gamblingitems.fabric.ModContent.id(path));
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        float scaled = volume * ClientOptions.soundVolume();
        if (scaled > 0) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, scaled));
    }

    public static void tick(float pitch) { play(TICK, pitch, .25f); }

    public static void start() { play(START, 1f, .4f); }

    public static void win() { play(WIN, 1.2f, .5f); }

    public static void lose() { play(LOSE, .5f, .5f); }

    /** Ticks each time a new item passes the marker, higher as the reel slows. */
    public static final class Reel {
        private int last = Integer.MIN_VALUE;

        public void follow(double position, double progress) {
            int cell = (int) Math.floor(position + .5);
            if (last != Integer.MIN_VALUE && cell != last && progress < 1) tick(1f + (float) progress * .6f);
            last = cell;
        }

        public void reset() { last = Integer.MIN_VALUE; }
    }
}
