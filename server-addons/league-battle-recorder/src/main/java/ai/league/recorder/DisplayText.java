package ai.league.recorder;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

final class DisplayText {
    private final DisplayNames names;
    private final Map<String, String> labels = new LinkedHashMap<>();
    private final Pattern references;
    DisplayText(BattleRecord record) {
        names = new DisplayNames(record);
        for (PokemonState p : record.party) labels.put(p.uuid, KoreanNames.label(p));
        String known = labels.keySet().stream().map(Pattern::quote).collect(Collectors.joining("|"));
        String uuid = "[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}";
        references = Pattern.compile("(?<![\\w-])(?:" + (known.isBlank() ? "" : known + "|") + uuid + ")(?![\\w-])");
    }

    String clean(String value) {
        String text = value == null ? "" : value;
        for (String uuid : labels.keySet()) text = text.replace(" [" + uuid + "]", "");
        text = text.replaceAll("\\bp\\d+[a-z]?:\\s*", "")
                .replaceAll("(^|;\\s*)p\\d+[a-z]?\\s+", "$1")
                .replace("[from]", "원인:").replace("[of]", "대상:")
                .replace("ability:", "특성:").replace("item:", "도구:").replace("move:", "기술:")
                .replace('|', ' ').replaceAll("\\s+", " ").strip();
        return references.matcher(text).replaceAll(m -> Matcher.quoteReplacement(labels.getOrDefault(m.group(), "개체")));
    }

    String person(String player, String pokemon) {
        return names.person(player, pokemon);
    }

    String person(String uuid) {
        return names.person(uuid);
    }

    String detail(BattleEvent e) {
        return DisplayDetails.from(e, this);
    }

    String switched(String value) {
        String named = names.switched(value);
        return named == null ? clean(value).replaceAll("\\s+(\\d+/\\d+)$", "($1)") : named;
    }

    String hp(String value) {
        String named = names.hp(value);
        if (named != null) return named;
        String text = clean(value);
        if (text.contains(";")) return text;
        var match = Pattern.compile("(\\d+/\\d+)$").matcher(text);
        return match.find() ? match.group(1) : text;
    }

    static String tag(BattleEvent e) {
        String[] parts = e.raw() == null ? new String[0] : e.raw().split("\\|", -1);
        return parts.length > 1 ? parts[1] : "";
    }

    static String target(BattleEvent e) {
        if (!tag(e).equals("-miss")) return e.targetId();
        String[] parts = e.raw().split("\\|", -1);
        if (parts.length < 4) return e.targetId();
        int colon = parts[3].indexOf(':');
        return (colon < 0 ? parts[3] : parts[3].substring(colon + 1)).strip();
    }

    static String cause(String value) {
        return switch (value == null ? "" : value) {
            case "tox" -> "맹독"; case "psn" -> "독"; case "brn" -> "화상";
            case "recoil" -> "반동"; case "drain" -> "흡수 회복";
            default -> value == null ? "" : value;
        };
    }
}
