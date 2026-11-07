package dev.cobbleleague

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class RecoveryTest {
    @TempDir lateinit var directory: Path
    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()
    private val point = ReturnPoint("minecraft:overworld", 12.5, 70.0, -8.5, 90f, 0f)

    @Test fun `restart reloads both participants original return positions`() {
        val file = directory.resolve("returns.json")
        ReturnJournal(file).record(mapOf(first to point, second to point.copy(x = 24.0)))
        val restarted = ReturnJournal(file)
        assertEquals(point, restarted[first]); assertEquals(24.0, restarted[second]!!.x)
    }
    @Test fun `failed durable entry write does not authorize a new return record`() {
        val journal = ReturnJournal(directory.resolve("returns.json")) { _, _ -> throw IOException("disk unavailable") }
        assertThrows(IOException::class.java) { journal.record(mapOf(first to point)) }
        assertNull(journal[first])
    }
    @Test fun `failed completion retains recovery data in memory and on disk`() {
        val file = directory.resolve("returns.json")
        ReturnJournal(file).record(mapOf(first to point))
        val journal = ReturnJournal(file) { _, _ -> throw IOException("disk unavailable") }
        assertThrows(IOException::class.java) { journal.complete(first) }
        assertEquals(point, journal[first]); assertEquals(point, ReturnJournal(file)[first])
    }
    @Test fun `unconfirmed return remains available for later reconnect`() {
        val file = directory.resolve("returns.json")
        val journal = ReturnJournal(file); journal.record(mapOf(first to point))
        assertThrows(IOException::class.java) { performRecordedReturn(journal,first) { throw IOException("teleport failed before completion") } }
        assertEquals(point, ReturnJournal(file)[first])
        performRecordedReturn(journal,first) {}
        assertNull(ReturnJournal(file)[first])
    }
    @Test fun `corrupt journal is rejected without overwriting its bytes`() {
        val file = directory.resolve("returns.json"); val broken = "{ broken journal"
        Files.writeString(file, broken)
        assertThrows(IOException::class.java) { ReturnJournal(file) }
        assertEquals(broken, Files.readString(file))
    }
    @Test fun `repeat recording preserves the first outside origin`() {
        val file = directory.resolve("returns.json"); val journal = ReturnJournal(file)
        journal.record(mapOf(first to point)); journal.record(mapOf(first to point.copy(dimension="cobbleleague:league",x=0.0)))
        assertEquals(point, ReturnJournal(file)[first])
        journal.complete(first); journal.complete(first)
        assertNull(ReturnJournal(file)[first])
    }
    @Test fun `one participant return failure does not prevent other cleanup and unlock`() {
        val participants = mutableSetOf(first,second); val unlocked = mutableSetOf<UUID>(); val restored = mutableSetOf<UUID>()
        val failures = mutableListOf<String>()
        cleanupParticipants(listOf(first,second),
            detach={participants.remove(it)}, discard={}, unlock={unlocked.add(it)},
            restore={ assertTrue(participants.isEmpty()); if(it==first) throw IOException("teleport failed"); restored.add(it) },
            notify={}, failed={step,_ -> failures.add(step)})
        assertTrue(participants.isEmpty()); assertEquals(setOf(first,second),unlocked)
        assertEquals(setOf(second),restored); assertEquals(listOf("restore"),failures)
    }
    @Test fun `entity cleanup and notification failures do not stop other operations`() {
        val operations = mutableListOf<String>()
        cleanupParticipants(listOf(first,second), detach={operations.add("detach:$it")},
            discard={throw IOException("entity error")}, unlock={operations.add("unlock:$it")},
            restore={operations.add("restore:$it")}, notify={throw IOException("connection closed")},
            failed={_,_ -> throw IOException("logger also failed")})
        for(id in listOf(first,second)) { assertTrue("unlock:$id" in operations); assertTrue("restore:$id" in operations) }
    }
    @Test fun `invalid positions are rejected before any durable write`() {
        val journal = ReturnJournal(directory.resolve("returns.json"))
        assertThrows(IllegalArgumentException::class.java) { journal.record(mapOf(first to point.copy(x=Double.NaN))) }
        assertNull(journal[first])
    }
    @Test fun `normal walking is allowed and dimension boundary and void exits are rejected`() {
        assertTrue(insideLeague(true,300.0,65.0,-280.0))
        assertFalse(insideLeague(false,0.0,65.0,0.0)); assertFalse(insideLeague(true,391.0,65.0,0.0))
        assertFalse(insideLeague(true,0.0,39.0,0.0)); assertFalse(insideLeague(true,0.0,65.0,Double.NaN))
    }
}
