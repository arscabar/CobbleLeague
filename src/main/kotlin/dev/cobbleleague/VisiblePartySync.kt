package dev.cobbleleague

import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore
import net.minecraft.server.level.ServerPlayer

/** PartyStore.sendTo always uses isThisPlayerParty=false. Bind the sidebar explicitly. */
fun syncVisibleParty(player: ServerPlayer, store: PlayerPartyStore) {
    VisiblePartySync.send(player,store)
}
