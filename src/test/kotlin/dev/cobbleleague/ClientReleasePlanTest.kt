package dev.cobbleleague

import com.google.gson.JsonObject
import com.google.gson.JsonArray
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClientReleasePlanTest {
    private val hash = "a".repeat(64)
    private fun manifest(spire: String? = "0.6.0-dev", channel: String = "development", extra: String? = null): ByteArray {
        val json = JsonObject()
        json.addProperty("schema", 1); json.addProperty("minecraft", "1.21.1"); json.addProperty("cobblemon", "1.7.3")
        json.addProperty("release", "independent-content-identity"); json.addProperty("channel", channel)
        val mods = JsonArray()
        val versions = linkedMapOf("cobbleleague" to "0.8.2", "cobblesynergy" to "0.4.2")
        if (spire != null) versions["cobblespire"] = spire
        if (extra != null) versions[extra] = "1.0.0"
        for ((id, version) in versions) {
            val entry = JsonObject()
            entry.addProperty("id", id); entry.addProperty("version", version)
            entry.addProperty("filename", "$id-$version.jar"); entry.addProperty("sha256", hash)
            mods.add(entry)
        }
        json.add("mods", mods)
        return json.toString().toByteArray()
    }
    private fun installed() = mutableMapOf("cobbleleague" to ("0.8.2" to hash), "cobblesynergy" to ("0.4.2" to hash), "cobblespire" to ("0.6.0-dev" to hash))

    @Test fun `missing Spire triggers without core release bump`() {
        val old = installed(); old.remove("cobblespire")
        assertNotNull(clientReleasePlan(manifest(), old, true))
    }
    @Test fun `Spire only newer preview triggers once`() {
        val old = installed()
        assertNotNull(clientReleasePlan(manifest("0.6.0-dev.2"), old, true))
        old["cobblespire"] = "0.6.0-dev.2" to hash
        assertNull(clientReleasePlan(manifest("0.6.0-dev.2"), old, true))
    }
    @Test fun `same version changed artifact is repaired without loop`() {
        val old = installed(); old["cobblespire"] = "0.6.0-dev" to "b".repeat(64)
        assertNotNull(clientReleasePlan(manifest(), old, true))
        assertNull(clientReleasePlan(manifest(), installed(), true))
    }
    @Test fun `pair ignores existing Spire and supports legacy missing channel`() {
        assertNull(clientReleasePlan(manifest(null, "stable"), installed(), false))
        val legacy = String(manifest(null, "stable")).replace(",\"channel\":\"stable\"", "")
        assertNull(clientReleasePlan(legacy.toByteArray(), installed(), false))
    }
    @Test fun `any downgrade blocks mixed plan and repeated restarts`() {
        val old = installed(); old["cobblespire"] = "0.6.0" to hash; old.remove("cobbleleague")
        assertNull(clientReleasePlan(manifest(), old, true))
    }
    @Test fun `stable refuses development channel and mislabeled preview`() {
        assertThrows(IllegalStateException::class.java) { clientReleasePlan(manifest(), installed(), false) }
        assertThrows(IllegalStateException::class.java) { clientReleasePlan(manifest(channel="stable"), installed(), false) }
    }
    @Test fun `unknown duplicate unsafe and corrupt entries fail closed`() {
        for (body in listOf(manifest(extra="unrelated"), String(manifest()).replace("cobblespire\"", "cobbleleague\"").toByteArray(), String(manifest()).replace("cobblespire-0.6.0-dev.jar", "../escape.jar").toByteArray(), String(manifest()).replace(hash, "oops").toByteArray())) {
            assertThrows(IllegalStateException::class.java) { clientReleasePlan(body, installed(), true) }
        }
    }
}
