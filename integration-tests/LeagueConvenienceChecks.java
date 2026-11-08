package ai.league.practice;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.BiConsumer;
public final class LeagueConvenienceChecks {
 static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static Object call(Object o,String n,Object... args)throws Exception{for(var m:o.getClass().getMethods())if(m.getName().equals(n)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(o,args);}throw new NoSuchMethodException(n);}
 static Object run(Object s,ServerPlayer p)throws Exception{return ((Map<?,?>)field(s,"runs")).get(p.getUUID());}
 static void action(Object s,ServerPlayer p,String operation,UUID pokemon,String other,Object prep)throws Exception{
  ((Map<?,?>)field(s,"lastRequest")).remove(p.getUUID());var j=new JsonObject();j.addProperty("action","buy");j.addProperty("operation",operation);j.addProperty("pokemon",pokemon.toString());j.addProperty("revision",(int)call(prep,"getRevision"));j.addProperty("view","equipment");
  if(operation.equals("buy"))j.addProperty("product",other);else j.addProperty("other",other);call(s,"handle",p,j);
 }
 public static void equipment(Object service,ServerPlayer p,BiConsumer<String,Boolean> check)throws Exception{
  var store=Cobblemon.INSTANCE.getStorage().getParty(p);Object r=run(service,p),prep=call(r,"getPreparation");
  @SuppressWarnings("unchecked") var stash=(Map<String,Integer>)field(r,"equipment");var before=new HashMap<>(stash);var mons=store.toGappyList().stream().filter(Objects::nonNull).toList();var held=mons.stream().map(m->m.heldItem().copy()).toList();int cash=(int)call(prep,"getMoney");
  try{
   var method=Arrays.stream(service.getClass().getDeclaredMethods()).filter(m->m.getName().equals("draftOwnershipCounts")).findFirst().orElse(null);
   if(method!=null){method.setAccessible(true);@SuppressWarnings("unchecked") var counts=(Map<String,Integer>)method.invoke(service,r);int total=0;for(Object other:((Map<?,?>)field(service,"runs")).values())if(Objects.equals(call(other,"getLobbyGame"),call(r,"getLobbyGame")))total+=(int)((List<?>)call(call(other,"getParty"),"toGappyList")).stream().filter(Objects::nonNull).count();check.accept("ownership-counts-include-other-participant-party",counts.values().stream().mapToInt(Integer::intValue).sum()==total&&total>mons.size());}
   call(prep,"credit",10);var a=mons.get(0);var b=mons.get(1);
   action(service,p,"buy",a.getUuid(),"cobblemon:air_balloon",prep);action(service,p,"buy",b.getUuid(),"cobblemon:muscle_band",prep);
   int afterBuy=(int)call(prep,"getMoney");var inventory=new HashMap<>(stash);action(service,p,"swap",a.getUuid(),b.getUuid().toString(),prep);
   check.accept("equipment-swap-no-money-or-stock-change",BuiltInRegistries.ITEM.getKey(a.heldItem().getItem()).getPath().equals("muscle_band")&&BuiltInRegistries.ITEM.getKey(b.heldItem().getItem()).getPath().equals("air_balloon")&&inventory.equals(stash)&&afterBuy==(int)call(prep,"getMoney"));
   action(service,p,"unequip_all",a.getUuid(),"",prep);var once=new HashMap<>(stash);
   check.accept("equipment-unequip-all-returns-every-held-item",mons.stream().allMatch(m->m.heldItem().isEmpty())&&stash.getOrDefault("cobblemon:air_balloon",0)==inventory.getOrDefault("cobblemon:air_balloon",0)+1&&stash.getOrDefault("cobblemon:muscle_band",0)==inventory.getOrDefault("cobblemon:muscle_band",0)+1&&afterBuy==(int)call(prep,"getMoney"));
   boolean rejected=false;try{action(service,p,"unequip_all",a.getUuid(),"",prep);}catch(InvocationTargetException e){rejected=true;}
   check.accept("empty-unequip-cannot-duplicate-stash",rejected&&once.equals(stash));
  }finally{
   for(int i=0;i<mons.size();i++)Class.forName("dev.cobbleleague.mixin.DungeonHeldItemAccessor").getMethod("cobbleleague$setVirtualItem",ItemStack.class).invoke(mons.get(i),held.get(i));
   stash.clear();stash.putAll(before);var f=prep.getClass().getDeclaredField("money");f.setAccessible(true);f.setInt(prep,cash);((Map<?,?>)field(service,"lastRequest")).remove(p.getUUID());
  }
 }
 static net.minecraft.world.phys.Vec3 origin;
 public static void spectate(Object service,ServerPlayer viewer,ServerPlayer fighter,BiConsumer<String,Boolean> check)throws Exception{
  var target=BattleRegistry.getBattleByParticipatingPlayer(fighter);var party=Cobblemon.INSTANCE.getStorage().getParty(viewer).toGappyList().stream().map(m->m==null?null:m.getUuid()).toList();origin=viewer.position();var opponentPos=fighter.position();
  call(service,"watchBattle",viewer,target.getBattleId());
  check.accept("two-player-gym-observer-teleported-to-target-room",viewer.distanceToSqr(fighter)<1600&&target.getSpectators().contains(viewer.getUUID())&&BattleRegistry.getBattleByParticipatingPlayer(viewer)==null);
  call(service,"tick");check.accept("observer-keeps-own-run-outside-original-room",run(service,viewer)!=null&&(boolean)call(service,"isWatching",viewer.getUUID())&&!viewer.isSpectator());
  boolean refused=false;try{call(service,"watchBattle",fighter,target.getBattleId());}catch(InvocationTargetException e){refused=true;}
  check.accept("active-fighter-cannot-spectate-or-lose-battle",refused&&BattleRegistry.getBattleByParticipatingPlayer(fighter)==target&&fighter.position().equals(opponentPos));
  call(service,"stopWatching",viewer);
  check.accept("spectator-stop-restores-position-and-party",viewer.position().distanceToSqr(origin)<.01&&!target.getSpectators().contains(viewer.getUUID())&&party.equals(Cobblemon.INSTANCE.getStorage().getParty(viewer).toGappyList().stream().map(m->m==null?null:m.getUuid()).toList()));
  call(service,"watchBattle",viewer,target.getBattleId());
 }
 public static void afterSpectate(Object service,ServerPlayer p,BiConsumer<String,Boolean> check)throws Exception{
  check.accept("spectator-auto-restored-after-gym-end",!(boolean)call(service,"isWatching",p.getUUID())&&p.position().distanceToSqr(origin)<.01);
 }
}
