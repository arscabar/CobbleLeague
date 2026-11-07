package ai.league.recorder;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import kotlin.Unit;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

final class SessionManager {
    private final ArenaConfig arena;
    private final Path directory;
    private final Map<UUID, Session> open = new HashMap<>();
    private final Set<UUID> checked = new HashSet<>();
    private boolean stopping;

    SessionManager(ArenaConfig arena, Path directory) { this.arena = arena; this.directory = directory; }

    void start(PokemonBattle battle) {
        if (stopping || battle.getEnded() || !checked.add(battle.getBattleId())) return;
        battle.getOnEndHandlers().add(done -> {
            checked.remove(done.getBattleId());
            LeagueRecorder.guard(() -> finish(done.getBattleId(), ""));
            return Unit.INSTANCE;
        });
        BattleRecord record = BattleSnapshot.capture(battle, arena);
        if (record == null) return;
        open.put(record.id, new Session(battle, record, new ProtocolLog(record)));
    }

    void accept(PokemonBattle battle, String raw) {
        start(battle);
        Session session = open.get(battle.getBattleId());
        if (session != null) {
            try {
                session.protocol.accept(raw);
                if (session.protocol.isTie()) finish(battle.getBattleId(), "");
            }
            catch (RuntimeException error) {
                session.reason = "기록 파싱 오류로 일부 내용이 누락될 수 있음";
                LeagueRecorder.LOG.error("전투 기록 파싱 실패: {}", session.record.id, error);
            }
        }
    }

    void disconnected(UUID player) {
        for (Session session : open.values())
            for (UUID participant : session.battle.getPlayerUUIDs())
                if (participant.equals(player)) session.reason = "접속 종료";
    }

    void resume() { stopping = false; }

    void shutdown() {
        stopping = true;
        for (UUID id : List.copyOf(open.keySet()))
            LeagueRecorder.guard(() -> finish(id, "서버 종료"));
        checked.clear();
    }

    private void finish(UUID id, String reason) {
        Session session = open.remove(id);
        if (session == null) return;
        String winners = session.battle.getWinners().stream()
                .map(actor -> actor.getName().getString()).collect(Collectors.joining(", "));
        String result = winners.isBlank() ? "중단" : winners + " 승리";
        String ending = reason.isBlank() ? session.reason : reason;
        if (ending.isBlank() && winners.isBlank()) ending = "전투 중단";
        try { session.protocol.finish(result, ending); }
        catch (RuntimeException error) {
            session.record.endedAt = Instant.now();
            session.record.result = result;
            session.record.endReason = ending + "; 기록 마무리 오류";
            LeagueRecorder.LOG.error("전투 기록 마무리 실패: {}", id, error);
        }
        LeagueRecorder.save(directory, session.record);
    }

    private static final class Session {
        final PokemonBattle battle;
        final BattleRecord record;
        final ProtocolLog protocol;
        String reason = "";
        Session(PokemonBattle battle, BattleRecord record, ProtocolLog protocol) {
            this.battle = battle; this.record = record; this.protocol = protocol;
        }
    }
}
