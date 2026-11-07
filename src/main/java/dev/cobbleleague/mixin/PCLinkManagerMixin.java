package dev.cobbleleague.mixin;

import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.api.storage.pc.link.PCLink;
import com.cobblemon.mod.common.api.storage.pc.link.PCLinkManager;
import dev.cobbleleague.LeagueMod;
import dev.cobbleleague.LeagueService;
import kotlin.jvm.functions.Function1;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;

/** All standard Cobblemon PC movement packets, including Quick Teams, resolve this access link. */
@Mixin(value = PCLinkManager.class, remap = false)
public abstract class PCLinkManagerMixin {
    @Inject(method = "getPC", at = @At("HEAD"), cancellable = true)
    private void cobbleleague$denyPC(ServerPlayer player, CallbackInfoReturnable<PCStore> callback) {
        LeagueService service = LeagueMod.Companion.getService();
        if (service != null && service.isStorageLocked(player.getUUID())) {
            service.rejectPcAccess(player);
            callback.setReturnValue(null);
        }
    }

    @Inject(method = "getLink", at = @At("HEAD"), cancellable = true)
    private void cobbleleague$denyLink(UUID playerId, CallbackInfoReturnable<PCLink> callback) {
        LeagueService service = LeagueMod.Companion.getService();
        if (service != null && service.isStorageLocked(playerId)) callback.setReturnValue(null);
    }

    @Inject(method = "addLink(Lcom/cobblemon/mod/common/api/storage/pc/link/PCLink;)V", at = @At("HEAD"), cancellable = true)
    private void cobbleleague$denyNewLink(PCLink link, CallbackInfo callback) {
        LeagueService service = LeagueMod.Companion.getService();
        if (service != null && service.isStorageLocked(link.getPlayerID())) callback.cancel();
    }

    @Inject(method = "addLink(Ljava/util/UUID;Lcom/cobblemon/mod/common/api/storage/pc/PCStore;Lkotlin/jvm/functions/Function1;)V", at = @At("HEAD"), cancellable = true)
    private void cobbleleague$denyNewConditionalLink(UUID playerId, PCStore pc, Function1<ServerPlayer, Boolean> condition, CallbackInfo callback) {
        LeagueService service = LeagueMod.Companion.getService();
        if (service != null && service.isStorageLocked(playerId)) callback.cancel();
    }
}
