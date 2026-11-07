package ai.league.practice;

import java.util.*;

public record PokemonSpec(String species, String aspects, String nature, String ability,
                          String heldItem, List<String> moves, int[] ivs, int[] evs,
                          Map<String, String> features, String expectedForm) {
    private static final String[] STATS = {"hp", "attack", "defence", "special_attack", "special_defence", "speed"};

    public PokemonSpec {
        moves = moves == null ? null : List.copyOf(moves);
        ivs = ivs == null ? null : ivs.clone();
        evs = evs == null ? null : evs.clone();
        features = features == null ? Map.of() : Map.copyOf(features);
    }
    public PokemonSpec(String species, String aspects, String nature, String ability, String heldItem,
                       List<String> moves, int[] ivs, int[] evs) {
        this(species, aspects, nature, ability, heldItem, moves, ivs, evs, Map.of(), null);
    }
    @Override public int[] ivs() { return ivs == null ? null : ivs.clone(); }
    @Override public int[] evs() { return evs == null ? null : evs.clone(); }

    public void validate() {
        SpecChecks.id(species, "species"); SpecChecks.id(nature, "nature");
        SpecChecks.id(ability, "ability");
        if (heldItem != null && !heldItem.isBlank()) SpecChecks.id(heldItem, "heldItem");
        if (aspects != null && !aspects.isBlank())
            for (String aspect : aspects.strip().split("\\s+")) SpecChecks.id(aspect, "aspects");
        if (moves == null || moves.isEmpty() || moves.size() > 4 || new HashSet<>(moves).size() != moves.size())
            throw new IllegalArgumentException("moves: 서로 다른 기술 1~4개가 필요합니다.");
        moves.forEach(move -> SpecChecks.id(move, "moves"));
        features.forEach((key,value) -> { SpecChecks.id(key,"feature key"); SpecChecks.id(value,"feature value"); });
        SpecChecks.stats(ivs, 31, "ivs");
        if (SpecChecks.stats(evs, 252, "evs") > 510)
            throw new IllegalArgumentException("evs: 합계는 510 이하여야 합니다.");
    }

    public String properties() {
        validate();
        String item = heldItem == null || heldItem.isBlank() ? "minecraft:air" : heldItem;
        StringBuilder text = new StringBuilder("species=" + species + " level=50 nature=" + nature
                + " ability=" + ability + " held_item=" + item + " moves=" + String.join(",", moves));
        if (aspects != null && !aspects.isBlank())
            for (String aspect : aspects.strip().split("\\s+")) text.append(" aspect=").append(aspect);
        for (int i = 0; i < STATS.length; i++) text.append(' ').append(STATS[i]).append("_iv=").append(ivs[i])
                .append(' ').append(STATS[i]).append("_ev=").append(evs[i]);
        features.forEach((key,value) -> text.append(' ').append(key).append('=').append(value));
        return text.toString();
    }
}
