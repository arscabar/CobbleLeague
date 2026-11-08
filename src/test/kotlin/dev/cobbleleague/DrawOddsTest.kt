package dev.cobbleleague
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DrawOddsTest {
    @Test fun `old configured weights split legends and give mega its own one percent`() {
        val odds=DrawOdds.migrateLegacy(95,1,2,2)
        assertEquals(DrawOdds(),odds);assertEquals(10000,odds.values.values.sum())
        val rolls=(0..9999).map(odds::category).groupingBy{it}.eachCount()
        odds.values.forEach{(kind,count)->assertEquals(count,rolls[kind])}
    }
    @Test fun `mega classification never leaks into the normal pool and base forms remain ordinary`() {
        assertEquals(DrawCategory.NORMAL,DrawCategory.classify(setOf("gen1"),false))
        assertEquals(DrawCategory.MEGA,DrawCategory.classify(setOf("gen1"),true))
        assertEquals(DrawCategory.LEGENDARY,DrawCategory.classify(setOf("legendary","restricted")))
        assertEquals(DrawCategory.SUBLEGENDARY,DrawCategory.classify(setOf("legendary")))
        assertEquals(DrawCategory.SUBLEGENDARY,DrawCategory.classify(setOf("ultra_beast")))
        assertEquals(DrawCategory.MYTHICAL,DrawCategory.classify(setOf("legendary","mythical")))
        assertEquals(DrawCategory.PARADOX,DrawCategory.classify(setOf("paradox")))
    }
    @Test fun `upgrade transfers the configured percentage points to the selected category`() {
        val initial=DrawOdds();val updated=initial.boosted(DrawCategory.LEGENDARY)
        assertEquals(70,updated.values[DrawCategory.LEGENDARY]);assertEquals(9380,updated.values[DrawCategory.NORMAL])
        DrawCategory.entries.filter{it !in setOf(DrawCategory.NORMAL,DrawCategory.LEGENDARY)}.forEach{assertEquals(initial.values[it],updated.values[it])}
        assertEquals(10000,updated.values.values.sum())
    }
    @Test fun `one upgrade per round rejects a second category and persists next round`() {
        val state=DrawOddsBoosts();state.buy(DrawCategory.MEGA,1)
        assertFalse(state.available(1));assertThrows(IllegalArgumentException::class.java){state.buy(DrawCategory.MYTHICAL,1)}
        assertTrue(state.available(2));state.buy(DrawCategory.MEGA,2);assertEquals(200,state.odds.values[DrawCategory.MEGA])
        assertThrows(IllegalArgumentException::class.java){state.buy(DrawCategory.MYTHICAL,1)}
        assertEquals(200,state.odds.values[DrawCategory.MYTHICAL])
    }
    @Test fun `spending is atomic when the round is already used or category is invalid`() {
        val prep=DraftPreparation();repeat(3){prep.awardLoss()};val state=DrawOddsBoosts()
        prep.pay(1,prep.revision,0){state.buy(DrawCategory.PARADOX,1)};assertEquals(2,prep.money)
        assertThrows(IllegalArgumentException::class.java){prep.pay(1,prep.revision,0){state.buy(DrawCategory.LEGENDARY,1)}}
        assertEquals(2,prep.money);assertThrows(IllegalArgumentException::class.java){prep.pay(1,prep.revision,0){state.buy(DrawCategory.NORMAL,2)}}
        assertEquals(2,prep.money);assertTrue(state.available(2))
    }
    @Test fun `new dungeon resets upgrades without modifying shared base odds`() {
        val base=DrawOdds();val old=DrawOddsBoosts(base);old.buy(DrawCategory.MEGA,3)
        val next=DrawOddsBoosts(base);assertEquals(100,next.odds.values[DrawCategory.MEGA]);assertTrue(next.available(1));assertEquals(100,base.values[DrawCategory.MEGA])
    }
    @Test fun `all battles use the same escalating damage`() {
        val game=EliminationGame(List(2){java.util.UUID.randomUUID()},10,1,4,3)
        assertEquals(listOf(1,1,1,1,2,2,2,2,3,3),(1..10).map(game::damageAt))
    }
    @Test fun `configured default damage is one from ten starting points`() {
        val settings=LobbyBalance();assertEquals(15,settings.startingHealth);assertEquals(1,settings.lossDamage)
    }
}
