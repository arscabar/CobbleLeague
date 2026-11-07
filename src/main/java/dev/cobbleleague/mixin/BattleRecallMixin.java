package dev.cobbleleague.mixin;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.cobbleleague.BattleAnimationRecovery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;

@Mixin(PokemonEntity.class)
public abstract class BattleRecallMixin {
    @Inject(method="recallWithAnimation", at=@At("RETURN"), remap=false)
    private void leagueWatchRecall(CallbackInfoReturnable<CompletableFuture<Pokemon>> cir) {
        BattleAnimationRecovery.watchRecall((PokemonEntity)(Object)this,cir.getReturnValue());
    }
}
