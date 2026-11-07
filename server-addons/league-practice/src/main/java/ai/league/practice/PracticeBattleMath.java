package ai.league.practice;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pure-Java Gen 9 battle math and type chart helper used by {@link PracticeTacticalBattleAI}.
 */
public final class PracticeBattleMath {
    public static final Map<String, Map<String, Integer>> SETUP_MOVE_BOOSTS = buildSetupMoveBoosts();

    private PracticeBattleMath() {
    }

    private static Map<String, Map<String, Integer>> buildSetupMoveBoosts() {
        Map<String, Map<String, Integer>> map = new HashMap<>();
        map.put("swordsdance", Map.of("atk", 2));
        map.put("nastyplot", Map.of("spa", 2));
        map.put("dragondance", Map.of("atk", 1, "spe", 1));
        map.put("quiverdance", Map.of("spa", 1, "spd", 1, "spe", 1));
        map.put("calmmind", Map.of("spa", 1, "spd", 1));
        map.put("bulkup", Map.of("atk", 1, "def", 1));
        map.put("irondefense", Map.of("def", 2));
        map.put("acidarmor", Map.of("def", 2));
        map.put("amnesia", Map.of("spd", 2));
        map.put("cottonguard", Map.of("def", 3));
        map.put("agility", Map.of("spe", 2));
        map.put("rockpolish", Map.of("spe", 2));
        map.put("autotomize", Map.of("spe", 2));
        map.put("tailwind", Map.of("spe", 2));
        map.put("shellsmash", Map.of("atk", 2, "spa", 2, "spe", 2));
        map.put("shiftgear", Map.of("atk", 1, "spe", 2));
        map.put("coil", Map.of("atk", 1, "def", 1));
        map.put("victorydance", Map.of("atk", 1, "def", 1, "spe", 1));
        map.put("tidyup", Map.of("atk", 1, "spe", 1));
        map.put("honeclaws", Map.of("atk", 1));
        map.put("curse", Map.of("atk", 1, "def", 1));
        map.put("cosmicpower", Map.of("def", 1, "spd", 1));
        map.put("bellydrum", Map.of("atk", 6));
        map.put("filletaway", Map.of("atk", 2, "spa", 2, "spe", 2));
        map.put("clangoroussoul", Map.of("atk", 1, "def", 1, "spa", 1, "spd", 1, "spe", 1));
        map.put("noretreat", Map.of("atk", 1, "def", 1, "spa", 1, "spd", 1, "spe", 1));
        map.put("tailglow", Map.of("spa", 3));
        map.put("geomancy", Map.of("spa", 2, "spd", 2, "spe", 2));
        map.put("growth", Map.of("atk", 1, "spa", 1));
        map.put("workup", Map.of("atk", 1, "spa", 1));
        map.put("howl", Map.of("atk", 1));
        map.put("flamecharge", Map.of("spe", 1));
        map.put("trailblaze", Map.of("spe", 1));
        map.put("aquastep", Map.of("spe", 1));
        map.put("torchsong", Map.of("spa", 1));
        map.put("poweruppunch", Map.of("atk", 1));
        return Collections.unmodifiableMap(map);
    }

    public static String normalizeId(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static double stageMultiplier(int stage) {
        int clamped = Math.max(-6, Math.min(6, stage));
        return clamped >= 0 ? (2.0 + clamped) / 2.0 : 2.0 / (2.0 - clamped);
    }

    public static double getSingleTypeEffectiveness(String rawAttackType, String rawDefendType) {
        String atk = normalizeId(rawAttackType);
        String def = normalizeId(rawDefendType);
        return switch (atk) {
            case "normal" -> switch (def) {
                case "rock", "steel" -> 0.5;
                case "ghost" -> 0.0;
                default -> 1.0;
            };
            case "fire" -> switch (def) {
                case "grass", "ice", "bug", "steel" -> 2.0;
                case "fire", "water", "rock", "dragon" -> 0.5;
                default -> 1.0;
            };
            case "water" -> switch (def) {
                case "fire", "ground", "rock" -> 2.0;
                case "water", "grass", "dragon" -> 0.5;
                default -> 1.0;
            };
            case "electric" -> switch (def) {
                case "water", "flying" -> 2.0;
                case "electric", "grass", "dragon" -> 0.5;
                case "ground" -> 0.0;
                default -> 1.0;
            };
            case "grass" -> switch (def) {
                case "water", "ground", "rock" -> 2.0;
                case "fire", "grass", "poison", "flying", "bug", "dragon", "steel" -> 0.5;
                default -> 1.0;
            };
            case "ice" -> switch (def) {
                case "grass", "ground", "flying", "dragon" -> 2.0;
                case "fire", "water", "ice", "steel" -> 0.5;
                default -> 1.0;
            };
            case "fighting" -> switch (def) {
                case "normal", "ice", "rock", "dark", "steel" -> 2.0;
                case "poison", "flying", "psychic", "bug", "fairy" -> 0.5;
                case "ghost" -> 0.0;
                default -> 1.0;
            };
            case "poison" -> switch (def) {
                case "grass", "fairy" -> 2.0;
                case "poison", "ground", "rock", "ghost" -> 0.5;
                case "steel" -> 0.0;
                default -> 1.0;
            };
            case "ground" -> switch (def) {
                case "fire", "electric", "poison", "rock", "steel" -> 2.0;
                case "grass", "bug" -> 0.5;
                case "flying" -> 0.0;
                default -> 1.0;
            };
            case "flying" -> switch (def) {
                case "grass", "fighting", "bug" -> 2.0;
                case "electric", "rock", "steel" -> 0.5;
                default -> 1.0;
            };
            case "psychic" -> switch (def) {
                case "fighting", "poison" -> 2.0;
                case "psychic", "steel" -> 0.5;
                case "dark" -> 0.0;
                default -> 1.0;
            };
            case "bug" -> switch (def) {
                case "grass", "psychic", "dark" -> 2.0;
                case "fire", "fighting", "poison", "flying", "ghost", "steel", "fairy" -> 0.5;
                default -> 1.0;
            };
            case "rock" -> switch (def) {
                case "fire", "ice", "flying", "bug" -> 2.0;
                case "fighting", "ground", "steel" -> 0.5;
                default -> 1.0;
            };
            case "ghost" -> switch (def) {
                case "psychic", "ghost" -> 2.0;
                case "dark" -> 0.5;
                case "normal" -> 0.0;
                default -> 1.0;
            };
            case "dragon" -> switch (def) {
                case "dragon" -> 2.0;
                case "steel" -> 0.5;
                case "fairy" -> 0.0;
                default -> 1.0;
            };
            case "dark" -> switch (def) {
                case "psychic", "ghost" -> 2.0;
                case "fighting", "dark", "fairy" -> 0.5;
                default -> 1.0;
            };
            case "steel" -> switch (def) {
                case "ice", "rock", "fairy" -> 2.0;
                case "fire", "water", "electric", "steel" -> 0.5;
                default -> 1.0;
            };
            case "fairy" -> switch (def) {
                case "fighting", "dragon", "dark" -> 2.0;
                case "fire", "poison", "steel" -> 0.5;
                default -> 1.0;
            };
            default -> 1.0;
        };
    }

    /**
     * Determines whether the active Pokemon is in an unfavorable matchup that warrants switching out.
     */
    public static boolean shouldVoluntarySwitch(
        boolean faster,
        int userHp,
        int userMaxHp,
        int oppHp,
        int ourBestDmg,
        int oppBestDmg,
        int offensiveBoosts
    ) {
        // Rule 3: NEVER switch out if we can KO the opponent this turn
        if (ourBestDmg >= oppHp && (faster || oppBestDmg < userHp)) {
            return false;
        }

        // Rule 4: NEVER switch out if active mon already has positive offensive boosts and deals damage
        if (offensiveBoosts >= 1 && ourBestDmg > 0) {
            return false;
        }

        // Duel calculation:
        int turnsToKillOpp = ourBestDmg <= 0 ? 99 : (int) Math.ceil((double) oppHp / ourBestDmg);
        int turnsToDie = oppBestDmg <= 0 ? 99 : (int) Math.ceil((double) userHp / oppBestDmg);

        boolean winsDuel = faster ? (turnsToKillOpp <= turnsToDie) : (turnsToKillOpp < turnsToDie);

        // Disadvantaged criteria:
        // 1. Lethal threat: Opponent OHKOs and we can't faster OHKO
        boolean lethalThreat = oppBestDmg >= userHp && (!faster || ourBestDmg < oppHp);

        // 2. Mathematically losing the damage race:
        // Opponent kills us before we can kill opponent, and opponent deals meaningful damage
        boolean losingDamageRace = !winsDuel && (oppBestDmg >= userMaxHp * 0.25 || ourBestDmg < oppHp * 0.40);

        // 3. Offensive Stalemate / Stall Trap:
        // Active mon deals < 28% and opponent deals >= 18% (trapped in futile stall)
        boolean offensiveStalemate = ourBestDmg < oppHp * 0.28 && oppBestDmg >= userMaxHp * 0.18;

        // 4. Heavy Disadvantage Trade:
        double activeRatio = (double) ourBestDmg / oppHp;
        double opponentRatio = (double) oppBestDmg / userMaxHp;
        boolean outDamagedTrade = opponentRatio >= activeRatio * 1.35 && ourBestDmg < oppHp * 0.50;

        return lethalThreat || losingDamageRace || offensiveStalemate || outDamagedTrade;
    }

    /**
     * Checks if a bench Pokemon is viable as a switch-in against the current opponent.
     */
    public static boolean isSwitchCandidateViable(
        boolean isImmune,
        int incomingOnSwitch,
        int benchHp,
        int benchMaxHp,
        int benchBestOut,
        int oppHp,
        boolean benchFaster,
        boolean isMatchingWall,
        boolean hasRecoveryOrStatus
    ) {
        if (isImmune) {
            return benchBestOut >= oppHp * 0.30 || hasRecoveryOrStatus;
        }

        // Allow up to 35% for walls and 46% for offensive counters
        double maxAllowedIncomingFrac = isMatchingWall ? 0.35 : 0.46;
        if (incomingOnSwitch > benchHp * 0.48 || incomingOnSwitch > benchMaxHp * maxAllowedIncomingFrac) {
            return false;
        }

        boolean canOhko = benchBestOut >= oppHp;
        boolean can2Hko = benchBestOut * 2 >= oppHp;

        boolean winsOffensively = canOhko && (benchFaster || incomingOnSwitch * 2 < benchHp);
        boolean stabilizesOffensively = can2Hko && benchFaster && (incomingOnSwitch * 2 < benchHp);
        boolean slow2HkoSafe = can2Hko && !benchFaster && (incomingOnSwitch * 3 < benchHp);

        boolean stabilizesDefensively = isMatchingWall
            && incomingOnSwitch <= benchMaxHp * 0.30
            && (hasRecoveryOrStatus || benchBestOut >= oppHp * 0.20 || incomingOnSwitch * 4 < benchHp);

        return winsOffensively || stabilizesOffensively || slow2HkoSafe || stabilizesDefensively;
    }

    /**
     * Scores a bench switch-in candidate against the opponent.
     */
    public static double evaluateSwitchScore(
        boolean isImmune,
        int ourDmg,
        int oppDmg,
        int oppHp,
        int candHp,
        int candMaxHp,
        boolean faster,
        boolean isSpecialWall,
        boolean isPhysicalWall
    ) {
        double score = 0.0;

        if (isImmune) {
            score += 85.0;
        }

        if (ourDmg >= oppHp) {
            score += faster ? 120.0 : 90.0;
        } else if (ourDmg * 2 >= oppHp) {
            score += faster ? 75.0 : 50.0;
        } else {
            score += Math.min(1.5, (double) ourDmg / oppHp) * 45.0;
        }

        if (isSpecialWall) {
            score += 48.0;
        }
        if (isPhysicalWall) {
            score += 48.0;
        }

        if (oppDmg > 0 && oppDmg <= candMaxHp * 0.20) {
            score += 30.0;
        }

        double incomingFrac = Math.min(1.5, (double) oppDmg / candMaxHp);
        score -= incomingFrac * 55.0;

        score += ((double) candHp / candMaxHp) * 20.0;

        if (faster) {
            score += 15.0;
        }

        return score;
    }

    /**
     * Evaluates recovery move score, prioritizing attacks when healthy and preventing consecutive recovery stall loops.
     */
    public static double scoreRecoveryMove(
        double hpFrac,
        boolean faster,
        int userHp,
        int userMaxHp,
        int oppHp,
        int highestDamage,
        int oppBestDamage,
        boolean lastMoveWasRecovery
    ) {
        if (hpFrac >= 0.75) {
            return -60.0;
        }

        // If slower and opponent deals lethal damage, recovery is futile
        if (!faster && oppBestDamage >= userHp) {
            return -100.0;
        }

        // Consecutive recovery guard: never chain recovery moves if already at comfortable health
        if (lastMoveWasRecovery && hpFrac >= 0.45) {
            return -50.0;
        }

        // If we are faster and can 2HKO the opponent while safe from lethal, prefer attacking!
        if (faster && highestDamage * 2 >= oppHp && oppBestDamage < userHp) {
            return 40.0;
        }

        // Critical emergency recovery (hpFrac <= 0.38)
        if (hpFrac <= 0.38) {
            if (faster || oppBestDamage < userHp) {
                return 95.0;
            }
        }

        // Moderate HP recovery (0.38 < hpFrac <= 0.65)
        if (oppBestDamage < userMaxHp * 0.45 && hpFrac <= 0.60) {
            if (highestDamage >= oppHp * 0.40) {
                return 55.0; // Damaging move will take priority (~65-80)
            }
            return 68.0;
        }

        return -20.0;
    }

    /**
     * Evaluates pure defensive setup moves (Cosmic Power, Iron Defense, Amnesia, Acid Armor, Cotton Guard).
     * Prevents purposeless stall loops, blocks boosting wrong defensive stats, and caps setup turns.
     */
    public static double scorePureDefensiveSetup(
        boolean hasBodyPress,
        int usedCount,
        int currentDefStage,
        int currentSpDStage,
        int highestDamage,
        int oppHp,
        boolean oppIsSpecial,
        boolean oppIsPhysical,
        boolean moveBoostsDef,
        boolean moveBoostsSpD,
        int hpAfterSetup,
        int oppBestDamage
    ) {
        // Wrong defensive boost against opponent primary attack type
        if (moveBoostsDef && !moveBoostsSpD && oppIsSpecial) {
            return -500.0; // Boosting Def vs Special Attacker is completely useless
        }
        if (moveBoostsSpD && !moveBoostsDef && oppIsPhysical) {
            return -500.0; // Boosting SpD vs Physical Attacker is completely useless
        }

        // Without Body Press, never boost pure defense more than once!
        if (!hasBodyPress) {
            if (usedCount >= 1 || currentDefStage >= 1 || currentSpDStage >= 1) {
                return -500.0;
            }
            // If active user cannot threaten opponent, do NOT boost defense blindly
            if (highestDamage < oppHp * 0.35) {
                return 10.0;
            }
        } else {
            // With Body Press, allow up to +2 if healthy
            if (usedCount >= 2 || currentDefStage >= 3) {
                return -80.0;
            }
        }

        if (hpAfterSetup > oppBestDamage * 1.5) {
            return hasBodyPress ? 78.0 : 54.0;
        }

        return 20.0;
    }
}
