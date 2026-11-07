package ai.league.practice;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor;
import com.cobblemon.mod.common.battles.interpreter.instructions.SwitchInstruction;
import java.util.function.BiConsumer;

/** Destructive fault injection: use only in a disposable server, just before ending its battle. */
public final class BattleRecoveryChecks {
    public static void run(PokemonBattle battle, BiConsumer<String,Boolean> check) {
        try {
            Object recovery=Class.forName("dev.cobbleleague.BattleAnimationRecovery").getField("INSTANCE").get(null);
            var tick=recovery.getClass().getMethod("tick",PokemonBattle.class,int.class);
            var slots=java.util.stream.StreamSupport.stream(battle.getActivePokemon().spliterator(),false)
                    .filter(a->a.getBattlePokemon()!=null&&a.getBattlePokemon().getHealth()>0&&a.getBattlePokemon().getEntity()!=null)
                    .toList();
            if(slots.isEmpty())throw new IllegalStateException("No live entities");
            for(var slot:slots) {
            var mon=slot.getBattlePokemon();var old=mon.getEntity();int hp=mon.getHealth();int now=battle.getPlayers().getFirst().getServer().getTickCount();
            old.discard();tick.invoke(recovery,battle,now);tick.invoke(recovery,battle,now+121);
            check.accept("missing-active-entity-restored",mon.getEntity()!=null&&!mon.getEntity().isRemoved()&&mon.getEntity()!=old&&mon.getEntity().getBattleId().equals(battle.getBattleId()));
            check.accept("recovery-preserves-hp-identity-and-owner",mon.getHealth()==hp&&slot.getBattlePokemon()==mon&&java.util.Objects.equals(mon.getEntity().getOwnerUUID(),mon.getEffectedPokemon().getOwnerUUID()));
            var actor=slot.getActor();var next=actor.getPokemonList().stream().filter(m->m!=mon&&m.getHealth()>0).findFirst().orElse(null);
            if(next==null)continue;
            old=mon.getEntity();old.setPhasingTargetId(-1);
            var result=SwitchInstruction.Companion.createEntitySwitch(battle,actor,((EntityBackedBattleActor<?>)actor).getEntity(),slot.getPNX(),slot,next,null,false);
            // Destroy the outgoing entity before its scheduled recall callback can run.
            old.discard();tick.invoke(recovery,battle,now+242);tick.invoke(recovery,battle,now+363);
            check.accept("stalled-switch-finishes-without-skipping-dispatch",result.canProceed()&&slot.getBattlePokemon()==next&&next.getEntity()!=null&&!next.getEntity().isRemoved());
            }
        } catch(Exception e){throw new IllegalStateException("Native battle recovery check failed",e);}
    }
}
