package ai.league.recorder;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleDisplayEdgesTest {
    @Test void knockoutKeepsDamageHpRangeAndPoisonIsNotMoveDamage() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|turn|1\n|move|p2a: c|Toxic|p1a: a\n|-status|p1a: a|tox\n|upkeep\n|-damage|p1a: a|110/120 tox|[from] psn\n|turn|2\n|move|p1a: a|Thunderbolt|p2a: c\n|-damage|p2a: c|0 fnt\n|faint|p2a: c");
        var rows = XlsxRows.events(r);
        var poison = rows.stream().filter(row -> row.get(3).equals("맹독 피해")).findFirst().orElseThrow();
        assertEquals("", poison.get(2)); assertTrue(poison.get(4).toString().contains("Alice"));
        assertEquals(10, poison.get(6)); assertEquals("120 → 110 / 120", poison.get(7));
        var hit = rows.stream().filter(row -> row.get(3).equals("10만볼트")).findFirst().orElseThrow();
        assertEquals(200, hit.get(6)); assertEquals("200 → 0 / 200", hit.get(7));
        assertTrue(hit.get(5).toString().contains("기절"));
    }

    @Test void criticalWithoutHpChangeStaysWithItsMoveAndNeverLeaks() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|turn|1\n|move|p1a: a|Tackle|p2a: c\n|-crit|p2a: c\n|-activate|p2a: c|Substitute|[damage]\n|turn|2\n|move|p1a: a|Thunderbolt|p2a: c\n|-damage|p2a: c|180/200");
        var rows = XlsxRows.events(r);
        var protectedHit = rows.stream().filter(row -> row.get(3).equals("몸통박치기")).findFirst().orElseThrow();
        assertTrue(protectedHit.get(5).toString().contains("급소"));
        assertNull(protectedHit.get(6));
        var next = rows.stream().filter(row -> row.get(3).equals("10만볼트")).findFirst().orElseThrow();
        assertFalse(next.get(5).toString().contains("급소")); assertEquals(20, next.get(6));
    }

    @Test void unknownEffectsAndHpCorrectionsStayReadableWithoutIds() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|turn|1\n|-future-effect|p2a: c|unknown detail|[from] future source");
        var rows = XlsxRows.events(r);
        var effect = rows.stream().filter(row -> row.get(3).equals("기타 효과")).findFirst().orElseThrow();
        assertTrue(effect.get(5).toString().contains("unknown detail"));
        assertFalse(effect.toString().contains("p2a:"));
        assertFalse(effect.toString().contains("[from]"));
        assertTrue(rows.stream().allMatch(row -> row.size() == 10));
    }
}
