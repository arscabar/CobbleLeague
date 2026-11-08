package ai.league.practice;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
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
public class LateGymSmoke implements ModInitializer {
 Object service,bot;MinecraftServer server;ServerPlayer[] ps=new ServerPlayer[2];boolean active;int phase,index,next,deadline;int[] targets={4,8,12,16,20};int[] hp=new int[2];List<Map<String,Object>> rows=new ArrayList<>();Set<UUID> forced=new HashSet<>();List<UUID> original;String originalTera;
 static Object f(Object o,String n)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{var x=c.getDeclaredField(n);x.setAccessible(true);return x.get(o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(n);}
 static void set(Object o,String n,Object value)throws Exception{var x=o.getClass().getDeclaredField(n);x.setAccessible(true);x.set(o,value);}
 static Object call(Object o,String n,Object... a)throws Exception{for(var m:o.getClass().getMethods())if(m.getName().equals(n)&&m.getParameterCount()==a.length){m.setAccessible(true);return m.invoke(o,a);}throw new NoSuchMethodException(n);}
 void record(String n,boolean ok,Object detail)throws Exception{rows.add(Map.of("test",n,"passed",ok,"detail",String.valueOf(detail)));Files.writeString(Path.of("late-gym-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(rows));if(!ok)throw new IllegalStateException(n+" / "+detail);System.out.println("[LATE-GYM] "+n+" PASS "+detail);}
 Object run(ServerPlayer p)throws Exception{return ((Map<?,?>)f(service,"runs")).get(p.getUUID());}
 Object game()throws Exception{return f(service,"lobbyGame");}
 void action(ServerPlayer p,String name,Object... kv)throws Exception{((Map<?,?>)f(service,"lastRequest")).remove(p.getUUID());var j=new JsonObject();j.addProperty("action",name);if(run(p)!=null)j.addProperty("revision",(int)call(call(run(p),"getPreparation"),"getRevision"));for(int i=0;i<kv.length;i+=2){Object v=kv[i+1];if(v instanceof Number n)j.addProperty(kv[i].toString(),n);else if(v instanceof Boolean b)j.addProperty(kv[i].toString(),b);else j.addProperty(kv[i].toString(),v.toString());}call(service,"handle",p,j);}
 void command(String cmd){server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),cmd);}
 @Override public void onInitialize(){CommandRegistrationCallback.EVENT.register((d,r,e)->d.register(Commands.literal("lategymtest").requires(s->s.hasPermission(4)).executes(c->{if(!Boolean.getBoolean("league.smoke.enabled"))return 0;server=c.getSource().getServer();active=true;phase=0;index=0;rows.clear();forced.clear();deadline=server.getTickCount()+16000;return 1;})));
 ServerTickEvents.END_SERVER_TICK.register(s->{if(!active)return;try{if(bot!=null){var m=bot.getClass().getDeclaredMethod("capture");m.setAccessible(true);m.invoke(bot);}if(s.getTickCount()>deadline)throw new IllegalStateException("Timeout phase "+phase);if(s.getTickCount()<next)return;next=s.getTickCount()+10;step();}catch(Throwable e){active=false;try{record("failure",false,e+" / "+e.getCause());}catch(Exception ignored){}e.printStackTrace();for(var p:ps)if(p!=null)try{call(service,"leave",p);}catch(Exception ignored){}}});}
 @SuppressWarnings("unchecked") void step()throws Exception{
  if(phase==0){command("player LateA spawn");command("player LateB spawn");phase=1;return;}
  if(phase==1){ps[0]=server.getPlayerList().getPlayerByName("LateA");ps[1]=server.getPlayerList().getPlayerByName("LateB");if(ps[0]==null||ps[1]==null)return;service=call(Class.forName("dev.cobbleleague.LeagueMod").getField("Companion").get(null),"getService");
   for(var p:ps){p.setNoGravity(true);p.setInvulnerable(true);var data=Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(p);data.setStarterSelected(true);data.setStarterPrompted(true);var store=Cobblemon.INSTANCE.getStorage().getParty(p);for(int i=0;i<6;i++)store.set(i,PokemonProperties.Companion.parse("pikachu level=50").create());Object addon=((NetworkHandlerExtensions)(Object)p.connection).getAddon();((Set<ResourceLocation>)f(addon,"sendableChannels")).add(ResourceLocation.parse("cobbleleague:message"));action(p,"hello","protocol",11);}
   original=Cobblemon.INSTANCE.getStorage().getParty(ps[0]).toGappyList().stream().map(p->p.getUuid()).toList();originalTera=Cobblemon.INSTANCE.getStorage().getParty(ps[0]).get(0).getTeraType().getId().toString();
   bot=new CreepRoundSmoke();set(bot,"service",service);set(bot,"server",server);System.arraycopy(ps,0,(ServerPlayer[])f(bot,"people"),0,2);phase=2;return;
  }
  if(phase==2||phase==3){action(ps[phase-2],"lobby_join");phase++;return;}
  if(phase==4||phase==5){action(ps[phase-4],"lobby_ready","ready",true);phase++;return;}
  if(phase==6){Object g=game();if(g==null)return;Object model=call(g,"getModel");set(model,"plan",null);set(model,"round",targets[index]-1);
   for(var p:ps){Object r=run(p),state=call(r,"getState");set(state,"round",targets[index]-1);var store=Cobblemon.INSTANCE.getStorage().getParty(p);for(int i=0;i<6;i++){var mon=PokemonProperties.Companion.parse("snorlax level=50 moves=protect,tackle,rest,bodyslam").create();store.set(i,mon);((Map<UUID,List<String>>)f(r,"authorizedMoves")).put(mon.getUuid(),List.of("protect","tackle","rest","bodyslam"));}call(state,"syncTeamSize",6);}
   var m=Arrays.stream(service.getClass().getDeclaredMethods()).filter(x->x.getName().equals("beginLobbyRound")).findFirst().orElseThrow();m.setAccessible(true);m.invoke(service,g);phase=7;return;
  }
  if(phase==7){Object g=game();if(!call(g,"getPhase").toString().equals("SELECTION"))return;for(var p:ps){Object r=run(p),state=call(r,"getState");if(!(boolean)call(state,"getReady")){if((int)call(r,"getRoom")<0)return;action(p,"pick_skip","version",call(state,"getVersion"));}}phase=8;return;}
  if(phase==8){if(!call(game(),"getPhase").toString().equals("PREPARATION"))return;
   if(index==0){Object prep=call(run(ps[0]),"getPreparation");call(prep,"credit",10);var mon=Cobblemon.INSTANCE.getStorage().getParty(ps[0]).get(0);int before=(int)call(prep,"getMoney");action(ps[0],"buy","operation","tera","pokemon",mon.getUuid(),"tera","cobblemon:fire","view","tera");record("tera-three-gold-updates-temporary-mon",mon.getTeraType().getId().toString().equals("cobblemon:fire")&&(int)call(prep,"getMoney")==before-3,"native tera setter");before=(int)call(prep,"getMoney");boolean rejected=false;try{action(ps[0],"buy","operation","tera","pokemon",mon.getUuid(),"tera","cobblemon:fire");}catch(InvocationTargetException e){rejected=true;}record("same-tera-does-not-charge",rejected&&(int)call(prep,"getMoney")==before,"same type rejected");rejected=false;try{action(ps[0],"buy","operation","tera","pokemon",mon.getUuid(),"tera","invalid:type");}catch(InvocationTargetException e){rejected=true;}record("invalid-tera-does-not-charge",rejected&&(int)call(prep,"getMoney")==before,"invalid type rejected");}
   Object model=call(game(),"getModel");Map<UUID,Object> standings=(Map<UUID,Object>)call(model,"getStandings");for(int i=0;i<2;i++){hp[i]=(int)call(standings.get(ps[i].getUUID()),"getHealth");action(ps[i],"ready");}phase=9;return;
  }
  if(phase==9){Object g=game(),model=call(g,"getModel");String stage=call(g,"getPhase").toString();if(stage.equals("RESULTS")||stage.equals("REWARDS")){int loser=index==4?0:1;Map<UUID,Object> standings=(Map<UUID,Object>)call(model,"getStandings");int amount=1+(targets[index]-1)/4;record("boss-loss-same-escalating-damage-"+targets[index],(int)call(standings.get(ps[loser].getUUID()),"getHealth")==hp[loser]-amount,"loss "+amount);phase=10;return;}
   Map<UUID,Object> current=(Map<UUID,Object>)call(g,"getCurrentCreeps");for(var p:ps){var b=BattleRegistry.getBattleByParticipatingPlayer(p);if(b==null||!b.getStarted()||b.getTurn()<2||!forced.add(b.getBattleId()))continue;var enemy=java.util.stream.StreamSupport.stream(b.getActors().spliterator(),false).filter(a->java.util.stream.StreamSupport.stream(a.getPlayerUUIDs().spliterator(),false).noneMatch(id->id.equals(p.getUUID()))).findFirst().orElseThrow();int count=targets[index]==4?4:6;record("native-trainer-team-"+targets[index]+"-"+p.getGameProfile().getName(),enemy.getPokemonList().size()==count&&enemy.getPokemonList().stream().allMatch(m->m.getEffectedPokemon().getLevel()==50),call(call(current.get(p.getUUID()),"getTrainer"),"getName"));boolean loss=(index==4?p==ps[0]:p==ps[1]);b.writeShowdownAction(">forcelose "+(loss?b.getActor(p).getShowdownId():enemy.getShowdownId()));}return;
  }
  if(phase==10){if(!call(game(),"getPhase").toString().equals("REWARDS"))return;if(index==0)record("tera-retained-after-battle",Cobblemon.INSTANCE.getStorage().getParty(ps[0]).get(0).getTeraType().getId().toString().equals("cobblemon:fire"),"temporary party retains purchased type");if(++index<targets.length){phase=6;return;}call(service,"leave",ps[0]);phase=11;return;}
  if(phase==11){record("original-party-and-tera-unchanged",original.equals(Cobblemon.INSTANCE.getStorage().getParty(ps[0]).toGappyList().stream().map(p->p.getUuid()).toList())&&originalTera.equals(Cobblemon.INSTANCE.getStorage().getParty(ps[0]).get(0).getTeraType().getId().toString()),"original store restored");record("completed",true,"bosses4/8/12/16/20 and tera shop");active=false;for(var p:ps)command("player "+p.getGameProfile().getName()+" kill");}
 }
}
