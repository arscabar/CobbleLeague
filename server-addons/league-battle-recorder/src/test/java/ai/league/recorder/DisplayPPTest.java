package ai.league.recorder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisplayPPTest {
    @Test void initialRepeatedPpAndClockAreHiddenAndNothingMergesIntoMove() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|t:|1792976848\n|pp_update|p1: a|splash: 40, toxic: 10\n|pp_update|p1: a|splash: 40, toxic: 10\n|turn|1\n|move|p1a: a|Splash|p1a: a\n|-nothing\n|pp_update|p1: a|splash: 39, toxic: 10");
        var rows = XlsxRows.events(r);
        assertEquals(4, rows.size());
        var splash = rows.getLast(); assertEquals("튀어오르기", splash.get(3));
        assertTrue(splash.get(5).toString().contains("효과 없음"));
        assertTrue(splash.get(5).toString().contains("PP 40 → 39"));
        assertTrue(rows.stream().noneMatch(row -> row.toString().contains("1792976848") || row.toString().contains("pp_update")));
    }

    @Test void delayedPpAttachesToPreviousTurnWithoutOverwritingMoveHp() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|pp_update|p1: a|toxic: 10\n|turn|1\n|move|p1a: a|Toxic|p2a: c\n|-status|p2a: c|tox\n|upkeep\n|-damage|p1a: a|110/120|[from] brn\n|turn|2\n|pp_update|p1: a|toxic: 9");
        var toxic = XlsxRows.events(r).stream().filter(row -> row.get(3).equals("맹독")).findFirst().orElseThrow();
        assertTrue(toxic.get(5).toString().contains("PP 10 → 9"));
        assertEquals("120/120", toxic.get(8)); assertEquals("200/200", toxic.get(9));
    }

    @Test void externalOrOldChangesAreStandaloneAndRestorationNeverLooksLikeUse() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|pp_update|p1: a|toxic: 10\n|turn|1\n|move|p1a: a|Toxic|p2a: c\n|pp_update|p1: a|toxic: 9\n|pp_update|p1: a|toxic: 5\n|turn|4\n|pp_update|p1: a|toxic: 3\n|move|p1a: a|Toxic|p2a: c\n|pp_update|p1: a|toxic: 10\n|pp_update|p1: a|toxic: 8");
        var rows = XlsxRows.events(r);
        var toxic = rows.stream().filter(row -> row.get(3).equals("맹독")).findFirst().orElseThrow();
        assertEquals("PP 10 → 9", toxic.get(5));
        assertEquals(3, rows.stream().filter(row -> row.get(3).equals("PP 감소")).count());
        var lastUse = rows.stream().filter(row -> row.get(3).equals("맹독")).toList().getLast();
        assertEquals("", lastUse.get(5));
        var restore = rows.stream().filter(row -> row.get(3).equals("PP 회복")).findFirst().orElseThrow();
        assertTrue(restore.get(4).toString().contains("Alice")); assertNull(restore.get(6));
    }
}
