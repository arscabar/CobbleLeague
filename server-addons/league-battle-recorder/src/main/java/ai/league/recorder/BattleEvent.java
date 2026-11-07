package ai.league.recorder;

import java.util.*;

public record BattleEvent(int order, int turn, String time, String type, String player,
                          String pokemon, String pokemonId, String move, String targetPlayer,
                          String targetPokemon, String targetId, Integer damage,
                          Integer hpBefore, Integer hpAfter, Integer hpMax,
                          String player1Hp, String player2Hp, String withdrawn,
                          String sentOut, String reason, String raw) {
    public List<Object> cells() {
        return Arrays.asList(order, turn, time, type, player, pokemon, pokemonId, move,
                targetPlayer, targetPokemon, targetId, damage, hpBefore, hpAfter, hpMax,
                player1Hp, player2Hp, withdrawn, sentOut, reason, raw);
    }
}
