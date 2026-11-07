package ai.league.recorder;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

final class EngineFixtureData {
    static JsonArray cases() throws IOException {
        try (var in = EngineFixtureData.class.getResourceAsStream("/engine-fixtures.json")) {
            if (in == null) throw new IOException("Missing real-engine fixtures");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("cases");
        }
    }

    static BattleRecord record(JsonObject fixture) {
        var record = new BattleRecord(UUID.randomUUID(), Instant.now(), "Alice", "Bob",
                "실제 엔진 검증", "minecraft:overworld");
        for (var entry : fixture.getAsJsonArray("party")) {
            var p = entry.getAsJsonObject();
            record.party.add(new PokemonState(s(p, "side"), s(p, "player"), n(p, "slot"),
                    s(p, "uuid"), s(p, "species"), s(p, "nickname"), n(p, "level"), s(p, "moves"),
                    n(p, "hp"), n(p, "maxHp")));
        }
        return record;
    }

    static String s(JsonObject p, String key) { return p.get(key).getAsString(); }
    static int n(JsonObject p, String key) { return p.get(key).getAsInt(); }
}
