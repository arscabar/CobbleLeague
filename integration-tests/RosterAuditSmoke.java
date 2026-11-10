package ai.league.practice;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import java.util.*;
import java.nio.file.*;
import com.google.gson.*;
public class RosterAuditSmoke implements ModInitializer {
 public void onInitialize(){CommandRegistrationCallback.EVENT.register((d,r,e)->d.register(Commands.literal("rosteraudit").requires(s->s.hasPermission(4)).executes(c->{if(!Boolean.getBoolean("league.smoke.enabled"))return 0;try{run();return 1;}catch(Exception ex){ex.printStackTrace();throw new RuntimeException(ex);}})));}
 static void run()throws Exception{
  Object roster=Class.forName("dev.cobbleleague.RandomRoster").getConstructor().newInstance();var method=roster.getClass().getDeclaredMethod("all");method.setAccessible(true);var candidates=(List<?>)method.invoke(roster);Set<String> pool=new HashSet<>();
  for(Object candidate:candidates)pool.add((String)LeagueConvenienceChecks.call(candidate,"getKey"));
  var rows=new ArrayList<Map<String,Object>>();
  for(var species:PokemonSpecies.getSpecies()){
   var forms=new ArrayList<Map<String,Object>>();for(var form:species.getForms())forms.add(Map.of("name",form.getName(),"labels",form.getLabels(),"aspects",form.getAspects(),"evolutions",form.getEvolutions().size(),"inPool",pool.contains(species.getResourceIdentifier()+"|"+form.getName())));
   rows.add(Map.of("id",species.getResourceIdentifier().toString(),"implemented",species.getImplemented(),"labels",species.getLabels(),"evolutions",species.getStandardForm().getEvolutions().size(),"inPool",pool.contains(species.getResourceIdentifier()+"|"+species.getStandardForm().getName()),"forms",forms));
  }
  Files.writeString(Path.of("roster-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(rows));System.out.println("[ROSTER-AUDIT] species="+rows.size()+" implemented="+PokemonSpecies.getImplemented().size()+" pool="+pool.size());
 }
}
