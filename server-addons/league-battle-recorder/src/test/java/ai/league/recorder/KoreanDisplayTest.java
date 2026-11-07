package ai.league.recorder;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KoreanDisplayTest {
    private BattleRecord record() {
        var r = new BattleRecord(UUID.randomUUID(), Instant.now(), "Toxic", "Bob", "리그", "minecraft:overworld");
        r.party.add(new PokemonState("p1", "Toxic", 1, "one", "Throh", "Storm Throw", 50, "Storm Throw, Toxic", 120, 120));
        r.party.add(new PokemonState("p1", "Toxic", 2, "two", "Pikachu", "Toxic", 50, "Thunderbolt", 100, 100));
        r.party.add(new PokemonState("p2", "Bob", 1, "three", "Snorlax", "", 50, "Splash", 200, 200));
        return r;
    }

    @Test void typedNamesTranslateWhilePlayersNicknamesAndNumbersStayOriginal() {
        var r = record(); var log = new ProtocolLog(r);
        log.accept("|switch|p1a: one|Throh|120/120\n|switch|p2a: three|Snorlax|200/200\n|turn|1\n|move|p1a: one|Storm Throw|p2a: three\n|-crit|p2a: three\n|-damage|p2a: three|170/200\n|switch|p1a: two|Pikachu|100/100");
        log.finish("Toxic 승리", "Storm Throw는 별명 / Toxic 승리");
        var rows = XlsxRows.events(r);
        var hit = rows.stream().filter(row -> row.get(3).equals("업어후리기")).findFirst().orElseThrow();
        assertEquals("Toxic · 던지미 (Storm Throw)", hit.get(2));
        assertEquals("Bob · 잠만보", hit.get(4));
        assertEquals(30, hit.get(6)); assertEquals("200 → 170 / 200", hit.get(7));
        assertEquals("Toxic HP (직후)", rows.getFirst().get(8));
        var change = rows.stream().filter(row -> row.get(3).equals("교체")).toList().getLast();
        assertEquals("Toxic", change.get(2));
        assertTrue(change.get(5).toString().contains("던지미 (Storm Throw)"));
        assertTrue(change.get(5).toString().contains("피카츄 (Toxic)"));
        assertTrue(rows.getLast().get(5).toString().contains("Toxic 승리"));
        assertTrue(rows.getLast().get(5).toString().contains("Storm Throw는 별명"));
        var party = XlsxRows.parties(r).get(11);
        assertEquals("Toxic", party.get(0)); assertEquals("던지미", party.get(2));
        assertEquals("Storm Throw", party.get(3)); assertTrue(party.get(5).toString().contains("업어후리기"));
    }

    @Test void structuredLabelsTranslateInSwitchAndMultipleHpWithoutTouchingNicknames() {
        var text = new DisplayText(record());
        assertEquals("던지미 (Storm Throw)(90/120)", text.switched("Throh (Storm Throw) [one] 90/120"));
        assertEquals("던지미 (Storm Throw) 90/120; 피카츄 (Toxic) 100/100",
                text.hp("p1a Throh (Storm Throw) 90/120; p1b Pikachu (Toxic) 100/100"));
        assertEquals("던지미 (Storm Throw)", text.clean("p1a: one"));
        assertEquals("Toxic · 던지미 (Storm Throw)", text.person("Toxic", "Throh (Storm Throw)"));
    }

    @Test void abilitiesAndStandalonePpUseTypedTranslations() {
        var r = record(); var log = new ProtocolLog(r);
        log.accept("|-ability|p1a: one|Mold Breaker\n|-activate|p1a: one|ability: Mold Breaker\n|-activate|p1a: one|item: Metronome\n|pp_update|p1: one|toxic: 10\n|pp_update|p1: one|toxic: 8");
        var rows = XlsxRows.events(r);
        assertTrue(rows.stream().anyMatch(row -> row.get(5).toString().contains("틀깨기")));
        assertTrue(rows.stream().noneMatch(row -> row.get(5).toString().contains("ability:")));
        assertTrue(rows.stream().anyMatch(row -> row.get(5).equals("도구: Metronome")));
        var pp = rows.stream().filter(row -> row.get(3).equals("PP 감소")).findFirst().orElseThrow();
        assertEquals("맹독 PP 10 → 8", pp.get(5));
        assertEquals("Toxic · 던지미 (Storm Throw)", pp.get(4));
    }
}
