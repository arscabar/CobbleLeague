package dev.cobbleleague
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.api.moves.categories.DamageCategories
object DungeonRandomMoves {
 fun apply(mon:Pokemon){
  val legal=DungeonLearnset.allEntries(mon,mon.level).keys.associateBy{it.name}
  if(legal.isEmpty())return
  val types=mon.types.toSet()
  val options=legal.values.map{DraftMoveOption(it.name,it.damageCategory!=DamageCategories.STATUS,it.elementalType in types,it.elementalType.name)}
  val selected=DraftMoveSelection.choose(options)
  mon.moveSet.clear();selected.forEach{mon.moveSet.add(legal.getValue(it.id).create())}
 }
}
