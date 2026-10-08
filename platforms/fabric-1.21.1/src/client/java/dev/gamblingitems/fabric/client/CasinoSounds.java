package dev.gamblingitems.fabric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** The few sounds of the casino, played for this player only: a reel ticking, a win, a loss. */
public final class CasinoSounds {
    private CasinoSounds() {}

    public static void tick(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), pitch, .25f));
    }

    public static void win() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2f, .5f));
    }

    public static void lose() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), .5f, .5f));
    }

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
