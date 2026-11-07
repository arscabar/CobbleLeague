package ai.league.practice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SettingsJsonTest {
    @TempDir Path root;
    @Test void rejectsMalformedOrIncompleteCoordinateJsonAndPreservesInput() throws Exception {
        var files = new PracticeFiles(root); files.bootstrap();
        Path path = root.resolve("settings.json");
        for (String source : new String[]{"{", "null", "{/*comment*/}", "{\"enabled\":\"true\"}",
                "{\"buttons\":{\"beginner\":{}}}", "{\"spawn\":{\"x\":0,\"y\":64,\"z\":0}}"}) {
            Files.writeString(path, source);
            assertThrows(IOException.class, files::loadSettings);
            assertEquals(source, Files.readString(path));
        }
    }
}
