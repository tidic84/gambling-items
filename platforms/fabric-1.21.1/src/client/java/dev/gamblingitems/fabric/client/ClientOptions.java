package dev.gamblingitems.fabric.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.gamblingitems.fabric.platform.Platform;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This player's own preferences, in config/gamblingitems/client.json. They never reach the
 * server. A file that cannot be read is left untouched and the defaults are used instead.
 */
public final class ClientOptions {
    /** Multiplies every casino sound; the vanilla UI sounds they use otherwise follow the master slider only. */
    private static float soundVolume = 1;
    /** Plays settle at once: the result is shown without the wheel, reels or ball moving. */
    private static boolean reducedMotion;
    private static boolean loaded;

    private ClientOptions() {}

    public static float soundVolume() {
        load();
        return soundVolume;
    }

    public static boolean reducedMotion() {
        load();
        return reducedMotion;
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Path path = Platform.configDir().resolve("gamblingitems").resolve("client.json");
        try {
            JsonObject json = Files.exists(path) ? JsonParser.parseString(Files.readString(path)).getAsJsonObject() : new JsonObject();
            if (json.has("soundVolume")) soundVolume = Math.max(0, Math.min(1, json.get("soundVolume").getAsFloat()));
            if (json.has("reducedMotion")) reducedMotion = json.get("reducedMotion").getAsBoolean();
            // A missing option is added with its default; the ones already set are written back unchanged.
            if (!json.has("soundVolume") || !json.has("reducedMotion")) {
                json.addProperty("soundVolume", soundVolume);
                json.addProperty("reducedMotion", reducedMotion);
                Files.createDirectories(path.getParent());
                Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger("gamblingitems").warn("Cannot read {}, using defaults: {}", path, exception.getMessage());
        }
    }
}
