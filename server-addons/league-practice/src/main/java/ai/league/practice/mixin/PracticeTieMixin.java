package ai.league.practice.mixin;

import ai.league.practice.LeaguePractice;
import com.cobblemon.mod.common.battles.ShowdownInterpreter;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShowdownInterpreter.class, remap = false)
public abstract class PracticeTieMixin {
    @Inject(method = "interpret", at = @At("HEAD"), remap = false)
    private void practiceTie(PokemonBattle battle, String message, CallbackInfo ci) {
        LeaguePractice.observe(battle.getBattleId(), message);
    }
}
