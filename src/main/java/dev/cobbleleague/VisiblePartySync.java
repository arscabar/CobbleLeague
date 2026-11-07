package dev.cobbleleague;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.net.messages.client.storage.party.InitializePartyPacket;
import com.cobblemon.mod.common.net.messages.client.storage.party.SetPartyPokemonPacket;
import net.minecraft.server.level.ServerPlayer;
/** Uses the pinned native packet's public JVM constructor, marked Kotlin-internal upstream. */
public final class VisiblePartySync {
 public static void send(ServerPlayer player,PlayerPartyStore store){
  var slots=store.toGappyList();
  new InitializePartyPacket(true,store.getUuid(),slots.size()).sendToPlayer(player);
  for(int index=0;index<slots.size();index++){
   var pokemon=slots.get(index);
   if(pokemon!=null)new SetPartyPokemonPacket(store.getUuid(),new PartyPosition(index),registry->pokemon).sendToPlayer(player);
  }
 }
}
