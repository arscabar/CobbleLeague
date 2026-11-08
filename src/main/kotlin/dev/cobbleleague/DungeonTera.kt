package dev.cobbleleague
import com.cobblemon.mod.common.api.types.tera.TeraTypes
import com.cobblemon.mod.common.pokemon.Pokemon
object DungeonTera {
    const val PRICE=3
    val types get()=TeraTypes.filter{it.legalAsStatic}
    fun apply(mon:Pokemon,id:String){
        val type=requireNotNull(types.firstOrNull{it.id.toString()==id}){"사용할 수 없는 테라타입입니다."}
        require(mon.teraType!=type){"이미 같은 테라타입입니다."}
        mon.teraType=type
    }
}
