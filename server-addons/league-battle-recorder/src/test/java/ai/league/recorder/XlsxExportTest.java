package ai.league.recorder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class XlsxExportTest {
    @TempDir Path directory;

    private BattleRecord record(String first, String second) {
        BattleRecord r = new BattleRecord(UUID.randomUUID(), Instant.parse("2026-10-26T00:01:02.003Z"), first, second, "리그장", "minecraft:overworld");
        r.party.add(new PokemonState("p1", first, 1, UUID.randomUUID().toString(), "피카츄", "=1+1\u0001<&", 50, "10만볼트", 120, 120));
        r.party.getFirst().hp = 45;
        r.events.add(new BattleEvent(1, 1, "09:01:02", "피해", first, "피카츄", "uuid1", "10만볼트", second, "거북왕", "uuid2", 45, 150, 105, 150, "120/120", "105/150", null, null, "기술", "|-damage|p2a: uuid2|105/150"));
        return r;
    }

    @Test void namesUseKoreanTimeAndNeverOverwrite() throws Exception {
        BattleRecord r = record("Player1", "Player2");
        Path first = XlsxExport.write(directory, r);
        byte[] original = Files.readAllBytes(first);
        Path second = XlsxExport.write(directory, r);
        assertEquals("battle_Player1_Player2_20261026_090102_003.xlsx", first.getFileName().toString());
        assertEquals("battle_Player1_Player2_20261026_090102_003_2.xlsx", second.getFileName().toString());
        assertArrayEquals(original, Files.readAllBytes(first));
    }

    @Test void packageContainsTwoReadableSheetsAndTypedData() throws Exception {
        Path result = XlsxExport.write(directory, record("Alice", "Bob"));
        try (ZipFile zip = new ZipFile(result.toFile())) {
            var parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            var entries = zip.entries();
            while (entries.hasMoreElements()) try (var input = zip.getInputStream(entries.nextElement())) { parser.parse(input); }
            String book = read(zip, "xl/workbook.xml");
            assertTrue(book.indexOf("파티") < book.indexOf("전투기록"));
            String party = read(zip, "xl/worksheets/sheet1.xml");
            assertTrue(party.contains("=1+1&lt;&amp;"));
            assertFalse(party.contains("<f>"));
            assertFalse(party.contains("\u0001"));
            assertTrue(party.contains("<v>120</v>"));
            assertTrue(party.contains("state=\"frozen\""));
            String events = read(zip, "xl/worksheets/sheet2.xml");
            assertTrue(events.contains("기술·행동"));
            assertFalse(events.contains("원본 메시지"));
            assertFalse(events.contains("개체 UUID"));
        }
    }

    @Test void filenamesStayInsideDirectoryAndNullsAreAllowed() throws Exception {
        Path result = XlsxExport.write(directory, record("../A:*?", null));
        assertEquals(directory.toAbsolutePath(), result.toAbsolutePath().getParent());
        assertFalse(result.getFileName().toString().contains("*"));
        assertTrue(result.getFileName().toString().contains("_player_"));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test void invalidOversizedCellsLeaveNoPartialWorkbook() {
        BattleRecord r = record("A", "B");
        r.result = "x".repeat(32768);
        assertThrows(java.io.IOException.class, () -> XlsxExport.write(directory, r));
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
        catch (java.io.IOException e) { fail(e); }
    }

    private String read(ZipFile zip, String entry) throws Exception {
        try (var input = zip.getInputStream(zip.getEntry(entry))) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
    }
}
