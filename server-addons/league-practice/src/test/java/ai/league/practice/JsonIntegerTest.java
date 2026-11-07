package ai.league.practice;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonIntegerTest {
    @Test void rejectsRoundingNullAndOverflowBeforeGsonCoercion() {
        for (String input : new String[]{"null", "1.5", "4294967296", "-2147483649", "\"31\"", "true"})
            assertThrows(IllegalArgumentException.class, () -> JsonInteger.check(JsonParser.parseString(input), "x"));
        JsonInteger.check(JsonParser.parseString("31"), "x");
        JsonInteger.check(JsonParser.parseString("1.0"), "x");
    }
    @Test void rejectsInvalidButtonAndAcceptsDecimalSpawn() {
        for (String input : new String[]{"null", "1.5", "4294967296"})
            assertThrows(IllegalArgumentException.class, () -> SettingsShape.check(JsonParser.parseString(
                    "{\"buttons\":{\"beginner\":{\"x\":" + input + ",\"y\":64,\"z\":0}}}")));
        SettingsShape.check(JsonParser.parseString("{\"spawn\":{\"x\":1.5,\"y\":64,\"z\":0,\"yaw\":0}}"));
    }
    @Test void rejectsBadStatsWhileAllowingEmptyPartyTemplates() {
        TeamShape.check(JsonParser.parseString("[{\"id\":\"beginner-01\",\"pokemon\":[]}]"));
        for (String input : new String[]{"null", "1.5", "4294967296", "\"31\""})
            assertThrows(IllegalArgumentException.class, () -> TeamShape.check(JsonParser.parseString(
                    "[{\"pokemon\":[{\"ivs\":[" + input + ",31,31,31,31,31],\"evs\":[0,0,0,0,0,0]}]}]")));
    }
}
