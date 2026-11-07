package dev.cobbleleague

import java.util.UUID

class ReadyLobby(private val capacity:Int=8) {
    private val members=linkedSetOf<UUID>()
    private val ready=mutableSetOf<UUID>()
    fun members()=members.toList()
    fun isReady(id:UUID)=id in ready
    fun join(id:UUID){require(id in members||members.size<capacity){"로비가 가득 찼습니다."};members.add(id)}
    fun setReady(id:UUID,value:Boolean){require(id in members){"로비에 먼저 입장하세요."};if(value)ready.add(id) else ready.remove(id)}
    fun leave(id:UUID){members.remove(id);ready.remove(id)}
    val canStart get()=members.size>=2&&members.all{it in ready}
    fun takeReady():List<UUID>{check(canStart);return members.toList().also{members.clear();ready.clear()}}
}

data class LeagueDuel(val first:UUID,val second:UUID)
data class EliminationRound(val number:Int,val players:List<UUID>,val bye:UUID?,val duels:List<LeagueDuel>,val kind:DungeonRoundKind=DungeonRoundKind.PVP)
data class PlayerStanding(val id:UUID,var health:Int,var wins:Int=0,var losses:Int=0,var draws:Int=0,var byes:Int=0,var finalPlace:Int?=null)

/** Round-level rewards/selection; battles remain ordinary two-sided Cobblemon PvP. */
class EliminationGame(players:List<UUID>,val startingHealth:Int=10,val lossDamage:Int=1, val escalateFromGym:Int=Int.MAX_VALUE, val maxLossDamage:Int=100) {
    init{require(players.size in 2..8&&players.distinct().size==players.size);require(startingHealth>0&&lossDamage>0)}
    val standings=players.associateWith{PlayerStanding(it,startingHealth)}.toMutableMap()
    private val seating=players.toList()
    private var lastBye:UUID?=null
    private val previousOpponent=mutableMapOf<UUID,UUID>()
    private val completed=mutableSetOf<LeagueDuel>()
    private val creepCompleted=mutableSetOf<UUID>()
    var round=0;private set
    var plan:EliminationRound?=null;private set
    val alive get()=seating.filter{standings.getValue(it).health>0}
    val finished get()=alive.size<=1
    val winner get()=alive.singleOrNull()
    fun beginRound():EliminationRound {
        check(!finished&&plan==null)
        val active=alive
        val kind=DungeonRoundKind.at(round+1)
        creepCompleted.clear()
        if(kind!=DungeonRoundKind.PVP){completed.clear();return EliminationRound(round+1,active,null,emptyList(),kind).also{plan=it}}
        val order=active.drop(round%active.size)+active.take(round%active.size)
        val bye=if(active.size%2==1)order.filter{it!=lastBye}.minByOrNull{standings.getValue(it).byes} else null
        if(bye!=null){standings.getValue(bye).byes++;lastBye=bye}
        val remaining=order.filter{it!=bye}.toMutableList();val duels=mutableListOf<LeagueDuel>()
        while(remaining.isNotEmpty()){
            val a=remaining.removeAt(0)
            val bi=remaining.indexOfFirst{it!=previousOpponent[a]}.takeIf{it>=0}?:0
            val b=remaining.removeAt(bi);duels.add(LeagueDuel(a,b))
        }
        completed.clear();return EliminationRound(round+1,active,bye,duels).also{plan=it}
    }
    fun complete(duel:LeagueDuel,winner:UUID?) {
        require(duel in requireNotNull(plan).duels&&duel !in completed){"이미 처리했거나 잘못된 대전입니다."}
        require(winner==null||winner==duel.first||winner==duel.second)
        completed.add(duel);previousOpponent[duel.first]=duel.second;previousOpponent[duel.second]=duel.first
        for(id in listOf(duel.first,duel.second)){
            val row=standings.getValue(id)
            when{winner==null->row.draws++;winner==id->row.wins++;else->{row.losses++;loseHealth(id)}}
        }
    }
    fun damageAt(number:Int):Int {
        if(DungeonRoundKind.at(number)!=DungeonRoundKind.PVP)return 1
        val increases=(1 until number).count{it>=escalateFromGym && DungeonRoundKind.at(it)!=DungeonRoundKind.PVP}
        return (lossDamage+increases).coerceAtMost(maxOf(lossDamage,maxLossDamage))
    }
    private fun loseHealth(id:UUID){
        val row=standings.getValue(id);if(row.health<=0)return
        row.health=(row.health-damageAt(requireNotNull(plan).number)).coerceAtLeast(0)
        if(row.health==0&&row.finalPlace==null)row.finalPlace=alive.size+1
    }
    fun withdraw(id:UUID){val row=standings[id]?:return;if(row.health<=0)return;row.health=0;if(row.finalPlace==null)row.finalPlace=alive.size+1}
    fun rank(id:UUID):Int {
        val row=standings.getValue(id)
        row.finalPlace?.let{return it}
        if(winner==id)return 1
        return 1+alive.count{standings.getValue(it).health>row.health}
    }
    fun finalPlacements():Map<UUID,Int> = standings.mapNotNull{(id,row)->(if(winner==id)1 else row.finalPlace)?.let{id to it}}.toMap()
    fun completeCreep(id:UUID,won:Boolean) {
        val p=requireNotNull(plan)
        require(p.kind!=DungeonRoundKind.PVP&&id in p.players&&id !in creepCompleted){"이미 처리했거나 잘못된 관장전입니다."}
        creepCompleted.add(id);val row=standings.getValue(id)
        if(won)row.wins++ else {row.losses++;loseHealth(id)}
    }
    fun isCreepCompleted(id:UUID)=id in creepCompleted
    fun isCompleted(duel:LeagueDuel)=duel in completed
    val roundComplete get()=plan?.let{p->if(p.kind==DungeonRoundKind.PVP)p.duels.all{it in completed}else p.players.all{it in creepCompleted||standings.getValue(it).health<=0}}==true
    fun closeRound(){check(roundComplete);round++;plan=null;completed.clear();creepCompleted.clear()}
}
