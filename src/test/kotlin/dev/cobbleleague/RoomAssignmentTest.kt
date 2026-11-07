package dev.cobbleleague

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RoomAssignmentTest {
    @Test fun `two participants reuse separate rooms for all twelve rounds`() {
        var first = reusableSelectionRoom(-1, emptySet(), 2)
        var second = reusableSelectionRoom(-1, setOf(first), 2)
        assertEquals(0, first); assertEquals(1, second)
        repeat(12) {
            first = reusableSelectionRoom(first, setOf(second), 2)
            second = reusableSelectionRoom(second, setOf(first), 2)
            assertEquals(0, first); assertEquals(1, second)
        }
    }
    @Test fun `a full selection area never assigns an occupied room`() {
        assertThrows(IllegalStateException::class.java) { reusableSelectionRoom(-1, setOf(0, 1), 2) }
        assertEquals(1, reusableSelectionRoom(11, setOf(0), 2))
    }
}
