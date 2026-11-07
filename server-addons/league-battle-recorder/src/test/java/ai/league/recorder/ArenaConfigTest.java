package ai.league.recorder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ArenaConfigTest {
    @TempDir Path temp;
    private ArenaConfig config() {
        ArenaConfig c = new ArenaConfig();
        c.enabled = true;
        c.min = new double[]{0, 10, -5};
        c.max = new double[]{20, 30, 5};
        return c;
    }
    private ArenaConfig load(Path file) {
        return ArenaConfig.load(file, LoggerFactory.getLogger(ArenaConfigTest.class));
    }
    private void rejected(String source) throws Exception {
        Path file = temp.resolve("arena.json");
        Files.writeString(file, source);
        assertFalse(load(file).enabled);
        assertEquals(source, Files.readString(file));
    }
    @Test void defaultsAreDisabled() {
        ArenaConfig c = new ArenaConfig();
        assertFalse(c.enabled);
        assertFalse(c.valid());
    }
    @Test void includesBoundaries() {
        ArenaConfig c = config();
        assertTrue(c.contains(c.dimension, 0, 10, -5));
        assertTrue(c.contains(c.dimension, 20, 30, 5));
        assertTrue(c.contains(c.dimension, 10, 20, 0));
    }
    @Test void rejectsOutsidePoints() {
        ArenaConfig c = config();
        assertFalse(c.contains("minecraft:the_nether", 10, 20, 0));
        for (double x : new double[]{-0.1, 20.1, Double.NaN, Double.POSITIVE_INFINITY})
            assertFalse(c.contains(c.dimension, x, 20, 0));
        c.enabled = false;
        assertFalse(c.contains(c.dimension, 10, 20, 0));
    }
    @Test void rejectsInvalidBounds() {
        for (double[] bound : new double[][]{null, {}, {1, 2}, {1, 2, 3, 4},
                {Double.NaN, 10, 0}, {Double.POSITIVE_INFINITY, 10, 0}, {21, 10, 0}}) {
            ArenaConfig c = config();
            c.min = bound;
            assertFalse(c.valid());
        }
    }
    @Test void rejectsDimensions() {
        for (String dimension : new String[]{null, "", "overworld", "Minecraft:overworld", "minecraft:"}) {
            ArenaConfig c = config();
            c.dimension = dimension;
            assertFalse(c.valid());
        }
    }
    @Test void writesDefaults() {
        Path file = temp.resolve("config/arena.json");
        assertFalse(load(file).enabled);
        assertTrue(Files.isRegularFile(file));
    }
    @Test void readsValidFile() throws Exception {
        Path file = temp.resolve("arena.json");
        Files.writeString(file, "{\"enabled\":true,\"min\":[0,10,-5],\"max\":[20,30,5]}");
        assertTrue(load(file).contains("minecraft:overworld", 0, 10, -5));
    }
    @Test void preservesInvalidFiles() throws Exception {
        for (String source : new String[]{"{", "null", "[]", "{/*comment*/}",
                "{\"enabled\":true}", "{} trailing", "{\"enabled\":true,\"min\":[NaN,0,0],\"max\":[1,1,1]}"})
            rejected(source);
    }
}
