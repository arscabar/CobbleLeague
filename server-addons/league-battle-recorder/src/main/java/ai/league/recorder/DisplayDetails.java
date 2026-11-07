package ai.league.recorder;

import java.util.*;

final class DisplayDetails {
    static String from(BattleEvent e, DisplayText text) {
        if (e.type().equals("전투 종료")) return e.reason() == null ? "" : e.reason();
        String detail = text.clean(e.reason());
        if (e.type().startsWith("상태이상") || e.type().startsWith("능력치"))
            return detail.contains(" / ") ? detail.substring(0, detail.indexOf(" / ")) : detail;
        String[] p = e.raw() == null ? new String[0] : e.raw().split("\\|", -1);
        String tag = DisplayText.tag(e);
        int index = 3;
        String named;
        switch (tag) {
            case "-ability", "-endability" -> named = "특성: " + KoreanNames.ability(part(p, 3));
            case "-item", "-enditem", "bagitem" -> named = technical("item: " + part(p, 3));
            case "-prepare" -> named = KoreanNames.move(part(p, 3));
            case "-activate", "-start", "-end", "-singlemove", "-singleturn", "-sidestart", "-sideend",
                    "-terastallize" -> named = technical(part(p, 3));
            case "-mega", "-primal", "-burst", "-formechange", "detailschange" -> named = KoreanNames.species(part(p, 3));
            case "-weather", "-fieldstart", "-fieldend", "-fieldactivate" -> {
                index = 2; named = technical(part(p, 2));
            }
            default -> {
                return e.type().equals("피해") || e.type().equals("회복") ? text.clean(technical(e.reason())) : detail;
            }
        }
        List<String> pieces = new ArrayList<>();
        pieces.add(named);
        for (int i = index + 1; i < p.length; i++) {
            String field = p[i];
            if (field.isBlank() || field.equals("[silent]")) continue;
            if (field.startsWith("[of] ")) pieces.add("대상: " + text.clean(field.substring(5)));
            else if (field.startsWith("[from] ")) pieces.add("원인: " + technical(field.substring(7)));
            else pieces.add(field.matches("(?i)^(ability|move|item):.*") ? technical(field) : text.clean(field));
        }
        return String.join(" · ", pieces);
    }
    private static String technical(String field) {
        if (field.matches("(?i)^ability:.*")) return "특성: " + KoreanNames.ability(field);
        if (field.matches("(?i)^move:.*")) return "기술: " + KoreanNames.move(field);
        if (field.matches("(?i)^item:.*")) return field.replaceFirst("(?i)^item:\\s*", "도구: ");
        return KoreanNames.text(field);
    }
    private static String part(String[] fields, int n) { return n < fields.length ? fields[n] : ""; }
}
