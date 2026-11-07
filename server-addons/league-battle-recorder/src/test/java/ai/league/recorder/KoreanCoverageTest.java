package ai.league.recorder;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class KoreanCoverageTest {
    @Test void coversAllStandardSpeciesMovesAndAbilities() {
        var en = KoCatalog.read("en_us"); var ko = KoCatalog.read("ko_kr");
        int species = 0, moves = 0, abilities = 0;
        for (String key : en.keySet()) {
            String actual;
            if (key.matches("cobblemon\\.species\\.[^.]+\\.name")) {
                actual = KoreanNames.species(en.get(key).getAsString()); species++;
                if (key.contains(".nidoranf.")) actual = actual.replace("♀", "");
                if (key.contains(".nidoranm.")) actual = actual.replace("♂", "");
            } else if (key.matches("cobblemon\\.move\\.[^.]+")) {
                actual = KoreanNames.move(en.get(key).getAsString()); moves++;
            } else if (key.matches("cobblemon\\.ability\\.[^.]+")) {
                actual = KoreanNames.ability(en.get(key).getAsString()); abilities++;
            } else continue;
            assertEquals(ko.get(key).getAsString(), actual, key);
        }
        assertEquals(1025, species); assertEquals(929, moves); assertEquals(310, abilities);
    }

    @Test void coversEveryBundledBattleForm() throws Exception {
        try (var stream = getClass().getResourceAsStream("/korean-form-cases.json")) {
            assertNotNull(stream);
            var forms = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
            assertEquals(366, forms.size());
            for (var element : forms) {
                String english = element.getAsJsonObject().get("name").getAsString();
                String korean = KoreanNames.species(english);
                assertTrue(korean.codePoints().anyMatch(c -> c >= 0xAC00 && c <= 0xD7A3), english);
                assertEquals(korean, KoreanNames.text(english), "Phrase form " + english);
            }
        }
    }
}
