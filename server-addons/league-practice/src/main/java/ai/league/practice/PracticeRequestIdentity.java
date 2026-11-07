package ai.league.practice;

import com.google.gson.*;
import java.util.*;

/** Restore only the UUID field dropped by some battle form changes. All battle data is preserved. */
final class PracticeRequestIdentity {
    private static final String PREFIX="|request|";
    static boolean needsRepair(String message){
        if(!message.contains(PREFIX))return false;
        for(String line:message.split("\n",-1))if(line.startsWith(PREFIX)){
            JsonElement json=JsonParser.parseString(line.substring(PREFIX.length()));if(!json.isJsonObject())continue;
            JsonObject request=json.getAsJsonObject();if(!request.has("side"))continue;
            for(JsonElement entry:request.getAsJsonObject("side").getAsJsonArray("pokemon"))if(!hasUuid(entry.getAsJsonObject().get("details").getAsString()))return true;
        }
        return false;
    }
    static String repair(String message,Map<String,UUID> identities){
        String[] lines=message.split("\n",-1);
        for(int i=0;i<lines.length;i++)if(lines[i].startsWith(PREFIX)){
            JsonElement json=JsonParser.parseString(lines[i].substring(PREFIX.length()));if(!json.isJsonObject())continue;
            JsonObject request=json.getAsJsonObject();if(!request.has("side"))continue;
            for(JsonElement entry:request.getAsJsonObject("side").getAsJsonArray("pokemon")){
                JsonObject pokemon=entry.getAsJsonObject();String details=pokemon.get("details").getAsString();if(hasUuid(details))continue;
                String ident=pokemon.get("ident").getAsString();UUID id=identities.get(ident);
                if(id==null)throw new IllegalStateException("변신 포켓몬의 전투 식별자를 안전하게 확인하지 못했습니다: "+ident);
                String[] parts=details.split(", ",-1);pokemon.addProperty("details",parts[0]+", "+id+(parts.length>1?", "+String.join(", ",Arrays.copyOfRange(parts,1,parts.length)):""));
            }
            lines[i]=PREFIX+request;
        }
        return String.join("\n",lines);
    }
    private static boolean hasUuid(String details){String[] parts=details.split(", ",-1);if(parts.length<2)return false;try{return UUID.fromString(parts[1]).toString().equalsIgnoreCase(parts[1]);}catch(IllegalArgumentException e){return false;}}
}
