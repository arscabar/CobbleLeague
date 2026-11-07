package ai.league.practice;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.*;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import net.minecraft.server.level.*;
import java.util.*;
import java.util.function.Consumer;
import kotlin.Unit;

public final class NpcBattles {
    private NpcBattles() {}

    public interface Handle {
        UUID battleId();
        void abort();
        void tick();
    }

    public static Handle start(ServerPlayer player, ServerLevel world, SpawnSite site,
                               Tier tier, TeamSpec team, Consumer<BattleOutcome> callback) {
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null)
            throw new IllegalStateException("이미 진행 중인 전투가 있습니다.");
        NPCEntity npc = NpcSpawn.create(world, site, tier, team);
        PokemonBattle started = null;
        try {
            if (tier == Tier.MASTER) PracticePartyPlanner.selectLead(player,npc);
            var base = BattleFormat.Companion.getGEN_9_SINGLES();
            var format = new BattleFormat(base.getMod(), base.getBattleType(), Set.copyOf(base.getRuleSet()), base.getGen(), 50);
            // Only the battle copy is adjusted; the real party stays unchanged.
            var result = BattleBuilder.INSTANCE.pvn(player, npc, null, format, true, true);
            if (!(result instanceof SuccessfulBattleStart success)) {
                StringJoiner errors = new StringJoiner(" / ");
                if (result instanceof ErroredBattleStart failed)
                    failed.getErrors().forEach(error -> errors.add(error.getMessageFor(player).getString()));
                throw new IllegalStateException("전투 시작 실패: " + errors);
            }
            started = success.getBattle();
            var handle = new NpcBattleHandle(player.getUUID(), npc, started, callback);
            success.getBattle().getOnEndHandlers().add(done -> { handle.finish(); return Unit.INSTANCE; });
            return handle;
        } catch (RuntimeException error) {
            if (started != null) NpcBattleCleanup.stop(started);
            PracticeNpcCleanup.remove(npc);
            throw error;
        }
    }
}
