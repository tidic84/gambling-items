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
    private static boolean loaded;

    private ClientOptions() {}

    public static float soundVolume() {
        load();
        return soundVolume;
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Path path = Platform.configDir().resolve("gamblingitems").resolve("client.json");
        try {
            if (!Files.exists(path)) {
                JsonObject json = new JsonObject();
                json.addProperty("soundVolume", soundVolume);
                Files.createDirectories(path.getParent());
                Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
                return;
            }
            JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (json.has("soundVolume")) soundVolume = Math.max(0, Math.min(1, json.get("soundVolume").getAsFloat()));
        } catch (IOException | RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger("gamblingitems").warn("Cannot read {}, using defaults: {}", path, exception.getMessage());
        }
    }
}
