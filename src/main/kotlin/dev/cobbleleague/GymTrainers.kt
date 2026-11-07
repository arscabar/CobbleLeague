package dev.cobbleleague

import kotlin.random.Random

data class GymTrainer(val id:String,val name:String,val kind:DungeonRoundKind) {
    val resource get()="/data/cobbleleague/creep/$id.json"
}
object GymTrainers {
    val all=listOf(
        GymTrainer("round4","웅",DungeonRoundKind.GYM4),
        GymTrainer("misty","이슬",DungeonRoundKind.GYM4),
        GymTrainer("surge","마티스",DungeonRoundKind.GYM4),
        GymTrainer("erika","민화",DungeonRoundKind.GYM4),
        GymTrainer("round8","비주기",DungeonRoundKind.GYM8),
        GymTrainer("sabrina","초련",DungeonRoundKind.GYM8),
        GymTrainer("blaine","강연",DungeonRoundKind.GYM8),
        GymTrainer("koga","독수",DungeonRoundKind.GYM8)
    )
    fun pool(kind:DungeonRoundKind)=all.filter{it.kind==kind}
    fun draw(kind:DungeonRoundKind,random:Random=Random.Default)=pool(kind).random(random)
}

data class RoundRank(val player:java.util.UUID,val health:Int,val rank:Int)
data class RoundRanks(val round:Int,val rows:List<RoundRank>)
/** Completed-round HP history plus authoritative placement when the game ends. */
class RankHistory {
    private val entries=mutableListOf<RoundRanks>()
    val rounds get()=entries.toList()
    private fun snapshot(round:Int,health:Map<java.util.UUID,Int>,placements:Map<java.util.UUID,Int>)=
        RoundRanks(round,health.map{(id,value)->RoundRank(id,value,placements[id]?:1+health.values.count{it>value})})
    fun record(round:Int,health:Map<java.util.UUID,Int>,placements:Map<java.util.UUID,Int> = emptyMap()) {
        require(entries.lastOrNull()?.round?.let{round>it}?:true)
        entries.add(snapshot(round,health,placements))
    }
    fun recordFinal(round:Int,health:Map<java.util.UUID,Int>,placements:Map<java.util.UUID,Int>) {
        val last=entries.lastOrNull();require(last==null||round>=last.round)
        val result=snapshot(round,health,placements)
        if(last?.round==round)entries[entries.lastIndex]=result else entries.add(result)
    }
}
