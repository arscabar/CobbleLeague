package ai.league.recorder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HpAccuracyTest {
    @Test void distinguishesToxicUntilKnockoutAndCure() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|-status|p2a: c|tox\n|-damage|p2a: c|180/200 tox|[from] psn");
        assertEquals("맹독", r.events.getLast().reason());
        log.accept("|-damage|p2a: c|0 fnt|[from] psn");
        assertEquals("맹독", r.events.getLast().reason());
        log.accept("|-curestatus|p2a: c|tox"); assertEquals("", r.pokemon("c").status);
    }
    @Test void maximumChangesAreNotHealingOrDamage() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|-heal|p1a: a|240/240|[silent]\n|-heal|p1a: a|120/120|[silent]");
        assertEquals("최대 HP 변경", r.events.getLast().type());
        assertNull(r.events.getLast().damage()); assertEquals(120, r.pokemon("a").hp);
        assertEquals(0, r.events.stream().filter(e -> e.type().equals("회복")).count());
    }
    @Test void powderDoesNotCreditAttemptedFireMove() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|move|p2a: c|Flamethrower|p1a: a\n|-activate|p2a: c|move: Powder\n|-damage|p2a: c|150/200|[silent]");
        assertEquals("move: Powder", r.events.getLast().move());
        assertEquals("", r.events.getLast().player());
    }
    @Test void inconsistentDamageIsFlaggedInsteadOfSilentlyZero() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|-damage|p2a: c|100/200\n|-damage|p2a: c|110/200");
        assertEquals("HP 검증 필요", r.events.getLast().type());
        assertNull(r.events.getLast().damage()); assertEquals(110, r.pokemon("c").hp);
    }
    @Test void hpLessIllusionRevealKeepsHp() {
        var r = BattleFixtures.record(); var log = BattleFixtures.started(r);
        log.accept("|replace|p1a: a|Zoroark, L50");
        assertEquals("모습 공개", r.events.getLast().type());
        assertEquals(120, r.pokemon("a").hp);
    }
}
