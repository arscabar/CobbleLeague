package dev.cobbleleague
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class CreepRewardsTest {
    @Test fun `every fourth round replaces PVP through champion tiers`() {
        assertEquals(DungeonRoundKind.GYM4,DungeonRoundKind.at(4));assertEquals(4,DungeonRoundKind.at(4).teamSize)
        assertEquals(DungeonRoundKind.GYM8,DungeonRoundKind.at(8));assertEquals(6,DungeonRoundKind.at(8).teamSize)
        assertEquals(DungeonRoundKind.GYM12,DungeonRoundKind.at(12));listOf(16,20,24,100).forEach{assertEquals(DungeonRoundKind.GYM16,DungeonRoundKind.at(it))}
        listOf(1,3,5,7,9,11,13,17).forEach{assertEquals(DungeonRoundKind.PVP,DungeonRoundKind.at(it))}
    }
    @Test fun `milestone has no PVP pairing or bye and waits for every NPC result`() {
        val ids=List(3){UUID.randomUUID()};val g=EliminationGame(ids,100,2)
        repeat(3){val p=g.beginRound();p.duels.forEach{g.complete(it,null)};g.closeRound()}
        val p=g.beginRound();assertEquals(DungeonRoundKind.GYM4,p.kind);assertNull(p.bye);assertTrue(p.duels.isEmpty())
        g.completeCreep(ids[0],true);assertFalse(g.roundComplete)
        g.completeCreep(ids[1],false);assertEquals(98,g.standings.getValue(ids[1]).health)
        assertThrows(IllegalArgumentException::class.java){g.completeCreep(ids[1],false)}
        g.completeCreep(ids[2],true);assertTrue(g.roundComplete);g.closeRound();assertEquals(4,g.round)
    }
    @Test fun `legendary previews queue without granting a ticket`() {
        val wallet=CreepRewardWallet()
        wallet.reveal("lugia")
        val first=wallet.pending!!
        wallet.reveal("hooh")
        assertEquals(first,wallet.pending)
        assertEquals(0,wallet.count(CreepRewardKind.LEGENDARY))
        wallet.finish(first.token)
        assertEquals("hooh",wallet.pending?.candidate)
    }
    @Test fun `same battle cannot grant more than one voucher`() {
        val w=CreepRewardWallet();assertTrue(w.grant(CreepRewardKind.MYTHICAL,"battle"));assertFalse(w.grant(CreepRewardKind.MYTHICAL,"battle"));assertFalse(w.grant(CreepRewardKind.LEGENDARY,"battle"));assertEquals(1,w.count(CreepRewardKind.MYTHICAL))
    }
    @Test fun `reopening pending draw does not reroll or debit again`() {
        val w=CreepRewardWallet();w.grant(CreepRewardKind.MYTHICAL,"a");var calls=0
        val first=w.draw(CreepRewardKind.MYTHICAL){calls++;"mew"}
        assertEquals(first,w.draw(CreepRewardKind.MYTHICAL){calls++;"arceus"});assertEquals(1,calls);assertEquals(0,w.count(CreepRewardKind.MYTHICAL))
    }
    @Test fun `failed draw and failed insertion preserve recoverable reward`() {
        val w=CreepRewardWallet();w.grant(CreepRewardKind.LEGENDARY,"a")
        assertThrows(IllegalStateException::class.java){w.draw(CreepRewardKind.LEGENDARY){error("registry failed")}}
        assertEquals(1,w.count(CreepRewardKind.LEGENDARY))
        val p=w.draw(CreepRewardKind.LEGENDARY){"lugia"}
        assertThrows(IllegalStateException::class.java){w.finish(p.token){error("roster changed")}}
        assertEquals(p,w.pending)
    }
    @Test fun `declining consumes the result without restoring ticket and token replay fails`() {
        val w=CreepRewardWallet();w.grant(CreepRewardKind.MYTHICAL,"a");val p=w.draw(CreepRewardKind.MYTHICAL){"mew"}
        w.finish(p.token);assertNull(w.pending);assertEquals(0,w.count(CreepRewardKind.MYTHICAL))
        assertThrows(IllegalArgumentException::class.java){w.finish(p.token){fail("must not insert twice")}}
    }
}
