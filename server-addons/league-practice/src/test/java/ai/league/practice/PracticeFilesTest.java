package ai.league.practice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PracticeFilesTest {
    @TempDir Path root;
    private Path settings() { return root.resolve("settings.json"); }
    private Path teams() { return root.resolve("teams/beginner.json"); }
    private TeamSpec filled(String id) {
        return new TeamSpec(id, List.of(new PokemonSpec("pikachu", "", "jolly", "static", "",
                List.of("tackle"), new int[]{31,31,31,31,31,31}, new int[6])));
    }
    @Test void bootstrapCreatesTwentyNineEmptyTeamsWithoutOverwriting() throws Exception {
        var files = new PracticeFiles(root); files.bootstrap();
        assertFalse(files.loadSettings().enabled);
        int count = 0;
        for (Tier tier : Tier.values()) {
            var list = files.loadTeams(tier); count += list.size();
            assertTrue(list.stream().allMatch(t -> t.pokemon().isEmpty()));
            assertThrows(IllegalArgumentException.class, () -> files.readyTeams(tier));
        }
        assertEquals(27, count);
        Files.writeString(settings(), "{broken"); files.bootstrap();
        assertEquals("{broken", Files.readString(settings()));
    }
    @Test void oneReadyTierWorksWhileOtherTiersRemainEmpty() throws Exception {
        var files = new PracticeFiles(root); files.bootstrap();
        PracticeJson.save(teams(), List.of(filled("one"), filled("two")));
        assertEquals(2, files.readyTeams(Tier.BEGINNER).size());
        assertThrows(IllegalArgumentException.class, () -> files.readyTeams(Tier.MASTER));
    }
    @Test void missingSlotsAndDuplicateIdsAreRejected() throws Exception {
        var files = new PracticeFiles(root); files.bootstrap();
        PracticeJson.save(teams(), List.of(filled("one")));
        assertThrows(IllegalArgumentException.class, () -> files.readyTeams(Tier.BEGINNER));
        PracticeJson.save(teams(), List.of(filled("one"), filled("one")));
        assertThrows(IllegalArgumentException.class, () -> files.loadTeams(Tier.BEGINNER));
    }
    @Test void validSaveRoundTripsAndInvalidSavePreservesFile() throws Exception {
        var files = new PracticeFiles(root); files.bootstrap();
        var s = files.loadSettings(); s.dimension = "minecraft:the_nether"; files.saveSettings(s);
        assertEquals(s.dimension, files.loadSettings().dimension);
        String before = Files.readString(settings()); s.dimension = "bad dimension";
        assertThrows(IllegalArgumentException.class, () -> files.saveSettings(s));
        assertEquals(before, Files.readString(settings()));
    }
}
