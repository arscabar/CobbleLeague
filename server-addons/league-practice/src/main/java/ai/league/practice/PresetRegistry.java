package ai.league.practice;

import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.*;
import com.cobblemon.mod.common.pokemon.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.Arrays;

final class PresetRegistry {
    static Species species(String id) {
        return id.contains(":") ? PokemonSpecies.getByIdentifier(ResourceLocation.parse(id))
                : PokemonSpecies.getByName(id);
    }
    static void validate(PokemonSpec spec) {
        spec.validate();
        require(species(spec.species()) != null, "존재하지 않는 species: " + spec.species());
        for (String move : spec.moves())
            require(Moves.getByName(move) != null, "존재하지 않는 move: " + move);
        require(Abilities.get(spec.ability()) != null, "존재하지 않는 ability: " + spec.ability());
        require(Natures.getNature(spec.nature()) != null, "존재하지 않는 nature: " + spec.nature());
        if (spec.heldItem() != null && !spec.heldItem().isBlank())
            require(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(spec.heldItem())),
                    "존재하지 않는 heldItem: " + spec.heldItem());
    }
    static void verify(Pokemon pokemon, PokemonSpec spec) {
        if (spec.aspects() != null && !spec.aspects().isBlank())
            require(pokemon.getAspects().containsAll(Arrays.asList(spec.aspects().strip().split("\\s+"))),
                    "aspects 생성 결과 불일치: " + spec.aspects());
        require(pokemon.getSpecies() == species(spec.species()), "species 생성 결과 불일치");
        require(pokemon.getAbility().getName().equals(Abilities.get(spec.ability()).getName()),
                "ability 생성 결과 불일치");
        require(pokemon.getNature().getName().equals(Natures.getNature(spec.nature()).getName()),
                "nature 생성 결과 불일치");
        var moves = pokemon.getMoveSet().getMoves().stream().map(move -> move.getName()).toList();
        var expected = spec.moves().stream().map(move -> Moves.getByName(move).getName()).toList();
        require(moves.equals(expected), "moves 생성 결과 불일치");
        String item = spec.heldItem() == null || spec.heldItem().isBlank() ? "minecraft:air" : spec.heldItem();
        require(pokemon.heldItem().getItem() == BuiltInRegistries.ITEM.get(ResourceLocation.parse(item)),
                "heldItem 생성 결과 불일치");
        if (spec.features().containsKey("gmax_factor")) require(pokemon.getGmaxFactor() == Boolean.parseBoolean(spec.features().get("gmax_factor")), "거다이맥스 인자 생성 결과 불일치");
        if (spec.features().containsKey("dmax_level")) require(pokemon.getDmaxLevel() == Integer.parseInt(spec.features().get("dmax_level")), "다이맥스 레벨 생성 결과 불일치");
        String tera = spec.features().get("tera_type");
        if (tera != null) require(pokemon.getTeraType().getId().toString().equals(tera), "테라 타입 생성 결과 불일치");
    }
    static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
}
