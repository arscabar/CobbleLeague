package dev.cobbleleague

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class RulesTest {
    @org.junit.jupiter.api.Test fun `void attempt preserves round picks roster and score`() {
        val state=DraftState(1);state.choose(state.version);state.fight()
        state.retryBattle()
        org.junit.jupiter.api.Assertions.assertEquals(0,state.round)
        org.junit.jupiter.api.Assertions.assertTrue(state.ready)
        org.junit.jupiter.api.Assertions.assertEquals(1,state.teamSize)
        org.junit.jupiter.api.Assertions.assertEquals(0,state.points)
        state.fight();state.finishRound(RoundOutcome.WIN)
        org.junit.jupiter.api.Assertions.assertEquals(1,state.round)
        org.junit.jupiter.api.Assertions.assertEquals(3,state.points)
    }

    @Test fun `game surrender awards opponent victory even at equal or higher forfeiter points`() {
        assertEquals(1, pointsWinner(0,0,0))
        assertEquals(1, pointsWinner(30,3,0))
        assertEquals(0, pointsWinner(3,30,1))
        assertNull(pointsWinner(18,18))
    }
    @Test fun `equal final points are a draw regardless of the numeric score`() {
        assertNull(pointsWinner(0, 0))
        assertNull(pointsWinner(18, 18))
        assertEquals(0, pointsWinner(21, 15))
        assertEquals(1, pointsWinner(15, 21))
    }
    @Test fun `default weights allocate exactly 95 normal 1 legendary 2 mythical 2 paradox`() {
        val weights = SpawnWeights()
        val all = (0..99).map(weights::category)
        assertEquals(95, all.count { it == null })
        assertEquals(1, all.count { it == Rarity.LEGENDARY })
        assertEquals(2, all.count { it == Rarity.MYTHICAL })
        assertEquals(2, all.count { it == Rarity.PARADOX })
        assertThrows(IllegalArgumentException::class.java) { SpawnWeights(normal = 100).validate() }
    }
    @Test fun `only special species receive ground aura`() {
        assertEquals(Rarity.LEGENDARY, Rarity.fromLabels(setOf("legendary")))
        assertEquals(Rarity.MYTHICAL, Rarity.fromLabels(setOf("MYTHICAL")))
        assertEquals(Rarity.PARADOX, Rarity.fromLabels(setOf("paradox")))
        assertNull(Rarity.fromLabels(setOf("starter")))
    }
    @Test fun `replayed offer cannot grant extra pokemon`() {
        val run = DraftState(2)
        val old = run.version
        run.choose(old)
        assertThrows(IllegalArgumentException::class.java) { run.choose(old) }
        assertEquals(1, run.teamSize)
        run.choose(run.version)
        assertTrue(run.ready)
        assertThrows(IllegalArgumentException::class.java) { run.choose(run.version) }
    }
    @Test fun `full party requires explicit valid replacement`() {
        val run = DraftState(1)
        repeat(6) { run.choose(run.version); run.fight(); run.finishRound(RoundOutcome.WIN) }
        assertThrows(IllegalArgumentException::class.java) { run.choose(run.version) }
        run.choose(run.version, 3)
        assertEquals(6, run.teamSize)
    }
    @Test fun `last victory completes run and disallows next pick`() {
        val run = DraftState(1, 2)
        repeat(2) { run.choose(run.version); run.fight(); run.finishRound(RoundOutcome.WIN) }
        assertTrue(run.completed)
        assertThrows(IllegalArgumentException::class.java) { run.choose(run.version) }
    }
    @Test fun `occupied arena cannot be reused before release`() {
        val pool = ArenaPool(2)
        val a = UUID.randomUUID(); val b = UUID.randomUUID(); val c = UUID.randomUUID()
        assertEquals(0, pool.acquire(a)); assertEquals(1, pool.acquire(b)); assertNull(pool.acquire(c))
        pool.release(a); assertEquals(0, pool.acquire(c))
        assertThrows(IllegalArgumentException::class.java) { pool.acquire(c) }
    }
    @Test fun `single battle starts after one pick and each battle grants exactly one new pick`() {
        val run = DraftState(1)
        run.choose(run.version)
        assertTrue(run.ready); assertEquals(1, run.teamSize)
        assertThrows(IllegalArgumentException::class.java) { run.choose(run.version) }
        run.fight()
        assertThrows(IllegalArgumentException::class.java) { run.choose(run.version) }
        run.finishRound(RoundOutcome.LOSS)
        assertEquals(1, run.remainingPicks); assertFalse(run.ready)
        assertThrows(IllegalArgumentException::class.java) { run.fight() }
        run.choose(run.version)
        assertTrue(run.ready); assertEquals(2, run.teamSize)
        assertThrows(IllegalArgumentException::class.java) { run.choose(run.version) }
    }
    @Test fun `doubles and triples require only the starting minimum then one extra per round`() {
        for (minimum in 2..3) {
            val run = DraftState(minimum)
            repeat(minimum) { run.choose(run.version) }
            assertTrue(run.ready); assertEquals(minimum, run.teamSize)
            run.fight(); run.finishRound(RoundOutcome.WIN)
            assertEquals(1, run.requiredPicks)
            run.choose(run.version)
            assertTrue(run.ready); assertEquals(minimum + 1, run.teamSize)
        }
    }
    @Test fun `wins draws and losses accumulate points and duplicate results are rejected`() {
        val run = DraftState(1, 4)
        val results = listOf(RoundOutcome.WIN, RoundOutcome.LOSS, RoundOutcome.DRAW, RoundOutcome.WIN)
        results.forEach { result ->
            run.choose(run.version); run.fight(); run.finishRound(result)
            assertThrows(IllegalArgumentException::class.java) { run.finishRound(result) }
        }
        assertEquals(7, run.points); assertEquals(2, run.wins)
        assertEquals(1, run.draws); assertEquals(1, run.losses)
        assertEquals(0, run.remainingPicks); assertTrue(run.completed)
    }
}
