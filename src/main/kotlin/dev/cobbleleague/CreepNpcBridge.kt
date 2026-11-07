package dev.cobbleleague

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor
import com.cobblemon.mod.common.battles.*
import com.cobblemon.mod.common.entity.npc.NPCEntity
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import java.util.function.Consumer
import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI

class CreepNpcHandle(val battle:PokemonBattle,private val handle:Any) {
    private fun invoke(name:String){handle.javaClass.getMethod(name).also{it.isAccessible=true}.invoke(handle)}
    fun tick()=invoke("tick")
    fun abort()=invoke("abort")
}

/** Reuses the installed practice NPC factory, Run & Bun hook and switch/output recovery. */
class CreepNpcBridge {
    companion object {
        const val MARKER="cobbleleague_creep_npc"
        @JvmStatic fun choose(actor:AIBattleActor) {
            val tactical=Class.forName("ai.league.practice.PracticeTacticalBattleAI")
            try {
                val ai=Class.forName("ai.league.practice.PracticeRunBunAI").getMethod("forActor",AIBattleActor::class.java).invoke(null,actor) as BattleAI
                tactical.getMethod("handleChoiceRequested",AIBattleActor::class.java,BattleAI::class.java).invoke(null,actor,ai)
            }catch(e:Exception){
                LeagueMod.LOGGER.warn("Creep AI recovery",e)
                tactical.getMethod("handleChoiceRequested",AIBattleActor::class.java).invoke(null,actor)
            }
        }
    }
    private val teams=mutableMapOf<String,Any>()
    private var verified=false
    private fun type(name:String)=Class.forName("ai.league.practice.$name")
    fun verify() {
        if(verified)return
        require(FabricLoader.getInstance().isModLoaded("league_practice")&&FabricLoader.getInstance().isModLoaded("rbrctai")){"관장전에 필요한 서버 연습장·Run & Bun 애드온이 없습니다."}
        val teamType=type("TeamSpec")
        for(trainer in GymTrainers.all){
            val kind=trainer.kind
            val team=javaClass.getResourceAsStream(trainer.resource)!!.bufferedReader().use{LeagueMod.GSON.fromJson(it,teamType)}
            type("NpcPartyFactory").getMethod("validate",teamType).invoke(null,team)
            val count=(teamType.getMethod("pokemon").invoke(team) as List<*>).size
            require(count==kind.teamSize){"${kind.label} 팀 수가 올바르지 않습니다."};teams[trainer.id]=team
        }
        verified=true
    }
    fun start(player:ServerPlayer,world:ServerLevel,trainer:GymTrainer,format:BattleFormat,x:Double,z:Double):CreepNpcHandle {
        verify();val kind=trainer.kind;require(kind!=DungeonRoundKind.PVP)
        val site=type("SpawnSite").getConstructor(Double::class.javaPrimitiveType,Double::class.javaPrimitiveType,Double::class.javaPrimitiveType,Float::class.javaPrimitiveType)
            .newInstance(x+12.5,65.0,z+0.5,90f)
        val tier=type("Tier").enumConstants.first{(it as Enum<*>).name==if(kind==DungeonRoundKind.GYM4)"INTERMEDIATE" else "MASTER"}
        val spawn=type("NpcSpawn").declaredMethods.single{it.name=="create"&&it.parameterCount==4}.also{it.isAccessible=true}
        val npc=spawn.invoke(null,world,site,tier,teams.getValue(trainer.id)) as NPCEntity
        npc.addTag(MARKER)
        npc.customName=Component.literal("${trainer.name} 관장전")
        var started:PokemonBattle?=null
        try {
            val result=BattleBuilder.pvn(player,npc,battleFormat=format,cloneParties=true,healFirst=true)
            require(result is SuccessfulBattleStart){"관장전을 시작하지 못했습니다."}
            val battle=result.battle
            started=battle
            val ctor=type("NpcBattleHandle").declaredConstructors.single().also{it.isAccessible=true}
            val raw=ctor.newInstance(player.uuid,npc,battle,Consumer<Any>{})
            val finish=raw.javaClass.getDeclaredMethod("finish").also{it.isAccessible=true}
            battle.onEndHandlers.add{finish.invoke(raw);kotlin.Unit}
            check(battle.actors.filter{player.uuid !in it.getPlayerUUIDs()}.flatMap{it.pokemonList}.let{mons->mons.size==kind.teamSize&&mons.all{it.effectedPokemon.level==50}}){"관장 파티 레벨·마릿수 검증 실패"}
            return CreepNpcHandle(battle,raw)
        } catch(e:Exception){
            started?.let{battle->type("NpcBattleCleanup").declaredMethods.first{it.name=="stop"&&it.parameterCount==1}.also{it.isAccessible=true}.invoke(null,battle)}
            type("PracticeNpcCleanup").declaredMethods.firstOrNull{it.name=="remove"&&it.parameterCount==1&&it.parameterTypes[0].isAssignableFrom(npc.javaClass)}?.also{it.isAccessible=true}?.invoke(null,npc)
            throw e
        }
    }
}
