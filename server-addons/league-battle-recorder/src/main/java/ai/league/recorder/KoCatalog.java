package ai.league.recorder;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

final class KoCatalog {
    final Map<String, String> species = new LinkedHashMap<>(), moves = new LinkedHashMap<>();
    final Map<String, String> abilities = new LinkedHashMap<>(), all = new LinkedHashMap<>();
    final Map<String, String> speciesIds = new LinkedHashMap<>();

    static String id(String value) {
        if (value.equalsIgnoreCase("Unown-!")) return "unownexclamation";
        if (value.equalsIgnoreCase("Unown-?")) return "unownquestion";
        return Normalizer.normalize(value.replace("♀", "f").replace("♂", "m"), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static KoCatalog load() {
        JsonObject en = read("en_us"), ko = read("ko_kr");
        KoCatalog data = new KoCatalog();
        for (var entry : en.entrySet()) {
            String key = entry.getKey(), category, suffix;
            Map<String, String> target;
            if (key.matches("cobblemon\\.species\\.[^.]+\\.name")) {
                category = "cobblemon.species."; target = data.species;
                suffix = key.substring(category.length(), key.length() - 5);
            } else if (key.matches("cobblemon\\.move\\.[^.]+")) {
                category = "cobblemon.move."; target = data.moves; suffix = key.substring(category.length());
            } else if (key.matches("cobblemon\\.ability\\.[^.]+")) {
                category = "cobblemon.ability."; target = data.abilities; suffix = key.substring(category.length());
            } else continue;
            if (!ko.has(key)) continue;
            String korean = ko.get(key).getAsString();
            if (suffix.equals("nidoranf")) korean += "♀";
            if (suffix.equals("nidoranm")) korean += "♂";
            for (String alias : List.of(key, suffix, entry.getValue().getAsString())) {
                target.put(id(alias), korean); data.all.put(id(alias), korean);
            }
            if (target == data.species) data.speciesIds.put(id(suffix), korean);
        }
        KoForms.add(data, en, ko);
        KoFormAliases.add(data, ko);
        return data;
    }

    static JsonObject read(String language) {
        String path = "/assets/cobblemon/lang/" + language + ".json";
        try (InputStream stream = KoreanNames.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException error) { throw new IllegalStateException("Cannot load Cobblemon name translations", error); }
    }

    String species(String value) { return name(species, value); }
    String name(Map<String, String> names, String value) {
        return value == null ? "" : names.getOrDefault(id(value), value);
    }
}
