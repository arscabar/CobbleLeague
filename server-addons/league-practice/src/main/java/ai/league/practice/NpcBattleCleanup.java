package ai.league.practice;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.net.messages.client.battle.BattleEndPacket;

final class NpcBattleCleanup {
    static void stop(PokemonBattle battle) {
        try { if (!battle.getEnded()) battle.stop(); }
        catch (RuntimeException error) { LeaguePractice.LOG.error("훈련 전투 중단 실패: {}", battle.getBattleId(), error); }
        if (BattleRegistry.getBattle(battle.getBattleId()) == null) return;
        // Recovery is limited to this battle's cloned Pokemon, never the real party.
        for (var actor : battle.getActors()) for (var member : actor.getPokemonList()) {
            try {
                var entity = member.getEntity();
                if (entity != null) entity.setBattleId(null);
                member.getEffectedPokemon().recall();
            } catch (RuntimeException error) { LeaguePractice.LOG.error("전투 복사본 회수 실패", error); }
        }
        try { battle.sendUpdate(new BattleEndPacket()); }
        catch (RuntimeException error) { LeaguePractice.LOG.error("전투 종료 화면 갱신 실패", error); }
        try { BattleRegistry.closeBattle(battle); }
        catch (RuntimeException error) { LeaguePractice.LOG.error("훈련 전투 등록 해제 실패", error); }
    }
}
