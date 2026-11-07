package dev.cobbleleague

import com.google.gson.GsonBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class DeckSelectionTest {
    private val a = UUID(0, 1)
    private val b = UUID(0, 2)
    private fun session(mode: NativeLeagueMode) = DeckSelection(a, b, mode, mapOf(a to setOf("a0", "a1"), b to setOf("b0", "b1")))
    private fun ready(mode: NativeLeagueMode) = session(mode).also { it.select(a, "b1"); it.select(b, "a0"); it.confirm(a); it.confirm(b) }

    @Test fun `designated mode keeps ownership and opponent chooses what each player uses`() {
        val s = ready(NativeLeagueMode.PICK)
        assertEquals(DeckAssignment(a, "a0"), s.assignment(a))
        assertEquals(DeckAssignment(b, "b1"), s.assignment(b))
    }
    @Test fun `borrow mode applies the selected opponent deck to the selecting player`() {
        val s = ready(NativeLeagueMode.BORROW)
        assertEquals(DeckAssignment(b, "b1"), s.assignment(a))
        assertEquals(DeckAssignment(a, "a0"), s.assignment(b))
    }
    @Test fun `clicking names does not start until both explicit confirmations arrive`() {
        val s = session(NativeLeagueMode.PICK)
        s.select(a, "b0"); s.select(b, "a1")
        assertFalse(s.ready)
        assertThrows(IllegalStateException::class.java) { s.assignment(a) }
        s.confirm(a); assertFalse(s.ready)
        s.confirm(b); assertTrue(s.ready)
    }
    @Test fun `own unlisted and outsider choices are refused`() {
        val s = session(NativeLeagueMode.BORROW)
        assertThrows(IllegalArgumentException::class.java) { s.select(a, "a0") }
        assertThrows(IllegalArgumentException::class.java) { s.select(a, "missing") }
        assertThrows(IllegalStateException::class.java) { s.select(UUID(0, 9), "b0") }
        assertThrows(IllegalArgumentException::class.java) { s.confirm(a) }
    }
    @Test fun `selection can change before confirmation and is locked afterwards`() {
        val s = session(NativeLeagueMode.PICK)
        s.select(a, "b0"); s.select(a, "b1"); assertEquals("b1", s.selected(a))
        s.confirm(a)
        assertThrows(IllegalArgumentException::class.java) { s.select(a, "b0") }
        assertThrows(IllegalArgumentException::class.java) { s.confirm(a) }
    }
    @Test fun `arena allocation failure can reopen one confirmation for a safe retry`() {
        val s = ready(NativeLeagueMode.BORROW)
        s.reopenAfterStartFailure(b); assertFalse(s.ready); assertTrue(s.isConfirmed(a))
        s.confirm(b); assertTrue(s.ready)
        assertEquals(DeckAssignment(b, "b1"), s.assignment(a))
    }
    @Test fun `engine party verification accepts exactly the selected temporary copies`() {
        verifyBattleParties(mapOf(a to listOf(UUID(1, 1)), b to listOf(UUID(2, 1))),
            mapOf(a to listOf(UUID(1, 1)), b to listOf(UUID(2, 1))))
    }
    @Test fun `wrong engine owner member or ordering blocks the battle`() {
        val x = UUID(1, 1); val y = UUID(1, 2)
        val expected = mapOf(a to listOf(x, y))
        assertThrows(IllegalStateException::class.java) { verifyBattleParties(expected, mapOf(b to listOf(x, y))) }
        assertThrows(IllegalStateException::class.java) { verifyBattleParties(expected, mapOf(a to listOf(y, x))) }
        assertThrows(IllegalStateException::class.java) { verifyBattleParties(expected, mapOf(a to listOf(x))) }
    }
    @Test fun `original party restores ordering and gaps using only safe swaps`() {
        val expected = listOf(a, null, b, null, null, null)
        val current = mutableListOf<UUID?>(null, b, null, a, null, null)
        restoreOriginalPartyOrder(expected, { current.toList() }) { first, second -> val old = current[first]; current[first] = current[second]; current[second] = old }
        assertEquals(expected, current)
    }
    @Test fun `unexpected original party membership is not overwritten or duplicated`() {
        var writes = 0
        assertThrows(IllegalStateException::class.java) { restoreOriginalPartyOrder(listOf(a, null), { listOf(b, null) }) { _, _ -> writes++ } }
        assertEquals(0, writes)
    }
    @Test fun `interrupted reorder can be retried without replacing Pokemon`() {
        val expected = listOf(a, b, null)
        val current = mutableListOf<UUID?>(b, null, a)
        var writes = 0
        assertThrows(IllegalStateException::class.java) {
            restoreOriginalPartyOrder(expected, { current.toList() }) { first, second ->
                val old = current[first]; current[first] = current[second]; current[second] = old
                if (++writes == 1) error("injected swap callback failure")
            }
        }
        restoreOriginalPartyOrder(expected, { current.toList() }) { first, second -> val old = current[first]; current[first] = current[second]; current[second] = old }
        assertEquals(expected, current)
    }
    @Test fun `export actual Kotlin assignments for independent real engine checks`() {
        val catalogues = mapOf(
            a.toString() to mapOf("a0" to listOf("Charizard", "Garchomp", "Lucario", "Togekiss", "Ferrothorn", "Rotom-Wash"),
                "a1" to listOf("Venusaur", "Torkoal", "Arcanine", "Whimsicott", "Scizor", "Dragonite")),
            b.toString() to mapOf("b0" to listOf("Pelipper", "Swampert", "Kingdra", "Scizor", "Ferrothorn", "Dragonite"),
                "b1" to listOf("Metagross", "Salamence", "Milotic", "Gardevoir", "Snorlax", "Arcanine")))
        val fixtures = listOf(NativeLeagueMode.PICK, NativeLeagueMode.BORROW).flatMap { mode ->
            listOf("singles", "doubles", "triples").map { format ->
                val s = ready(mode)
                mapOf("mode" to mode.wireName, "format" to format,
                    "assignments" to listOf(a, b).associate { it.toString() to s.assignment(it) })
            }
        }
        val report = mapOf("first" to a, "second" to b, "catalogues" to catalogues,
            "originalParties" to mapOf(a to listOf("Blastoise", "Pikachu"), b to listOf("Gengar", "Machamp")), "fixtures" to fixtures)
        val target = Path.of("build/reports/selected-decks/fixtures.json")
        Files.createDirectories(target.parent)
        Files.writeString(target, GsonBuilder().setPrettyPrinting().create().toJson(report))
        assertEquals(6, fixtures.size)
    }
}
