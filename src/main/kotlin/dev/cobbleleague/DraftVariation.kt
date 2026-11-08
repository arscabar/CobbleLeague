package dev.cobbleleague
import kotlin.random.Random
/** Soft exclusions are relaxed inside the already-drawn rarity group, in priority order. */
object DraftDiversity {
 fun <T> preferred(pool:List<T>,key:(T)->String,avoid:List<Set<String>>):List<T>{
  var selected=pool
  for(set in avoid){val fresh=selected.filter{key(it) !in set};if(fresh.isNotEmpty())selected=fresh}
  return selected
 }
}
data class DraftMoveOption(val id:String,val offensive:Boolean,val stab:Boolean,val type:String)
object DraftMoveSelection {
 fun choose(options:List<DraftMoveOption>,random:Random=Random.Default):List<DraftMoveOption>{
  val pool=options.distinctBy{it.id};val selected=mutableListOf<DraftMoveOption>()
  val offense=pool.filter{it.offensive}
  if(offense.isNotEmpty())selected.add(offense.filter{it.stab}.ifEmpty{offense}.random(random))
  val second=offense.filter{it !in selected}
  if(second.isNotEmpty())selected.add(second.filter{it.type!=selected.first().type}.ifEmpty{second}.random(random))
  selected.addAll(pool.filter{it !in selected}.shuffled(random).take((4-selected.size).coerceAtLeast(0)))
  return selected
 }
}

object DraftSpeciesWeight {
 fun ownership(copies:Int)=kotlin.math.max(0.25,Math.pow(0.95,copies.coerceAtLeast(0).toDouble()))
 fun value(copies:Int,seenThisRound:Boolean,recent:Boolean)=ownership(copies)*(if(seenThisRound)0.65 else 1.0)*(if(recent)0.70 else 1.0)
 fun <T> pick(pool:List<T>,weight:(T)->Double,random:Random=Random.Default):T {
  require(pool.isNotEmpty())
  val weights=pool.map{weight(it).also{w->require(w>0&&w.isFinite())}}
  var ticket=random.nextDouble(weights.sum())
  pool.forEachIndexed{i,item->ticket-=weights[i];if(ticket<0)return item}
  return pool.last()
 }
}
