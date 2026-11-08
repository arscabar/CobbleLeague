package dev.cobbleleague

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.pokemon.Pokemon

/** One authority for both the visible catalogue and purchase validation. No legacy/event moves. */
object DungeonLearnset {
    fun entries(mon:Pokemon):Map<MoveTemplate,List<String>> = allEntries(mon).filterKeys{move->mon.moveSet.none{it.name==move.name}}
    fun allEntries(mon: Pokemon,levelLimit:Int?=null): Map<MoveTemplate, List<String>> {
        val moves = mon.form.moves
        val result = linkedMapOf<MoveTemplate, MutableList<String>>()
        fun add(move: MoveTemplate, source: String) { result.getOrPut(move) { mutableListOf() }.add(source) }
        moves.tmMoves.forEach { add(it, "기술머신") }
        moves.eggMoves.forEach { add(it, "알기술") }
        moves.levelUpMoves.toSortedMap().filterKeys{levelLimit==null||it<=levelLimit}.forEach { (level, list) -> list.forEach { add(it, "자력기 Lv.$level") } }
        moves.evolutionMoves.forEach { add(it, "진화") }
        moves.tutorMoves.forEach { add(it, "가르침") }
        return result.mapValues { it.value.distinct() }
    }
}
