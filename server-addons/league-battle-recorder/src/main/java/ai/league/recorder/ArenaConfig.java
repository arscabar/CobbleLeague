package ai.league.recorder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class ArenaConfig {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    public boolean enabled = false;
    public String arenaName = "league", dimension = "minecraft:overworld";
    public double[] min = null, max = null;

    public static ArenaConfig load(Path file, Logger logger) {
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.toAbsolutePath().getParent());
                Files.writeString(file, JSON.toJson(new ArenaConfig()), StandardOpenOption.CREATE_NEW);
                return new ArenaConfig();
            }
            String source = Files.readString(file);
            // Gson's object decoder accepts lenient syntax, so validate the stream first.
            try (JsonReader reader = new JsonReader(new StringReader(source))) {
                reader.setLenient(false);
                reader.skipValue();
                if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing JSON data");
            }
            ArenaConfig config = JSON.fromJson(source, ArenaConfig.class);
            if (config == null || !config.valid()) throw new IOException("Invalid arena configuration");
            return config;
        } catch (IOException | RuntimeException error) {
            logger.warn("Battle recording disabled; configuration was not changed: {}", file, error);
            return new ArenaConfig();
        }
    }

    public boolean valid() {
        if (arenaName == null || arenaName.isBlank() || dimension == null
                || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")
                || min == null || max == null || min.length != 3 || max.length != 3) return false;
        for (int i = 0; i < 3; i++) {
            if (!Double.isFinite(min[i]) || !Double.isFinite(max[i]) || min[i] > max[i]) return false;
        }
        return true;
    }

    public boolean contains(String world, double x, double y, double z) {
        return enabled && valid() && dimension.equals(world)
                && x >= min[0] && x <= max[0] && y >= min[1] && y <= max[1]
                && z >= min[2] && z <= max[2];
    }
}
