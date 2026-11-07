package ai.league.practice;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import java.util.UUID;
import java.util.function.Consumer;

final class NpcBattleHandle implements NpcBattles.Handle {
    private final UUID player;
    private final NPCEntity npc;
    private final PokemonBattle battle;
    private final Consumer<BattleOutcome> callback;
    private boolean closed;
    private String interruption = "";
    private int unchangedTicks;
    private int messageCount = -1;

    NpcBattleHandle(UUID player, NPCEntity npc, PokemonBattle battle, Consumer<BattleOutcome> callback) {
        this.player = player; this.npc = npc; this.battle = battle; this.callback = callback;
    }

    @Override public UUID battleId() { return battle.getBattleId(); }
    @Override public void abort() {
        if (closed) return;
        interruption = "전투 중단";
        try { NpcBattleCleanup.stop(battle); }
        finally { finish(); }
    }
    @Override public void tick() {
        if (!closed && (npc.isRemoved() || battle.getEnded())) abort();
        if (closed) return;
        int count = battle.getShowdownMessages().size();
        if (count != messageCount) { messageCount = count; unchangedTicks = 0; }
        else if (++unchangedTicks >= 100) {
            unchangedTicks = 0;
            PracticeBattleBridge.recover(battle, npc.getUUID());
        }
    }

    void finish() {
        if (closed) return;
        closed = true;
        PracticeRunBunAI.release(battleId());
        PracticePartyPlanner.release(battleId());
        PracticeBattleOutput.clear(battleId());
        var winners = battle.getWinners().stream().map(actor -> actor.getUuid()).toList();
        boolean forfeited = battle.getBattleLog().stream().anyMatch(line -> line.startsWith(">forcelose "));
        BattleOutcome outcome = BattleOutcome.decide(battleId(), player, winners, forfeited, interruption);
        try { PracticeNpcCleanup.remove(npc); }
        catch (RuntimeException error) { LeaguePractice.LOG.error("임시 NPC 제거 실패: {}", npc.getUUID(), error); }
        try { callback.accept(outcome); }
        catch (RuntimeException error) { LeaguePractice.LOG.error("훈련 전투 종료 처리 실패: {}", battleId(), error); }
    }
}
