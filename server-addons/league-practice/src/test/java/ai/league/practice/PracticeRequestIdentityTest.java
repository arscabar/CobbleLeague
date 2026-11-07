package ai.league.practice;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PracticeRequestIdentityTest {
    private final UUID id=UUID.fromString("12345678-1234-4234-8234-123456789abc");
    private String frame(String details){
        var mon=new JsonObject();mon.addProperty("ident","p1: Zacian");mon.addProperty("details",details);mon.addProperty("condition","150/150");mon.addProperty("active",false);
        var list=new JsonArray();list.add(mon);var side=new JsonObject();side.add("pokemon",list);var request=new JsonObject();request.add("side",side);
        return "sideupdate\np1\n|request|"+request+"\n";
    }
    @Test void restoresCrownedUuidWithoutChangingFormLevelOrHp(){
        String input=frame("Zacian-Crowned, L50");assertTrue(PracticeRequestIdentity.needsRepair(input));
        String output=PracticeRequestIdentity.repair(input,Map.of("p1: Zacian",id));assertFalse(PracticeRequestIdentity.needsRepair(output));
        assertTrue(output.contains("Zacian-Crowned, "+id+", L50"));assertTrue(output.contains("150/150"));assertTrue(output.endsWith("\n"));
    }
    @Test void validIdentityIsNeverOverwritten(){String s=frame("Zacian-Crowned, "+id+", L50");assertFalse(PracticeRequestIdentity.needsRepair(s));assertTrue(PracticeRequestIdentity.repair(s,Map.of("p1: Zacian",UUID.randomUUID())).contains(id.toString()));}
    @Test void refusesToGuessAnAmbiguousOrUnknownPokemon(){assertThrows(IllegalStateException.class,()->PracticeRequestIdentity.repair(frame("Zacian-Crowned, L50"),Map.of()));}
    @Test void unrelatedOutputIsUnchanged(){String s="update\n|move|p1a: Pikachu|Thunderbolt|p2a: Blastoise\n";assertFalse(PracticeRequestIdentity.needsRepair(s));assertEquals(s,PracticeRequestIdentity.repair(s,Map.of()));}
}
