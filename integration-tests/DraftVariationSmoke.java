package ai.league.practice;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.google.gson.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import java.nio.file.*;
import java.util.*;
public class DraftVariationSmoke implements ModInitializer {
 @Override public void onInitialize(){CommandRegistrationCallback.EVENT.register((d,r,e)->d.register(Commands.literal("draftvariationtest").requires(s->s.hasPermission(4)).executes(c->{if(!Boolean.getBoolean("league.smoke.enabled"))return 0;try{run();return 1;}catch(Exception ex){ex.printStackTrace();throw new RuntimeException(ex);}})));}
 static void run()throws Exception{
  Object moves=Class.forName("dev.cobbleleague.DungeonRandomMoves").getField("INSTANCE").get(null),learn=Class.forName("dev.cobbleleague.DungeonLearnset").getField("INSTANCE").get(null);List<Map<String,Object>> rows=new ArrayList<>();
  for(String species:List.of("charizard","garchomp","milotic","gardevoir","ditto")){
   Set<Set<String>> samples=new HashSet<>();boolean legal=true,unique=true,offense=true;int expected=0;
   for(int i=0;i<40;i++){
    var mon=PokemonProperties.Companion.parse(species+" level=50").create();
    @SuppressWarnings("unchecked") var pool=(Map<com.cobblemon.mod.common.api.moves.MoveTemplate,List<String>>)LeagueConvenienceChecks.call(learn,"allEntries",mon,50);
    LeagueConvenienceChecks.call(moves,"apply",mon);
    var actual=mon.getMoveSet().getMoves().stream().map(m->m.getName()).toList();samples.add(new HashSet<>(actual));expected=Math.min(4,pool.size());
    legal&=actual.size()==expected&&actual.stream().allMatch(n->pool.keySet().stream().anyMatch(m->m.getName().equals(n)));
    unique&=actual.size()==new HashSet<>(actual).size();long attacks=pool.keySet().stream().filter(m->m.getDamageCategory()!=DamageCategories.INSTANCE.getSTATUS()).count();
    if(attacks>=2)offense&=mon.getMoveSet().getMoves().stream().filter(m->m.getTemplate().getDamageCategory()!=DamageCategories.INSTANCE.getSTATUS()).count()>=2;
   }
   boolean ok=legal&&unique&&offense&&(expected<4||samples.size()>1);rows.add(Map.of("species",species,"passed",ok,"distinctSets",samples.size(),"moveSlots",expected));
  }
  rows.addAll(Draft080Checks.run());
  Files.writeString(Path.of("draft-variation-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(rows));if(rows.stream().anyMatch(row->!Boolean.TRUE.equals(row.get("passed"))))throw new IllegalStateException(rows.toString());
  System.out.println("[DRAFT-VARIATION] registry legality, distinct sets and offense PASS "+rows);
 }
}
