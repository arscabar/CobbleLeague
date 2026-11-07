package ai.league.recorder;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleDisplayTest {
    private List<List<?>> data(BattleRecord r) { return XlsxRows.events(r).subList(1, XlsxRows.events(r).size()); }

    @Test void criticalMergesIntoOneHitAndMultiHitsRemainSeparate() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|turn|1\n|move|p1a: a|Thunderbolt|p2a: c\n|-crit|p2a: c\n|-damage|p2a: c|160/200\n|-damage|p2a: c|130/200");
        var hits = data(r).stream().filter(row -> row.get(3).equals("10만볼트")).toList();
        assertEquals(2, hits.size()); assertEquals(40, hits.get(0).get(6)); assertEquals(30, hits.get(1).get(6));
        assertTrue(hits.get(0).get(5).toString().contains("급소"));
        assertFalse(hits.get(1).get(5).toString().contains("급소"));
        assertTrue(data(r).stream().noneMatch(row -> row.get(3).equals("급소")));
    }

    @Test void markersBelongToTheirTargetAndMissIsNotADamageHit() {
        var r = BattleFixtures.record();
        r.party.add(new PokemonState("p2", "Bob", 2, "d", "Charizard", "", 50, "Surf", 150, 150));
        var log = BattleFixtures.started(r);
        log.accept("|turn|1\n|move|p1a: a|Surf|p2a: c\n|-crit|p2b: d\n|-damage|p2a: c|190/200\n|-damage|p2b: d|100/150\n|move|p1a: a|Tackle|p2a: c\n|-miss|p1a: a|p2a: c");
        var surf = data(r).stream().filter(row -> row.get(3).equals("파도타기")).toList();
        assertFalse(surf.get(0).get(5).toString().contains("급소"));
        assertTrue(surf.get(1).get(5).toString().contains("급소"));
        var miss = data(r).stream().filter(row -> row.get(3).equals("몸통박치기")).findFirst().orElseThrow();
        assertEquals("빗나감", miss.get(5)); assertEquals(0, miss.get(6));
    }

    @Test void statusTargetAndSwitchAreReadableWithoutEngineFields() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|turn|1\n|move|p2a: c|Toxic|p1a: a\n|-status|p1a: a|tox\n|switch|p1a: b|Pikachu, L50|130/130");
        var rows = data(r);
        var toxic = rows.stream().filter(row -> row.get(3).equals("맹독")).findFirst().orElseThrow();
        assertTrue(toxic.get(4).toString().contains("Alice"));
        assertTrue(toxic.get(5).toString().contains("맹독 부여"));
        var change = rows.getLast(); assertEquals("교체", change.get(3));
        assertTrue(change.get(5).toString().contains("120/120) →"));
        assertTrue(rows.stream().allMatch(row -> row.size() == 10));
        assertTrue(rows.stream().noneMatch(row -> row.toString().contains("p1a:") || row.toString().contains("[a]")));
        assertFalse(XlsxRows.events(r).getFirst().contains("원본 메시지"));
    }
}
