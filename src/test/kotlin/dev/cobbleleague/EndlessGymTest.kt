package dev.cobbleleague
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID
class EndlessGymTest {
 @Test fun `damage continues every four rounds including all bosses`() {
  val game=EliminationGame(List(2){UUID.randomUUID()},100,1,4,15)
  assertEquals((1..24).map{1+(it-1)/4},(1..24).map(game::damageAt))
  assertEquals(15,game.damageAt(10000))
 }
 @Test fun `actual champion losses use the same damage and wait for both results`() {
  val ids=List(2){UUID.randomUUID()};val g=EliminationGame(ids,100,1,4,15)
  for(n in 1..20){val p=g.beginRound();val before=ids.map{g.standings.getValue(it).health}
   if(n%4==0){g.completeCreep(ids[0],false);assertFalse(g.roundComplete);g.completeCreep(ids[1],false);ids.forEachIndexed{i,id->assertEquals(before[i]-(1+(n-1)/4),g.standings.getValue(id).health)}}else p.duels.forEach{g.complete(it,null)}
   assertTrue(g.roundComplete);g.closeRound()
  }
 }
}
