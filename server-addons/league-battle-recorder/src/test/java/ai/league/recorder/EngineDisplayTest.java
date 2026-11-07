package ai.league.recorder;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class EngineDisplayTest {
    @TestFactory Stream<DynamicTest> displayedDamageMatchesEngine() throws Exception {
        return EngineFixtureData.cases().asList().stream().map(element -> {
            var fixture = element.getAsJsonObject();
            return DynamicTest.dynamicTest(EngineFixtureData.s(fixture, "caseName"), () -> verify(fixture));
        });
    }

    private static void verify(JsonObject fixture) {
        var record = EngineFixtureData.record(fixture);
        var log = new ProtocolLog(record);
        fixture.getAsJsonArray("packets").forEach(packet -> log.accept(packet.getAsString()));
        log.finish("엔진 표시 검증", "");
        List<List<?>> rows = XlsxRows.events(record);
        assertEquals(10, rows.getFirst().size());
        assertFalse(rows.getFirst().toString().contains("UUID"));
        assertFalse(rows.getFirst().toString().contains("원본"));
        var displayed = rows.stream().skip(1)
                .filter(r -> r.get(6) instanceof Number n && n.intValue() > 0).toList();
        var expected = fixture.getAsJsonObject("expected").getAsJsonArray("damage").asList().stream()
                .map(JsonElement::getAsJsonObject).filter(h -> EngineFixtureData.n(h, "amount") > 0).toList();
        assertEquals(expected.size(), displayed.size(), "Displayed damage-event count");
        for (int i = 0; i < expected.size(); i++) {
            var h = expected.get(i); var row = displayed.get(i);
            assertEquals(EngineFixtureData.n(h, "amount"), row.get(6), "Damage " + i);
            String hp = EngineFixtureData.n(h, "hpBefore") + " → " + EngineFixtureData.n(h, "hpAfter")
                    + " / " + EngineFixtureData.n(h, "maxHp");
            assertEquals(hp, row.get(7), "Displayed HP " + i);
            var mon = record.pokemon(EngineFixtureData.s(h, "targetId"));
            assertTrue(row.get(4).toString().contains(KoreanNames.species(mon.species)), "Target " + i);
            assertFalse(row.toString().contains(mon.uuid), "UUID must not be displayed");
            String sourceId = EngineFixtureData.s(h, "sourceId");
            var internal = record.events.stream().filter(e -> e.damage() != null && e.damage() > 0).toList().get(i);
            if (!sourceId.isBlank() && !internal.pokemonId().isBlank()
                    && KoreanNames.move(EngineFixtureData.s(h, "effect")).equals(row.get(3)))
                assertTrue(row.get(2).toString().contains(KoreanNames.species(record.pokemon(sourceId).species)), "Attacker " + i);
            if (internal.pokemonId().isBlank()) assertTrue(row.get(2).toString().isBlank(), "Unknown source must not be guessed");
        }
        if (EngineFixtureData.s(fixture, "caseName").equals("guaranteed-critical")) {
            assertTrue(displayed.getFirst().get(5).toString().contains("급소"));
            assertEquals(KoreanNames.move("Storm Throw"), displayed.getFirst().get(3));
        }
        if (EngineFixtureData.s(fixture, "caseName").equals("toxic-two-residuals"))
            displayed.forEach(r -> assertEquals("맹독 피해", r.get(3)));
    }
}
