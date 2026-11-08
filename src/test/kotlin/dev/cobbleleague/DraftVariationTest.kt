package dev.cobbleleague
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.random.Random
class DraftVariationTest {
 @Test fun `each current copy reduces ownership weight five percent with a positive floor`() {
  assertEquals(1.0,DraftSpeciesWeight.ownership(0));assertEquals(.95,DraftSpeciesWeight.ownership(1),.000001)
  assertEquals(.9025,DraftSpeciesWeight.ownership(2),.000001);assertEquals(.25,DraftSpeciesWeight.ownership(100))
  assertEquals(1.0,DraftSpeciesWeight.ownership(-1))
 }
 @Test fun `owned species stay drawable while becoming less common in the same rarity group`() {
  val r=Random(247);val count=(1..10000).count{DraftSpeciesWeight.pick(listOf("fresh","owned"),{if(it=="fresh")1.0 else .25},r)=="owned"}
  assertTrue(count in 1800..2200)
 }
 @Test fun `recent offers and shared offers have soft nonzero penalties`() {
  assertEquals(.65,DraftSpeciesWeight.value(0,true,false));assertEquals(.70,DraftSpeciesWeight.value(0,false,true))
  assertEquals(.95*.65*.70,DraftSpeciesWeight.value(1,true,true),.000001)
 }

 @Test fun `avoid shared recent and owned candidates if enough remain`() {
  assertEquals(listOf("d"),DraftDiversity.preferred(listOf("a","b","c","d"),{it},listOf(setOf("a"),setOf("b"),setOf("c"))))
 }
 @Test fun `exhausted exclusions relax inside rarity rather than falling into another category`() {
  val myth=listOf("mew","celebi")
  assertEquals(myth,DraftDiversity.preferred(myth,{it},listOf(myth.toSet(),myth.toSet())))
  assertEquals(listOf("celebi"),DraftDiversity.preferred(myth,{it},listOf(setOf("mew"),myth.toSet())))
 }
 @Test fun `moves vary remain distinct and retain two attacks including stab`() {
  val pool=(0..11).map{DraftMoveOption("move$it",it<8,it<3,if(it<3)"fire" else "normal")}
  val samples=(0..99).map{DraftMoveSelection.choose(pool,Random(it))}
  assertTrue(samples.map{it.map{it.id}.toSet()}.toSet().size>20)
  samples.forEach{assertEquals(4,it.size);assertEquals(4,it.map{it.id}.distinct().size);assertTrue(it.count{it.offensive}>=2);assertTrue(it.any{it.stab});assertTrue(pool.containsAll(it))}
 }
 @Test fun `one move species do not receive invented duplicate moves`() {
  val transform=DraftMoveOption("transform",false,false,"normal")
  assertEquals(listOf(transform),DraftMoveSelection.choose(listOf(transform,transform),Random(1)))
  assertTrue(DraftMoveSelection.choose(emptyList()).isEmpty())
 }
}
