package ai.league.recorder;

import java.util.*;

final class DisplayPP {
    private static final class Use {
        final DisplayRow row;
        boolean counted;
        Use(DisplayMove move) { row = move.first; }
    }
    private final Map<String, Map<String, Integer>> snapshots = new HashMap<>();
    private final Map<String, Use> uses = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();

    DisplayPP(BattleRecord record) {
        for (PokemonState p : record.party)
            for (String name : p.moves.split("\\s*[/,]\\s*"))
                if (!id(name).isBlank()) names.putIfAbsent(id(name), name);
    }

    void used(DisplayMove move) {
        BattleEvent e = move.event;
        if (!e.pokemonId().isBlank() && !e.raw().contains("[from]")) {
            uses.put(e.pokemonId() + ":" + id(e.move()), new Use(move));
            names.put(id(e.move()), e.move());
        }
    }

    void accept(BattleEvent e, List<DisplayRow> rows, DisplayText text) {
        String[] fields = e.raw().split("\\|", -1);
        if (fields.length < 4) return;
        String pokemon = e.targetId();
        Map<String, Integer> prior = snapshots.computeIfAbsent(pokemon, k -> new HashMap<>());
        for (String field : fields[3].split(",")) {
            int colon = field.lastIndexOf(':');
            if (colon < 0) continue;
            String move = id(field.substring(0, colon));
            int value;
            try { value = Integer.parseInt(field.substring(colon + 1).strip()); }
            catch (NumberFormatException ignored) { continue; }
            if (move.isBlank() || value < 0) continue;
            Integer old = prior.put(move, value);
            Use use = uses.get(pokemon + ":" + move);
            if (old == null) { if (use != null) use.counted = true; continue; }
            if (old == value) continue;
            String change = "PP " + old + " → " + value;
            if (value < old && use != null && !use.counted
                    && e.turn() >= use.row.turn && e.turn() - use.row.turn <= 1) {
                use.row.add(change); use.counted = true;
            } else {
                DisplayRow row = new DisplayRow(e, text);
                row.actor = ""; row.target = text.person(pokemon);
                row.action = value < old ? "PP 감소" : "PP 회복";
                row.result = KoreanNames.move(names.getOrDefault(move, move)) + " " + change;
                rows.add(row);
                if (use != null && e.turn() >= use.row.turn) use.counted = true;
            }
        }
    }

    private static String id(String move) {
        return move.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
