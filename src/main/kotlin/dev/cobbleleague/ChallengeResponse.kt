package dev.cobbleleague

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Only accepting registered-team mode reads and shares the receiver's Quick Teams list. */
fun leagueChallengeResponse(id: String, mode: NativeLeagueMode, accept: Boolean, teams: () -> JsonArray): JsonObject =
    JsonObject().apply {
        addProperty("action", if (accept) "accept" else "decline")
        addProperty("id", id)
        if (accept && mode.requiresSavedTeams) {
            val snapshot = teams()
            require(!snapshot.isEmpty) { "Quick Teams에 저장된 덱이 없습니다." }
            add("teams", snapshot)
        }
    }
