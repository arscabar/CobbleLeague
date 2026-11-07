package ai.league.practice;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.types.tera.TeraTypes;
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import java.util.*;
import ai.league.practice.mixin.PracticeHeldItemAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class NpcPartyFactory {
    private static final Stats[] STATS = {Stats.HP, Stats.ATTACK, Stats.DEFENCE,
            Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};
    private NpcPartyFactory() {}

    public static void validate(TeamSpec team) {
        team.validate();
        for (int i = 0; i < team.pokemon().size(); i++) {
            try { PresetRegistry.validate(team.pokemon().get(i)); }
            catch (RuntimeException error) { throw invalid(team, i, error); }
        }
    }

    static NPCPartyStore create(NPCEntity npc, TeamSpec team) {
        validate(team);
        NPCPartyStore party = new NPCPartyStore(npc);
        for (int slot = 0; slot < team.pokemon().size(); slot++) {
            try {
                PokemonSpec spec = team.pokemon().get(slot);
                // Mega Showdown's held-item swap listener dereferences a live entity for
                // Rusted Sword/Shield. Fresh NPC presets do not have one yet. Initialize
                // the fresh object's held stack directly; normal battle form logic still runs.
                var pokemon = PokemonProperties.Companion.parse(spec.properties().replaceFirst("held_item=\\S+", "held_item=minecraft:air")).create();
                if (spec.heldItem() != null && !spec.heldItem().isBlank())
                    ((PracticeHeldItemAccessor)(Object)pokemon).leaguePractice$initializeHeldItem(
                            new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(spec.heldItem()))));
                if (spec.aspects() != null && !spec.aspects().isBlank()) {
                    pokemon.setForcedAspects(new HashSet<>(Arrays.asList(spec.aspects().strip().split("\\s+"))));
                }
                // PokemonProperties does not retain the requested move-slot order.
                pokemon.getMoveSet().clear();
                for (int moveSlot = 0; moveSlot < spec.moves().size(); moveSlot++)
                    pokemon.getMoveSet().setMove(moveSlot, Moves.getByName(spec.moves().get(moveSlot)).create());
                if (spec.features().containsKey("gmax_factor")) pokemon.setGmaxFactor(Boolean.parseBoolean(spec.features().get("gmax_factor")));
                if (spec.features().containsKey("dmax_level")) pokemon.setDmaxLevel(Integer.parseInt(spec.features().get("dmax_level")));
                String teraId = spec.features().get("tera_type");
                if (teraId != null) {
                    boolean found = false;
                    for (var tera : TeraTypes.INSTANCE) {
                        if (tera.getId().toString().equals(teraId)) {
                            pokemon.setTeraType(tera); found = true; break;
                        }
                    }
                    PresetRegistry.require(found, "존재하지 않는 테라 타입: " + teraId);
                }
                if (spec.expectedForm() != null && !spec.expectedForm().isBlank())
                    PresetRegistry.require(pokemon.getForm().getName().equalsIgnoreCase(spec.expectedForm()),
                            "form 생성 결과 불일치: " + spec.expectedForm() + " / " + pokemon.getForm().getName());
                PresetRegistry.verify(pokemon, spec);
                int[] ivs = spec.ivs(), evs = spec.evs();
                for (int i = 0; i < STATS.length; i++) {
                    PresetRegistry.require(pokemon.getIvs().getOrDefault(STATS[i]) == ivs[i],
                            STATS[i] + " IV 생성 결과 불일치");
                    PresetRegistry.require(pokemon.getEvs().getOrDefault(STATS[i]) == evs[i],
                            STATS[i] + " EV 생성 결과 불일치");
                }
                pokemon.setLevel(50);
                pokemon.heal();
                party.set(slot, pokemon);
            } catch (RuntimeException error) { throw invalid(team, slot, error); }
        }
        return party;
    }

    private static IllegalArgumentException invalid(TeamSpec team, int slot, RuntimeException error) {
        return new IllegalArgumentException(team.id() + " / " + (slot + 1) + "번: " + error.getMessage(), error);
    }
}
