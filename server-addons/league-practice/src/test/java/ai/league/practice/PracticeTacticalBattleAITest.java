package ai.league.practice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PracticeTacticalBattleAITest {

    @Test
    void singleTypeEffectivenessMatchesGen9Chart() {
        assertEquals(0.0, PracticeBattleMath.getSingleTypeEffectiveness("ground", "flying"));
        assertEquals(0.0, PracticeBattleMath.getSingleTypeEffectiveness("dragon", "fairy"));
        assertEquals(2.0, PracticeBattleMath.getSingleTypeEffectiveness("ice", "dragon"));
        assertEquals(0.5, PracticeBattleMath.getSingleTypeEffectiveness("fire", "water"));
        assertEquals(2.0, PracticeBattleMath.getSingleTypeEffectiveness("steel", "fairy"));
        assertEquals(2.0, PracticeBattleMath.stageMultiplier(2));
        assertEquals(0.5, PracticeBattleMath.stageMultiplier(-2));
    }

    @Test
    void shouldVoluntarySwitchTriggersWhenActiveMonInLosingDuel() {
        // Eternatus (250 HP, dealing 65 [~26%] with Dynamax Cannon) vs Calyrex-Shadow (175 HP, dealing 110 [~44%] with Astral Barrage, faster)
        // turnsToKillOpp = ceil(175/65) = 3. turnsToDie = ceil(250/110) = 3. Slower -> winsDuel = (3 < 3) = false.
        // Losing damage race -> should voluntary switch!
        boolean switchDecision = PracticeBattleMath.shouldVoluntarySwitch(
            false, // slower
            250,   // userHp
            250,   // userMaxHp
            175,   // oppHp
            65,    // ourBestDmg
            110,   // oppBestDmg
            0      // offensiveBoosts
        );
        assertTrue(switchDecision, "Active mon in losing damage race should trigger voluntary switch");
    }

    @Test
    void shouldVoluntarySwitchBlocksWhenWinningOrBoosted() {
        // Active mon can OHKO right now
        assertFalse(PracticeBattleMath.shouldVoluntarySwitch(true, 100, 100, 80, 90, 40, 0));

        // Active mon has offensive boost (+2 Atk)
        assertFalse(PracticeBattleMath.shouldVoluntarySwitch(false, 100, 100, 100, 60, 55, 2));

        // Active mon faster and wins 2HKO duel
        assertFalse(PracticeBattleMath.shouldVoluntarySwitch(true, 100, 100, 100, 55, 40, 0));
    }

    @Test
    void offensiveCounterSwitchCandidateEvaluation() {
        // Yveltal into Calyrex-Shadow:
        // Yveltal takes 74 on switch (benchMaxHp=201, ~36.8%). Deals 240 (OHKO, oppHp=175).
        boolean viable = PracticeBattleMath.isSwitchCandidateViable(
            false, // not immune
            74,    // incomingOnSwitch
            201,   // benchHp
            201,   // benchMaxHp
            240,   // benchBestOut (OHKO)
            175,   // oppHp
            true,  // benchFaster
            false, // isMatchingWall
            false  // hasRecoveryOrStatus
        );
        assertTrue(viable, "Yveltal taking 36% and OHKOing back should be a viable switch candidate");

        double score = PracticeBattleMath.evaluateSwitchScore(
            false, 240, 74, 175, 201, 201, true, false, false
        );
        assertTrue(score > 100.0, "Yveltal switch score should be high (> 100), got: " + score);
    }

    @Test
    void defensiveWallSwitchCandidateEvaluation() {
        // Blissey (Special Wall) into Special Attacker:
        // Takes 45 on switch (benchMaxHp=330, ~13.6%). Deals 50 (Seismic Toss, oppHp=175). Has Soft-Boiled.
        boolean viable = PracticeBattleMath.isSwitchCandidateViable(
            false, // not immune
            45,    // incomingOnSwitch
            330,   // benchHp
            330,   // benchMaxHp
            50,    // benchBestOut
            175,   // oppHp
            false, // benchFaster
            true,  // isMatchingWall (Special wall vs Special attacker)
            true   // hasRecoveryOrStatus
        );
        assertTrue(viable, "Dedicated special wall taking 14% damage should be viable without 50% burst damage");

        double score = PracticeBattleMath.evaluateSwitchScore(
            false, 50, 45, 175, 330, 330, false, true, false
        );
        assertTrue(score > 80.0, "Special wall switch score should be high, got: " + score);
    }

    @Test
    void recoveryMoveScoringPreventsLoopsAndSpam() {
        // 1. High HP (>= 75%): Recovery is rejected (-60)
        assertEquals(-60.0, PracticeBattleMath.scoreRecoveryMove(0.85, true, 85, 100, 100, 40, 20, false));

        // 2. Faster 2HKO while safe: prefers attack (Recover score 40.0)
        assertEquals(40.0, PracticeBattleMath.scoreRecoveryMove(0.55, true, 55, 100, 100, 55, 20, false));

        // 3. Consecutive recovery when healthy: penalize (-50.0)
        assertEquals(-50.0, PracticeBattleMath.scoreRecoveryMove(0.60, false, 60, 100, 100, 25, 20, true));

        // 4. Critical emergency recovery (<= 38%): high priority (95.0)
        assertEquals(95.0, PracticeBattleMath.scoreRecoveryMove(0.30, true, 30, 100, 100, 30, 25, false));
    }

    @Test
    void pureDefensiveSetupScoringPreventsEndlessStall() {
        // 1. Physical defense setup vs Special Attacker: completely rejected (-500)
        assertEquals(-500.0, PracticeBattleMath.scorePureDefensiveSetup(
            false, 0, 0, 0, 40, 100, true, false, true, false, 80, 20
        ));

        // 2. Special defense setup vs Physical Attacker: completely rejected (-500)
        assertEquals(-500.0, PracticeBattleMath.scorePureDefensiveSetup(
            false, 0, 0, 0, 40, 100, false, true, false, true, 80, 20
        ));

        // 3. Cosmic Power already used once without Body Press: rejected (-500)
        assertEquals(-500.0, PracticeBattleMath.scorePureDefensiveSetup(
            false, 1, 1, 1, 40, 100, false, false, true, true, 80, 20
        ));

        // 4. Cosmic Power on passive mon dealing < 35%: capped at 10.0 (attack or switch preferred)
        assertEquals(10.0, PracticeBattleMath.scorePureDefensiveSetup(
            false, 0, 0, 0, 20, 100, false, false, true, true, 80, 20
        ));

        // 5. Valid first Cosmic Power: capped at 54.0 so decent attacks (score ~65-75) outscore it
        assertEquals(54.0, PracticeBattleMath.scorePureDefensiveSetup(
            false, 0, 0, 0, 40, 100, false, false, true, true, 80, 20
        ));
    }

    @Test
    void allPracticeTeamsHaveCompetitiveIvsAndEvs() throws Exception {
        Path teamsDir = Path.of("config/league-practice/teams");

        for (String fileName : List.of("intermediate.json", "advanced.json", "master.json")) {
            Path file = teamsDir.resolve(fileName);
            assertTrue(Files.exists(file), "Missing team file: " + file);

            String raw = Files.readString(file, StandardCharsets.UTF_8);
            if (!raw.isEmpty() && raw.charAt(0) == '\uFEFF') {
                raw = raw.substring(1);
            }
            JsonArray teams = JsonParser.parseString(raw).getAsJsonArray();
            assertNotNull(teams, "Missing teams array in " + fileName);
            assertTrue(teams.size() >= 5, "Expected at least 5 teams in " + fileName);

            for (JsonElement teamEl : teams) {
                JsonObject team = teamEl.getAsJsonObject();
                String teamId = team.get("id").getAsString();
                JsonArray pokemonList = team.getAsJsonArray("pokemon");
                assertEquals(6, pokemonList.size(), "Expected 6 pokemon in " + teamId);

                for (JsonElement pkmEl : pokemonList) {
                    JsonObject pkm = pkmEl.getAsJsonObject();
                    String species = pkm.get("species").getAsString();
                    JsonArray ivs = pkm.getAsJsonArray("ivs");
                    assertNotNull(ivs, "Missing IVs on " + teamId + " " + species);
                    assertEquals(6, ivs.size(), teamId + " " + species + " IVs array length");
                    for (int i = 0; i < 6; i++) {
                        int iv = ivs.get(i).getAsInt();
                        assertTrue(iv == 31 || iv == 0, teamId + " " + species + " IV[" + i + "]=" + iv);
                    }

                    JsonArray evs = pkm.getAsJsonArray("evs");
                    assertNotNull(evs, "Missing EVs on " + teamId + " " + species);
                    assertEquals(6, evs.size(), teamId + " " + species + " EVs array length");
                    int totalEvs = 0;
                    for (int i = 0; i < 6; i++) {
                        totalEvs += evs.get(i).getAsInt();
                    }
                    assertTrue(totalEvs >= 508 && totalEvs <= 510,
                        teamId + " " + species + " total EVs expected 508..510 but got " + totalEvs);
                }
            }
        }
    }
}
