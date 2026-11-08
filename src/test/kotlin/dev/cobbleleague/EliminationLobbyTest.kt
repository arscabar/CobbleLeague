package dev.cobbleleague
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class EliminationLobbyTest {
    private fun players(n:Int)=List(n){UUID.nameUUIDFromBytes("player$it".toByteArray())}
    @Test fun `two or more must all explicitly ready`() {
        val p=players(3);val lobby=ReadyLobby()
        lobby.join(p[0]);lobby.setReady(p[0],true);assertFalse(lobby.canStart)
        lobby.join(p[1]);assertFalse(lobby.canStart);lobby.setReady(p[1],true);assertTrue(lobby.canStart)
        lobby.join(p[2]);assertFalse(lobby.canStart);lobby.setReady(p[2],true);assertTrue(lobby.canStart)
        lobby.setReady(p[1],false);assertFalse(lobby.canStart)
    }
    @Test fun `ready set cannot leak into next lobby`() {
        val p=players(2);val l=ReadyLobby();p.forEach{l.join(it);l.setReady(it,true)}
        assertEquals(p,l.takeReady());p.forEach{l.join(it)};assertFalse(l.canStart)
    }
    @Test fun `three players rotate byes without repeats`() {
        val p=players(3);val game=EliminationGame(p,100,1);val byes=mutableListOf<UUID>()
        repeat(11){val plan=game.beginRound();if(plan.kind==DungeonRoundKind.PVP){byes.add(plan.bye!!);assertEquals(1,plan.duels.size);plan.duels.forEach{game.complete(it,null)}}else plan.players.forEach{game.completeCreep(it,true)};game.closeRound()}
        assertTrue(byes.zipWithNext().none{it.first==it.second});assertEquals(setOf(3),byes.groupingBy{it}.eachCount().values.toSet())
    }
    @Test fun `five players rotate fairly and each plays at most once`() {
        val game=EliminationGame(players(5),100,1);repeat(12){val p=game.beginRound();if(p.kind==DungeonRoundKind.PVP){val fighting=p.duels.flatMap{listOf(it.first,it.second)};assertEquals(4,fighting.distinct().size);assertFalse(p.bye in fighting);p.duels.forEach{game.complete(it,null)}}else p.players.forEach{game.completeCreep(it,true)};game.closeRound()};assertEquals(9,game.standings.values.sumOf{it.byes});assertTrue(game.standings.values.maxOf{it.byes}-game.standings.values.minOf{it.byes}<=1)
    }
    @Test fun `only loser loses health and duplicate result cannot debit again`() {
        val p=players(2);val game=EliminationGame(p,4,2);val d=game.beginRound().duels.single();game.complete(d,p[0]);assertEquals(4,game.standings.getValue(p[0]).health);assertEquals(2,game.standings.getValue(p[1]).health)
        assertThrows(IllegalArgumentException::class.java){game.complete(d,p[0])};game.closeRound();val next=game.beginRound().duels.single();game.complete(next,p[0]);game.closeRound();assertTrue(game.finished);assertEquals(p[0],game.winner)
    }
    @Test fun `elimination recomputes even pairing without a fake bye`() {
        val p=players(3);val game=EliminationGame(p);val first=game.beginRound();first.duels.forEach{game.complete(it,null)};game.closeRound();game.withdraw(first.bye!!);assertNull(game.beginRound().bye)
    }
}
