package dev.cobbleleague

/** Mutually exclusive categories; percentages are stored as integer hundredths. */
enum class DrawCategory(val id:String,val label:String) {
    NORMAL("normal","일반·기본형"), LEGENDARY("legendary","전설"), SUBLEGENDARY("sublegendary","준전설"),
    MYTHICAL("mythical","환상"), PARADOX("paradox","패러독스"), MEGA("mega","메가진화");
    val boostBasisPoints get()=when(this){NORMAL->0;LEGENDARY->20;SUBLEGENDARY->30;MYTHICAL->40;PARADOX,MEGA->50}
    companion object {
        fun parse(id:String)=entries.firstOrNull{it.id==id}?:error("잘못된 뽑기 분류입니다.")
        fun classify(labels:Set<String>,mega:Boolean=false):DrawCategory {
            val l=labels.map{it.lowercase()}.toSet()
            return when {
                mega->MEGA
                "mythical" in l->MYTHICAL
                "paradox" in l->PARADOX
                "legendary" in l&&"restricted" in l->LEGENDARY
                "legendary" in l||"sub_legendary" in l||"sublegendary" in l||"ultra_beast" in l->SUBLEGENDARY
                else->NORMAL
            }
        }
    }
}
data class DrawOdds(val values:Map<DrawCategory,Int> = mapOf(
    DrawCategory.NORMAL to 9400,DrawCategory.LEGENDARY to 50,DrawCategory.SUBLEGENDARY to 50,
    DrawCategory.MYTHICAL to 200,DrawCategory.PARADOX to 200,DrawCategory.MEGA to 100
)) {
    init{require(DrawCategory.entries.all{it in values}&&values.values.all{it>=0}&&values.values.sum()==10000){"뽑기 확률 합계는 100%여야 합니다."}}
    fun category(roll:Int):DrawCategory {
        require(roll in 0..9999);var remaining=roll
        for(category in DrawCategory.entries){remaining-=values.getValue(category);if(remaining<0)return category}
        error("잘못된 확률 합계")
    }
    fun boosted(category:DrawCategory):DrawOdds {
        val amount=category.boostBasisPoints
        require(category!=DrawCategory.NORMAL){"희귀 분류를 선택하세요."};require(values.getValue(DrawCategory.NORMAL)>=amount){"더 이상 확률을 강화할 수 없습니다."}
        return DrawOdds(values+mapOf(DrawCategory.NORMAL to values.getValue(DrawCategory.NORMAL)-amount,category to values.getValue(category)+amount))
    }
    companion object {
        fun migrateLegacy(normal:Int,legendary:Int,mythical:Int,paradox:Int):DrawOdds {
            require(listOf(normal,legendary,mythical,paradox).all{it>=0}&&normal+legendary+mythical+paradox==100)
            val mega=minOf(100,normal*100)
            return DrawOdds(mapOf(DrawCategory.NORMAL to normal*100-mega,DrawCategory.LEGENDARY to legendary*50,
                DrawCategory.SUBLEGENDARY to legendary*50,DrawCategory.MYTHICAL to mythical*100,DrawCategory.PARADOX to paradox*100,DrawCategory.MEGA to mega))
        }
    }
}
class DrawOddsBoosts(initial:DrawOdds=DrawOdds()) {
    var odds=initial;private set
    var usedRound=-1;private set
    fun available(round:Int)=round>usedRound
    fun buy(category:DrawCategory,round:Int){require(round>=1&&available(round)){"이번 라운드에는 이미 확률을 강화했습니다."};val updated=odds.boosted(category);odds=updated;usedRound=round}
}
