package ai.league.recorder;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class EngineFixtureTest {
    @TestFactory Stream<DynamicTest> matchesBundledEngineState() throws Exception {
        return EngineFixtureData.cases().asList().stream().map(element -> {
            JsonObject fixture = element.getAsJsonObject();
            return DynamicTest.dynamicTest(EngineFixtureData.s(fixture, "caseName"), () -> verify(fixture));
        });
    }

    private static void verify(JsonObject fixture) {
        var record = EngineFixtureData.record(fixture);
        var log = new ProtocolLog(record);
        for (var packet : fixture.getAsJsonArray("packets")) log.accept(packet.getAsString());
        log.finish("엔진 검증", "");
        assertTrue(record.events.stream().noneMatch(e -> e.type().startsWith("해석 오류")),
                () -> record.events.stream().filter(e -> e.type().startsWith("해석 오류")).toList().toString());
        assertTrue(record.events.stream().noneMatch(e -> e.type().equals("HP 검증 필요")));
        var actual = record.events.stream().filter(e -> e.damage() != null).toList();
        var expected = fixture.getAsJsonObject("expected");
        var hits = expected.getAsJsonArray("damage");
        assertEquals(hits.size(), actual.size(), "Damage-event count");
        for (int i = 0; i < hits.size(); i++) {
            JsonObject h = hits.get(i).getAsJsonObject();
            BattleEvent e = actual.get(i);
            assertEquals(EngineFixtureData.s(h, "targetId"), e.targetId(), "Target " + i);
            assertEquals(EngineFixtureData.n(h, "amount"), e.damage(), "Damage " + i);
            assertEquals(EngineFixtureData.n(h, "hpBefore"), e.hpBefore(), "HP before " + i);
            assertEquals(EngineFixtureData.n(h, "hpAfter"), e.hpAfter(), "HP after " + i);
            assertEquals(e.hpBefore() - e.hpAfter(), e.damage());
            String source = EngineFixtureData.s(h, "sourceId");
            assertTrue(e.pokemonId().isBlank() || e.pokemonId().equals(source), "Wrong damage source " + i);
            if (!source.isBlank() && EngineFixtureData.s(h, "effect").equals(e.move()))
                assertEquals(source, e.pokemonId(), "Direct move source " + i);
        }
        for (var entry : expected.getAsJsonArray("finalHp")) {
            var hp = entry.getAsJsonObject();
            var mon = record.pokemon(EngineFixtureData.s(hp, "uuid"));
            assertEquals(EngineFixtureData.n(hp, "hp"), mon.hp, mon.species + " final HP");
            assertEquals(EngineFixtureData.n(hp, "maxHp"), mon.maxHp);
            assertEquals(EngineFixtureData.s(hp, "status"), mon.status);
        }
    }
}
