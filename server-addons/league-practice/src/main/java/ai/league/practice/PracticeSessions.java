package ai.league.practice;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.util.UUID;

final class PracticeSessions {
    private final MinecraftServer server;
    private final Rewards rewards;
    private final BattleGate gate = new BattleGate();
    private Active current;
    PracticeSessions(MinecraftServer server, Rewards rewards) { this.server = server; this.rewards = rewards; }
    boolean busy() { return gate.busy(); }
    void start(ServerPlayer player, ServerLevel world, SpawnSite spawn, Tier tier, TeamSpec team) {
        Active session = new Active(gate.acquire(), player.getUUID(), tier, world.dimension().location().toString());
        current = session;
        try {
            session.handle = NpcBattles.start(player, world, spawn, tier, team,
                    outcome -> server.execute(() -> finish(session, outcome)));
        } catch (RuntimeException error) { release(session); throw error; }
    }
    void cancel(UUID player, String reason) {
        Active s = current;
        if (s == null || !s.player.equals(player) || s.outcome != null) return;
        s.cancelled = true;
        try { if (s.handle != null) s.handle.abort(); }
        finally { release(s); }
    }
    void tie(UUID battle) {
        if (current != null && current.handle != null && current.handle.battleId().equals(battle))
            current.tie = true;
    }
    void tick() {
        Active s = current;
        if (s == null) return;
        if (s.outcome != null) { if (server.getTickCount() % 100 == 0) persist(s); return; }
        ServerPlayer player = server.getPlayerList().getPlayer(s.player);
        if (s.tie || player == null || !player.isAlive() ||
                !player.serverLevel().dimension().location().toString().equals(s.dimension)) {
            cancel(s.player, "전투 중단"); return;
        }
        if (s.handle != null) s.handle.tick();
    }
    void shutdown() {
        if (current == null) return;
        if (current.outcome != null) persist(current);
        else cancel(current.player, "서버 종료");
    }
    private void finish(Active s, BattleOutcome outcome) {
        if (current != s || s.outcome != null) return;
        s.outcome = outcome;
        if (s.cancelled || !outcome.won()) { release(s); return; }
        persist(s);
    }
    private void persist(Active s) {
        try {
            rewards.award(s.outcome.battleId(), s.player, s.tier);
            release(s);
            ServerPlayer player = server.getPlayerList().getPlayer(s.player);
            if (player != null) {
                player.sendSystemMessage(Component.literal(s.tier.label() + " 전투에서 승리했습니다!"));
                rewards.claim(player);
            }
        } catch (IOException error) { LeaguePractice.LOG.error("승리 보상 보관/지급 실패: battle={} player={} tier={}. 재시도합니다.",
                s.outcome.battleId(), s.player, s.tier.id(), error); }
    }
    private void release(Active s) { if (gate.release(s.token)) current = null; }
    private static final class Active {
        final UUID token, player;
        final Tier tier;
        final String dimension;
        NpcBattles.Handle handle;
        BattleOutcome outcome;
        boolean cancelled, tie;
        Active(UUID token, UUID player, Tier tier, String dimension) {
            this.token = token; this.player = player; this.tier = tier; this.dimension = dimension;
        }
    }
}
