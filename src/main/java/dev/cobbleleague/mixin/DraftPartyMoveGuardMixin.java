package dev.cobbleleague.mixin;
import com.cobblemon.mod.common.net.serverhandling.storage.party.MovePartyPokemonHandler;
import com.cobblemon.mod.common.net.messages.server.storage.party.MovePartyPokemonPacket;
import dev.cobbleleague.LeagueMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=MovePartyPokemonHandler.class,remap=false)
public abstract class DraftPartyMoveGuardMixin {
 @Inject(method="handle(Lcom/cobblemon/mod/common/net/messages/server/storage/party/MovePartyPokemonPacket;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)V",at=@At("HEAD"),cancellable=true,remap=true)
 private void cobbleleague$lockBattleOrder(MovePartyPokemonPacket packet,MinecraftServer server,ServerPlayer player,CallbackInfo ci){
  var service=LeagueMod.Companion.getService();
  if(service!=null&&!service.mayReorderDraft(player)){ci.cancel();}
 }
}
