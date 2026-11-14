package dev.cobbleleague

import com.google.gson.JsonParser
import net.fabricmc.loader.api.Version

/** Shared manifest contract; legacy pairs deliberately leave an installed Spire alone. */
internal fun clientReleasePlan(body: ByteArray, installed: Map<String, Pair<String, String?>>, offline: Boolean): String? {
    val manifest = JsonParser.parseString(String(body, Charsets.UTF_8).trimStart('\uFEFF')).asJsonObject
    check(manifest.get("schema").asInt == 1 && manifest.get("minecraft").asString == "1.21.1" && manifest.get("cobblemon").asString == "1.7.3")
    val entries = manifest.getAsJsonArray("mods").map { it.asJsonObject }
    val ids = entries.map { it.get("id").asString }
    check(ids.size in 2..3 && ids.toSet().size == ids.size && ids.containsAll(listOf("cobbleleague", "cobblesynergy")) && ids.all { it in setOf("cobbleleague", "cobblesynergy", "cobblespire") })
    check(entries.map { it.get("filename").asString.lowercase() }.toSet().size == entries.size)
    val development = manifest.get("channel")?.asString == "development"
    check(manifest.get("channel")?.asString in listOf(null, "stable", "development"))
    check(!development || offline)
    var changed = false
    for (entry in entries) {
        val filename = entry.get("filename").asString
        check(filename.matches(Regex("[A-Za-z0-9_.+-]+\\.jar")))
        val hash = entry.get("sha256").asString
        check(hash.matches(Regex("[0-9a-f]{64}")))
        val version = entry.get("version").asString
        check(version.matches(Regex("[0-9]+(?:\\.[0-9]+)*(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?")))
        check(development || '-' !in version.substringBefore('+'))
        val old = installed[entry.get("id").asString]
        if (old == null) { changed = true; continue }
        val comparison = Version.parse(version).compareTo(Version.parse(old.first))
        // Never hand off an impossible mixed downgrade, which would restart on every launch.
        if (comparison < 0) return null
        if (comparison > 0 || (old.second != null && old.second != hash)) changed = true
    }
    return if (changed) manifest.get("release").asString else null
}
