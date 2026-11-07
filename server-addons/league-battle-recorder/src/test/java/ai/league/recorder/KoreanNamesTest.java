package ai.league.recorder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KoreanNamesTest {
    @Test void resolvesOfficialNamesAndIds() {
        assertEquals("리자몽", KoreanNames.species("Charizard"));
        assertEquals("던지미", KoreanNames.species("throh"));
        assertEquals("업어후리기", KoreanNames.move("Storm Throw"));
        assertEquals("업어후리기", KoreanNames.move("stormthrow"));
        assertEquals("틀깨기", KoreanNames.ability("Mold Breaker"));
        assertEquals("재생력", KoreanNames.ability("regenerator"));
        assertEquals("매직미러", KoreanNames.ability("ability: Magic Bounce"));
        assertEquals("미래예지", KoreanNames.move("move: Future Sight"));
        assertEquals("플라베베", KoreanNames.species("Flabébé"));
        assertEquals("니드런♀", KoreanNames.species("Nidoran-F"));
        assertEquals("니드런♂", KoreanNames.species("Nidoran♂"));
    }

    @Test void handlesFormsAndKeepsSeparators() {
        assertEquals("메가리자몽X", KoreanNames.species("Charizard-Mega-X"));
        assertEquals("테오키스 (어택폼)", KoreanNames.species("Deoxys-Attack"));
        assertTrue(KoreanNames.species("Basculin-White-Striped").contains("백색근"));
        assertTrue(KoreanNames.species("Genesect-Douse").contains("아쿠아카세트"));
        assertTrue(KoreanNames.species("Ogerpon-Teal-Tera").contains("테라스탈"));
        assertEquals("맹독, 10만볼트", KoreanNames.moves("Toxic, Thunderbolt"));
        assertEquals("맹독,10만볼트", KoreanNames.moves("Toxic,Thunderbolt"));
        assertEquals("Mold / Breaker", KoreanNames.text("Mold / Breaker"));
        assertEquals("Storm → Throw", KoreanNames.text("Storm → Throw"));
        assertFalse(KoreanNames.text("Zygarde-10%").endsWith("폼)%"));
        assertEquals("특성: 틀깨기 / 메가리자몽X", KoreanNames.text("특성: Mold Breaker / Charizard-Mega-X"));
        assertEquals("Unknown Custom Species", KoreanNames.species("Unknown Custom Species"));
    }

    @Test void preservesUserNickname() {
        var mon = new PokemonState("p1", "Toxic", 1, "a", "Pikachu", "Storm Throw", 50, "Toxic", 100, 100);
        assertEquals("피카츄 (Storm Throw)", KoreanNames.label(mon));
        assertEquals("Storm Throw", mon.nickname);
    }
}
