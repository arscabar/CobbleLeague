package ai.league.practice;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleOutcomeTest {
    private final UUID battle = UUID.randomUUID(), player = UUID.randomUUID(), npc = UUID.randomUUID();

    @Test void onlyACompletedPlayerVictoryWins() {
        assertTrue(BattleOutcome.decide(battle, player, List.of(player), false, "").won());
        assertFalse(BattleOutcome.decide(battle, player, List.of(npc), false, "").won());
        assertFalse(BattleOutcome.decide(battle, player, List.of(), false, "").won());
    }
    @Test void cancellationOverridesEvenAnAlreadySetWinner() {
        var outcome = BattleOutcome.decide(battle, player, List.of(player), false, "접속 종료");
        assertFalse(outcome.won());
        assertEquals("접속 종료", outcome.reason());
        assertEquals(battle, outcome.battleId());
    }
    @Test void forfeitNeverAwardsVictory() {
        var outcome = BattleOutcome.decide(battle, player, List.of(player), true, "");
        assertFalse(outcome.won());
        assertEquals("기권", outcome.reason());
    }
}
