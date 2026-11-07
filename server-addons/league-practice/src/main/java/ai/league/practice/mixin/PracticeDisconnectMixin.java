package ai.league.practice.mixin;

import ai.league.practice.LeaguePractice;
import com.cobblemon.mod.common.battles.BattleRegistry;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BattleRegistry.class, remap = false)
public abstract class PracticeDisconnectMixin {
    @Inject(method = "onPlayerDisconnect", at = @At("HEAD"), remap = false)
    private void practiceDisconnect(ServerPlayer player, CallbackInfo ci) {
        LeaguePractice.disconnected(player.getUUID());
    }
}
