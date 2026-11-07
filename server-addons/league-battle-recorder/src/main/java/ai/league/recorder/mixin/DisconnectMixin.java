package ai.league.recorder.mixin;

import ai.league.recorder.LeagueRecorder;
import com.cobblemon.mod.common.battles.BattleRegistry;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BattleRegistry.class, remap = false)
public abstract class DisconnectMixin {
    @Inject(method = "onPlayerDisconnect", at = @At("HEAD"), remap = false)
    private void leagueDisconnect(ServerPlayer player, CallbackInfo ci) {
        LeagueRecorder.disconnected(player.getUUID());
    }
}
