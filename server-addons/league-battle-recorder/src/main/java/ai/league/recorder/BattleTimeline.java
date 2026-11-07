package ai.league.recorder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

final class BattleTimeline {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.of("Asia/Seoul"));
    final BattleRecord record;
    final Map<String, PokemonState> active = new LinkedHashMap<>();
    final Map<String, String> damageCause = new HashMap<>();
    final Map<String, String> pendingDamageCause = new HashMap<>();
    final Map<String, PokemonState> damageSource = new HashMap<>();
    int turn;
    PokemonState lastUser, lastTarget;
    String lastMove = "";

    BattleTimeline(BattleRecord record) { this.record = record; }

    PokemonState resolve(String ref) {
        int colon = ref.indexOf(':');
        String id = colon < 0 ? ref.strip() : ref.substring(colon + 1).strip();
        PokemonState mon = record.pokemon(id);
        return mon != null ? mon : colon < 0 ? active.get(slot(ref)) : null;
    }

    String slot(String ref) {
        int colon = ref.indexOf(':');
        return (colon < 0 ? ref : ref.substring(0, colon)).strip();
    }

    void add(String type, PokemonState user, PokemonState target, String move,
             Integer damage, Integer before, Integer after, String withdrawn,
             String sentOut, String reason, String raw) {
        record.events.add(new BattleEvent(record.events.size() + 1, turn,
                CLOCK.format(Instant.now()), type, user == null ? "" : user.player,
                user == null ? "" : user.label(), user == null ? "" : user.uuid, move,
                target == null ? "" : target.player, target == null ? "" : target.label(),
                target == null ? "" : target.uuid, damage, before, after,
                target == null ? null : target.maxHp, hp(record.player1), hp(record.player2),
                withdrawn, sentOut, reason, raw));
    }

    String hp(String player) {
        return active.entrySet().stream().filter(e -> e.getValue().player.equals(player))
                .map(e -> e.getKey() + " " + e.getValue().label() + " " + e.getValue().hp
                        + "/" + e.getValue().maxHp)
                .collect(Collectors.joining("; "));
    }

    static String part(String[] p, int n) { return n < p.length ? p[n] : ""; }
}
