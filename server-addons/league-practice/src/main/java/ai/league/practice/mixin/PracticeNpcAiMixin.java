package ai.league.practice.mixin;

import ai.league.practice.PracticeNpcTags;
import ai.league.practice.PracticeTacticalBattleAI;
import ai.league.practice.PracticeRunBunAI;
import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import com.cobblemon.mod.common.battles.ShowdownActionRequest;
import kotlin.Unit;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AIBattleActor.class, remap = false)
public abstract class PracticeNpcAiMixin {
    @Unique private ShowdownActionRequest leaguePractice$scheduled;
    @Unique private ShowdownActionRequest leaguePractice$answered;
    @Inject(method = "onChoiceRequested", at = @At("HEAD"), cancellable = true, remap = false)
    private void leaguePractice$useTacticalAi(CallbackInfo ci) {
        if ((Object) this instanceof NPCBattleActor npcActor && PracticeNpcTags.isPracticeNpc(npcActor.getEntity())) {
            ci.cancel();
            var request = npcActor.getRequest();
            if (request == null || request == leaguePractice$scheduled || request == leaguePractice$answered) return;
            leaguePractice$scheduled = request;
            npcActor.getBattle().doWhenClear(() -> {
                if (leaguePractice$scheduled == request) leaguePractice$scheduled = null;
                if (!npcActor.getBattle().getEnded() && npcActor.getRequest() == request && leaguePractice$answered != request) {
                    leaguePractice$answered = request;
                    try { PracticeTacticalBattleAI.handleChoiceRequested(npcActor, PracticeTacticalBattleAI.INSTANCE); }
                    finally { npcActor.getPokemonList().forEach(member -> member.setWillBeSwitchedIn(false)); }
                }
                return Unit.INSTANCE;
            });
        }
    }
}
