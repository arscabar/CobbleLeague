package ai.league.recorder;

import java.util.*;
import java.util.regex.Pattern;

final class KoPhrase {
    private static final String LETTER = "A-Za-z0-9\\u00C0-\\u024F♀♂";
    private static final Pattern WORD = Pattern.compile("(?i:Unown-[!?])|(?:[0-9]{1,3}(?:,[0-9]{3})+|[" + LETTER + "]+)(?:[.'’:\\-][" + LETTER + "]+)*%?");
    private static final Pattern JOIN = Pattern.compile("[\\s.:'’\\-]*");
    private record Word(int start, int end) {}

    static String translate(String input, KoCatalog data) {
        if (input == null || input.isBlank()) return input == null ? "" : input;
        var matcher = WORD.matcher(input);
        List<Word> words = new ArrayList<>();
        while (matcher.find()) words.add(new Word(matcher.start(), matcher.end()));
        StringBuilder output = new StringBuilder();
        int position = 0;
        for (int i = 0; i < words.size();) {
            Word first = words.get(i);
            String found = null;
            int end = i;
            for (int j = Math.min(i + 7, words.size() - 1); j >= i; j--) {
                boolean joined = true;
                for (int k = i; k < j; k++)
                    if (!JOIN.matcher(input.substring(words.get(k).end, words.get(k + 1).start)).matches())
                        joined = false;
                if (!joined) continue;
                String phrase = input.substring(first.start, words.get(j).end);
                found = data.all.get(KoCatalog.id(phrase));
                if (found != null) { end = j; break; }
            }
            if (found == null) { i++; continue; }
            output.append(input, position, first.start).append(found);
            position = words.get(end).end;
            i = end + 1;
        }
        return output.append(input, position, input.length()).toString();
    }
}
