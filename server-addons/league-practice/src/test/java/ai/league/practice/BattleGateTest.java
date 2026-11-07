package ai.league.practice;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BattleGateTest {
    @Test void onlyOneBattleAndOnlyItsOwnerCanRelease() {
        var gate = new BattleGate();
        UUID first = gate.acquire();
        assertThrows(IllegalStateException.class, gate::acquire);
        assertFalse(gate.release(UUID.randomUUID()));
        assertTrue(gate.busy());
        assertTrue(gate.release(first));
        UUID next = gate.acquire();
        assertFalse(gate.release(first));
        assertTrue(gate.busy());
        assertTrue(gate.release(next));
    }
}
