package ai.league.practice;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.runner.ShowdownService;
import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService;
import java.util.UUID;

/** Repairs only this addon's stalled NPC battle, without changing HP or player choices. */
final class PracticeBattleBridge {
    static void recover(PokemonBattle battle, UUID npc) {
        if (battle.getEnded() || PracticeBattleOutput.pending(battle.getBattleId()) || !battle.getDispatches().isEmpty()) return;
        if (!(ShowdownService.Companion.getService() instanceof GraalShowdownService service)) return;
        try {
            var context = service.getContext();
            context.getBindings("js").putMember("__leaguePracticeBridgeService", service);
            String result = context.eval("js", """
                JSON.stringify((() => {
                  const stream = battleMap.get('%s');
                  if (!stream?.battle) return {missing:true};
                  const engine = stream.battle;
                  let switched = false, readerStarted = false;
                  const side = engine.sides.find(s => s.name === '%s');
                  // A completed/pending AI choice is never replaced. Ordinary moves are untouched.
                  if (!engine.ended && engine.requestState === 'switch' &&
                      side?.requestState === 'switch' &&
                      side.activeRequest?.forceSwitch?.some(Boolean) &&
                      side.choice.actions.length === 0) {
                    const next = side.pokemon.find(p => p.hp > 0 && !p.fainted && !p.isActive);
                    if (next) {
                      stream.write('>' + side.id + ' switch ' + (next.position + 1));
                      switched = true;
                    }
                  }
                  // Never attach a second reader. The normal reader is protected by
                  // the practice output queue, so Java callbacks cannot kill it or re-enter parsing.
                  return {switched,readerStarted};
                })())
                """.formatted(battle.getBattleId(), npc)).asString();
            if (result.contains("\"switched\":true") || result.contains("\"readerStarted\":true"))
                LeaguePractice.LOG.warn("훈련 전투 교체/메시지 전달 복구: {} {}", battle.getBattleId(), result);
        } catch (RuntimeException error) {
            LeaguePractice.LOG.warn("훈련 전투 전달 상태 확인 실패: {}", battle.getBattleId(), error);
        }
    }
}
