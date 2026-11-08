package dev.cobbleleague
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
class ReleaseVersionTest {
    @Test fun `stable replaces preview and older releases never replace new clients`() {
        assertTrue(releaseIsNewer("0.7.4","0.7.4-rc1"))
        assertTrue(releaseIsNewer("0.3.0","0.2.0+cl0.7.3"))
        assertFalse(releaseIsNewer("0.7.4","0.7.4"))
        assertFalse(releaseIsNewer("0.7.4","0.7.5"))
        assertFalse(releaseIsNewer("0.7.4-rc1","0.7.4"))
        assertFalse(releaseIsNewer("invalid","0.7.4"))
    }
}
