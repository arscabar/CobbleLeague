package dev.cobbleleague

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

data class ReturnPoint(val dimension: String, val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float) {
    fun validate() {
        require(dimension.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")))
        require(x.isFinite() && y.isFinite() && z.isFinite() && yaw.isFinite() && pitch.isFinite())
        require(kotlin.math.abs(x) <= 30_000_000 && kotlin.math.abs(z) <= 30_000_000)
    }
}

/** New snapshots become visible in memory only after the durable write succeeds. */
class ReturnJournal(private val file: Path, private val writer: (Path, String) -> Unit = ::atomicWrite) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var entries = linkedMapOf<UUID, ReturnPoint>()
    init {
        if (Files.exists(file)) {
            try {
                val parsed = linkedMapOf<UUID, ReturnPoint>()
                JsonParser.parseString(Files.readString(file)).asJsonObject.entrySet().forEach { (id, value) ->
                    val point = requireNotNull(gson.fromJson(value, ReturnPoint::class.java)); point.validate()
                    parsed[UUID.fromString(id)] = point
                }
                entries = parsed
            } catch (e: Exception) { throw IOException("Could not read return journal; original file retained: $file", e) }
        }
    }
    operator fun get(id: UUID): ReturnPoint? = entries[id]
    fun record(points: Map<UUID, ReturnPoint>) {
        val updated = LinkedHashMap(entries)
        points.forEach { (id, point) -> point.validate(); updated.putIfAbsent(id, point) }
        commit(updated)
    }
    /** Call only after teleport has succeeded; write failure retains the recovery entry. */
    fun complete(id: UUID) { commit(LinkedHashMap(entries).also { it.remove(id) }) }
    private fun commit(updated: LinkedHashMap<UUID, ReturnPoint>) {
        if (updated == entries) return
        val json = JsonObject(); updated.forEach { (id, point) -> json.add(id.toString(), gson.toJsonTree(point)) }
        writer(file, gson.toJson(json))
        entries = updated
    }
    companion object {
        private fun atomicWrite(file: Path, content: String) {
            Files.createDirectories(file.toAbsolutePath().parent)
            val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
            FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { channel ->
                val bytes = StandardCharsets.UTF_8.encode(content)
                while (bytes.hasRemaining()) channel.write(bytes)
                channel.force(true)
            }
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING) }
        }
    }
}

/** Detach every participant before doing external work; isolate each fallible effect. */
fun <T> cleanupParticipants(
    members: List<T>, detach: (T) -> Unit, discard: (T) -> Unit, unlock: (T) -> Unit,
    restore: (T) -> Unit, notify: (T) -> Unit, failed: (String, Exception) -> Unit
) {
    fun step(name: String, member: T, operation: (T) -> Unit) {
        try { operation(member) } catch (e: Exception) { runCatching { failed(name, e) } }
    }
    members.forEach { step("detach", it, detach) }
    members.forEach { member ->
        step("discard", member, discard); step("unlock", member, unlock)
        step("restore", member, restore); step("notify", member, notify)
    }
}

fun insideLeague(sameDimension: Boolean, x: Double, y: Double, z: Double): Boolean =
    sameDimension && x.isFinite() && y.isFinite() && z.isFinite() &&
        x in -390.0..390.0 && z in -390.0..390.0 && y in 40.0..256.0

fun performRecordedReturn(journal: ReturnJournal?, id: UUID, teleport: () -> Unit) {
    teleport()
    journal?.complete(id)
}
