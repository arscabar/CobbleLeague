package ai.league.recorder;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

final class BattleSnapshot {
    static BattleRecord capture(PokemonBattle battle, ArenaConfig arena) {
        if (!battle.isPvP()) return null;
        List<BattleActor> actors = new ArrayList<>();
        battle.getActors().forEach(actors::add);
        if (actors.size() != 2 || actors.stream().anyMatch(a -> !(a instanceof PlayerBattleActor)))
            return null;
        for (BattleActor actor : actors) {
            var player = ((PlayerBattleActor) actor).getEntity();
            if (!arena.contains(player.level().dimension().location().toString(),
                    player.getX(), player.getY(), player.getZ())) return null;
        }
        BattleRecord record = new BattleRecord(battle.getBattleId(), Instant.now(),
                actors.get(0).getName().getString(), actors.get(1).getName().getString(),
                arena.arenaName, arena.dimension);
        for (BattleActor actor : actors) {
            int slot = 0;
            for (var member : actor.getPokemonList()) {
                var pokemon = member.getEffectedPokemon();
                String nickname = pokemon.getNickname() == null ? "" : pokemon.getNickname().getString();
                String moves = member.getMoveSet().getMoves().stream()
                        .map(move -> move.getName()).collect(Collectors.joining(", "));
                record.party.add(new PokemonState(actor.getShowdownId(), actor.getName().getString(),
                        ++slot, member.getUuid().toString(), pokemon.getSpecies().getName(),
                        nickname, pokemon.getLevel(), moves, member.getHealth(), member.getMaxHealth()));
            }
        }
        return record;
    }
}
