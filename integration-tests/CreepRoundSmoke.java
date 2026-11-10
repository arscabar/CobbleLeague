package ai.league.practice;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.actor.*;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.battles.*;
import com.google.gson.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.impl.networking.NetworkHandlerExtensions;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Isolated milestones 4/8 integration. Wins/losses are forced after real AI turns. */
public final class CreepRoundSmoke implements ModInitializer {
 private MinecraftServer server;private Object service;private final ServerPlayer[] people=new ServerPlayer[2];
 private final Map<UUID,List<UUID>> originals=new HashMap<>(),pcs=new HashMap<>();
 private final Map<UUID,Object> answered=new HashMap<>();private final Set<UUID> handled=new HashSet<>();
 private final List<Map<String,Object>> report=new ArrayList<>();
 private boolean active;private int phase,nextTick,deadline;private com.cobblemon.mod.common.api.battles.model.PokemonBattle observed;
 static Object f(Object o,String n)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field x=c.getDeclaredField(n);x.setAccessible(true);return x.get(o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(n);}
 static Object call(Object o,String n,Object...a)throws Exception{for(Method m:o.getClass().getMethods())if(m.getName().equals(n)&&m.getParameterCount()==a.length){m.setAccessible(true);return m.invoke(o,a);}throw new NoSuchMethodException(n);}
 private Object game()throws Exception{return f(service,"lobbyGame");}
 private Object run(ServerPlayer p)throws Exception{return ((Map<?,?>)f(service,"runs")).get(p.getUUID());}
 private Object prep(ServerPlayer p)throws Exception{return f(run(p),"preparation");}
 private Object wallet(ServerPlayer p)throws Exception{return f(run(p),"rewardWallet");}
 private int revision(ServerPlayer p)throws Exception{return (int)call(prep(p),"getRevision");}
 private static List<UUID> party(ServerPlayer p){return Cobblemon.INSTANCE.getStorage().getParty(p).toGappyList().stream().map(m->m==null?null:m.getUuid()).toList();}
 private static List<UUID> pc(ServerPlayer p){List<UUID>a=new ArrayList<>();for(var m:Cobblemon.INSTANCE.getStorage().getPC(p))a.add(m.getUuid());return a.stream().sorted().toList();}
 private void record(String n,boolean ok,Object detail)throws Exception{report.add(Map.of("test",n,"passed",ok,"detail",String.valueOf(detail)));Files.writeString(Path.of("creep-round-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println("[CREEP-SMOKE] "+n+" "+ok+" "+detail);if(!ok)throw new IllegalStateException(n);}
 private void command(String s){server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),s);}
 private void action(ServerPlayer p,String name,Object...kv)throws Exception{JsonObject j=new JsonObject();j.addProperty("action",name);j.addProperty("revision",run(p)==null?-1:revision(p));for(int i=0;i<kv.length;i+=2){Object v=kv[i+1];if(v instanceof Number n)j.addProperty(kv[i].toString(),n);else if(v instanceof Boolean b)j.addProperty(kv[i].toString(),b);else j.addProperty(kv[i].toString(),v.toString());}call(service,"handle",p,j);}
 @SuppressWarnings("unchecked") private void setup(ServerPlayer p,int i)throws Exception{
  p.setNoGravity(true);p.setInvulnerable(true);p.teleportTo(server.overworld(),1304+i*3,76,1083,0,0);
  var data=Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(p);data.setStarterSelected(true);data.setStarterPrompted(true);
  var store=Cobblemon.INSTANCE.getStorage().getParty(p);String[] mons={"venusaur","blastoise","dragonite","snorlax","rotom","scizor"};for(int n=0;n<6;n++)store.set(n,PokemonProperties.Companion.parse(mons[n]+" level=50").create());Cobblemon.INSTANCE.getStorage().getPC(p).add(PokemonProperties.Companion.parse("gengar level=50").create());
  originals.put(p.getUUID(),party(p));pcs.put(p.getUUID(),pc(p));Object addon=((NetworkHandlerExtensions)(Object)p.connection).getAddon();((Set<ResourceLocation>)f(addon,"sendableChannels")).add(ResourceLocation.parse("cobbleleague:message"));action(p,"hello","protocol",11);
 }
 @Override public void onInitialize(){CommandRegistrationCallback.EVENT.register((d,r,e)->d.register(Commands.literal("creepsmoke").requires(s->s.hasPermission(4)).then(Commands.literal("start").executes(c->{if(!Boolean.getBoolean("league.smoke.enabled"))return 0;server=c.getSource().getServer();phase=0;active=true;report.clear();answered.clear();handled.clear();labelsChecked=false;labelRefs.clear();oddsStage=0;repairId=null;repairChecked=false;deadline=server.getTickCount()+24000;return 1;}))));
  ServerTickEvents.END_SERVER_TICK.register(s->{if(!active)return;try{capture();if(s.getTickCount()>deadline)throw new IllegalStateException("Timeout phase "+phase);if(s.getTickCount()<nextTick)return;nextTick=s.getTickCount()+10;step();}catch(Throwable error){active=false;try{record("failure",false,error+" / "+error.getCause());}catch(Exception ignored){}error.printStackTrace();for(var p:people)if(p!=null)try{call(service,"leave",p);}catch(Exception ignored){}}});}
 private void testLearnsetPurchases()throws Exception {
  var p=people[0];var party=Cobblemon.INSTANCE.getStorage().getParty(p);var original=party.get(0);
  var mon=PokemonProperties.Companion.parse("pikachu level=50 moves=thunderbolt,protect,quickattack,thunderwave").create();
  party.set(0,mon);
  try {
   Object policy=Class.forName("dev.cobbleleague.DungeonLearnset").getField("INSTANCE").get(null);
   @SuppressWarnings("unchecked") var entries=(Map<com.cobblemon.mod.common.api.moves.MoveTemplate,List<String>>)call(policy,"entries",mon);
   for(String source:List.of("알기술","자력기")) {
    var template=entries.entrySet().stream().filter(e->e.getValue().stream().anyMatch(v->v.startsWith(source))&&!mon.getForm().getMoves().getTmMoves().contains(e.getKey())&&mon.getMoveSet().getMoves().stream().noneMatch(m->m.getName().equals(e.getKey().getName()))).map(Map.Entry::getKey).findFirst().orElseThrow();
    call(prep(p),"awardLoss");call(prep(p),"awardLoss");int before=(int)call(prep(p),"getMoney");
    var untouched=mon.getMoveSet().getMoves().stream().skip(1).map(m->m.getName()).toList();
    ((Map<?,?>)f(service,"lastRequest")).remove(p.getUUID());action(p,"buy","pokemon",mon.getUuid(),"operation","tm","move",template.getName(),"slot",0);
    record("native-buy-"+source,mon.getMoveSet().get(0).getName().equals(template.getName())&&(int)call(prep(p),"getMoney")==before-1&&mon.getMoveSet().getMoves().stream().skip(1).map(m->m.getName()).toList().equals(untouched),template.getName()+" / 1 coin / exactly slot0");
   }
   call(prep(p),"awardLoss");call(prep(p),"awardLoss");int before=(int)call(prep(p),"getMoney");boolean rejected=false;
   try{((Map<?,?>)f(service,"lastRequest")).remove(p.getUUID());action(p,"buy","pokemon",mon.getUuid(),"operation","tm","move","spacialrend","slot",0);}catch(InvocationTargetException e){rejected=true;}
   record("illegal-move-purchase-no-charge",rejected&&(int)call(prep(p),"getMoney")==before,"Pikachu Spacial Rend rejected");
   for(int i=0;i<5;i++)call(prep(p),"awardLoss");
   ((Map<?,?>)f(service,"lastRequest")).remove(p.getUUID());
   action(p,"buy","pokemon",mon.getUuid(),"operation","buy","product","minecraft:gold_nugget");
   record("gold-cap-maxes-six-ivs",java.util.stream.StreamSupport.stream(mon.getIvs().spliterator(),false).filter(e->e.getValue()==31).count()==6,"six IVs at 31");
   String prior=mon.getAbility().getName();
   ((Map<?,?>)f(service,"lastRequest")).remove(p.getUUID());
   action(p,"buy","pokemon",mon.getUuid(),"operation","buy","product","cobblemon:ability_capsule","ability",prior.equals("static")?"lightningrod":"static");
   record("training-changes-native-ability",!mon.getAbility().getName().equals(prior),prior+" -> "+mon.getAbility().getName());
   ((Map<?,?>)f(service,"lastRequest")).remove(p.getUUID());var money=prep(p).getClass().getDeclaredField("money");money.setAccessible(true);money.setInt(prep(p),1);
  }finally{party.set(0,original);((Map<?,?>)f(run(p),"authorizedMoves")).remove(mon.getUuid());}
 }
 private void capture()throws Exception{
  if(service==null)return;Object g=game();if(g==null)return;Object plan=call(f(g,"model"),"getPlan");boolean creep=plan!=null&&!call(plan,"getKind").toString().equals("PVP");
  for(var p:people){if(p==null)continue;var battle=BattleRegistry.getBattleByParticipatingPlayer(p);if(battle!=null)observed=battle;if(!creep||battle==null||battle.getEnded()||!battle.getStarted())continue;
   BattleActor actor=battle.getActor(p);var request=actor.getRequest();if(request==null||request.getWait()||answered.get(p.getUUID())==request)continue;answered.put(p.getUUID(),request);
   battle.doWhenClear(()->{if(battle.getEnded()||actor.getRequest()!=request)return kotlin.Unit.INSTANCE;
    try{actor.setActionResponses(request.iterate(actor.getActivePokemon(),(slot,moves,forced)->{
     if(forced){for(var candidate:actor.getPokemonList())if(candidate.getHealth()>0&&actor.getActivePokemon().stream().noneMatch(a->a.getBattlePokemon()==candidate)){var response=new SwitchActionResponse(candidate.getUuid());if(response.isValid(slot,moves,true)){candidate.setWillBeSwitchedIn(true);return response;}}return new ForcePassActionResponse();}
     if(moves!=null)for(var move:moves.getMoves())if(!move.getDisabled()&&move.getPp()>0){var opposite=slot.getOppositeOpponent();String[] targets=Set.of("normal","any","adjacentFoe","adjacentAlly","adjacentAllyOrSelf").contains(move.getTarget().name())?new String[]{opposite==null?null:opposite.getPNX(),slot.getPNX()}:new String[]{null};for(String target:targets){var response=new MoveActionResponse(move.getId(),target,null);if(response.isValid(slot,moves,false))return response;}}
     return PassActionResponse.INSTANCE;
    }));}finally{actor.getPokemonList().forEach(m->m.setWillBeSwitchedIn(false));}return kotlin.Unit.INSTANCE;});
  }
 }
 @SuppressWarnings("unchecked") private void step()throws Exception{
  if(phase==0){command("player CreepTestA spawn");command("player CreepTestB spawn");phase=1;return;}
  if(phase==1){people[0]=server.getPlayerList().getPlayerByName("CreepTestA");people[1]=server.getPlayerList().getPlayerByName("CreepTestB");if(people[0]==null||people[1]==null)return;Object c=Class.forName("dev.cobbleleague.LeagueMod").getField("Companion").get(null);service=call(c,"getService");for(int i=0;i<2;i++)setup(people[i],i);Object roster=f(service,"roster");record("mega-and-base-pools-coexist",(boolean)call(roster,"megaBasesPresent"),call(roster,"poolAudit"));phase=2;return;}
  if(phase==2||phase==3){action(people[phase-2],"lobby_join");phase++;return;}
  if(phase==4||phase==5){action(people[phase-4],"lobby_ready","ready",true);phase++;return;}
  if(phase==6){Object g=game();if(g==null||!f(g,"phase").toString().equals("SELECTION"))return;
   int currentRound=(int)call(call(f(g,"model"),"getPlan"),"getNumber");
   if(currentRound==1&&!labelsChecked){for(var p:people){List<?> labels=(List<?>)f(run(p),"choiceLabels");if(labels.isEmpty())return;for(var label:labels)labelRefs.add((net.minecraft.world.entity.Entity)label);}record("selection-platform-labels-created",labelRefs.size()==6&&labelRefs.stream().allMatch(e->e.getTags().contains("cobbleleague_choice_label")),"three labeled platforms per participant");Object testRun=run(people[0]);Object testRoster=f(service,"roster");String megaKey="cobblemon:charizard|Mega-X";Object megaCandidate=call(testRoster,"resolve",megaKey);
    var megaPokemon=(com.cobblemon.mod.common.pokemon.Pokemon)call(megaCandidate,"create");
    var firstDisplay=(com.cobblemon.mod.common.entity.pokemon.PokemonEntity)call(((List<?>)f(testRun,"displays")).getFirst(),"getFirst");firstDisplay.setPokemon(megaPokemon);
    var forced=new ArrayList<String>((List<String>)f(testRun,"choices"));forced.set(0,megaKey);var choiceField=testRun.getClass().getDeclaredField("choices");choiceField.setAccessible(true);choiceField.set(testRun,forced);
    ((Map<UUID,Object>)f(testRun,"displayMegaForms")).put(firstDisplay.getUUID(),megaCandidate);
    labelsChecked=true;}
   if(currentRound==5&&oddsStage==0){oddsMoney=(int)call(prep(people[1]),"getMoney");Object odds=f(run(people[1]),"drawOdds");choicesBefore=List.copyOf((List<String>)f(run(people[1]),"choices"));action(people[1],"odds_boost","category","legendary");record("selection-only-boost-cost-one-coin",(int)call(prep(people[1]),"getMoney")==oddsMoney-1&&(int)call(odds,"getUsedRound")==5,"round5");record("boost-does-not-redraw-visible-candidates",choicesBefore.equals(f(run(people[1]),"choices")),"next offer or physical reroll only");oddsStage=1;return;}
   if(currentRound==5&&oddsStage==1){boolean rejected=false;try{action(people[1],"odds_boost","category","mythical");}catch(InvocationTargetException e){rejected=true;}record("second-boost-same-round-rejected-without-charge",rejected&&(int)call(prep(people[1]),"getMoney")==oddsMoney-1,"one purchase per global round");oddsStage=2;return;}
   if(currentRound==6&&oddsStage==2){Object odds=f(run(people[1]),"drawOdds");record("next-round-can-upgrade-and-previous-buff-persists",(int)call(odds,"getUsedRound")==5&&(boolean)call(odds,"available",6),call(call(odds,"getOdds"),"getValues"));action(people[1],"odds_boost","category","mega");oddsStage=3;return;}

   boolean waiting=false;
   for(var p:people){
    Object r=run(p);
    if((boolean)call(f(r,"state"),"getReady"))continue;
    List<?> displays=(List<?>)f(r,"displays");if(displays.isEmpty()){waiting=true;continue;}
    var entity=(com.cobblemon.mod.common.entity.pokemon.PokemonEntity)call(displays.getFirst(),"getFirst");
    if(entity.isRemoved()){waiting=true;continue;}
    String version=call(f(r,"state"),"getVersion").toString();
    if(currentRound==1&&p==people[0]&&repairId==null){repairId=entity.getUUID();repairPokemon=entity.getPokemon().getUuid();entity.discard();waiting=true;continue;}
    if(currentRound==1&&p==people[0]&&!repairChecked){
     record("removed-display-restored-with-same-pokemon",!entity.getUUID().equals(repairId)&&entity.getPokemon().getUuid().equals(repairPokemon),"candidate UUID retained");
     repairChecked=true;
    }
    if(currentRound==2&&p==people[0]){
     var before=party(p);action(p,"pick_skip","version",version);
     record("skip-keeps-party",before.equals(party(p))&&(boolean)call(f(r,"state"),"getReady"),"confirmed unchanged party");
     continue;
    }
    p.teleportTo((net.minecraft.server.level.ServerLevel)entity.level(),entity.getX(),entity.getY(),entity.getZ(),0,0);
    Object candidate=((List<?>)f(r,"choices")).getFirst();
    var before=party(p);
    if(f(r,"pendingPick")!=null){
     action(p,"pick","pokemon",candidate,"version",version,"replace",before.get(1));
     var after=party(p);
     record("explicit-draft-slot-replacement",!before.get(1).equals(after.get(1))&&java.util.stream.IntStream.range(0,6).filter(i->i!=1).allMatch(i->before.get(i).equals(after.get(i))),"only selected slot replaced");
    }else{
     action(p,"pick","pokemon",candidate,"version",version);
     if(candidate.toString().equals("cobblemon:charizard|Mega-X")){
      var obtained=Cobblemon.INSTANCE.getStorage().getParty(p).get(0);
      record("real-pick-clone-keeps-base-and-exact-mega-stone",obtained.getForm()==obtained.getSpecies().getStandardForm()&&net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(obtained.heldItem().getItem()).toString().equals("mega_showdown:charizardite_x"),"real pick endpoint after missing-display recovery");
     }
     if(f(r,"pendingPick")!=null){
      record("full-party-preview-keeps-roster",party(p).equals(before),"candidate selected before replacement");
      waiting=true;
     }
    }
   }
   if(waiting)return;
   phase=7;return;
  }
  if(phase==7){Object g=game();if(!f(g,"phase").toString().equals("PREPARATION"))return;int round=(int)call(call(f(g,"model"),"getPlan"),"getNumber");
   if(round==3)LeagueConvenienceChecks.equipment(service,people[0],(name,ok)->{try{record(name,ok,"equipment native transaction");}catch(Exception e){throw new RuntimeException(e);}});
   if(round==4||round==8){record("milestone-replaces-pvp",((List<?>)call(call(f(g,"model"),"getPlan"),"getDuels")).isEmpty(),"round="+round);
    for(var p:people)for(var mon:Cobblemon.INSTANCE.getStorage().getParty(p)){mon.getMoveSet().clear();mon.getMoveSet().add(Moves.getByName("protect").create());((Map<UUID,List<String>>)f(run(p),"authorizedMoves")).put(mon.getUuid(),List.of("protect"));}
   }
   if(round==1){testLearnsetPurchases();record("platform-labels-removed-on-selection",labelRefs.stream().allMatch(net.minecraft.world.entity.Entity::isRemoved)&&Arrays.stream(people).allMatch(p->{try{return ((List<?>)f(run(p),"choiceLabels")).isEmpty();}catch(Exception e){return false;}}),"no labels during preparation/battle");}
   if(round==1||round==5){int money=(int)call(prep(people[1]),"getMoney");boolean rejected=false;try{action(people[1],"odds_boost","category","mythical");}catch(InvocationTargetException e){rejected=e.getCause().getMessage().contains("선택");}record("boost-blocked-after-selection",rejected&&(int)call(prep(people[1]),"getMoney")==money,"preparation stage");phase=71;return;}
   observed=null;for(var p:people)action(p,"ready");phase=8;return;
  }
  if(phase==71){observed=null;for(var p:people)action(p,"ready");phase=8;return;}
  if(phase==8){Object g=game();if(g==null)throw new IllegalStateException("Game ended before milestone checks");Object model=f(g,"model");Object plan=call(model,"getPlan");String stage=f(g,"phase").toString();
   if(stage.equals("RESULTS")||stage.equals("REWARDS")){int round=(int)call(model,"getRound");phase=round==4?40:round==8?80:9;return;}
   if(!stage.equals("BATTLES"))return;
   int round=(int)call(plan,"getNumber");
   if(round!=4&&round!=8){var b=observed;
    if(round==3&&!pvpRetried){
     if(b==null||!b.getStarted())return;var matches=(Map<?,?>)f(service,"matches");Object m=matches.values().iterator().next();if(server.getTickCount()-(int)call(m,"getCreatedAt")<100)return;
     var old=b;Object score=call(model,"getStandings");String snapshot=score.toString();int money=(int)call(prep(people[0]),"getMoney");
     call(service,"restartBattle",people[0],old.getBattleId().toString());record("one-pvp-vote-does-not-restart",BattleRegistry.getBattleByParticipatingPlayer(people[0])==old,"requires both participants");
     call(service,"restartBattle",people[1],old.getBattleId().toString());var fresh=BattleRegistry.getBattleByParticipatingPlayer(people[0]);
     record("pvp-retry-new-attempt-without-score-or-money",fresh!=null&&fresh!=old&&BattleRegistry.getBattle(old.getBattleId())==null&&snapshot.equals(call(model,"getStandings").toString())&&(int)call(prep(people[0]),"getMoney")==money,"same round, no result settlement");
     boolean stale=false;try{call(service,"restartBattle",people[0],old.getBattleId().toString());}catch(InvocationTargetException e){stale=true;}record("old-attempt-retry-request-rejected",stale,"old battle UUID");pvpRetried=true;observed=null;return;
    }
if(b!=null&&b.getStarted()&&!b.getEnded()&&handled.add(b.getBattleId())){if(round==1||round==3)BattleRecoveryChecks.run(b,(name,passed)->{try{record(name,passed,"PvP native fault injection");}catch(Exception e){throw new RuntimeException(e);}});call(service,"surrender",people[round>=5?0:1]);if(round==1)record("stalled-pvp-surrender-closes-registry",BattleRegistry.getBattle(b.getBattleId())==null,"immediate exit without awaiting dispatch");}return;}
   Map<UUID,Object> concurrent=(Map<UUID,Object>)f(g,"currentCreeps");
   if(round==4&&!creepRetried){
    if(concurrent.size()!=2)return;var old=BattleRegistry.getBattleByParticipatingPlayer(people[0]);var other=BattleRegistry.getBattleByParticipatingPlayer(people[1]);if(old==null||other==null||!old.getStarted())return;
    Object active=concurrent.get(people[0].getUUID());Object m=((Map<?,?>)f(service,"matches")).get(call(active,"getMatchId"));if(server.getTickCount()-(int)call(m,"getCreatedAt")<100)return;
    String standings=call(model,"getStandings").toString(),trainer=call(call(active,"getTrainer"),"getId").toString();int money=(int)call(prep(people[0]),"getMoney");
    call(service,"restartBattle",people[0],old.getBattleId().toString());var fresh=BattleRegistry.getBattleByParticipatingPlayer(people[0]);
    record("creep-retry-only-caller-battle",fresh!=null&&fresh!=old&&BattleRegistry.getBattleByParticipatingPlayer(people[1])==other&&!other.getEnded(),"parallel opponent battle UUID preserved");
    record("creep-retry-preserves-trainer-round-score-money",trainer.equals(call(call(concurrent.get(people[0].getUUID()),"getTrainer"),"getId"))&&standings.equals(call(model,"getStandings").toString())&&(int)call(prep(people[0]),"getMoney")==money,"no rewards or HP settlement on void attempt");creepRetried=true;return;
   }
   UUID parallelKey=UUID.nameUUIDFromBytes(("parallel-"+round).getBytes());
   if(concurrent.size()==2&&handled.add(parallelKey)){
    record("simultaneous-independent-battles",Math.abs(people[0].getX()-people[1].getX())>=200&&people[0].getX()>900&&people[1].getX()>900,"round="+round+", live="+concurrent.size());
    List<String> identities=new ArrayList<>(),teams=new ArrayList<>();
    for(Object current:concurrent.values()){
     Object trainer=call(current,"getTrainer");identities.add(call(trainer,"getId").toString());
     UUID owner=(UUID)call(current,"getPlayer");var b=(com.cobblemon.mod.common.api.battles.model.PokemonBattle)call(call(current,"getHandle"),"getBattle");
     var enemy=java.util.stream.StreamSupport.stream(b.getActors().spliterator(),false).filter(a->!a.getUuid().equals(owner)).findFirst().orElseThrow();
     teams.add(enemy.getPokemonList().stream().map(m->m.getEffectedPokemon().getSpecies().getName()+":"+m.getEffectedPokemon().getAbility().getName()+":"+java.util.stream.StreamSupport.stream(m.getEffectedPokemon().getMoveSet().spliterator(),false).map(move->move.getName()).toList().toString()).toList().toString());
    }
    record("one-trainer-draw-shared-by-all",identities.stream().distinct().count()==1&&teams.stream().distinct().count()==1,"round="+round+", trainers="+identities);
   }
   for(Object current:new ArrayList<>(concurrent.values())){
    UUID owner=(UUID)call(current,"getPlayer");ServerPlayer p=Arrays.stream(people).filter(x->x.getUUID().equals(owner)).findFirst().orElseThrow();
    if(p==people[1]&&!(boolean)call(model,"isCreepCompleted",people[0].getUUID()))continue;
    var b=BattleRegistry.getBattleByParticipatingPlayer(p);if(b==null||!b.getStarted()||b.getTurn()<2||!handled.add(b.getBattleId()))continue;
    var enemy=java.util.stream.StreamSupport.stream(b.getActors().spliterator(),false).filter(a->!a.getUuid().equals(owner)).findFirst().orElseThrow();
    int expected=round==4?4:6;record("trainer-size-level-single",enemy.getPokemonList().size()==expected&&enemy.getActivePokemon().size()==1&&enemy.getPokemonList().stream().allMatch(m->m.getEffectedPokemon().getLevel()==50),"round="+round+", count="+enemy.getPokemonList().size());
    record("real-ai-turns",b.getTurn()>=2,"turn="+b.getTurn());Object ai=Class.forName("ai.league.practice.PracticeRunBunAI").getMethod("forActor",AIBattleActor.class).invoke(null,(AIBattleActor)enemy);record("run-bun-connected",(int)call(ai,"choices")>0,"choices="+call(ai,"choices")+", failures="+call(ai,"failures"));
    if(p==people[1]){
     Method progress=Arrays.stream(service.getClass().getDeclaredMethods()).filter(m->m.getName().equals("creepProgress")).findFirst().orElseThrow();progress.setAccessible(true);JsonArray rows=(JsonArray)progress.invoke(service,g);
     record("finished-player-sees-remaining-battle",rows.size()==2&&rows.asList().stream().anyMatch(e->e.getAsJsonObject().get("status").getAsString().equals("win"))&&rows.asList().stream().anyMatch(e->e.getAsJsonObject().get("status").getAsString().equals("battling")),rows);
     record("gym-round-stays-shared-while-another-player-fights",f(g,"phase").toString().equals("BATTLES")&&(int)call(plan,"getNumber")==round,"winner still in the same global round");
     if(round==4)LeagueConvenienceChecks.spectate(service,people[0],people[1],(name,ok)->{try{record(name,ok,"two-player simultaneous gym battles");}catch(Exception e){throw new RuntimeException(e);}});
     boolean shopBlocked=false;
     try{action(people[0],"shop","view","tms");}catch(InvocationTargetException e){shopBlocked=e.getCause().getMessage().contains("관장전과 보상");}
     record("winner-cannot-open-shop-before-other-battle-finishes",shopBlocked,"shop waits for the shared intermission");

    }
    if(round==4&&p==people[0])BattleRecoveryChecks.run(b,(name,passed)->{try{record(name,passed,"PvE native fault injection");}catch(Exception e){throw new RuntimeException(e);}});
    String target=round==4&&p==people[1]?b.getActor(p).getShowdownId():enemy.getShowdownId();b.writeShowdownAction(">forcelose "+target);
   }
   return;
  }
  if(phase==9){Object g=game();if(f(g,"phase").toString().equals("BUILDING")||f(g,"phase").toString().equals("SELECTION"))phase=6;return;}
  if(phase==40){if(!f(game(),"phase").toString().equals("REWARDS"))return;LeagueConvenienceChecks.afterSpectate(service,people[0],(name,ok)->{try{record(name,ok,"observer restored after target battle");}catch(Exception e){throw new RuntimeException(e);}});record("completed-round-rank-history",((List<?>)call(f(game(),"history"),"getRounds")).size()==5,"start plus four complete round snapshots");
   Object wa=wallet(people[0]),wb=wallet(people[1]);Class<?> kind=Class.forName("dev.cobbleleague.CreepRewardKind");Object myth=Arrays.stream(kind.getEnumConstants()).filter(x->x.toString().equals("MYTHICAL")).findFirst().orElseThrow();
   Object offer=f(run(people[0]),"gymReward"),choice=call(offer,"getChoice");var options=(List<?>)call(choice,"getOptions");
   record("money-fixed-plus-two-distinct-random-options",options.size()==3&&options.stream().distinct().count()==3&&options.getFirst().toString().equals("MONEY"),options);
   record("victory-does-not-auto-grant-all-rewards",(int)call(wa,"count",myth)==0&&call(wa,"getPending")==null&&(int)call(prep(people[0]),"getFreeLessons")==0,"no voucher, legendary, or lesson before selection");
   boolean blocked=false;try{action(people[0],"reward_done");}catch(InvocationTargetException e){blocked=true;}record("reward-ready-blocked-until-choice",blocked,"winner must choose one reward");
   int cash=(int)call(prep(people[0]),"getMoney");choosePrize(people[0],"MYTHICAL");
   record("chosen-mythical-only",(int)call(wa,"count",myth)==1&&(int)call(wb,"count",myth)==0&&call(wa,"getPending")==null&&(int)call(prep(people[0]),"getMoney")==cash&&(int)call(prep(people[0]),"getFreeLessons")==0,"unchosen money, legendary and lesson excluded");
   phase=401;return;
  }
  if(phase==401){action(people[0],"reward_draw","kind","mythical");phase=41;return;}
  if(phase==41){var p=people[0];Object r=run(p),pending=call(wallet(p),"getPending");String token=call(pending,"getToken").toString();var mon=(com.cobblemon.mod.common.pokemon.Pokemon)f(r,"pendingRewardPokemon");record("mythical-pool",mon.getSpecies().getLabels().contains("mythical")&&mon.getLevel()==50,mon.getSpecies().getName());var store=Cobblemon.INSTANCE.getStorage().getParty(p);store.swap(1,5);List<UUID> before=party(p);record("pending-result-fixed",call(call(wallet(p),"getPending"),"getToken").toString().equals(token),"pending token preserved until acceptance");action(p,"reward_accept","token",token);record("gapped-party-preserves-occupied-slots",party(p).get(1)!=null&&java.util.stream.IntStream.range(0,6).filter(i->before.get(i)!=null).allMatch(i->party(p).get(i).equals(before.get(i))),"empty slot1 filled; occupied slots unchanged");record("optional-free-slot-insertion",party(p).stream().filter(Objects::nonNull).count()==before.stream().filter(Objects::nonNull).count()+1,"accepted after preview");phase=42;return;}
  if(phase==42){
   var p=people[0];var mon=Cobblemon.INSTANCE.getStorage().getParty(p).get(0);
   Class<?> learnset=Class.forName("dev.cobbleleague.DungeonLearnset");
   var options=(Map<?,?>)call(learnset.getField("INSTANCE").get(null),"entries",mon);
   var template=(com.cobblemon.mod.common.api.moves.MoveTemplate)options.keySet().iterator().next();
   int coins=(int)call(prep(p),"getMoney");
   record("unchosen-free-lesson-not-granted",(int)call(prep(p),"getFreeLessons")==0,"mythical selected instead");call(prep(p),"awardFreeLesson");
   action(p,"buy","operation","tm","pokemon",mon.getUuid(),"move",template.getName(),"slot",0);
   record("first-lesson-uses-credit-without-spending",(int)call(prep(p),"getMoney")==coins&&(int)call(prep(p),"getFreeLessons")==0&&mon.getMoveSet().get(0).getName().equals(template.getName()),"move learned and credit consumed");
   phase=423;return;
  }
  if(phase==423){
   var p=people[0];var mon=Cobblemon.INSTANCE.getStorage().getParty(p).get(0);
   Class<?> training=Class.forName("dev.cobbleleague.DungeonTraining");
   String item=(String)((List<?>)call(training.getField("INSTANCE").get(null),"choices","z_choice")).getFirst();
   @SuppressWarnings("unchecked") var stash=(Map<String,Integer>)f(run(p),"equipment");
   stash.put("z_choice",1);
   int old=stash.getOrDefault(item,0);
   action(p,"buy","operation","redeem","pokemon",mon.getUuid(),"product",item);
   record("choice-ticket-is-separate-from-owned-stock",stash.get("z_choice")==0&&stash.getOrDefault(item,0)==old,"one shared ticket used, stock unchanged");
   phase=421;return;
  }
  if(phase==421){action(people[0],"reward_done");phase=422;return;}
  if(phase==422){
   record("one-confirmation-does-not-advance",f(game(),"phase").toString().equals("REWARDS")&&(int)call(f(game(),"model"),"getRound")==4,"waiting for the second participant");
   boolean shopBlocked=false;try{action(people[0],"shop","view","tms");}catch(InvocationTargetException e){shopBlocked=e.getCause().getMessage().contains("관장전과 보상");}
   record("completed-player-waits-until-resuming",shopBlocked,"ready player cannot reopen a mutable shop");
   action(people[1],"reward_done");phase=9;return;
  }
  if(phase==80){if(!f(game(),"phase").toString().equals("REWARDS"))return;record("full-roster-before-legendary",Arrays.stream(people).allMatch(p->party(p).stream().filter(Objects::nonNull).count()==6),"six occupied slots");beforeA=party(people[0]);choosePrize(people[0],"LEGENDARY");action(people[0],"reward_view");phase=81;return;}
  if(phase==81){Object r=run(people[0]),pending=call(wallet(people[0]),"getPending");var mon=(com.cobblemon.mod.common.pokemon.Pokemon)f(r,"pendingRewardPokemon");record("legendary-pool-excludes-mythical",mon.getSpecies().getLabels().contains("legendary")&&!mon.getSpecies().getLabels().contains("mythical"),mon.getSpecies().getName());record("draw-never-forces-replacement",party(people[0]).equals(beforeA),"preview keeps full party unchanged");action(people[0],"reward_decline","token",call(pending,"getToken"));record("decline-preserves-party",party(people[0]).equals(beforeA),"explicit rejection");phase=82;return;}
  if(phase==82){beforeB=party(people[1]);choosePrize(people[1],"LEGENDARY");action(people[1],"reward_view");phase=83;return;}
  if(phase==83){Object pending=call(wallet(people[1]),"getPending");action(people[1],"reward_accept","token",call(pending,"getToken"),"replace",beforeB.get(2));List<UUID> after=party(people[1]);record("selected-slot-only-replaced",after.size()==beforeB.size()&&!after.get(2).equals(beforeB.get(2))&&java.util.stream.IntStream.range(0,6).filter(i->i!=2).allMatch(i->after.get(i).equals(beforeB.get(i))),"slot3 chosen, other five unchanged");phase=84;return;}
  if(phase==84){for(var p:people)action(p,"reward_done");phase=85;return;}
  if(phase==85){call(service,"leave",people[0]);phase=86;return;}
  if(phase==86){for(var p:people)record("original-party-and-pc-restored",party(p).equals(originals.get(p.getUUID()))&&pc(p).equals(pcs.get(p.getUUID())),p.getGameProfile().getName());boolean rejectedOutside=false;try{action(people[0],"odds_boost","category","legendary");}catch(InvocationTargetException e){rejectedOutside=true;}record("boost-blocked-outside-dungeon",rejectedOutside,"original party already restored");record("completed",true,"NPC4/NPC8 level50, real AI turns, win-only tickets, preview/add/decline/replace, originals preserved");active=false;for(var p:people)command("player "+p.getGameProfile().getName()+" kill");}
 }
 private boolean pvpRetried,creepRetried;
 private void choosePrize(ServerPlayer p,String prize)throws Exception {
  Object reward=f(run(p),"gymReward"),choice=call(reward,"getChoice");
  // This test fixture selects an offered category deterministically, independently of production RNG.
  var options=call(choice,"getOptions");
  Class<?> cls=Class.forName("dev.cobbleleague.GymPrize");Object selected=Arrays.stream(cls.getEnumConstants()).filter(e->e.toString().equals(prize)).findFirst().orElseThrow();
  if(!((List<?>)options).contains(selected)){
   var replacement=Class.forName("dev.cobbleleague.GymRewardChoice").getConstructor(List.class,String.class).newInstance(List.of(selected),UUID.randomUUID().toString());
   var field=reward.getClass().getDeclaredField("choice");field.setAccessible(true);field.set(reward,replacement);choice=replacement;
  }
  ((Map<?,?>)f(service,"lastRequest")).remove(p.getUUID());action(p,"gym_reward_choose","token",call(choice,"getToken"),"prize",prize);
  boolean rejected=false;((Map<?,?>)f(service,"lastRequest")).remove(p.getUUID());try{action(p,"gym_reward_choose","token",call(choice,"getToken"),"prize",prize);}catch(InvocationTargetException e){rejected=true;}
  record("duplicate-reward-claim-rejected",rejected,prize);
  ((Map<?,?>)f(service,"lastRequest")).remove(p.getUUID());
 }
 private UUID repairId,repairPokemon;private boolean repairChecked;private List<UUID> beforeA,beforeB;private int oddsMoney,oddsStage;private List<String> choicesBefore;private boolean labelsChecked;private final List<net.minecraft.world.entity.Entity> labelRefs=new ArrayList<>();
}
