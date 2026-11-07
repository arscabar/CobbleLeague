package ai.league.recorder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolCoverageTest {
    @Test void unknownAndMalformedEventsAreNotDropped() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|-damage|p2a: c|badHP\n|-futureevent|p1a: a|new mechanic\n|turn|2");
        assertTrue(r.events.stream().anyMatch(e -> e.raw().contains("badHP")));
        assertTrue(r.events.stream().anyMatch(e -> e.raw().contains("new mechanic")));
        assertEquals(2, r.events.getLast().turn());
        log.finish("중단", "접속 종료"); assertTrue(r.endReason.contains("원문 보존"));
    }
    @Test void handlesBothSetHpTargetsAndDynamaxMaximum() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|-sethp|p1a: a|100/240|p2a: c|100/200");
        assertEquals(100, r.pokemon("a").hp); assertEquals(240, r.pokemon("a").maxHp);
        assertEquals(100, r.pokemon("c").hp);
        assertEquals(1, r.events.stream().filter(e -> e.type().equals("HP 조정")).count());
        assertEquals(1, r.events.stream().filter(e -> e.type().equals("최대 HP 변경")).count());
    }
    @Test void distinguishesActualTieAndForcedStop() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|tie"); log.finish("중단", "전투 중단"); assertEquals("무승부", r.result);
        var r2 = BattleFixtures.record(); var log2 = BattleFixtures.started(r2);
        log2.accept("|tie"); log2.finish("중단", "접속 종료"); assertEquals("중단", r2.result);
    }
    @Test void reflectedMoveAndPositionSwapAreVisible() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|move|p1a: a|Thunder Wave|p2a: c|[from] ability: Magic Bounce\n|switch|p1b: b|Pikachu, L50|130/130\n|swap|p1a: a|1");
        assertTrue(r.events.stream().anyMatch(e -> e.type().contains("매직미러")));
        assertTrue(r.events.getLast().player1Hp().contains("p1a Pikachu (둘째)"));
        assertTrue(r.events.getLast().player1Hp().contains("p1b Pikachu (첫째)"));
    }
}
