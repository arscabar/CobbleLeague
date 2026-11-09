package dev.cobbleleague.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleRegistry;
import dev.cobbleleague.LeagueMod;
import dev.cobbleleague.LeagueService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Compatibility hook; drafted Pokemon now use native held-item transformation rules. */
@Mixin(value = BattleRegistry.class, remap = false)
public abstract class BattleRegistryMegaMixin {
    @Inject(method = "startShowdown", at = @At("HEAD"))
    private void cobbleleague$preserveDraftedMega(PokemonBattle battle, CallbackInfo callback) {
        LeagueService service = LeagueMod.Companion.getService();
        if (service != null) service.prepareBattleForms(battle);
    }
}
