package dev.cobbleleague

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AutoDraftStartTest {
    @Test fun `single double and triple wait until both participants finish all required picks`() {
        for (slots in 1..3) {
            val first = DraftState(slots); val second = DraftState(slots)
            repeat(slots) { first.choose(first.version) }
            assertFalse(autoDraftStartReady(first, second, true))
            repeat(slots) { index ->
                second.choose(second.version)
                assertEquals(index == slots - 1, autoDraftStartReady(first, second, true))
            }
        }
    }
    @Test fun `each next round starts after exactly one additional selection per participant`() {
        val first = DraftState(1); val second = DraftState(1)
        repeat(12) {
            first.choose(first.version, if (first.teamSize == 6) 0 else -1)
            assertFalse(autoDraftStartReady(first, second, true))
            second.choose(second.version, if (second.teamSize == 6) 0 else -1)
            assertTrue(autoDraftStartReady(first, second, true))
            first.fight(); second.fight()
            assertFalse(autoDraftStartReady(first, second, true))
            first.finishRound(RoundOutcome.WIN); second.finishRound(RoundOutcome.LOSS)
            assertFalse(autoDraftStartReady(first, second, true))
        }
        assertTrue(first.completed); assertTrue(second.completed)
    }
    @Test fun `ready participants stay queued while the only arena is occupied`() {
        val first = DraftState(1); val second = DraftState(1)
        first.choose(first.version); second.choose(second.version)
        assertFalse(autoDraftStartReady(first, second, false))
        assertTrue(autoDraftStartReady(first, second, true))
    }
}
