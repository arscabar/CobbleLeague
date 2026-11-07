package ai.league.recorder;

import java.util.Arrays;
import java.util.Locale;

final class EffectEvents {
    static void handle(BattleTimeline t, String[] p, String raw) {
        String tag = BattleTimeline.part(p, 1);
        String details = String.join(" ", Arrays.copyOfRange(p, Math.min(2, p.length), p.length)).strip();
        PokemonState subject = t.resolve(BattleTimeline.part(p, 2));
        PokemonState source = null, target = subject;
        for (String field : p)
            if (field.startsWith("[of] ")) source = t.resolve(field.substring(5));
        String type = EffectLabels.type(tag), reason = details, move = "";
        if ((tag.equals("-immune") || tag.equals("-fail")) && subject != null)
            t.pendingDamageCause.remove(subject.uuid);
        switch (tag) {
            case "-transform" -> {
                source = subject;
                target = t.resolve(BattleTimeline.part(p, 3));
                reason = "대상의 모습으로 변신; 원래 포켓몬 UUID 유지 / " + details;
            }
            case "-boost", "-unboost", "-setboost" -> {
                String sign = tag.equals("-boost") ? "+" : tag.equals("-unboost") ? "-" : "";
                reason = EffectLabels.stat(BattleTimeline.part(p, 3)) + " " + sign
                        + BattleTimeline.part(p, 4) + "단계 / " + details;
            }
            case "-status", "-curestatus" -> {
                if (subject != null) subject.status = tag.equals("-status") ? BattleTimeline.part(p, 3) : "";
                reason = EffectLabels.status(BattleTimeline.part(p, 3)) + " / " + details;
            }
            case "-cureteam" -> t.record.party.stream()
                    .filter(mon -> mon.side.equals(BattleTimeline.part(p, 2))).forEach(mon -> mon.status = "");
            case "-prepare" -> move = BattleTimeline.part(p, 3);
            case "-activate" -> {
                target = source;
                source = subject;
                if (details.toLowerCase(Locale.ROOT).contains("magic bounce")) type = "매직미러 반사";
                if (subject != null && BattleTimeline.part(p, 3).equalsIgnoreCase("move: Powder"))
                    t.pendingDamageCause.put(subject.uuid, "move: Powder");
            }
            case "-swapboost" -> {
                source = subject;
                target = t.resolve(BattleTimeline.part(p, 3));
            }
            case "-copyboost" -> source = t.resolve(BattleTimeline.part(p, 3));
            case "-start", "-end" -> {
                if (details.toLowerCase(Locale.ROOT).contains("substitute"))
                    type = tag.equals("-start") ? "대타 생성" : "대타 종료";
                String effect = BattleTimeline.part(p, 3).strip();
                if (tag.equals("-end") && subject != null
                        && effect.matches("(?i)(?:move:\\s*)?(?:Future Sight|Doom Desire)"))
                    t.pendingDamageCause.put(subject.uuid, effect);
            }
        }
        t.add(type, source, target, move, null, null, null, "", "", reason, raw);
    }
}
