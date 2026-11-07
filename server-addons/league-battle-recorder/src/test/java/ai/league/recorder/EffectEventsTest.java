package ai.league.recorder;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EffectEventsTest {
    private BattleTimeline timeline() {
        var record = new BattleRecord(UUID.randomUUID(), Instant.now(), "A", "B", "League", "overworld");
        record.party.add(new PokemonState("p1", "A", 1, "one", "Ditto", "", 50, "Transform", 100, 100));
        record.party.add(new PokemonState("p2", "B", 1, "two", "Espeon", "Mirror", 50, "Psychic", 140, 140));
        return new BattleTimeline(record);
    }

    private BattleEvent apply(BattleTimeline t, String line) {
        EffectEvents.handle(t, line.split("\\|", -1), line);
        return t.record.events.getLast();
    }

    @Test void transformPreservesIdentityAndDoesNotInventDamage() {
        var t = timeline();
        var event = apply(t, "|-transform|p1a: one|p2a: two");
        assertEquals("one", event.pokemonId());
        assertEquals("two", event.targetId());
        assertEquals("Ditto", event.pokemon());
        assertNull(event.damage());
        assertEquals(100, t.record.pokemon("one").hp);
    }

    @Test void magicBounceIsExplicitAndPreservesEvidence() {
        var t = timeline();
        String line = "|-activate|p2a: two|ability: Magic Bounce|[of] p1a: one";
        var event = apply(t, line);
        assertEquals("매직미러 반사", event.type());
        assertEquals("two", event.pokemonId());
        assertEquals("one", event.targetId());
        assertEquals(line, event.raw());
    }

    @Test void statusAndRanksUseReadableNames() {
        var t = timeline();
        assertTrue(apply(t, "|-status|p1a: one|brn").reason().contains("화상"));
        assertEquals("상태이상 해제", apply(t, "|-curestatus|p1a: one|brn").type());
        assertTrue(apply(t, "|-boost|p1a: one|atk|2").reason().contains("공격 +2단계"));
        assertTrue(apply(t, "|-unboost|p1a: one|spe|1").reason().contains("스피드 -1단계"));
    }

    @Test void substituteAndFutureMessagesAreNeverDropped() {
        var t = timeline();
        assertEquals("대타 생성", apply(t, "|-start|p1a: one|Substitute").type());
        assertEquals("대타 종료", apply(t, "|-end|p1a: one|Substitute").type());
        String line = "|-future-effect|p2a: two|unknown value|[from] future source";
        var event = apply(t, line);
        assertTrue(event.reason().contains("unknown value [from] future source"));
        assertEquals(line, event.raw());
        assertNull(event.damage());
        assertEquals(3, t.record.events.size());
    }
}
