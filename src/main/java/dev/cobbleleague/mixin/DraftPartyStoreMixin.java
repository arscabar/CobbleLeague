package dev.cobbleleague.mixin;

import com.cobblemon.mod.common.api.storage.PokemonStoreManager;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import dev.cobbleleague.LeagueMod;
import net.minecraft.core.RegistryAccess;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A temporary view, never a replacement in Cobblemon's persistent store factory. */
@Mixin(value=PokemonStoreManager.class,remap=false)
public abstract class DraftPartyStoreMixin {
 @Inject(method="getParty(Ljava/util/UUID;Lnet/minecraft/core/RegistryAccess;)Lcom/cobblemon/mod/common/api/storage/party/PlayerPartyStore;",at=@At("HEAD"),cancellable=true,remap=true)
 private void cobbleleague$draftParty(UUID id, RegistryAccess registry, CallbackInfoReturnable<PlayerPartyStore> result){
  var service=LeagueMod.Companion.getService();
  if(service!=null){var temporary=service.draftParty(id);if(temporary!=null)result.setReturnValue(temporary);}
 }
}
