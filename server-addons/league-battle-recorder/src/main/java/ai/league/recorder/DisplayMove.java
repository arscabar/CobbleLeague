package ai.league.recorder;

import java.util.*;

final class DisplayMove {
    final BattleEvent event;
    final DisplayRow first;
    final List<DisplayRow> output;
    final DisplayText text;
    final Map<String, List<DisplayRow>> hits = new HashMap<>();
    private final Map<String, DisplayRow> effects = new HashMap<>();
    private boolean used;

    DisplayMove(BattleEvent event, List<DisplayRow> output, DisplayText text) {
        this.event = event; this.output = output; this.text = text;
        first = new DisplayRow(event, text);
        if (event.type().contains("반사")) first.add("매직미러 반사");
        output.add(first);
    }

    boolean matches(BattleEvent e) {
        return event.turn() == e.turn() && !event.pokemonId().isBlank()
                && event.pokemonId().equals(e.pokemonId()) && event.move().equals(e.move());
    }

    DisplayRow target(String id) {
        List<DisplayRow> rows = hits.get(id);
        if (rows != null && !rows.isEmpty()) return rows.getLast();
        if (effects.containsKey(id)) return effects.get(id);
        return Objects.equals(first.targetId, id) ? first : null;
    }

    DisplayRow effect(BattleEvent e) {
        DisplayRow row = target(e.targetId());
        if (row != null) return row;
        row = new DisplayRow(event, text); row.targetId = e.targetId();
        row.target = text.person(e.targetPlayer(), e.targetPokemon());
        effects.put(e.targetId(), row); output.add(row);
        return row;
    }

    DisplayRow hit(BattleEvent e) {
        DisplayRow row = effects.remove(e.targetId());
        if (row == null) {
            row = used ? new DisplayRow(event, text) : first;
            if (used) output.add(row);
        }
        if (row == first) used = true;
        row.targetId = e.targetId();
        row.target = text.person(e.targetPlayer(), e.targetPokemon());
        row.damage = e.damage(); row.update(e, text);
        List<DisplayRow> same = hits.computeIfAbsent(e.targetId(), k -> new ArrayList<>());
        if (same.size() == 1) same.getFirst().add("1타");
        same.add(row);
        if (same.size() > 1) row.add(same.size() + "타");
        return row;
    }

    void unconsumed(String id, Set<String> flags) {
        DisplayRow row = target(id);
        if (row == null || row.damage != null) {
            DisplayRow prior = row;
            row = new DisplayRow(event, text); row.targetId = id;
            row.target = text.person(id); row.targetHp = "";
            if (prior != null) { row.p1Hp = prior.p1Hp; row.p2Hp = prior.p2Hp; }
            output.add(row);
        }
        flags.forEach(row::add);
        row.add("대상 HP 변화 기록 없음");
    }
}
