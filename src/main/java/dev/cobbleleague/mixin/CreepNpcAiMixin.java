package dev.cobbleleague.mixin;
import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import com.cobblemon.mod.common.battles.ShowdownActionRequest;
import dev.cobbleleague.CreepNpcBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import kotlin.Unit;
/** Only the new marked creep NPCs; the existing practice AI is left in place. */
@Mixin(value=AIBattleActor.class,remap=false,priority=900)
public abstract class CreepNpcAiMixin {
 @Unique private ShowdownActionRequest cobbleleague$scheduled,cobbleleague$answered;
 @Inject(method="onChoiceRequested",at=@At("HEAD"),cancellable=true,remap=false)
 private void cobbleleague$creepChoice(CallbackInfo ci){
  if(!((Object)this instanceof NPCBattleActor actor)||!actor.getEntity().getTags().contains(CreepNpcBridge.MARKER))return;
  ci.cancel();var request=actor.getRequest();
  if(request==null||request==cobbleleague$scheduled||request==cobbleleague$answered)return;
  cobbleleague$scheduled=request;
  actor.getBattle().doWhenClear(()->{
   if(cobbleleague$scheduled==request)cobbleleague$scheduled=null;
   if(!actor.getBattle().getEnded()&&actor.getRequest()==request&&cobbleleague$answered!=request){
    cobbleleague$answered=request;
    try{CreepNpcBridge.choose(actor);}finally{actor.getPokemonList().forEach(mon->mon.setWillBeSwitchedIn(false));}
   }
   return Unit.INSTANCE;
  });
 }
}
