package ai.league.recorder;

import com.google.gson.JsonObject;
import java.util.*;

final class KoForms {
    static void add(KoCatalog data, JsonObject en, JsonObject ko) {
        String prefix = "cobblemon.ui.pokedex.info.form.";
        List<String> species = new ArrayList<>(data.speciesIds.keySet());
        species.sort(Comparator.comparingInt(String::length).reversed());
        for (String key : en.keySet()) {
            if (!key.startsWith(prefix) || !ko.has(key)) continue;
            String form = key.substring(prefix.length()), id = KoCatalog.id(form);
            String base = species.stream().filter(s -> id.startsWith(s) && id.length() > s.length())
                    .findFirst().orElse(null);
            if (base == null) continue;
            String name = data.speciesIds.get(base), value = ko.get(key).getAsString();
            String translated = value.contains(name) ? value : name + " (" + value + ")";
            data.species.putIfAbsent(id, translated);
            data.all.putIfAbsent(id, translated);
        }
    }
}
