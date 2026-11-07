package ai.league.practice;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PracticeSettingsTest {
    @Test void preservesTierCountsAndSkill() {
        assertArrayEquals(new int[]{2, 5, 10, 10}, java.util.Arrays.stream(Tier.values()).mapToInt(Tier::slots).toArray());
        assertArrayEquals(new int[]{1, 3, 4, 5}, java.util.Arrays.stream(Tier.values()).mapToInt(Tier::skill).toArray());
    }
    @Test void disabledSetupCanBeIncompleteButEnabledSetupCannot() {
        var settings = new PracticeSettings();
        assertDoesNotThrow(settings::validate); assertFalse(settings.ready());
        settings.enabled = true;
        assertThrows(IllegalArgumentException.class, settings::validate);
        settings.spawn = new SpawnSite(0, 64, 0, 90);
        int i = 0;
        for (Tier tier : Tier.values()) settings.buttons.put(tier.id(), new BlockSite(i++, 64, 0));
        assertTrue(settings.ready());
    }
    @Test void duplicateButtonsAndInvalidDimensionsAreRejected() {
        var s = new PracticeSettings();
        s.buttons.put("beginner", new BlockSite(0, 64, 0));
        s.buttons.put("master", new BlockSite(0, 64, 0));
        assertThrows(IllegalArgumentException.class, s::validate);
        s.buttons.clear(); s.dimension = "over world";
        assertThrows(IllegalArgumentException.class, s::validate);
    }
    @Test void nonfiniteCoordinatesCannotEnableSetup() {
        var s = new PracticeSettings();
        for (SpawnSite point : new SpawnSite[]{new SpawnSite(Double.NaN, 64, 0, 0),
                new SpawnSite(0, 64, 0, Float.POSITIVE_INFINITY), new SpawnSite(30_000_000, 64, 0, 0)}) {
            s.spawn = point;
            assertThrows(IllegalArgumentException.class, s::validate);
        }
    }
}
