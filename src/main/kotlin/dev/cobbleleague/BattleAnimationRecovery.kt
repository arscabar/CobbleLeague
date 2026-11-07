package dev.cobbleleague

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.effects.IllusionEffect
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.activestate.InactivePokemonState
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** Completes only stalled visual operations. Never bypasses engine input/turn dispatches. */
object BattleAnimationRecovery {
    private data class Pending(val battle:UUID,val mon:Pokemon,val started:Int,val future:CompletableFuture<*>,val recall:Boolean,val repair:()->Unit)
    private val pending=mutableListOf<Pending>()
    private val missingSince=mutableMapOf<Pair<UUID,UUID>,Int>()
    private const val GRACE=120

    @JvmStatic fun watchSendOut(mon:Pokemon,world:ServerLevel,pos:Vec3,id:UUID?,illusion:IllusionEffect?,mutation:(PokemonEntity)->Unit,future:CompletableFuture<PokemonEntity>) {
        if(id==null||world.dimension().location().toString()!="cobbleleague:league")return
        pending.add(Pending(id,mon,world.server.tickCount,future,false) {
            LeagueMod.LOGGER.warn("Stalled send-out: battle={}, pokemon={}, entity={}, removed={}, beam={}",id,mon.uuid,mon.entity?.id,mon.entity?.isRemoved,mon.entity?.beamMode)
            val entity=mon.entity?.takeUnless{it.isRemoved} ?: run {
                mon.state=InactivePokemonState()
                requireNotNull(mon.sendOut(world,pos,illusion){mutation(it);it.ownerUUID=mon.getOwnerUUID();it.battleId=id}) {"복구 소환이 거절되었습니다."}
            }
            entity.phasingTargetId=-1;entity.beamMode=0
            check(!entity.isRemoved && world.getEntity(entity.uuid)===entity){"복구 소환 개체가 월드에 없습니다."}
            future.complete(entity)
        })
    }
    @JvmStatic fun watchRecall(entity:PokemonEntity,future:CompletableFuture<Pokemon>) {
        val world=entity.level() as? ServerLevel ?: return
        val id=entity.battleId ?: return
        if(world.dimension().location().toString()!="cobbleleague:league")return
        val mon=entity.pokemon
        pending.add(Pending(id,mon,world.server.tickCount,future,true) {
            LeagueMod.LOGGER.warn("Stalled recall: battle={}, pokemon={}, entity={}, removed={}, beam={}",id,mon.uuid,entity.id,entity.isRemoved,entity.beamMode)
            // An old delayed recall must never recall the replacement entity.
            if(mon.entity===entity)mon.recall()
            if(!entity.isRemoved)entity.discard()
            future.complete(mon)
        })
    }
    fun forget(id:UUID) {
        val old=pending.filter{it.battle==id};pending.removeAll(old.toSet())
        old.forEach{if(!it.future.isDone)it.future.cancel(false)}
        missingSince.keys.removeIf{it.first==id}
    }
    fun tick(battle:PokemonBattle,now:Int) {
        val work=pending.filter{it.battle==battle.battleId}
        if(battle.ended){forget(battle.battleId);return}
        // Remove before callbacks: completing a recall can synchronously enqueue a send-out.
        for(op in work) {
            if(op.future.isDone&&!op.future.isCompletedExceptionally){pending.remove(op);continue}
            if(now-op.started<GRACE)continue
            pending.remove(op)
            check(!op.future.isCompletedExceptionally){"소환·회수 작업이 실패했습니다."}
            op.repair()
            LeagueMod.LOGGER.warn("Recovered stalled battle animation: battle={}, pokemon={}, recall={}",op.battle,op.mon.uuid,op.recall)
        }
        val active=battle.activePokemon.mapNotNull{slot->slot.battlePokemon?.let{slot to it}}.toList()
        val keys=active.map{battle.battleId to it.second.uuid}.toSet()
        missingSince.keys.removeIf{it.first==battle.battleId&&it !in keys}
        for((slot,bm) in active) {
            val key=battle.battleId to bm.uuid
            val entity=bm.entity
            val exists=entity!=null&&!entity.isRemoved&&(entity.level() as? ServerLevel)?.getEntity(entity.uuid)===entity
            if(bm.health<=0||exists||pending.any{it.battle==battle.battleId&&it.mon===bm.effectedPokemon}){missingSince.remove(key);continue}
            val since=missingSince.getOrPut(key){now}
            if(now-since<GRACE)continue
            val world=slot.position?.first ?: battle.players.firstOrNull()?.serverLevel() ?: error("전투 월드가 없습니다.")
            val mon=bm.effectedPokemon
            mon.state=InactivePokemonState()
            val restored=requireNotNull(mon.sendOut(world,requireNotNull(slot.getSendOutPosition() ?: slot.position?.second),slot.illusion?.let{IllusionEffect(it.effectedPokemon)}){it.ownerUUID=mon.getOwnerUUID();it.battleId=battle.battleId})
            restored.phasingTargetId=-1;restored.beamMode=0
            check(!restored.isRemoved&&world.getEntity(restored.uuid)===restored){"전투 포켓몬 복구에 실패했습니다."}
            missingSince.remove(key)
            LeagueMod.LOGGER.warn("Restored missing battle entity: battle={}, pokemon={}",battle.battleId,bm.uuid)
        }
    }
    fun prune(active:Set<UUID>) {
        // An ordinary Cobblemon battle in this dimension must remain completely untouched.
        pending.removeAll{it.battle !in active}
        missingSince.keys.removeIf{it.first !in active}
    }
}
