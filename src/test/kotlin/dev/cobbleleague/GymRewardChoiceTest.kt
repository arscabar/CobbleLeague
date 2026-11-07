package dev.cobbleleague
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class GymRewardChoiceTest {
    @Test fun `three stable offers always include money and two distinct other prizes`() {
        val seen=mutableSetOf<List<GymPrize>>()
        for(seed in 0..99){
            val offer=GymRewardChoice.offer(GymPrize.entries,kotlin.random.Random(seed))
            assertEquals(3,offer.options.size);assertEquals(GymPrize.MONEY,offer.options.first())
            assertEquals(3,offer.options.distinct().size);val before=offer.options.toList()
            offer.choose(offer.token,GymPrize.MONEY){}
            assertEquals(before,offer.options);seen.add(before)
        }
        assertTrue(seen.size>5)
    }

    @Test fun `one choice excludes all other rewards and duplicate packets`() {
        val offer=GymRewardChoice(GymPrize.entries)
        var delivered=0
        offer.choose(offer.token,GymPrize.MONEY){delivered++}
        for(prize in GymPrize.entries)assertThrows(IllegalArgumentException::class.java){offer.choose(offer.token,prize){delivered++}}
        assertEquals(1,delivered);assertEquals(GymPrize.MONEY,offer.selected)
    }
    @Test fun `invalid token unsupported option and failed delivery cannot claim reward`() {
        val offer=GymRewardChoice(listOf(GymPrize.LEGENDARY))
        assertThrows(IllegalArgumentException::class.java){offer.choose("old",GymPrize.LEGENDARY){fail("not delivered")}}
        assertThrows(IllegalArgumentException::class.java){offer.choose(offer.token,GymPrize.MONEY){fail("not delivered")}}
        assertThrows(IllegalStateException::class.java){offer.choose(offer.token,GymPrize.LEGENDARY){error("registry unavailable")}}
        assertNull(offer.selected)
        offer.choose(offer.token,GymPrize.LEGENDARY){}
        assertEquals(GymPrize.LEGENDARY,offer.selected)
    }
    @Test fun `pvp requires both players once and old votes do not carry to next attempt`() {
        val a=UUID.randomUUID();val b=UUID.randomUUID();val vote=BattleRestartVote(setOf(a,b))
        assertFalse(vote.request(a));assertFalse(vote.request(a));assertEquals(1,vote.count)
        assertThrows(IllegalArgumentException::class.java){vote.request(UUID.randomUUID())}
        assertTrue(vote.request(b));assertEquals(2,vote.count)
        assertFalse(BattleRestartVote(setOf(a,b)).request(b))
        assertTrue(BattleRestartVote(setOf(a)).request(a))
    }
}
