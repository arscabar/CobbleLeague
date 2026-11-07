package ai.league.recorder;

import java.util.Map;

final class DisplaySignals {
    static boolean merge(BattleEvent e, DisplayMove move, Map<String, DisplayRow> lastHp, DisplayText text) {
        String tag = DisplayText.tag(e), id = DisplayText.target(e);
        DisplayRow target = move == null ? null : move.target(id);
        if (tag.equals("-status") && move != null && !e.raw().contains("[from]")) {
            if (target == null) target = move.effect(e);
            target.add(text.detail(e) + " 부여"); target.update(e, text); return true;
        }
        if (tag.equals("-activate") && e.type().equals("매직미러 반사") && move != null
                && move.event.targetId().equals(e.pokemonId())) {
            move.first.add("매직미러에 반사됨"); return true;
        }
        String result = switch (tag) {
            case "-miss" -> "빗나감"; case "-immune" -> "효과 없음";
            case "-fail" -> "실패"; case "-block" -> "방어됨";
            case "-notarget" -> "대상 없음"; case "-nothing" -> "효과 없음"; default -> "";
        };
        if (!result.isBlank() && move != null) {
            DisplayRow row = target == null ? move.first : target;
            row.add(result); if (row.damage == null) row.damage = 0;
            return true;
        }
        if (e.type().equals("기절")) {
            DisplayRow hp = lastHp.get(e.targetId());
            if (hp != null && hp.turn == e.turn() && hp.hpAfter != null && hp.hpAfter == 0
                    && e.hpAfter() != null && e.hpAfter() == 0) {
                hp.add("기절"); hp.snapshots(e, text); return true;
            }
        }
        return false;
    }
}
