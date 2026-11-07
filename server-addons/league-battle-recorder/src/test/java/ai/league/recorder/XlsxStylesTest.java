package ai.league.recorder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import static org.junit.jupiter.api.Assertions.*;

class XlsxStylesTest {
    @TempDir Path directory;

    @Test void eventTypesHaveDistinctStylesWithoutChangingLabelsOrHeaders() throws Exception {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|turn|1\n|move|p1a: a|Thunderbolt|p2a: c\n|-crit|p2a: c\n|-damage|p2a: c|160/200\n|move|p2a: c|Surf|p1a: a\n|-damage|p1a: a|0 fnt\n|faint|p1a: a\n|switch|p1a: b|Pikachu, L50|130/130\n|-heal|p2a: c|180/200|[from] item: Leftovers\n|move|p2a: c|Toxic|p1a: b\n|-status|p1a: b|tox");
        try (var zip = new ZipFile(XlsxExport.write(directory, r).toFile())) {
            var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(zip.getInputStream(zip.getEntry("xl/worksheets/sheet2.xml")));
            var cells = doc.getElementsByTagName("c");
            Map<String, Element> byRef = new HashMap<>();
            for (int i = 0; i < cells.getLength(); i++) {
                Element c = (Element) cells.item(i);
                byRef.put(c.getAttribute("r"), c);
            }
            assertEquals("1", byRef.get("D1").getAttribute("s"));
            var rows = XlsxRows.events(r);
            Set<String> observed = new HashSet<>();
            for (int i = 1; i < rows.size(); i++) {
                String action = rows.get(i).get(3).toString(), result = rows.get(i).get(5).toString();
                int style = result.contains("급소") ? 2 : action.equals("교체") ? 5 : result.contains("기절") ? 3 : action.equals("회복") ? 6 : result.contains("맹독 부여") ? 4 : 7;
                assertEquals("" + style, byRef.get("D" + (i + 1)).getAttribute("s"));
                assertEquals(action, byRef.get("D" + (i + 1)).getTextContent());
                assertEquals("" + style, byRef.get("G" + (i + 1)).getAttribute("s"));
                observed.add("" + style);
            }
            assertTrue(observed.containsAll(Set.of("2", "3", "4", "5", "6")));
            String styles = new String(zip.getInputStream(zip.getEntry("xl/styles.xml")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(styles.contains("rgb=\"FF9C0006\"/><b/>"));
            assertTrue(styles.contains("rgb=\"FFFFE3E3\""));
        }
    }
}
