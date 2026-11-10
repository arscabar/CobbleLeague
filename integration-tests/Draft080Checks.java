package ai.league.practice;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
public class Draft080Checks {
 public static class Stash {private final Map<String,Integer> equipment=new HashMap<>();}
 public static List<Map<String,Object>> run()throws Exception {
  var rows=new ArrayList<Map<String,Object>>();
  Object roster=Class.forName("dev.cobbleleague.RandomRoster").getConstructor().newInstance();
  var starters=(List<?>)LeagueConvenienceChecks.call(roster,"starterPool");
  boolean basic=starters.size()>=3;
  for(Object candidate:starters){var mon=(Pokemon)LeagueConvenienceChecks.call(candidate,"create");basic&=mon.getForm()==mon.getSpecies().getStandardForm() && mon.getLevel()==50;}
  rows.add(Map.of("check","first-round-final-starters-in-base-forms","passed",basic,"count",starters.size()));
  var types=(Set<?>)LeagueConvenienceChecks.call(roster,"availableDraftTypes",true);
  Object odds=LeagueConvenienceChecks.call(roster,"getBaseOdds");boolean typed=true;
  for(Object type:types){var picks=(List<?>)LeagueConvenienceChecks.call(roster,"chooseDiverse",3,Set.of(),odds,Set.of(),Set.of(),Map.of(),true,type);Set<String> ids=new HashSet<>();boolean matches=false;
   for(Object c:picks){var mon=(Pokemon)LeagueConvenienceChecks.call(c,"create");ids.add(mon.getSpecies().getResourceIdentifier().toString());for(var t:mon.getForm().getTypes())matches|=t.getName().equals(type);}
   typed&=picks.size()==3&&ids.size()==3&&matches;
  }
  rows.add(Map.of("check","typed-rerolls-valid-pool-no-duplicates","passed",typed,"types",types.size()));
  Object forms=Class.forName("dev.cobbleleague.DungeonForms").getField("INSTANCE").get(null);
  var charizard=PokemonProperties.Companion.parse("charizard level=50").create();var zacian=PokemonProperties.Companion.parse("zacian level=50").create();
  var stone=BuiltInRegistries.ITEM.get(ResourceLocation.parse("mega_showdown:charizardite_x"));
  rows.add(Map.of("check","native-mega-stone-species-validation","passed",(boolean)LeagueConvenienceChecks.call(forms,"eligible",stone,charizard)&&!(boolean)LeagueConvenienceChecks.call(forms,"eligible",stone,zacian)));
  boolean applicable=true;int count=0;
  for(Object product:(List<?>)LeagueConvenienceChecks.call(forms,"products",charizard)){String id=(String)LeagueConvenienceChecks.call(product,"getId");var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));applicable&=(boolean)LeagueConvenienceChecks.call(forms,"eligible",item,charizard);count++;}
  rows.add(Map.of("check","shop-only-native-compatible-special-items","passed",applicable&&count>2,"count",count));
  var sword=BuiltInRegistries.ITEM.get(ResourceLocation.parse("mega_showdown:rusted_sword"));
  boolean swordOK=(boolean)LeagueConvenienceChecks.call(forms,"eligible",sword,zacian)&&!(boolean)LeagueConvenienceChecks.call(forms,"eligible",sword,charizard);
  LeagueConvenienceChecks.call(forms,"nativeHeld",zacian,sword,true);boolean changed=zacian.getAspects().stream().anyMatch(a->a.contains("crowned"));
  LeagueConvenienceChecks.call(forms,"nativeHeld",zacian,sword,false);
  rows.add(Map.of("check","rusted-sword-native-form-apply-revert","passed",swordOK&&changed&&zacian.getAspects().stream().noneMatch(a->a.contains("crowned"))));
  rows.add(Map.of("check","dynamax-ineligible-species-hidden","passed",!(boolean)LeagueConvenienceChecks.call(forms,"trainingEligible","mega_showdown:dynamax_candy",zacian)));
  boolean accessory=false,power=false;
  for(var method:Class.forName("com.github.yajatkaul.mega_showdown.utils.AccessoriesUtils").getDeclaredMethods())accessory|=method.getName().contains("temporaryAccess");
  for(var method:Class.forName("com.github.yajatkaul.mega_showdown.utils.PlayerUtils").getDeclaredMethods())power|=method.getName().contains("temporaryPowerSpot");
  rows.add(Map.of("check","optional-native-accessory-hooks-applied","passed",accessory&&power));
  Object brain=Class.forName("dev.cobblesynergy.BotBrain").getField("INSTANCE").get(null);
  double megaValue=((Number)LeagueConvenienceChecks.call(brain,"itemValue",charizard,"mega_showdown:charizardite_x")).doubleValue();
  double wrongValue=((Number)LeagueConvenienceChecks.call(brain,"itemValue",zacian,"mega_showdown:charizardite_x")).doubleValue();
  rows.add(Map.of("check","ai-values-only-compatible-native-mega-stones","passed",megaValue>0&&wrongValue==0));
  var urshifu=PokemonProperties.Companion.parse("urshifu level=50 moves=surgingstrikes,closecombat").create();
  double glove=((Number)LeagueConvenienceChecks.call(brain,"itemValue",urshifu,"cobblemon:punching_glove")).doubleValue();
  rows.add(Map.of("check","ai-glove-works-without-iron-fist","passed",glove>=40&&!urshifu.getAbility().getName().equals("ironfist")));
  Object bot=Class.forName("dev.cobblesynergy.DungeonBot").getField("INSTANCE").get(null);
  var method=bot.getClass().getDeclaredMethod("offers",Object.class,List.class);method.setAccessible(true);
  var offers=(List<?>)method.invoke(bot,new Stash(),List.of(charizard));boolean includes=false,bad=false;
  for(Object offer:offers){String id=(String)LeagueConvenienceChecks.call(offer,"getItem");includes|=id.equals("mega_showdown:charizardite_x")&&((Number)LeagueConvenienceChecks.call(offer,"getPrice")).intValue()==3;bad|=id.equals("mega_showdown:venusaurite");}
  rows.add(Map.of("check","ai-purchase-offers-include-native-mega-catalogue","passed",includes&&!bad));
  var allMethod=roster.getClass().getDeclaredMethod("all");allMethod.setAccessible(true);var pool=(List<?>)allMethod.invoke(roster);
  boolean gifts=true,noRareMega=true,variants=true;int megaCount=0;
  for(Object candidate:pool){if(!(boolean)LeagueConvenienceChecks.call(candidate,"isMega"))continue;megaCount++;
   var mon=(Pokemon)LeagueConvenienceChecks.call(candidate,"create");String gifted=BuiltInRegistries.ITEM.getKey(mon.heldItem().getItem()).toString();
   boolean nativeCan=(boolean)Class.forName("com.github.yajatkaul.mega_showdown.gimmick.MegaGimmick").getMethod("canMega",Pokemon.class).invoke(null,mon);if(!nativeCan)System.out.println("[MEGA-FAIL] native "+LeagueConvenienceChecks.call(candidate,"getKey")+" held="+gifted+" aspects="+mon.getAspects());gifts&=nativeCan;
   boolean cloned=net.minecraft.world.item.ItemStack.isSameItemSameComponents(mon.clone(false,((net.minecraft.server.MinecraftServer)net.fabricmc.loader.api.FabricLoader.getInstance().getGameInstance()).registryAccess()).heldItem(),mon.heldItem());if(!cloned)System.out.println("[MEGA-FAIL] clone "+LeagueConvenienceChecks.call(candidate,"getKey"));gifts&=cloned;
   gifts&=!mon.heldItem().isEmpty()&&mon.getForm()==mon.getSpecies().getStandardForm()&&gifted.equals(LeagueConvenienceChecks.call(forms,"megaStoneFor",candidate));
   var labels=new HashSet<>(mon.getSpecies().getLabels());labels.addAll(((com.cobblemon.mod.common.pokemon.FormData)LeagueConvenienceChecks.call(candidate,"getForm")).getLabels());noRareMega&=Collections.disjoint(labels,Set.of("legendary","mythical","paradox","sub_legendary","sublegendary","ultra_beast"));
   String key=(String)LeagueConvenienceChecks.call(candidate,"getKey");if(key.contains("charizard")&&key.endsWith("Mega-X"))variants&=gifted.equals("mega_showdown:charizardite_x");if(key.contains("charizard")&&key.endsWith("Mega-Y"))variants&=gifted.equals("mega_showdown:charizardite_y");
  }
  rows.add(Map.of("check","all-ordinary-mega-draws-give-base-and-exact-stone","passed",gifts&&variants&&megaCount>40,"megaForms",megaCount));
  rows.add(Map.of("check","rare-species-excluded-from-mega-category","passed",noRareMega));
  var megaBases=(List<?>)LeagueConvenienceChecks.call(roster,"megaBasePool");
  rows.add(Map.of("check","first-typed-reroll-broader-than-starters","passed",megaBases.size()>starters.size()&&types.size()==18,"baseSpecies",megaBases.size()));
  @SuppressWarnings("unchecked") var coverage=(Map<String,Integer>)LeagueConvenienceChecks.call(roster,"coverageAudit");
  rows.add(Map.of("check","cobbleverse-1025-loaded-species-source","passed",coverage.get("implemented")==1025&&coverage.get("registered")==1025,"coverage",coverage));
  List<String> creationFailures=new ArrayList<>();
  for(Object candidate:pool){try{var mon=(Pokemon)LeagueConvenienceChecks.call(candidate,"create");if(mon.getMoveSet().getMoves().isEmpty())creationFailures.add((String)LeagueConvenienceChecks.call(candidate,"getKey"));}catch(Exception failure){creationFailures.add((String)LeagueConvenienceChecks.call(candidate,"getKey"));}}
  rows.add(Map.of("check","all-dungeon-pool-candidates-can-be-created-with-moves","passed",creationFailures.isEmpty(),"failures",creationFailures,"forms",pool.size()));
  return rows;
 }
}

