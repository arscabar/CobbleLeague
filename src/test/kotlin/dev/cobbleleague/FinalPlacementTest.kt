package dev.cobbleleague
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
class FinalPlacementTest {
    private fun ids(n:Int)=List(n){UUID.randomUUID()}
    @Test fun `sole survivor is first even with lower HP than both departing opponents`() {
        val p=ids(3);val g=EliminationGame(p);g.standings.getValue(p[0]).health=4;g.standings.getValue(p[1]).health=10;g.standings.getValue(p[2]).health=9
        assertEquals(3,g.rank(p[0]));g.withdraw(p[1]);assertEquals(3,g.rank(p[1]));g.withdraw(p[2])
        assertEquals(p[0],g.winner);assertEquals(1,g.rank(p[0]));assertEquals(2,g.rank(p[2]));assertEquals(3,g.rank(p[1]))
    }
    @Test fun `first elimination is last place and final order never changes on repeated leave`() {
        val p=ids(5);val g=EliminationGame(p)
        p.take(4).forEachIndexed{i,id->g.withdraw(id);assertEquals(5-i,g.rank(id))}
        p.take(4).reversed().forEach{g.withdraw(it)}
        assertEquals(listOf(5,4,3,2,1),p.map(g::rank))
    }
    @Test fun `HP defeat records elimination placement as well as manual withdrawal`() {
        val p=ids(3);val g=EliminationGame(p,1,1);val plan=g.beginRound();val duel=plan.duels.single();g.complete(duel,duel.first)
        assertEquals(3,g.rank(duel.second));g.withdraw(plan.bye!!);assertEquals(2,g.rank(plan.bye!!));assertEquals(1,g.rank(duel.first))
    }
    @Test fun `separate NPC defeat callbacks retain the actual elimination order`() {
        val p=ids(3);val g=EliminationGame(p,1,1)
        repeat(3){val round=g.beginRound();round.duels.forEach{g.complete(it,null)};g.closeRound()}
        g.beginRound();g.completeCreep(p[2],false);g.completeCreep(p[0],false);g.completeCreep(p[1],true)
        assertEquals(listOf(2,1,3),p.map(g::rank))
    }
    @Test fun `final snapshot refreshes same completed round after between-round departures`() {
        val p=ids(3);val h=RankHistory();h.record(0,p.associateWith{10});h.record(1,mapOf(p[0] to 4,p[1] to 10,p[2] to 9))
        h.recordFinal(1,mapOf(p[0] to 4,p[1] to 0,p[2] to 0),mapOf(p[0] to 1,p[1] to 3,p[2] to 2))
        assertEquals(2,h.rounds.size);assertEquals(listOf(1,3,2),h.rounds.last().rows.map{it.rank});assertEquals(listOf(4,0,0),h.rounds.last().rows.map{it.health});assertTrue(h.rounds.first().rows.all{it.health==10})
    }
    @Test fun `active participants still rank by HP while eliminated places remain distinct`() {
        val p=ids(4);val g=EliminationGame(p);g.standings.getValue(p[0]).health=3;g.standings.getValue(p[1]).health=7;g.withdraw(p[2]);g.withdraw(p[3])
        assertEquals(listOf(2,1,4,3),p.map(g::rank))
    }
}
