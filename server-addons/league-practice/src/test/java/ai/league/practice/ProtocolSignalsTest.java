package ai.league.practice;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolSignalsTest {
    @Test void detectsEngineTieWithOrWithoutTrailingDelimiter() {
        assertTrue(ProtocolSignals.tie("update\n|turn|5\n|tie\n"));
        assertTrue(ProtocolSignals.tie("update\r\n|tie|\r\n"));
    }
    @Test void ordinaryMessageCannotAbortTheBattle() {
        assertFalse(ProtocolSignals.tie("|message|tie\n|move|p1a: tie|Tackle|p2a: target\n|tie|unexpected"));
    }
}
