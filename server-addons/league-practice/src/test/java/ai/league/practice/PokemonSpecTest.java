package ai.league.practice;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PokemonSpecTest {
    private PokemonSpec spec(String species, List<String> moves, int[] iv, int[] ev) {
        return new PokemonSpec(species, "female", "jolly", "static", null, moves, iv, ev);
    }
    private int[] iv() { return new int[]{31, 30, 29, 28, 27, 26}; }
    private int[] ev() { return new int[]{0, 252, 0, 0, 4, 252}; }
    @Test void propertiesFixLevelAndEveryStat() {
        String text = spec("pikachu", List.of("thunderbolt"), iv(), ev()).properties();
        assertTrue(text.contains("level=50 nature=jolly ability=static held_item=minecraft:air"));
        assertTrue(text.contains("moves=thunderbolt aspect=female"));
        String[] names = {"hp", "attack", "defence", "special_attack", "special_defence", "speed"};
        for (int i = 0; i < names.length; i++) {
            assertTrue(text.contains(names[i] + "_iv=" + iv()[i]));
            assertTrue(text.contains(names[i] + "_ev=" + ev()[i]));
        }
    }
    @Test void rejectsTokenInjectionAndDuplicateMoves() {
        assertThrows(IllegalArgumentException.class, () -> spec("pikachu level=100", List.of("tackle"), iv(), ev()).validate());
        assertThrows(IllegalArgumentException.class, () -> spec("pikachu", List.of("tackle", "tackle"), iv(), ev()).validate());
        assertThrows(IllegalArgumentException.class, () -> spec("pikachu", List.of("tackle level=100"), iv(), ev()).validate());
        assertThrows(IllegalArgumentException.class, () -> spec("pikachu", List.of(), iv(), ev()).validate());
    }
    @Test void rejectsInvalidStatRangesAndTotals() {
        for (int[] bad : new int[][]{null, {31}, {32, 0, 0, 0, 0, 0}, {-1, 0, 0, 0, 0, 0}})
            assertThrows(IllegalArgumentException.class, () -> spec("pikachu", List.of("tackle"), bad, ev()).validate());
        assertThrows(IllegalArgumentException.class, () -> spec("pikachu", List.of("tackle"), iv(), new int[]{252, 252, 252, 0, 0, 0}).validate());
    }
    @Test void snapshotsCannotBeMutatedThroughInputOrAccessors() {
        int[] original = iv(); var moves = new ArrayList<>(List.of("tackle"));
        var p = spec("pikachu", moves, original, ev());
        original[0] = 0; moves.clear(); p.ivs()[1] = 0;
        assertEquals(31, p.ivs()[0]); assertEquals(30, p.ivs()[1]);
        assertEquals(List.of("tackle"), p.moves());
    }
    @Test void emptyTeamIsPendingAndOversizedTeamIsRejected() {
        var empty = new TeamSpec("beginner-01", List.of());
        assertDoesNotThrow(empty::validateShape);
        assertThrows(IllegalArgumentException.class, empty::validate);
        var p = spec("pikachu", List.of("tackle"), iv(), ev());
        assertThrows(IllegalArgumentException.class, () -> new TeamSpec("bad", Collections.nCopies(7, p)).validate());
    }
}
