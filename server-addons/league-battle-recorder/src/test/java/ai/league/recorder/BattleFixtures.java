package ai.league.recorder;

import java.time.Instant;
import java.util.UUID;

final class BattleFixtures {
    static BattleRecord record() {
        var r = new BattleRecord(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                Instant.parse("2026-10-26T00:01:02.003Z"), "Alice", "Bob", "league", "minecraft:overworld");
        r.party.add(new PokemonState("p1", "Alice", 1, "a", "Pikachu", "첫째", 50, "Thunderbolt", 120, 120));
        r.party.add(new PokemonState("p1", "Alice", 2, "b", "Pikachu", "둘째", 50, "Volt Switch", 130, 130));
        r.party.add(new PokemonState("p2", "Bob", 1, "c", "Blastoise", "", 50, "Surf", 200, 200));
        return r;
    }
    static ProtocolLog started(BattleRecord r) {
        var p = new ProtocolLog(r);
        p.accept("update\n|switch|p1a: a|Pikachu, L50|120/120\n|switch|p2a: c|Blastoise, L50|200/200\n|turn|1");
        return p;
    }
}
