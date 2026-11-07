package ai.league.recorder.mixin;

import ai.league.recorder.LeagueRecorder;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.ShowdownInterpreter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShowdownInterpreter.class, remap = false)
public abstract class ShowdownMixin {
    @Inject(method = "interpret(Lcom/cobblemon/mod/common/api/battles/model/PokemonBattle;Ljava/lang/String;)V",
            at = @At("HEAD"), remap = false)
    private void leagueRecord(PokemonBattle battle, String raw, CallbackInfo ci) {
        LeagueRecorder.observe(battle, raw);
    }
}
