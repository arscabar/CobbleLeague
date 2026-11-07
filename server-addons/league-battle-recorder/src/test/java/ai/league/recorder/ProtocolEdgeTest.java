package ai.league.recorder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolEdgeTest {
    @Test void blankPublicSplitDoesNotEatNextTurn() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("update\n|split|p1\n|-private-future|p1\n\n|turn|2");
        assertEquals(2, r.events.getLast().turn());
        assertTrue(r.events.stream().anyMatch(e -> e.raw().contains("-private-future")));
    }
    @Test void trailingBlankPublicDoesNotEatNextPacket() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("update\n|split|p1\n|-private-future|p1\n");
        log.accept("update\n|turn|2"); assertEquals(2, r.events.getLast().turn());
    }
    @Test void futureSightIsNotAttributedToLastUnrelatedMove() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|move|p2a: c|Surf|p1a: a\n|-end|p2a: c|move: Future Sight\n|-damage|p2a: c|0 fnt\n|faint|p2a: c");
        var damage = r.events.stream().filter(e -> e.type().equals("피해")).findFirst().orElseThrow();
        assertEquals("move: Future Sight", damage.move()); assertEquals("", damage.player());
        assertEquals("", r.events.getLast().player());
    }
    @Test void immuneDelayedAttackDoesNotAffectNextMove() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|-end|p2a: c|move: Doom Desire\n|-immune|p2a: c\n|move|p1a: a|Thunderbolt|p2a: c\n|-damage|p2a: c|150/200");
        assertEquals("Thunderbolt", r.events.getLast().move());
        assertEquals("Alice", r.events.getLast().player());
    }
    @Test void unknownExplicitIdCannotCorruptActivePokemonHp() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|-damage|p2a: unknown|10/200");
        assertEquals(200, r.pokemon("c").hp);
        assertTrue(r.events.getLast().type().contains("미확인"));
    }
}
