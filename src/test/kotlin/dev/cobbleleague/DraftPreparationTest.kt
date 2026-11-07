package dev.cobbleleague
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DraftPreparationTest {
    @Test fun `free lesson is used once only after successful learning`() {
        val prep=DraftPreparation()
        prep.awardFreeLesson()
        assertThrows(IllegalStateException::class.java) { prep.learn(prep.revision,0) { error("learn failed") } }
        assertEquals(1,prep.freeLessons)
        prep.learn(prep.revision,0) {}
        assertEquals(0,prep.freeLessons)
        assertEquals(0,prep.money)
        assertThrows(IllegalArgumentException::class.java) { prep.learn(prep.revision,0) {} }
    }

    @Test fun `one initial reroll is cumulative all rounds add rerolls only losses add money`() {
        val p=DraftPreparation(); assertEquals(1,p.rerolls)
        p.reroll(p.revision,0); assertEquals(0,p.rerolls)
        p.nextRound(false); assertEquals(1,p.rerolls); assertEquals(0,p.money)
        p.nextRound(true); p.nextRound(true); assertEquals(3,p.rerolls); assertEquals(2,p.money)
    }
    @Test fun `minute starts once and reaches ready exactly at deadline`() {
        val p=DraftPreparation(); assertFalse(p.ready(100)); assertEquals(-1,p.seconds(100))
        p.begin(100); p.begin(20_000)
        assertEquals(60_100,p.deadline); assertEquals(1,p.seconds(60_099))
        assertFalse(p.ready(60_099)); assertTrue(p.ready(60_100)); assertFalse(p.editable(60_100))
    }
    @Test fun `confirmation can be cancelled before expiry but not after`() {
        val p=DraftPreparation(); p.begin(0)
        p.confirm(true,p.revision,100); assertTrue(p.ready(100)); assertFalse(p.editable(100))
        p.confirm(false,p.revision,200); assertFalse(p.ready(200)); assertTrue(p.editable(200))
        assertThrows(IllegalArgumentException::class.java) {p.confirm(false,p.revision,60_000)}
    }
    @Test fun `replayed reroll does not consume a second credit`() {
        val p=DraftPreparation();p.nextRound(true); val token=p.revision
        p.reroll(token,0)
        assertThrows(IllegalArgumentException::class.java) {p.reroll(token,0)}
        assertEquals(1,p.rerolls)
    }
    @Test fun `failed purchase cannot debit money and replay cannot apply twice`() {
        val p=DraftPreparation();repeat(3){p.nextRound(true)}
        val token=p.revision
        assertThrows(IllegalStateException::class.java){p.pay(2,token,0){error("invalid item")}}
        assertEquals(3,p.money);assertEquals(token,p.revision)
        var effects=0;p.pay(2,token,0){effects++}
        assertThrows(IllegalArgumentException::class.java){p.pay(1,token,0){effects++}}
        assertEquals(1,effects);assertEquals(1,p.money)
    }
    @Test fun `confirmed and expired states reject purchases`() {
        val p=DraftPreparation();repeat(3){p.nextRound(true)};p.begin(100)
        p.confirm(true,p.revision,200)
        assertThrows(IllegalArgumentException::class.java){p.pay(1,p.revision,300){fail("must not apply")}}
        p.confirm(false,p.revision,400)
        assertThrows(IllegalArgumentException::class.java){p.pay(1,p.revision,60_100){fail("must not apply")}}
        assertEquals(3,p.money)
    }
    @Test fun `new round clears readiness but preserves unspent wallet and rerolls`() {
        val p=DraftPreparation();p.nextRound(true);p.begin(100);p.confirm(true,p.revision,200)
        p.nextRound(false);assertEquals(0,p.deadline);assertFalse(p.confirmed);assertEquals(1,p.money);assertEquals(3,p.rerolls)
    }
    @Test fun `insufficient money cannot execute effect`() {
        val p=DraftPreparation()
        assertThrows(IllegalArgumentException::class.java){p.pay(2,p.revision,0){fail("must not apply")}}
    }
}
