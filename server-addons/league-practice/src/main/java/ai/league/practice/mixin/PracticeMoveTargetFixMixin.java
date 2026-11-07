package ai.league.practice.mixin;

import com.cobblemon.mod.common.CobblemonMemories;
import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixes Cobblemon's synchronous target resolution bug in {@link MoveInstruction#invoke(PokemonBattle)}:
 * When a Pokemon switches in on the same turn an opponent attacks that slot, {@code SwitchInstruction}
 * updates {@code activeBattlePokemon.setBattlePokemon(newPokemon)} asynchronously inside {@code battle.dispatch},
 * whereas {@code MoveInstruction.invoke(battle)} reads {@code targetActivePokemon.getBattlePokemon()} synchronously
 * before any dispatch runs—capturing the recalled (old) Pokemon instead of the newly switched-in Pokemon.
 */
@Mixin(value = MoveInstruction.class, remap = false)
public abstract class PracticeMoveTargetFixMixin {
    @Shadow
    @Final
    private BattleMessage message;

    @Shadow
    private BattlePokemon userPokemon;

    @Shadow
    private BattlePokemon targetPokemon;

    @Shadow
    private List<BattlePokemon> spreadTargets;

    @Inject(
        method = "invoke",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/battles/model/PokemonBattle;dispatch(Lkotlin/jvm/functions/Function0;)V"
        ),
        remap = false
    )
    private void leaguePractice$fixSwitchedTargetResolution(PokemonBattle battle, CallbackInfo ci) {
        if (this.message == null || battle == null) {
            return;
        }
        BattlePokemon actualTarget = this.message.battlePokemon(1, battle);
        if (actualTarget != null && this.targetPokemon != null && actualTarget != this.targetPokemon) {
            BattlePokemon staleTarget = this.targetPokemon;
            this.targetPokemon = actualTarget;

            if (this.spreadTargets != null && !this.spreadTargets.isEmpty()) {
                List<BattlePokemon> updatedTargets = new ArrayList<>(this.spreadTargets.size());
                for (BattlePokemon bp : this.spreadTargets) {
                    updatedTargets.add(bp == staleTarget ? actualTarget : bp);
                }
                this.spreadTargets = updatedTargets;
            }

            if (this.userPokemon != null) {
                PokemonEntity userEntity = this.userPokemon.getEntity();
                if (userEntity != null) {
                    userEntity.getBrain().setMemory(CobblemonMemories.TARGETED_BATTLE_POKEMON, actualTarget.getUuid());
                }
            }
        }
    }
}
