package dev.cobbleleague

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeagueSpawnPolicyTest {
    @Test fun `selection displays and temporary battle sendouts must survive cleanup`() {
        val oldWild = SpawnProtection(age = 500)
        assertTrue(shouldRemoveLeagueWild(oldWild))
        assertFalse(shouldRemoveLeagueWild(oldWild.copy(display = true)))
        assertFalse(shouldRemoveLeagueWild(oldWild.copy(battle = true)))
        assertFalse(shouldRemoveLeagueWild(oldWild.copy(busy = true)))
        assertFalse(shouldRemoveLeagueWild(oldWild.copy(age = 40)))
    }
    @Test fun `owned party PC and pasture Pokemon must never be discarded`() {
        val oldWild = SpawnProtection(age = 10000)
        assertFalse(shouldRemoveLeagueWild(oldWild.copy(owned = true)))
        assertFalse(shouldRemoveLeagueWild(oldWild.copy(stored = true)))
        assertFalse(shouldRemoveLeagueWild(oldWild.copy(tethered = true)))
    }
}
