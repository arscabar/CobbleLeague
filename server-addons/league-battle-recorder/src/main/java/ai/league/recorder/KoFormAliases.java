package ai.league.recorder;

import com.google.gson.JsonObject;

final class KoFormAliases {
    static void add(KoCatalog data, JsonObject ko) {
        String[][] aliases = {
                {"basculinwhitestriped", "basculinwhite"},
                {"genesectdouse", "genesectwater"}, {"genesectshock", "genesectelectric"},
                {"genesectburn", "genesectfire"}, {"genesectchill", "genesectice"},
                {"toxtricitylowkeygmax", "toxtricitygmax"}
        };
        for (String[] pair : aliases) {
            String translated = data.species.get(pair[1]);
            if (translated != null) put(data, pair[0], translated);
        }
        String prefix = "cobblemon.ui.pokedex.info.form.ogerpon";
        for (String mask : new String[]{"teal", "wellspring", "hearthflame", "cornerstone"}) {
            String key = prefix + (mask.equals("teal") ? "" : "-" + mask);
            if (ko.has(key)) put(data, "ogerpon" + mask + "tera", data.speciesIds.get("ogerpon")
                    + " (" + ko.get(key).getAsString() + " · 테라스탈)");
        }
    }

    private static void put(KoCatalog data, String id, String korean) {
        data.species.put(id, korean); data.all.put(id, korean);
    }
}
