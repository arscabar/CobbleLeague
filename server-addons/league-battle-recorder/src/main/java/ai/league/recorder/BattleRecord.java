package ai.league.recorder;

import java.time.Instant;
import java.util.*;

public final class BattleRecord {
    public final UUID id;
    public final Instant startedAt;
    public final String player1, player2, arena, dimension;
    public Instant endedAt;
    public String result = "진행 중", endReason = "";
    public final List<PokemonState> party = new ArrayList<>();
    public final List<BattleEvent> events = new ArrayList<>();

    public BattleRecord(UUID id, Instant startedAt, String player1, String player2,
                        String arena, String dimension) {
        this.id = id;
        this.startedAt = startedAt;
        this.player1 = player1;
        this.player2 = player2;
        this.arena = arena;
        this.dimension = dimension;
    }

    public PokemonState pokemon(String uuid) {
        return party.stream().filter(p -> p.uuid.equals(uuid)).findFirst().orElse(null);
    }
}
