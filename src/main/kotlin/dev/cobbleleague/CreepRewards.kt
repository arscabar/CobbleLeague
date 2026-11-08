package dev.cobbleleague

import java.util.UUID

enum class CreepRewardKind(val id:String,val label:String) {
    MYTHICAL("mythical","환상 뽑기권"), LEGENDARY("legendary","전설 포켓몬");
    companion object { fun parse(id:String)=entries.firstOrNull{it.id==id}?:error("잘못된 뽑기권입니다.") }
}
enum class DungeonRoundKind(val label:String,val teamSize:Int,val reward:CreepRewardKind?) {
    PVP("플레이어 대전",0,null), GYM4("일반 관장전",4,CreepRewardKind.MYTHICAL), GYM8("상급 관장전",6,CreepRewardKind.LEGENDARY),
    GYM12("상위 관장·하위 챔피언전",6,CreepRewardKind.LEGENDARY), GYM16("최상위 챔피언전",6,CreepRewardKind.LEGENDARY);
    companion object { fun at(number:Int)=when{number<=0||number%4!=0->PVP;number==4->GYM4;number==8->GYM8;number==12->GYM12;else->GYM16} }
}
data class PendingReward(val token:String,val kind:CreepRewardKind,val candidate:String)

/** Session-only vouchers. Closing a window never changes an already drawn result. */
class CreepRewardWallet {
    private val grants=mutableSetOf<String>()
    private val amounts=mutableMapOf<CreepRewardKind,Int>()
    private val revealed=ArrayDeque<PendingReward>()
    fun reveal(candidate:String) { revealed.addLast(PendingReward(UUID.randomUUID().toString(),CreepRewardKind.LEGENDARY,candidate));if(pending==null)pending=revealed.removeFirst() }
    var pending:PendingReward?=null;private set
    fun count(kind:CreepRewardKind)=amounts[kind]?:0
    fun grant(kind:CreepRewardKind,battleId:String):Boolean {
        if(!grants.add(battleId))return false
        amounts[kind]=count(kind)+1;return true
    }
    fun draw(kind:CreepRewardKind,pick:()->String):PendingReward {
        pending?.let{return it}
        require(count(kind)>0){"사용할 뽑기권이 없습니다."}
        val candidate=pick() // A failed registry/model check must not consume a voucher.
        val offer=PendingReward(UUID.randomUUID().toString(),kind,candidate)
        amounts[kind]=count(kind)-1;pending=offer;return offer
    }
    fun finish(token:String,apply:()->Unit={}) {
        require(pending?.token==token){"이미 처리했거나 만료된 뽑기 결과입니다."}
        apply();pending=revealed.removeFirstOrNull()
    }
}
