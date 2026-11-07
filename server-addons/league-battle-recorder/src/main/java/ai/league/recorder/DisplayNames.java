package ai.league.recorder;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

final class DisplayNames {
    private final BattleRecord record;
    private final Map<String, String> labels = new HashMap<>();
    private final Pattern active;

    DisplayNames(BattleRecord record) {
        this.record = record;
        for (PokemonState p : record.party) labels.put(p.label(), KoreanNames.label(p));
        String names = labels.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote).collect(Collectors.joining("|"));
        active = names.isBlank() ? null : Pattern.compile("(^|;\\s*)p\\d+[a-z]?\\s+(" + names + ")\\s+(\\d+/\\d+)(?=;|$)");
    }

    String person(String player, String label) {
        String shown = label == null ? "" : label;
        for (PokemonState p : record.party)
            if (Objects.equals(p.player, player) && p.label().equals(shown)) {
                shown = KoreanNames.label(p); break;
            }
        String owner = player == null ? "" : player;
        return owner.isBlank() ? shown : shown.isBlank() ? owner : owner + " · " + shown;
    }

    String person(String uuid) {
        PokemonState p = record.pokemon(uuid);
        return p == null ? "" : person(p.player, p.label());
    }

    String switched(String value) {
        if (value == null) return "";
        for (PokemonState p : record.party) {
            String prefix = p.label() + " [" + p.uuid + "] ";
            if (value.startsWith(prefix)) {
                String hp = value.substring(prefix.length());
                if (hp.matches("\\d+/\\d+")) return KoreanNames.label(p) + "(" + hp + ")";
            }
        }
        return null;
    }

    String hp(String value) {
        if (value == null || active == null) return null;
        Matcher match = active.matcher(value);
        StringBuilder result = new StringBuilder();
        int end = 0, count = 0;
        String hp = "";
        while (match.find()) {
            if (match.start() != end) return null;
            if (count++ > 0) result.append("; ");
            hp = match.group(3);
            result.append(labels.get(match.group(2))).append(' ').append(hp);
            end = match.end();
        }
        return end != value.length() || count == 0 ? null : count == 1 ? hp : result.toString();
    }
}
