package dev.gamblingitems.fabric.client;

import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.block.GameStationEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The arcade's chiptune, looping softly from the nearest cabinet while the player is among them. It
 * follows the jukebox slider and can be turned off in config/gamblingitems/client.json ("ambientMusic").
 */
final class AmbientMusic extends AbstractTickableSoundInstance {
    private static final SoundEvent MUSIC = SoundEvent.createVariableRangeEvent(ModContent.id("music.arcade"));
    /** After this long without a cabinet drawn nearby, the music fades out. */
    private static final int LINGER = 200;
    private static AmbientMusic current;
    private static long heardAt;
    private static double heardX, heardY, heardZ, heardDistance = Double.MAX_VALUE;
    private float level;

    private AmbientMusic() {
        super(MUSIC, SoundSource.RECORDS, RandomSource.create());
        looping = true;
        delay = 0;
        attenuation = SoundInstance.Attenuation.LINEAR;
        volume = 0.01f;
        x = heardX;
        y = heardY;
        z = heardZ;
    }

    /** A cabinet was drawn: the music plays on, from the nearest one. */
    static void heard(GameStationEntity station) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !ClientOptions.ambientMusic()) return;
        long now = minecraft.level.getGameTime();
        var pos = station.getBlockPos();
        double distance = minecraft.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        if (now != heardAt) heardDistance = Double.MAX_VALUE;
        heardAt = now;
        if (distance < heardDistance) {
            heardDistance = distance;
            heardX = pos.getX() + 0.5;
            heardY = pos.getY() + 1.5;
            heardZ = pos.getZ() + 0.5;
        }
        if (distance < 24 * 24 && (current == null || current.isStopped())) {
            current = new AmbientMusic();
            minecraft.getSoundManager().play(current);
        }
    }

    @Override public void tick() {
        var minecraft = Minecraft.getInstance();
        boolean near = minecraft.level != null && minecraft.level.getGameTime() - heardAt < LINGER && ClientOptions.ambientMusic();
        level = near ? Math.min(1, level + 0.02f) : level - 0.02f;
        if (level <= 0 && !near) {
            stop();
            return;
        }
        volume = Math.max(0.001f, level * 0.45f * ClientOptions.soundVolume());
        x = heardX;
        y = heardY;
        z = heardZ;
    }
}
