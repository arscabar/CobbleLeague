package dev.cobbleleague.client

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.UUID

/** Optional interop with Quick Teams 1.5.5 public accessors. No Quick Teams code or assets are bundled. */
object QuickTeamsBridge {
    fun snapshot(): JsonArray {
        val type = try { Class.forName("com.mrlegitosity.cqt.client.QuickTeamsConfig") }
        catch (_: ClassNotFoundException) { error("Cobblemon Quick Teams가 설치되어 있지 않습니다.") }
        try {
            val instance = type.getMethod("getInstance").invoke(null)
            val count = type.getField("TEAM_COUNT").getInt(null).coerceIn(0, 24)
            val saved = type.getMethod("isTeamSaved", Int::class.javaPrimitiveType)
            val getTeam = type.getMethod("getTeam", Int::class.javaPrimitiveType)
            val getName = type.getMethod("getTeamName", Int::class.javaPrimitiveType)
            return JsonArray().also { out ->
                repeat(count) { index ->
                    if (saved.invoke(instance, index) == true) {
                        val team = (getTeam.invoke(instance, index) as Array<*>).filterIsInstance<UUID>()
                        if (team.isNotEmpty()) out.add(JsonObject().apply {
                            addProperty("id", index.toString())
                            addProperty("name", getName.invoke(instance, index).toString().take(48))
                            add("members", JsonArray().also { members -> team.forEach { members.add(it.toString()) } })
                        })
                    }
                }
            }
        } catch (e: ReflectiveOperationException) {
            error("이 Quick Teams 버전의 저장 형식을 읽지 못했습니다. 1.5.5 기준 연결입니다.")
        }
    }
}
