package dev.gamblingitems.fabric.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Frame-rate measure of the casino floor: builds the gallery's room, keeps every cabinet playing, and
 * times the frames from a few places, the furniture in view, up close, and behind the player's back.
 * Writes run/gamblingitems-bench.txt, then quits. Only active with -Dgamblingitems.clientsmoke=true and
 * -Dgamblingitems.clientsmoke.bench=true.
 */
public final class SmokeBench {
    public static final boolean ENABLED = Boolean.getBoolean("gamblingitems.clientsmoke.bench");
    private static final int START = 60, SETTLE = 60, MEASURE = 400, REPLAY = 100;
    /** A place to measure from, relative to the gallery's origin. */
    private record Scene(String name, double x, double y, double z, float yaw, float pitch) {}
    private static final Scene[] SCENES = {
            new Scene("floor", 0.5, 4, 14, 180, 18),
            new Scene("close", -6, 1, 2, 180, 4),
            new Scene("away", 0.5, 4, 14, 0, 18)};
    private static final StringBuilder REPORT = new StringBuilder();
    private static final long[] frames = new long[1 << 16], furnitureFrames = new long[1 << 16];
    private static int frameCount;
    private static long lastFrame, furnitureNanos, frameFurniture, measureStart;
    private static boolean measuring;

    private SmokeBench() {}

    /** A frame is starting; called through WorldCanvas.frame. */
    static void frame() {
        long now = System.nanoTime();
        if (measuring && lastFrame != 0 && frameCount < frames.length) {
            furnitureFrames[frameCount] = frameFurniture;
            frames[frameCount++] = now - lastFrame;
        }
        frameFurniture = 0;
        lastFrame = now;
    }

    /** Time spent drawing the casino's furniture. */
    static void furniture(long nanos) {
        if (!measuring) return;
        furnitureNanos += nanos;
        frameFurniture += nanos;
    }

    static void tick(Minecraft minecraft, int ticks) {
        var server = minecraft.getSingleplayerServer();
        var id = minecraft.player.getUUID();
        if (ticks == 20) server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) return;
            SmokeGallery.build(player);
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        if (ticks < START) return;
        minecraft.options.hideGui = true;
        minecraft.options.pauseOnLostFocus = false;
        minecraft.options.framerateLimit().set(260);
        minecraft.options.enableVsync().set(false);
        minecraft.getWindow().updateVsync(false);
        if (minecraft.screen instanceof net.minecraft.client.gui.screens.PauseScreen) minecraft.setScreen(null);
        int length = SETTLE + MEASURE, step = (ticks - START) / length, phase = (ticks - START) % length;
        if (step >= SCENES.length) {
            write(minecraft);
            minecraft.stop();
            return;
        }
        Scene scene = SCENES[step];
        if (phase % REPLAY == 0) server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) return;
            var origin = SmokeGallery.origin();
            if (phase == 0) player.connection.teleport(origin.getX() + scene.x(), origin.getY() + scene.y(), origin.getZ() + scene.z(),
                    scene.yaw(), scene.pitch());
            SmokeGallery.playAll(player);
        });
        if (phase > 1) {
            minecraft.player.setYRot(scene.yaw());
            minecraft.player.setXRot(scene.pitch());
        }
        if (phase == SETTLE) {
            frameCount = 0;
            furnitureNanos = 0;
            lastFrame = 0;
            measureStart = System.nanoTime();
            measuring = true;
        }
        if (phase == length - 1) {
            measuring = false;
            report(scene.name(), System.nanoTime() - measureStart);
        }
    }

    private static void report(String scene, long elapsed) {
        long[] sorted = Arrays.copyOf(frames, frameCount);
        Arrays.sort(sorted);
        double seconds = elapsed / 1e9;
        double average = frameCount / seconds;
        // 1 % low: the frame rate of the slowest hundredth of the frames.
        int slow = Math.max(1, frameCount / 100);
        double slowest = 0;
        for (int index = frameCount - slow; index < frameCount; index++) slowest += sorted[index];
        double low = slow / (slowest / 1e9);
        double worst = frameCount == 0 ? 0 : sorted[frameCount - 1] / 1e6;
        double median = frameCount == 0 ? 0 : sorted[frameCount / 2] / 1e6;
        double furniture = frameCount == 0 ? 0 : furnitureNanos / 1e6 / frameCount;
        // The furniture's share of the slowest hundredth of the frames: whether the stutters are ours.
        Integer[] byLength = new Integer[frameCount];
        for (int index = 0; index < frameCount; index++) byLength[index] = index;
        Arrays.sort(byLength, (a, b) -> Long.compare(frames[b], frames[a]));
        double slowFurniture = 0;
        for (int index = 0; index < slow && index < frameCount; index++) slowFurniture += furnitureFrames[byLength[index]];
        slowFurniture = slowFurniture / 1e6 / slow;
        String line = String.format(java.util.Locale.ROOT,
                "%-6s frames %6d  fps avg %7.1f  1%% low %7.1f  median %6.2f ms  worst %7.2f ms  furniture %6.3f ms/frame, %6.3f ms in the slowest 1%%",
                scene, frameCount, average, low, median, worst, furniture, slowFurniture);
        REPORT.append(line).append('\n');
        org.slf4j.LoggerFactory.getLogger("gamblingitems").info("BENCH {}", line);
    }

    private static void write(Minecraft minecraft) {
        try {
            Files.writeString(minecraft.gameDirectory.toPath().resolve("gamblingitems-bench.txt"), REPORT, StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            org.slf4j.LoggerFactory.getLogger("gamblingitems").warn("Cannot write the bench report: {}", exception.getMessage());
        }
    }
}
