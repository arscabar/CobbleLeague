package ai.league.recorder;

import java.util.*;

final class BattleDisplay {
    private static final Set<String> META = Set.of("player", "teamsize", "poke", "teampreview", "tier", "gametype", "gen", "rated", "rule", "clearpoke", "seed", "start", "upkeep", "win", "tie", "-hitcount", "t:");
    private final List<DisplayRow> rows = new ArrayList<>();
    private final Map<String, LinkedHashSet<String>> pending = new HashMap<>();
    private final Map<String, DisplayRow> lastHp = new HashMap<>();
    private final DisplayText text;
    private final DisplayPP pp;
    private DisplayMove move;

    BattleDisplay(BattleRecord record) {
        text = new DisplayText(record);
        pp = new DisplayPP(record);
        for (BattleEvent e : record.events) consume(e);
        closeMove();
    }

    List<List<?>> rows() {
        List<List<?>> result = new ArrayList<>();
        for (DisplayRow row : rows) result.add(row.cells(result.size() + 1));
        return result;
    }

    private void consume(BattleEvent e) {
        String tag = DisplayText.tag(e);
        if (tag.equals("turn") || tag.equals("upkeep")) { closeMove(); return; }
        if (META.contains(tag)) return;
        if (tag.equals("pp_update")) { pp.accept(e, rows, text); return; }
        if (tag.equals("move")) { closeMove(); move = new DisplayMove(e, rows, text); pp.used(move); return; }
        if (e.type().equals("포켓몬 교체") || e.type().equals("전투 종료")) closeMove();
        String marker = switch (tag) {
            case "-crit" -> "급소"; case "-supereffective" -> "효과가 굉장함";
            case "-resisted" -> "효과가 별로임"; default -> "";
        };
        if (!marker.isBlank()) { pending.computeIfAbsent(e.targetId(), k -> new LinkedHashSet<>()).add(marker); return; }
        if (DisplaySignals.merge(e, move, lastHp, text)) return;
        DisplayRow row;
        if (e.type().equals("피해") && move != null && move.matches(e)) row = move.hit(e);
        else { row = DisplayEffects.row(e, text); rows.add(row); }
        if (e.type().equals("피해")) {
            if (!e.move().isBlank()) {
                Set<String> flags = pending.remove(e.targetId());
                if (flags != null) flags.forEach(row::add);
            }
            lastHp.put(e.targetId(), row);
        } else if (e.type().equals("회복") || e.type().startsWith("HP ") || e.type().equals("최대 HP 변경")) lastHp.put(e.targetId(), row);
    }

    private void closeMove() {
        if (move != null) pending.forEach(move::unconsumed);
        move = null; pending.clear();
    }
}
