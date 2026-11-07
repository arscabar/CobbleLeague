package ai.league.practice;

import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.ShowdownInterpreter;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import java.util.UUID;
import java.util.*;
import com.google.gson.*;
import com.cobblemon.mod.common.battles.runner.ShowdownService;
import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService;

/** Separates Graal output delivery from Java interpretation for practice battles only. */
public final class PracticeBattleOutput {
    private static final OrderedBattleMessages QUEUE = new OrderedBattleMessages();
    public static boolean accept(String battleId, String message) {
        UUID id = UUID.fromString(battleId);
        var battle = BattleRegistry.getBattle(id);
        if (battle == null) return false;
        for (var actor : battle.getActors()) {
            if (actor instanceof NPCBattleActor npc && PracticeNpcTags.isPracticeNpc(npc.getEntity())) {
                QUEUE.add(id, message);
                return true;
            }
        }
        return false;
    }

    static void tick() {
        QUEUE.drain((id, message) -> {
            var battle = BattleRegistry.getBattle(id);
            if (battle == null || battle.getEnded()) { QUEUE.clear(id); return; }
            try {
                if(PracticeRequestIdentity.needsRepair(message))message=PracticeRequestIdentity.repair(message,identities(battle));
                ShowdownInterpreter.INSTANCE.interpretMessage(id, message);
            }
            catch (RuntimeException | LinkageError error) {
                LeaguePractice.LOG.error("훈련 전투 메시지 처리 실패; 해당 복사본 전투를 종료합니다: {}", id, error);
                QUEUE.clear(id);
                NpcBattleCleanup.stop(battle);
            }
        });
    }
    private static Map<String,UUID> identities(com.cobblemon.mod.common.api.battles.model.PokemonBattle battle){
        Map<String,UUID> result=new HashMap<>();Set<String> ambiguous=new HashSet<>();Set<UUID> actual=new HashSet<>();
        for(var actor:battle.getActors())for(var p:actor.getPokemonList())actual.add(p.getUuid());
        if(!(ShowdownService.Companion.getService() instanceof GraalShowdownService service))return result;
        String json=service.getContext().eval("js","JSON.stringify((battleMap.get('"+battle.getBattleId()+"')?.battle?.sides||[]).flatMap(s=>s.pokemon.map(p=>({ident:p.fullname,uuid:p.uuid}))))").asString();
        for(JsonElement e:JsonParser.parseString(json).getAsJsonArray()){
            JsonObject o=e.getAsJsonObject();if(!o.has("ident")||!o.has("uuid"))continue;
            UUID uuid;try{uuid=UUID.fromString(o.get("uuid").getAsString());}catch(IllegalArgumentException error){continue;}
            if(!actual.contains(uuid))continue;String ident=o.get("ident").getAsString();
            if(result.containsKey(ident)&&!result.get(ident).equals(uuid))ambiguous.add(ident);else result.put(ident,uuid);
        }
        ambiguous.forEach(result::remove);return result;
    }
    static boolean pending(UUID id) { return QUEUE.pending(id); }
    static void clear(UUID id) { QUEUE.clear(id); }
}
