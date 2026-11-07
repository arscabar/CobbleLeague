package ai.league.practice;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderedBattleMessagesTest {
    @Test void reentrantOutputsCannotOvertakeCurrentFrame() {
        var queue = new OrderedBattleMessages();
        var id = UUID.randomUUID();
        var seen = new ArrayList<String>();
        queue.add(id, "faint-update"); queue.add(id, "forced-switch-request");
        queue.drain((battle, message) -> {
            seen.add(message);
            if (message.equals("faint-update")) {
                queue.add(id, "switch-update");
                queue.drain((nested, output) -> fail("Reentrant interpretation"));
            }
        });
        assertEquals(List.of("faint-update", "forced-switch-request"), seen);
        queue.drain((battle, message) -> seen.add(message));
        assertEquals(List.of("faint-update", "forced-switch-request", "switch-update"), seen);
    }
    @Test void closingOneBattleRetainsOthersAndResetsDrainAfterFailure() {
        var queue = new OrderedBattleMessages();
        var first = UUID.randomUUID(); var second = UUID.randomUUID();
        queue.add(first, "old"); queue.add(second, "bad"); queue.add(second, "next");
        queue.clear(first); assertFalse(queue.pending(first));
        assertThrows(IllegalStateException.class, () -> queue.drain((id, text) -> { throw new IllegalStateException(); }));
        var seen = new ArrayList<String>();
        queue.drain((id, text) -> seen.add(text));
        assertEquals(List.of("next"), seen);
    }
}
