package dev.cobbleleague.client

import com.cobblemon.mod.common.client.CobblemonClient
import com.google.gson.JsonObject
import dev.cobbleleague.text
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

/** Repair a stale native party view; never fabricate or delete a client Pokemon. */
internal object DraftPartyBinding {
    private var mismatchTicks=0
    private var lastRequest=0L
    fun register(){ClientTickEvents.END_CLIENT_TICK.register{client->
        val data=DraftHud.data
        if(client.player==null||data==null||!ClientStorageLock.isLocked||LeagueClient.hasLeagueBattle){mismatchTicks=0;return@register}
        val expected=data.getAsJsonArray("cards")?.map{it.asJsonObject.text("uuid")}.orEmpty()
        val shown=CobblemonClient.storage.party.slots.filterNotNull().map{it.uuid.toString()}
        if(expected==shown){mismatchTicks=0;return@register}
        mismatchTicks++
        val now=System.currentTimeMillis()
        if(mismatchTicks>=5&&now-lastRequest>=1000){lastRequest=now;LeagueClient.send(JsonObject().apply{addProperty("action","sync_party")})}
    }}
}
