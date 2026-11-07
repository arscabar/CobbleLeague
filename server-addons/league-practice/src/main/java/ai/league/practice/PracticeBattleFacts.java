package ai.league.practice;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.runner.ShowdownService;
import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService;
import com.google.gson.*;

/** Read-only field snapshot. Never calls damage calculation or consumes the live engine's RNG. */
final class PracticeBattleFacts {
    final JsonObject data;
    private PracticeBattleFacts(JsonObject data){this.data=data;}
    static PracticeBattleFacts read(PokemonBattle battle) {
        if(battle==null)return empty();
        try {
            if(!(ShowdownService.Companion.getService() instanceof GraalShowdownService service))return empty();
            String value=service.getContext().eval("js","""
                JSON.stringify((()=>{const b=battleMap.get('%s')?.battle;if(!b)return {};
                const active=b.sides.flatMap(s=>s.active).filter(Boolean);
                return {room:!!b.field.pseudoWeather.trickroom,weather:b.field.weather||'',terrain:b.field.terrain||'',
                  weatherSuppressed:active.some(p=>['airlock','cloudnine'].includes(p.ability)),
                  sides:b.sides.map(s=>({id:s.id,rock:!!s.sideConditions.stealthrock,
                    spikes:s.sideConditions.spikes?.layers||0,toxic:s.sideConditions.toxicspikes?.layers||0,
                    web:!!s.sideConditions.stickyweb,tailwind:!!s.sideConditions.tailwind,reflect:!!s.sideConditions.reflect,
                    screen:!!s.sideConditions.lightscreen,veil:!!s.sideConditions.auroraveil}))};})())
                """.formatted(battle.getBattleId())).asString();
            return new PracticeBattleFacts(JsonParser.parseString(value).getAsJsonObject());
        }catch(RuntimeException error){return empty();}
    }
    static PracticeBattleFacts empty(){return new PracticeBattleFacts(new JsonObject());}
    boolean room(){return bool(data,"room");}
    String text(String name){return data.has(name)?data.get(name).getAsString():"";}
    JsonObject side(String id){
        if(data.has("sides"))for(JsonElement e:data.getAsJsonArray("sides"))if(e.getAsJsonObject().get("id").getAsString().equals(id))return e.getAsJsonObject();
        return new JsonObject();
    }
    static boolean bool(JsonObject o,String key){return o.has(key)&&o.get(key).getAsBoolean();}
    static int number(JsonObject o,String key){return o.has(key)?o.get(key).getAsInt():0;}
}
