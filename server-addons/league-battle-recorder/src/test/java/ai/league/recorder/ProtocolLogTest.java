package ai.league.recorder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolLogTest {
    @Test void usesPrivateHpOnceAcrossPackets() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("update\n|move|p1a: a|Thunderbolt|p2a: c\n|split|p2\n|-damage|p2a: c|160/200");
        log.accept("update\n|-damage|p2a: c|80/100\n|upkeep");
        assertEquals(160, r.pokemon("c").hp);
        var hit = r.events.stream().filter(e -> e.type().equals("피해")).toList();
        assertEquals(1, hit.size()); assertEquals(40, hit.getFirst().damage());
        assertEquals("Alice", hit.getFirst().player());
    }
    @Test void switchesSameSpeciesAndKeepsIndividualHp() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|-damage|p1a: a|90/120\n|switch|p1a: b|Pikachu, L50|130/130");
        var e = r.events.getLast();
        assertEquals("b", e.pokemonId()); assertTrue(e.withdrawn().contains("[a] 90/120"));
        assertTrue(e.sentOut().contains("[b] 130/130")); assertEquals(90, r.pokemon("a").hp);
    }
    @Test void statusDamageDoesNotBecomeLastMoveDamage() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|move|p1a: a|Thunderbolt|p2a: c\n|-damage|p2a: c|190/200\n|-damage|p1a: a|110/120|[from] brn");
        var e = r.events.getLast();
        assertEquals("", e.move()); assertEquals("", e.player()); assertEquals("화상", e.reason());
        assertEquals(10, e.damage());
    }
    @Test void knockoutAndHealAreSeparateAndEndIsIdempotent() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|move|p1a: a|Thunderbolt|p2a: c\n|-damage|p2a: c|0 fnt\n|faint|p2a: c\n|-heal|p1a: a|120/120|[from] item: Leftovers");
        var ko = r.events.stream().filter(e -> e.type().equals("기절")).findFirst().orElseThrow();
        assertEquals("Alice", ko.player()); assertEquals("c", ko.targetId());
        assertNull(r.events.getLast().damage());
        log.finish("Alice 승리", ""); int size = r.events.size();
        log.finish("Bob 승리", ""); log.accept("|turn|9");
        assertEquals(size, r.events.size()); assertEquals("Alice 승리", r.result);
    }
}
