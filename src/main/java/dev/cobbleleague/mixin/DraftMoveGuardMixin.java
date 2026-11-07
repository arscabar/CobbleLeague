package dev.cobbleleague.mixin;
import com.cobblemon.mod.common.net.serverhandling.storage.BenchMoveHandler;
import com.cobblemon.mod.common.net.messages.server.BenchMovePacket;
import dev.cobbleleague.LeagueMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=BenchMoveHandler.class,remap=false)
public abstract class DraftMoveGuardMixin {
 @Inject(method="handle(Lcom/cobblemon/mod/common/net/messages/server/BenchMovePacket;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)V",at=@At("HEAD"),cancellable=true,remap=true)
 private void cobbleleague$onlyPurchasedTm(BenchMovePacket packet,MinecraftServer server,ServerPlayer player,CallbackInfo ci){
  var service=LeagueMod.Companion.getService();
  if(service!=null&&service.draftParty(player.getUUID())!=null){service.rejectDraftMoveChange(player);ci.cancel();}
 }
}
