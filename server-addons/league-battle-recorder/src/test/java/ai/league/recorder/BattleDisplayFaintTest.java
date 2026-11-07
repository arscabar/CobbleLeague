package ai.league.recorder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BattleDisplayFaintTest {
    @Test void directFaintDoesNotBecomeEarlierOpponentsKnockout() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|turn|1\n|move|p2a: c|Tackle|p1a: a\n|-damage|p1a: a|90/120\n|move|p1a: a|Self-Destruct|p2a: c\n|faint|p1a: a");
        var rows = XlsxRows.events(r);
        var attack = rows.stream().filter(row -> row.get(3).equals("몸통박치기")).findFirst().orElseThrow();
        assertEquals(30, attack.get(6)); assertEquals("120 → 90 / 120", attack.get(7));
        assertFalse(attack.get(5).toString().contains("기절"));
        var faint = rows.stream().filter(row -> row.get(3).equals("기절")).findFirst().orElseThrow();
        assertTrue(faint.get(4).toString().contains("Alice")); assertNull(faint.get(6));
        assertEquals("90 → 0 / 120", faint.get(7));
    }
}
