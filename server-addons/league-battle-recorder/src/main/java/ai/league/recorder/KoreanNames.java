package ai.league.recorder;

public final class KoreanNames {
    private static KoCatalog data;
    private KoreanNames() {}

    private static synchronized KoCatalog catalog() {
        if (data == null) data = KoCatalog.load();
        return data;
    }

    public static String species(String value) { return catalog().species(value); }
    public static String move(String value) { return catalog().name(catalog().moves, strip(value, "move|기술")); }
    public static String ability(String value) { return catalog().name(catalog().abilities, strip(value, "ability|특성")); }
    public static String text(String value) { return KoPhrase.translate(value, catalog()); }
    public static String moves(String value) { return text(value); }

    private static String strip(String value, String prefix) {
        return value == null ? "" : value.replaceFirst("(?i)^(?:" + prefix + "):\\s*", "");
    }

    public static String label(PokemonState mon) {
        String name = species(mon.species);
        return mon.nickname.isBlank() ? name : name + " (" + mon.nickname + ")";
    }
}
