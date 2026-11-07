package ai.league.practice;

import java.util.Collection;
import java.util.UUID;

public record BattleOutcome(UUID battleId, boolean won, String reason) {
    static BattleOutcome decide(UUID battleId, UUID player, Collection<UUID> winners,
                                boolean forfeited, String interruption) {
        if (!interruption.isBlank()) return new BattleOutcome(battleId, false, interruption);
        if (forfeited) return new BattleOutcome(battleId, false, "기권");
        boolean won = winners.contains(player);
        return new BattleOutcome(battleId, won, won ? "승리" : winners.isEmpty() ? "무승부 또는 중단" : "패배");
    }
}
