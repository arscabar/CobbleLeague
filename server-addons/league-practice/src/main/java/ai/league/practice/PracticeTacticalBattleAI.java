package ai.league.practice;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.pokemon.status.Status;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.api.types.tera.TeraType;
import com.cobblemon.mod.common.api.types.tera.elemental.ElementalTypeTeraType;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BagItemActionResponse;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.ForcePassActionResponse;
import com.cobblemon.mod.common.battles.InBattleGimmickMove;
import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.PassActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import com.cobblemon.mod.common.battles.Targetable;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.IVs;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.status.PersistentStatusContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

/**
 * Calibrated Run & Bun + 1-2 Turn Tactical Lookahead AI for League Practice NPCs.
 *
 * Key upgrades over Cobblemon's vanilla StrongBattleAI:
 * 1. Crash-proof handling of double-faint forceSwitch & empty opponent active slots (fixes battle freeze/stall).
 * 2. Never mutates {@code willBeSwitchedIn} during candidate evaluation; sets it strictly once on the chosen switch.
 * 3. Uses real Level-50 Pokemon stats (including IVs, EVs, Nature, stat stages, items, abilities, status) instead of
 *    vanilla's hardcoded {@code 2 * baseStat + 36}.
 * 4. Calibrated Run & Bun Terastallization / Mega Evolution / Z-Move decision engine: compares normal vs Tera offensive
 *    and defensive damage ratios so AI Terastallizes for KOs, survival flips, or setup sweeps without wasting Tera.
 * 5. Strict switch discipline: prevents reckless switching out of winning matchups or ping-pong switching into free hits.
 */
public final class PracticeTacticalBattleAI implements BattleAI {
    public static final PracticeTacticalBattleAI INSTANCE = new PracticeTacticalBattleAI();
    private static final Logger LOGGER = LoggerFactory.getLogger("league-practice-ai");

    public static String status() {
        return "Full-party Tactical AI (상대 전체 파티·실능력치·기술 분석 / 두 턴 기술·교체 비교)";
    }

    private static final Map<UUID, Integer> LAST_VOLUNTARY_SWITCH_TURN = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<UUID, Integer>> BOOST_TURNS_USED = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_MOVE_BY_POKEMON = new ConcurrentHashMap<>();

    private static final Set<String> PROTECT_MOVES = Set.of(
        "protect", "detect", "kingsshield", "spikyshield", "banefulbunker", "silktrap", "burningbulwark", "obstruct", "endure"
    );

    private static final Set<String> RECOVERY_MOVES = Set.of(
        "recover", "roost", "slackoff", "softboiled", "milkdrink", "shoreup", "synthesis", "morningSun", "morningsun",
        "moonlight", "wish", "rest", "strengthsap", "junglehealing", "lunarblessing", "lifedew"
    );

    private static final Set<String> PIVOT_MOVES = Set.of(
        "uturn", "voltswitch", "flipturn", "partingshot", "chillyreception", "teleport", "shedtail", "batonpass"
    );

    private static final Set<String> OHKO_MOVES = Set.of(
        "fissure", "sheercold", "guillotine", "horndrill"
    );

    private static final Set<String> MULTI_HIT_2_TO_5 = Set.of(
        "iciclespear", "rockblast", "bulletseed", "pinmissile", "tailslap", "scaleshot", "watershuriken", "bonerush", "armthrust"
    );

    private static final Set<String> ALWAYS_CRIT_MOVES = Set.of(
        "wickedblow", "surgingstrikes", "flowertrick", "frostbreath", "stormthrow", "zippyzap"
    );

    private static final Set<String> SOUND_MOVES = Set.of(
        "hypervoice", "boomburst", "bugbuzz", "TORCHSONG", "torchsong", "psychicnoise", "alluringvoice",
        "CLANGINGSCALES", "clangingscales", "overdrive", "snarl", "partingshot", "roar", "whirlwind", "perishsong"
    );

    private static final Set<String> PUNCH_MOVES = Set.of(
        "machpunch", "bulletpunch", "drainpunch", "icepunch", "firepunch", "thunderpunch", "closecombat",
        "focuspunch", "ragefist", "jetpunch", "surgingstrikes", "wickedblow", "plasmafists", "shadowpunch",
        "meteorMash", "meteormash", "hammerarm", "icehammer"
    );

    private static final Set<String> BITING_MOVES = Set.of(
        "crunch", "bite", "firefang", "icefang", "thunderfang", "psychicfangs", "poisonfang", "jawlock", "fishiousrend", "hyperfang"
    );

    private static final Set<String> SLICING_MOVES = Set.of(
        "kowtowcleave", "sacredsword", "ceaselessedge", "stoneaxe", "aquacutter", "psychocut", "nightslash",
        "slash", "xscissor", "leafblade", "airslash", "solarblade", "bitterblade", "behemothblade", "populationbomb"
    );

    private static final Set<String> RECOIL_MOVES = Set.of(
        "flareblitz", "bravebird", "doubleedge", "woodhammer", "wavecrash", "headsmash", "wildcharge", "headcharge", "volttackle", "chloroblast"
    );

    private static final Set<String> HAZARDS = Set.of(
        "stealthrock", "spikes", "toxicspikes", "stickyweb"
    );

    private static final Set<String> SCREENS = Set.of(
        "reflect", "lightscreen", "auroraveil"
    );

    private static final Map<String, Map<String, Integer>> SETUP_MOVE_BOOSTS = buildSetupMoveBoosts();

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
        map.put("cottonGuard", Map.of("def", 3));
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
        map.put("noRetreat", Map.of("atk", 1, "def", 1, "spa", 1, "spd", 1, "spe", 1));
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

    public static void handleChoiceRequested(AIBattleActor actor) {
        handleChoiceRequested(actor, INSTANCE);
    }

    public static void handleChoiceRequested(AIBattleActor actor, BattleAI ai) {
        PokemonBattle battle = actor.getBattle();
        var request = actor.getRequest();
        if (battle == null || request == null) {
            return;
        }
        try {
            List<ActiveBattlePokemon> activeList = actor.getActivePokemon();
            List<Boolean> forceSwitchFlags = request.getForceSwitch();
            List<ShowdownMoveset> activeMovesets = request.getActive();
            List<ShowdownActionResponse> responses = new ArrayList<>(activeList.size());

            for (int slotIndex = 0; slotIndex < activeList.size(); slotIndex++) {
                ActiveBattlePokemon activeBattlePokemon = activeList.get(slotIndex);
                boolean forceSwitch = slotIndex < forceSwitchFlags.size() && Boolean.TRUE.equals(forceSwitchFlags.get(slotIndex));
                ShowdownMoveset moveset = (activeMovesets != null && slotIndex < activeMovesets.size())
                    ? activeMovesets.get(slotIndex)
                    : null;

                ShowdownActionResponse response;
                if ( request.getWait() ) {
                    response = PassActionResponse.INSTANCE;
                } else {
                    response = ai.choose(activeBattlePokemon, battle, actor.getSide(), moveset, forceSwitch);
                }
                responses.add(validateOrFallbackResponse(actor, activeBattlePokemon, moveset, forceSwitch, response));
            }
            actor.setActionResponses(responses);
        } catch (Throwable t) {
            LOGGER.warn("[league-practice] Recovering from AI choice exception", t);
            actor.setActionResponses(buildEmergencyFallbackResponses(actor));
        }
    }

    private static List<ShowdownActionResponse> buildEmergencyFallbackResponses(AIBattleActor actor) {
        var request = actor.getRequest();
        if (request == null) {
            return List.of();
        }
        List<ActiveBattlePokemon> activeList = actor.getActivePokemon();
        List<Boolean> forceSwitchFlags = request.getForceSwitch();
        List<ShowdownMoveset> activeMovesets = request.getActive();
        List<ShowdownActionResponse> fallback = new ArrayList<>(activeList.size());

        for (int i = 0; i < activeList.size(); i++) {
            ActiveBattlePokemon active = activeList.get(i);
            boolean forceSwitch = i < forceSwitchFlags.size() && Boolean.TRUE.equals(forceSwitchFlags.get(i));
            ShowdownMoveset moveset = (activeMovesets != null && i < activeMovesets.size()) ? activeMovesets.get(i) : null;
            fallback.add(validateOrFallbackResponse(actor, active, moveset, forceSwitch, null));
        }
        return fallback;
    }

    private static ShowdownActionResponse validateOrFallbackResponse(
        AIBattleActor actor,
        ActiveBattlePokemon activeBattlePokemon,
        ShowdownMoveset moveset,
        boolean forceSwitch,
        ShowdownActionResponse candidate
    ) {
        if (candidate != null) {
            try {
                if (candidate.isValid(activeBattlePokemon, moveset, forceSwitch)) {
                    return candidate;
                }
            } catch (Throwable ignored) {
            }
        }

        if (forceSwitch || activeBattlePokemon.isGone() || activeBattlePokemon.getBattlePokemon() == null) {
            for (BattlePokemon bench : actor.getPokemonList()) {
                if (bench != null && bench.canBeSentOut()) {
                    bench.setWillBeSwitchedIn(true);
                    return new SwitchActionResponse(bench.getUuid());
                }
            }
            return PassActionResponse.INSTANCE;
        }

        if (moveset != null && moveset.getMoves() != null) {
            for (int i = 0; i < moveset.getMoves().size(); i++) {
                InBattleMove move = moveset.getMoves().get(i);
                if (move != null && move.canBeUsed()) {
                    String targetPnx = pickValidTargetPnx(activeBattlePokemon, move);
                    MoveActionResponse moveResp = new MoveActionResponse(move.getId(), targetPnx, null);
                    try {
                        if (moveResp.isValid(activeBattlePokemon, moveset, false)) {
                            return moveResp;
                        }
                    } catch (Throwable ignored) {
                        return moveResp;
                    }
                }
            }
        }

        for (BattlePokemon bench : actor.getPokemonList()) {
            if (bench != null && bench.canBeSentOut()) {
                bench.setWillBeSwitchedIn(true);
                return new SwitchActionResponse(bench.getUuid());
            }
        }
        return PassActionResponse.INSTANCE;
    }

    @Override
    public ShowdownActionResponse choose(
        ActiveBattlePokemon activeBattlePokemon,
        PokemonBattle battle,
        BattleSide aiSide,
        ShowdownMoveset moveset,
        boolean forceSwitch
    ) {
        try {
            return chooseInternal(activeBattlePokemon, battle, aiSide, moveset, forceSwitch);
        } catch (Throwable t) {
            LOGGER.warn("[league-practice] Tactical AI fallback triggered", t);
            return fallbackChoice(activeBattlePokemon, moveset, forceSwitch);
        }
    }

    private ShowdownActionResponse chooseInternal(
        ActiveBattlePokemon activeBattlePokemon,
        PokemonBattle battle,
        BattleSide aiSide,
        ShowdownMoveset moveset,
        boolean forceSwitch
    ) {
        List<BattlePokemon> party = toList(activeBattlePokemon.getActor().getPokemonList());
        List<BattlePokemon> canSwitchIn = party.stream()
            .filter(Objects::nonNull)
            .filter(BattlePokemon::canBeSentOut)
            .toList();

        // 1. Handle forceSwitch or fainted active slot FIRST (prevents double-faint crash!)
        if (forceSwitch || activeBattlePokemon.isGone() || activeBattlePokemon.getBattlePokemon() == null) {
            if (canSwitchIn.isEmpty()) {
                return PassActionResponse.INSTANCE;
            }
            BattlePokemon opponent = findPrimaryOpponentPokemon(activeBattlePokemon, aiSide);
            BattlePokemon bestReplacement;
            try { bestReplacement=PracticePartyPlanner.replacement(activeBattlePokemon,aiSide,battle); }
            catch(RuntimeException error){bestReplacement=null;LOGGER.warn("Full-party replacement recovery",error);}
            if(bestReplacement==null)bestReplacement = chooseBestSwitchIn(canSwitchIn, opponent, battle);
            bestReplacement.setWillBeSwitchedIn(true);
            return new SwitchActionResponse(bestReplacement.getUuid());
        }

        if (moveset == null) {
            return PassActionResponse.INSTANCE;
        }
        if (moveset.getMoves().size() == 1 && "recharge".equals(moveset.getMoves().get(0).getId())) {
            return new MoveActionResponse("recharge", null, null);
        }

        BattlePokemon user = activeBattlePokemon.getBattlePokemon();
        BattlePokemon opponent = findPrimaryOpponentPokemon(activeBattlePokemon, aiSide);

        List<InBattleMove> usableMoves = moveset.getMoves().stream()
            .filter(Objects::nonNull)
            .filter(InBattleMove::canBeUsed)
            .toList();

        if (usableMoves.isEmpty()) {
            InBattleMove first = moveset.getMoves().isEmpty() ? null : moveset.getMoves().get(0);
            if (first != null) {
                return new MoveActionResponse(first.getId(), null, null);
            }
            return PassActionResponse.INSTANCE;
        }

        if (opponent == null) {
            InBattleMove move = usableMoves.get(0);
            return new MoveActionResponse(move.getId(), pickValidTargetPnx(activeBattlePokemon, move), null);
        }

        // 2. Evaluate all available moves under normal state and under available gimmicks (Tera / Mega / Z-Move)
        String availableGimmick = selectAvailableGimmickType(moveset);
        MoveDecision decision = evaluateBestMoveAndGimmick(
            activeBattlePokemon,
            user,
            opponent,
            usableMoves,
            moveset,
            availableGimmick,
            canSwitchIn,
            battle
        );

        PracticePartyPlanner.Plan plan = null;
        try {
            plan = PracticePartyPlanner.choose(activeBattlePokemon, aiSide, moveset,
                    decision.move == null ? "" : normalizeId(decision.move.getId()), decision.score, decision.useTera, battle);
        } catch (RuntimeException error) {
            LOGGER.warn("[league-practice] Full-party planner fallback in {}", battle.getBattleId(), error);
        }
        if (plan != null && plan.switchTo() != null) {
            for (var target : canSwitchIn) if (target.getUuid().equals(plan.switchTo())) {
                target.setWillBeSwitchedIn(true);
                return new SwitchActionResponse(target.getUuid());
            }
        }
        if (plan != null && plan.switchTo() == null &&
                (decision.gimmickId == null || "mega".equals(decision.gimmickId) || "terastal".equals(decision.gimmickId))) {
            for (var move : usableMoves) if (normalizeId(move.getId()).equals(plan.move())) {
                decision = new MoveDecision(move, plan.score(), decision.gimmickId, decision.useTera);
                break;
            }
        }
        // Existing selector is recovery only; it cannot veto a valid full-party stay decision.
        if (plan == null && !Boolean.TRUE.equals(moveset.getTrapped()) && !canSwitchIn.isEmpty()) {
            BattlePokemon switchTarget = considerVoluntarySwitch(
                user,
                opponent,
                usableMoves,
                decision,
                canSwitchIn,
                battle
            );
            if (switchTarget != null) {
                if (battle != null) {
                    LAST_VOLUNTARY_SWITCH_TURN.put(battle.getBattleId(), battle.getTurn());
                }
                switchTarget.setWillBeSwitchedIn(true);
                return new SwitchActionResponse(switchTarget.getUuid());
            }
        }

        InBattleMove chosenMove = decision.move != null ? decision.move : usableMoves.get(0);
        String chosenMoveId = normalizeId(chosenMove.getId());
        LAST_MOVE_BY_POKEMON.put(user.getUuid(), chosenMoveId);
        if (SETUP_MOVE_BOOSTS.containsKey(chosenMoveId) && battle != null) {
            BOOST_TURNS_USED
                .computeIfAbsent(battle.getBattleId(), k -> new ConcurrentHashMap<>())
                .merge(user.getUuid(), 1, Integer::sum);
        }

        String targetPnx = pickBestTargetPnx(activeBattlePokemon, chosenMove, user, aiSide, decision.useTera);
        return new MoveActionResponse(chosenMove.getId(), targetPnx, decision.gimmickId);
    }

    private ShowdownActionResponse fallbackChoice(
        ActiveBattlePokemon activeBattlePokemon,
        ShowdownMoveset moveset,
        boolean forceSwitch
    ) {
        if (forceSwitch || activeBattlePokemon.isGone() || activeBattlePokemon.getBattlePokemon() == null) {
            for (BattlePokemon bench : activeBattlePokemon.getActor().getPokemonList()) {
                if (bench != null && bench.canBeSentOut()) {
                    bench.setWillBeSwitchedIn(true);
                    return new SwitchActionResponse(bench.getUuid());
                }
            }
            return PassActionResponse.INSTANCE;
        }
        if (moveset != null && moveset.getMoves() != null) {
            for (InBattleMove move : moveset.getMoves()) {
                if (move != null && move.canBeUsed()) {
                    return new MoveActionResponse(move.getId(), pickValidTargetPnx(activeBattlePokemon, move), null);
                }
            }
        }
        return PassActionResponse.INSTANCE;
    }

    private static BattlePokemon findPrimaryOpponentPokemon(ActiveBattlePokemon activeBattlePokemon, BattleSide aiSide) {
        for (ActiveBattlePokemon oppSlot : activeBattlePokemon.getActor().getBattle().getSide2().equals(aiSide)
            ? aiSide.getOppositeSide().getActivePokemon()
            : aiSide.getOppositeSide().getActivePokemon()) {
            if (oppSlot != null && oppSlot.getBattlePokemon() != null && !oppSlot.isGone() && oppSlot.getBattlePokemon().getHealth() > 0) {
                return oppSlot.getBattlePokemon();
            }
        }
        // If all opponent active slots fainted simultaneously (double KO), evaluate against remaining alive opponent bench Pokemon
        for (var oppActor : aiSide.getOppositeSide().getActors()) {
            for (BattlePokemon oppBench : oppActor.getPokemonList()) {
                if (oppBench != null && oppBench.getHealth() > 0 && !oppBench.getGone()) {
                    return oppBench;
                }
            }
        }
        return null;
    }

    private static String selectAvailableGimmickType(ShowdownMoveset moveset) {
        List<ShowdownMoveset.Gimmick> gimmicks = moveset.getGimmicks();
        if (gimmicks == null || gimmicks.isEmpty()) {
            return null;
        }
        for (ShowdownMoveset.Gimmick g : gimmicks) {
            if (g == ShowdownMoveset.Gimmick.MEGA_EVOLUTION) {
                return g.getId();
            }
        }
        for (ShowdownMoveset.Gimmick g : gimmicks) {
            if (g == ShowdownMoveset.Gimmick.TERASTALLIZATION) {
                return g.getId();
            }
        }
        for (ShowdownMoveset.Gimmick g : gimmicks) {
            if (g == ShowdownMoveset.Gimmick.Z_POWER || g == ShowdownMoveset.Gimmick.ULTRA_BURST || g == ShowdownMoveset.Gimmick.DYNAMAX) {
                return g.getId();
            }
        }
        return null;
    }

    private MoveDecision evaluateBestMoveAndGimmick(
        ActiveBattlePokemon activeSlot,
        BattlePokemon user,
        BattlePokemon opponent,
        List<InBattleMove> usableMoves,
        ShowdownMoveset moveset,
        String availableGimmick,
        List<BattlePokemon> canSwitchIn,
        PokemonBattle battle
    ) {
        boolean canMega = ShowdownMoveset.Gimmick.MEGA_EVOLUTION.getId().equals(availableGimmick);
        boolean canTera = ShowdownMoveset.Gimmick.TERASTALLIZATION.getId().equals(availableGimmick);
        boolean canZPower = ShowdownMoveset.Gimmick.Z_POWER.getId().equals(availableGimmick);

        // Score all moves without Tera
        ScoredMove bestNormal = scoreAllMoves(activeSlot, user, opponent, usableMoves, false, canSwitchIn, battle);

        // Mega Evolution is almost always strictly beneficial on the turn a Mega stone user acts
        if (canMega) {
            return new MoveDecision(bestNormal.move, bestNormal.score + 35.0, ShowdownMoveset.Gimmick.MEGA_EVOLUTION.getId(), false);
        }

        // Z-Power evaluation: check if Z-move secures a KO or huge damage spike
        if (canZPower && bestNormal.move != null && bestNormal.move.getGimmickMove() != null && !bestNormal.move.getGimmickMove().getDisabled()) {
            int oppHp = Math.max(1, opponent.getHealth());
            int normalDmg = estimateMoveDamage(user, opponent, bestNormal.move.getId(), false, false, battle);
            int zDmg = (int) Math.round(normalDmg * 1.65);
            if (normalDmg < oppHp && zDmg >= oppHp) {
                return new MoveDecision(bestNormal.move, bestNormal.score + 55.0, ShowdownMoveset.Gimmick.Z_POWER.getId(), false);
            }
        }

        // Run & Bun + Calibrated Terastallization Evaluation
        if (canTera) {
            ScoredMove bestTera = scoreAllMoves(activeSlot, user, opponent, usableMoves, true, canSwitchIn, battle);
            if (shouldTerastallize(user, opponent, usableMoves, bestNormal, bestTera, canSwitchIn, battle)) {
                return new MoveDecision(bestTera.move, bestTera.score + 30.0, ShowdownMoveset.Gimmick.TERASTALLIZATION.getId(), true);
            }
        }

        return new MoveDecision(bestNormal.move, bestNormal.score, null, false);
    }

    /**
     * Calibrated Run & Bun Terastallization logic:
     * Compares offensive & defensive damage ratios before and after Terastallization:
     * 1. Offensive KO Flip: Normal attack fails to KO (or 2HKO), while Tera attack secures the OHKO (or faster 2HKO while surviving).
     * 2. Defensive Survival Flip: Opponent would OHKO (or 2HKO before we KO) in normal state, but Tera flips typing so we survive and KO/set up.
     * 3. Sweeper / Boosted Ace Conservation & Breakthrough: If user has stat boosts or is the last/strongest win-con, Tera to sweep or survive.
     * 4. Anti-Waste Guard: Never waste Tera if normal move already cleanly OHKOs and we outspeed (or take <35% damage), or if we get outsped and OHKO'd even after Tera.
     */
    private boolean shouldTerastallize(
        BattlePokemon user,
        BattlePokemon opponent,
        List<InBattleMove> usableMoves,
        ScoredMove bestNormal,
        ScoredMove bestTera,
        List<BattlePokemon> canSwitchIn,
        PokemonBattle battle
    ) {
        ElementalType teraType = resolveTeraElementalType(user);
        if (teraType == null) {
            return false;
        }

        int userHp = Math.max(1, user.getHealth());
        int userMaxHp = Math.max(1, user.getMaxHealth());
        int oppHp = Math.max(1, opponent.getHealth());

        int oppBestNormalDmg = estimateOpponentBestDamage(opponent, user, false, battle);
        int oppBestTeraDmg = estimateOpponentBestDamage(opponent, user, true, battle);

        int ourBestNormalDmg = bestNormal.damage;
        int ourBestTeraDmg = bestTera.damage;

        boolean weOutspeed = isFasterForMove(user, opponent, bestTera.move != null ? bestTera.move.getId() : "", battle);

        // Guard 1: If we get outsped and OHKO'd even after Tera (and our chosen move isn't priority KO), don't waste Tera!
        boolean teraMoveIsPriorityKo = bestTera.move != null && getMovePriority(user, bestTera.move.getId()) > 0 && ourBestTeraDmg >= oppHp;
        if (!weOutspeed && !teraMoveIsPriorityKo && oppBestTeraDmg >= userHp) {
            return false;
        }

        // Guard 2: If normal move already cleanly OHKOs and either we outspeed or we easily survive opponent's hit, save Tera!
        boolean normalMoveIsPriority = bestNormal.move != null && getMovePriority(user, bestNormal.move.getId()) > 0;
        if (ourBestNormalDmg >= oppHp && (weOutspeed || normalMoveIsPriority || oppBestNormalDmg < userHp * 0.65)) {
            return false;
        }

        // Case 1 (Run & Bun Offensive KO Flip): Tera turns a non-KO into an immediate OHKO!
        if (ourBestNormalDmg < oppHp && ourBestTeraDmg >= oppHp) {
            return true;
        }

        // Case 2 (Run & Bun Defensive Survival Flip): Opponent OHKOs us normally, but Tera lets us survive and counter-KO or set up!
        if (oppBestNormalDmg >= userHp && oppBestTeraDmg < userHp) {
            if (ourBestTeraDmg >= oppHp || (ourBestTeraDmg * 2 >= oppHp && oppBestTeraDmg * 2 < userHp)) {
                return true;
            }
            if (bestTera.move != null && SETUP_MOVE_BOOSTS.containsKey(normalizeId(bestTera.move.getId()))) {
                return true;
            }
        }

        // Case 3 (Run & Bun 1v1 Duel Flip): Tera flips a losing 2HKO race into a winning 2HKO race
        int normalTurnsToKillOpp = ourBestNormalDmg <= 0 ? 99 : (int) Math.ceil((double) oppHp / ourBestNormalDmg);
        int teraTurnsToKillOpp = ourBestTeraDmg <= 0 ? 99 : (int) Math.ceil((double) oppHp / ourBestTeraDmg);
        int normalTurnsToDie = oppBestNormalDmg <= 0 ? 99 : (int) Math.ceil((double) userHp / oppBestNormalDmg);
        int teraTurnsToDie = oppBestTeraDmg <= 0 ? 99 : (int) Math.ceil((double) userHp / oppBestTeraDmg);

        boolean winsNormal1v1 = weOutspeed ? (normalTurnsToKillOpp <= normalTurnsToDie) : (normalTurnsToKillOpp < normalTurnsToDie);
        boolean winsTera1v1 = weOutspeed ? (teraTurnsToKillOpp <= teraTurnsToDie) : (teraTurnsToKillOpp < teraTurnsToDie);
        if (!winsNormal1v1 && winsTera1v1 && teraTurnsToKillOpp <= 3) {
            return true;
        }

        // Case 4 (Boosted Sweeper / Last-Pokemon Ace): If user already has offensive boosts or is the final healthy Pokemon,
        // Terastallize when it meaningfully boosts STAB output (>= 25% damage increase) or halves incoming damage.
        int offensiveBoosts = Math.max(getStage(user, Stats.ATTACK), getStage(user, Stats.SPECIAL_ATTACK));
        boolean isLastPokemon = canSwitchIn.isEmpty();
        if ((offensiveBoosts >= 1 || isLastPokemon) && userHp >= userMaxHp * 0.45) {
            if (ourBestTeraDmg >= ourBestNormalDmg * 1.25 && ourBestTeraDmg >= oppHp * 0.45) {
                return true;
            }
            if (oppBestTeraDmg <= oppBestNormalDmg * 0.6 && oppBestNormalDmg >= userHp * 0.45) {
                return true;
            }
        }

        return false;
    }

    private ScoredMove scoreAllMoves(
        ActiveBattlePokemon activeSlot,
        BattlePokemon user,
        BattlePokemon opponent,
        List<InBattleMove> usableMoves,
        boolean userTerastallized,
        List<BattlePokemon> canSwitchIn,
        PokemonBattle battle
    ) {
        int oppHp = Math.max(1, opponent.getHealth());
        int userHp = Math.max(1, user.getHealth());
        int userMaxHp = Math.max(1, user.getMaxHealth());
        int oppBestDamage = estimateOpponentBestDamage(opponent, user, userTerastallized, battle);

        // Precompute highest max damage among our moves
        int highestDamage = 0;
        Map<String, Integer> moveDamages = new HashMap<>();
        for (InBattleMove move : usableMoves) {
            int dmg = estimateMoveDamage(user, opponent, move.getId(), userTerastallized, false, battle);
            moveDamages.put(move.getId(), dmg);
            if (dmg > highestDamage) {
                highestDamage = dmg;
            }
        }

        InBattleMove bestMove = usableMoves.get(0);
        double bestScore = Double.NEGATIVE_INFINITY;
        int bestMoveDamage = 0;

        for (InBattleMove move : usableMoves) {
            String moveId = normalizeId(move.getId());
            int dmg = moveDamages.getOrDefault(move.getId(), 0);
            double score = scoreSingleMove(
                activeSlot,
                user,
                opponent,
                move,
                moveId,
                dmg,
                highestDamage,
                oppBestDamage,
                userHp,
                userMaxHp,
                oppHp,
                userTerastallized,
                canSwitchIn,
                battle
            );
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
                bestMoveDamage = dmg;
            }
        }

        return new ScoredMove(bestMove, bestScore, bestMoveDamage);
    }

    private double scoreSingleMove(
        ActiveBattlePokemon activeSlot,
        BattlePokemon user,
        BattlePokemon opponent,
        InBattleMove move,
        String moveId,
        int damage,
        int highestDamage,
        int oppBestDamage,
        int userHp,
        int userMaxHp,
        int oppHp,
        boolean userTerastallized,
        List<BattlePokemon> canSwitchIn,
        PokemonBattle battle
    ) {
        MoveTemplate template = Moves.INSTANCE.getByName(moveId);
        if (template == null) {
            return -50.0;
        }

        boolean isStatus = template.getDamageCategory().equals(DamageCategories.INSTANCE.getSTATUS());
        int priority = getMovePriority(user, moveId);
        boolean faster = isFasterForMove(user, opponent, moveId, battle);
        double accuracy = template.getAccuracy() <= 0 ? 1.0 : Math.min(1.0, template.getAccuracy() / 100.0);

        // 1. Special handling for OHKO moves (Sheer Cold, Fissure, Guillotine, Horn Drill)
        if (OHKO_MOVES.contains(moveId)) {
            if (!canOhkoMoveHit(user, opponent, moveId, template)) {
                return -1000.0; // Never use Fissure on Flying/Levitate or Sheer Cold on Ice!
            }
            // If we have a reliable attack that 2HKOs, prefer standard attacks; otherwise OHKO move is a high-upside breaker
            if (highestDamage >= oppHp * 0.55) {
                return 18.0;
            }
            return 68.0;
        }

        // 2. Damaging Moves Evaluation (Run & Bun + 1-2 Turn Lookahead)
        if (!isStatus) {
            if (damage <= 0) {
                return -1000.0; // Immune (0x)
            }

            double score = 0.0;
            boolean killsOpponent = damage >= oppHp;
            boolean opponentKillsUs = oppBestDamage >= userHp;

            if (killsOpponent) {
                if (priority > 0) {
                    // Priority KO is the safest possible play in Pokemon
                    score += 180.0 + priority * 10.0;
                } else if (faster) {
                    // Faster OHKO
                    score += 155.0;
                } else if (!opponentKillsUs) {
                    // Slower OHKO while surviving opponent's attack
                    score += 125.0;
                } else {
                    // We are slower and may faint first, still try to hit or prefer priority
                    score += 65.0;
                }
                // Prefer higher accuracy and non-recoil/non-self-drop moves when multiple moves KO
                score += accuracy * 20.0;
                if (RECOIL_MOVES.contains(moveId)) {
                    score -= 12.0;
                }
                if ("closecombat".equals(moveId) || "superpower".equals(moveId) || "dracometeor".equals(moveId)
                    || "overheat".equals(moveId) || "leafstorm".equals(moveId) || "makeitrain".equals(moveId)
                    || "fleurcannon".equals(moveId)) {
                    score -= 8.0;
                }
            } else {
                // Non-KO damaging move: base score on percentage of opponent HP dealt * accuracy
                double hpRatio = Math.min(1.0, (double) damage / oppHp);
                score += hpRatio * 85.0 * (0.35 + 0.65 * accuracy);

                if (damage >= highestDamage && highestDamage > 0) {
                    score += 18.0; // Run & Bun highest-damage bonus
                }

                // Guaranteed faster 2HKO bonus: if we outspeed and 2HKO while surviving, strongly prefer attacking!
                if (faster && damage * 2 >= oppHp && oppBestDamage < userHp) {
                    score += 35.0;
                }

                // If opponent KOs us before we can move, priority chip is critical!
                if (opponentKillsUs && !faster && priority > 0) {
                    score += 95.0 + hpRatio * 40.0;
                }

                // Move secondary / tactical utility bonuses
                if ("fakeout".equals(moveId)) {
                    score += 110.0;
                } else if ("firstimpression".equals(moveId)) {
                    score += 45.0;
                } else if ("knockoff".equals(moveId) && hasRemovableItem(opponent)) {
                    score += 22.0;
                } else if ("rapidspin".equals(moveId) || "mortalspin".equals(moveId)) {
                    score += 16.0;
                } else if ("nuzzle".equals(moveId) && opponent.getEffectedPokemon().getStatus() == null) {
                    score += 28.0;
                } else if ("saltcure".equals(moveId)) {
                    score += 26.0;
                } else if (SETUP_MOVE_BOOSTS.containsKey(moveId)) {
                    // Damaging boost moves (Torch Song, Aqua Step, Trailblaze, Flame Charge, Power-Up Punch)
                    score += 20.0;
                } else if ("drainpunch".equals(moveId) || "gigadrain".equals(moveId) || "hornleech".equals(moveId)
                    || "bitterblade".equals(moveId) || "matchagotcha".equals(moveId) || "drainingkiss".equals(moveId)
                    || "paraboliccharge".equals(moveId) || "oblivionwing".equals(moveId)) {
                    if (userHp < userMaxHp * 0.75) {
                        score += 18.0;
                    }
                } else if (PIVOT_MOVES.contains(moveId) && !canSwitchIn.isEmpty()) {
                    // Pivot move (U-turn, Volt Switch, Flip Turn): great when opponent threatens us or resists our main moves
                    if (oppBestDamage >= userHp * 0.55 || hpRatio < 0.35) {
                        score += 28.0;
                    } else {
                        score += 10.0;
                    }
                }
            }
            return score;
        }

        // 3. Status Moves Evaluation (Run & Bun lookahead: setup, recovery, status, hazards, screens, trick room)
        String oppAbility = getAbilityId(opponent);
        if ("goodasgold".equals(oppAbility) || "magicbounce".equals(oppAbility)) {
            if (targetsOpponent(move)) {
                return -500.0;
            }
        }
        if (hasAbility(user, "prankster") && targetsOpponent(move) && hasElementalType(opponent, ElementalTypes.INSTANCE.getDARK())) {
            return -500.0; // Dark-types are immune to Prankster status moves targeting them
        }

        String lastMove = LAST_MOVE_BY_POKEMON.get(user.getUuid());

        // 3a. Protect / King's Shield / Spiky Shield / Baneful Bunker
        if (PROTECT_MOVES.contains(moveId)) {
            if (lastMove != null && PROTECT_MOVES.contains(normalizeId(lastMove))) {
                return -200.0; // Never double-Protect
            }
            String userItem = getHeldItemId(user);
            String userAbility = getAbilityId(user);
            boolean passiveBenefit = "leftovers".equals(userItem) || "black_sludge".equals(userItem)
                || "speedboost".equals(userAbility) || "poisonheal".equals(userAbility)
                || isOpponentBadlyDamagedPassively(opponent);
            if (passiveBenefit && userHp < userMaxHp * 0.85) {
                return 62.0;
            }
            return 12.0;
        }

        // 3b. Recovery Moves (Recover, Roost, Slack Off, Soft-Boiled, Rest, Strength Sap, etc.)
        if (RECOVERY_MOVES.contains(moveId)) {
            double hpFrac = (double) userHp / userMaxHp;
            if ("rest".equals(moveId) && user.getEffectedPokemon().getStatus() != null) {
                return -80.0;
            }
            boolean lastMoveWasRecovery = lastMove != null && RECOVERY_MOVES.contains(normalizeId(lastMove));
            return PracticeBattleMath.scoreRecoveryMove(
                hpFrac,
                faster,
                userHp,
                userMaxHp,
                oppHp,
                highestDamage,
                oppBestDamage,
                lastMoveWasRecovery
            );
        }

        // 3c. Setup Moves (Swords Dance, Dragon Dance, Nasty Plot, Quiver Dance, Calm Mind, Bulk Up, Shell Smash, etc.)
        if (SETUP_MOVE_BOOSTS.containsKey(moveId)) {
            int usedCount = battle != null
                ? BOOST_TURNS_USED.getOrDefault(battle.getBattleId(), Map.of()).getOrDefault(user.getUuid(), 0)
                : 0;

            Map<String, Integer> boosts = SETUP_MOVE_BOOSTS.get(moveId);
            int defBoost = boosts.getOrDefault("def", 0);
            int spdBoost = boosts.getOrDefault("spd", 0);
            int speBoost = boosts.getOrDefault("spe", 0);
            int atkBoost = boosts.getOrDefault("atk", 0);
            int spaBoost = boosts.getOrDefault("spa", 0);

            boolean isPureSpeed = speBoost > 0 && atkBoost == 0 && spaBoost == 0 && defBoost == 0 && spdBoost == 0;
            boolean isPureDefensive = (defBoost > 0 || spdBoost > 0) && atkBoost == 0 && spaBoost == 0 && speBoost == 0;

            // Pure Speed Setup Rules (Agility, Rock Polish, Autotomize, Tailwind):
            if (isPureSpeed) {
                // If already faster than opponent, never use speed setup!
                if (faster) {
                    return -500.0;
                }
                // Never boost pure speed more than once (+2 is already enough, +4 is completely useless)!
                if (getStage(user, Stats.SPEED) >= 1 || usedCount >= 1) {
                    return -500.0;
                }
                // If opponent 2HKOs us, wasting a turn on Agility means we faint after 1 attack instead of attacking immediately!
                if (oppBestDamage >= userHp * 0.45) {
                    return -500.0;
                }
                // If we can already OHKO right now, attack!
                if (highestDamage >= oppHp) {
                    return 10.0;
                }
                boolean fasterAfterBoost = getEffectiveSpeedWithExtraStage(user, speBoost, battle) >= getEffectiveSpeedWithExtraStage(opponent, 0, battle);
                if (fasterAfterBoost && userHp >= userMaxHp * 0.70 && userHp - oppBestDamage > oppBestDamage * 1.5) {
                    return 74.0;
                }
                return -200.0;
            }

            if (usedCount >= 2) {
                return -80.0; // Don't spam setup infinitely
            }
            if (usedCount >= 1 && (userHp < userMaxHp * 0.75 || oppBestDamage >= userMaxHp * 0.22)) {
                return -150.0; // Don't get greedy with 2nd boost if taking damage
            }

            if ("bellydrum".equals(moveId) && userHp <= userMaxHp * 0.55) {
                return -500.0;
            }
            if ("filletaway".equals(moveId) && userHp <= userMaxHp * 0.55) {
                return -500.0;
            }
            if ("clangoroussoul".equals(moveId) && userHp <= userMaxHp * 0.35) {
                return -500.0;
            }

            for (Map.Entry<String, Integer> entry : boosts.entrySet()) {
                Stat stat = resolveStat(entry.getKey());
                if (stat != null && getStage(user, stat) >= 3) {
                    return -60.0; // Already boosted enough
                }
            }

            // If we can already OHKO the opponent right now, attack instead of setting up!
            if (highestDamage >= oppHp) {
                return 10.0;
            }

            double postBoostDefFactor = (defBoost > 0 || spdBoost > 0) ? 1.0 / (1.0 + 0.5 * Math.max(defBoost, spdBoost)) : 1.0;
            int dmgTakenOnSetupTurn = faster ? (int) Math.round(oppBestDamage * postBoostDefFactor) : oppBestDamage;
            int hpAfterSetup = userHp - dmgTakenOnSetupTurn;
            if ("bellydrum".equals(moveId) || "filletaway".equals(moveId)) {
                hpAfterSetup -= userMaxHp / 2;
            }

            if (hpAfterSetup <= userMaxHp * 0.18 || oppBestDamage >= userHp * 0.50) {
                return -150.0; // Too risky to set up under lethal/2HKO pressure
            }

            if (isPureDefensive) {
                boolean hasBodyPress = false;
                Pokemon effUser = user.getEffectedPokemon();
                if (effUser.getMoveSet() != null) {
                    for (var m : effUser.getMoveSet().getMoves()) {
                        if (m != null && "bodypress".equals(normalizeId(m.getName()))) {
                            hasBodyPress = true;
                            break;
                        }
                    }
                }
                Pokemon effOpp = opponent.getEffectedPokemon();
                int oppAtk = effOpp.getAttack();
                int oppSpA = effOpp.getSpecialAttack();
                boolean oppIsSpecial = oppSpA > oppAtk * 1.25;
                boolean oppIsPhysical = oppAtk > oppSpA * 1.25;

                return PracticeBattleMath.scorePureDefensiveSetup(
                    hasBodyPress,
                    usedCount,
                    getStage(user, Stats.DEFENCE),
                    getStage(user, Stats.SPECIAL_DEFENCE),
                    highestDamage,
                    oppHp,
                    oppIsSpecial,
                    oppIsPhysical,
                    defBoost > 0,
                    spdBoost > 0,
                    hpAfterSetup,
                    oppBestDamage
                );
            }

            // Run & Bun fasterAndOHKOAfterBoost check:
            boolean fasterAfterBoost = getEffectiveSpeedWithExtraStage(user, speBoost, battle) >= getEffectiveSpeedWithExtraStage(opponent, 0, battle);
            double offensiveMultiplier = 1.0 + 0.5 * Math.max(atkBoost, spaBoost);
            int boostedBestDamage = (int) Math.round(highestDamage * offensiveMultiplier);

            if (fasterAfterBoost && boostedBestDamage >= oppHp) {
                return 112.0; // Setup turns a non-KO into a faster OHKO sweep!
            }
            if (hpAfterSetup > oppBestDamage * postBoostDefFactor && (atkBoost > 0 || spaBoost > 0) && boostedBestDamage >= oppHp * 0.65) {
                return 88.0; // Safe offensive setup opportunity against passive foe
            }
            return 20.0;
        }

        // 3d. Status-Inflicting Moves (Spore, Sleep Powder, Yawn, Will-O-Wisp, Thunder Wave, Glare, Toxic)
        if ("spore".equals(moveId) || "sleeppowder".equals(moveId) || "lovelykiss".equals(moveId)
            || "darkvoid".equals(moveId) || "hypnosis".equals(moveId) || "yawn".equals(moveId)) {
            if (opponent.getEffectedPokemon().getStatus() != null) {
                return -200.0;
            }
            if (("spore".equals(moveId) || "sleeppowder".equals(moveId)) && hasElementalType(opponent, ElementalTypes.INSTANCE.getGRASS())) {
                return -500.0;
            }
            if ("insomnia".equals(oppAbility) || "vitalspirit".equals(oppAbility) || "comatose".equals(oppAbility)
                || "purifyingsalt".equals(oppAbility) || "goodasgold".equals(oppAbility) || "sweetveil".equals(oppAbility)) {
                return -500.0;
            }
            if (highestDamage < oppHp && (faster || oppBestDamage < userHp)) {
                return 96.0 * accuracy;
            }
            return 20.0;
        }

        if ("willowisp".equals(moveId)) {
            if (opponent.getEffectedPokemon().getStatus() != null
                || hasElementalType(opponent, ElementalTypes.INSTANCE.getFIRE())
                || "flashfire".equals(oppAbility) || "thermalexchange".equals(oppAbility)
                || "waterbubble".equals(oppAbility) || "purifyingsalt".equals(oppAbility) || "guts".equals(oppAbility)) {
                return -500.0;
            }
            boolean opponentIsPhysical = opponent.getEffectedPokemon().getAttack() >= opponent.getEffectedPokemon().getSpecialAttack();
            if (opponentIsPhysical && highestDamage < oppHp && oppBestDamage < userHp) {
                return 86.0 * accuracy;
            }
            return 42.0 * accuracy;
        }

        if ("thunderwave".equals(moveId) || "glare".equals(moveId) || "stunspore".equals(moveId)) {
            if (opponent.getEffectedPokemon().getStatus() != null
                || hasElementalType(opponent, ElementalTypes.INSTANCE.getELECTRIC())
                || ("thunderwave".equals(moveId) && hasElementalType(opponent, ElementalTypes.INSTANCE.getGROUND()))
                || ("stunspore".equals(moveId) && hasElementalType(opponent, ElementalTypes.INSTANCE.getGRASS()))
                || "limber".equals(oppAbility) || "purifyingsalt".equals(oppAbility)) {
                return -500.0;
            }
            if (!faster && oppBestDamage < userHp) {
                return 85.0 * accuracy;
            }
            return 45.0 * accuracy;
        }

        if ("toxic".equals(moveId) || "poisonpowder".equals(moveId)) {
            if (opponent.getEffectedPokemon().getStatus() != null
                || hasElementalType(opponent, ElementalTypes.INSTANCE.getPOISON())
                || hasElementalType(opponent, ElementalTypes.INSTANCE.getSTEEL())
                || "immunity".equals(oppAbility) || "poisonheal".equals(oppAbility) || "purifyingsalt".equals(oppAbility)) {
                return -500.0;
            }
            if (highestDamage < oppHp * 0.55 && oppBestDamage < userHp) {
                return 80.0 * accuracy;
            }
            return 38.0 * accuracy;
        }

        // 3e. Hazards, Screens, Trick Room, Taunt, Encore, Trick/Switcheroo, Status Pivots
        if (HAZARDS.contains(moveId)) {
            if (lastMove != null && HAZARDS.contains(normalizeId(lastMove))) {
                return -200.0;
            }
            int oppAliveCount = countAlivePokemon(opponent.getActor().getPokemonList());
            if (oppAliveCount >= 3 && oppBestDamage < userHp * 0.7 && highestDamage < oppHp) {
                return 78.0;
            }
            return -25.0;
        }

        if (SCREENS.contains(moveId)) {
            if (lastMove != null && SCREENS.contains(normalizeId(lastMove))) {
                return -200.0;
            }
            if (oppBestDamage < userHp && highestDamage < oppHp) {
                return 76.0;
            }
            return 15.0;
        }

        if ("trickroom".equals(moveId)) {
            if ("trickroom".equals(lastMove != null ? normalizeId(lastMove) : "")) {
                return -300.0;
            }
            if (!faster && oppBestDamage < userHp) {
                return 88.0;
            }
            return -50.0;
        }

        if ("destinybond".equals(moveId)) {
            if ("destinybond".equals(lastMove != null ? normalizeId(lastMove) : "")) {
                return -300.0;
            }
            if (faster && oppBestDamage >= userHp && highestDamage < oppHp) {
                return 108.0;
            }
            return 10.0;
        }

        if (PIVOT_MOVES.contains(moveId) && !canSwitchIn.isEmpty()) {
            if ("shedtail".equals(moveId) && userHp > userMaxHp * 0.55) {
                return 94.0;
            }
            if ("partingshot".equals(moveId) || "chillyreception".equals(moveId) || "teleport".equals(moveId)) {
                if (oppBestDamage >= userHp * 0.5 && highestDamage < oppHp * 0.45) {
                    return 72.0;
                }
            }
            return 22.0;
        }

        return 15.0;
    }

    /**
     * Strict Voluntary Switch Evaluation:
     * Prevents vanilla StrongBattleAI's bug where healthy/winning Pokemon switch out into free hits or ping-pong switch.
     */
    private BattlePokemon considerVoluntarySwitch(
        BattlePokemon user,
        BattlePokemon opponent,
        List<InBattleMove> usableMoves,
        MoveDecision currentDecision,
        List<BattlePokemon> canSwitchIn,
        PokemonBattle battle
    ) {
        if (canSwitchIn.isEmpty()) {
            return null;
        }

        // Rule 1: Cooldown on voluntary switches so AI NEVER ping-pong switches on consecutive turns!
        if (battle != null) {
            Integer lastSwitchTurn = LAST_VOLUNTARY_SWITCH_TURN.get(battle.getBattleId());
            if (lastSwitchTurn != null && battle.getTurn() - lastSwitchTurn <= 2) {
                return null;
            }
        }

        // Rule 2: Special ability pivot: Zero to Hero Palafin in base form ("Zero" form has base Attack <= 70)
        if ("zerotohero".equals(getAbilityId(user)) && user.getEffectedPokemon().getForm().getBaseStats().getOrDefault(Stats.ATTACK, 100) <= 75) {
            boolean hasFlipTurn = usableMoves.stream().anyMatch(m -> PIVOT_MOVES.contains(normalizeId(m.getId())));
            int oppDmg = estimateOpponentBestDamage(opponent, user, false, battle);
            if (!hasFlipTurn || (!isFasterForMove(user, opponent, "flipturn", battle) && oppDmg >= user.getHealth())) {
                return chooseBestSwitchIn(canSwitchIn, opponent, battle);
            }
        }

        int userHp = Math.max(1, user.getHealth());
        int userMaxHp = Math.max(1, user.getMaxHealth());
        int oppHp = Math.max(1, opponent.getHealth());
        int ourBestDmg = currentDecision.move != null
            ? estimateMoveDamage(user, opponent, currentDecision.move.getId(), currentDecision.useTera, false, battle)
            : 0;
        int oppBestDmg = estimateOpponentBestDamage(opponent, user, currentDecision.useTera, battle);
        boolean faster = isFasterForMove(user, opponent, currentDecision.move != null ? currentDecision.move.getId() : "", battle);

        // Rule 3: NEVER switch out if we can KO the opponent this turn (faster OHKO, priority KO, or surviving 1 hit and OHKOing)!
        if (ourBestDmg >= oppHp && (faster || oppBestDmg < userHp)) {
            return null;
        }

        // Rule 4: NEVER switch out if we already have positive offensive boosts unless all our moves deal 0 damage (immune)!
        int boosts = Math.max(getStage(user, Stats.ATTACK), getStage(user, Stats.SPECIAL_ATTACK));

        boolean shouldSwitch = PracticeBattleMath.shouldVoluntarySwitch(
            faster,
            userHp,
            userMaxHp,
            oppHp,
            ourBestDmg,
            oppBestDmg,
            boosts
        );

        if (!shouldSwitch) {
            return null;
        }

        // Rule 6: Look for a hard counter / defensive answer on the bench:
        // A valid counter MUST:
        // 1) Be immune OR resist / comfortably tank opponent's attacks (taking <= 35% for walls, <= 46% for offensive counters)
        // 2) Have a winning matchup or wall the opponent effectively
        BattlePokemon bestBench = null;
        double bestBenchScore = Double.NEGATIVE_INFINITY;

        Pokemon oppPkm = opponent.getEffectedPokemon();
        int oppAtk = oppPkm.getAttack();
        int oppSpA = oppPkm.getSpecialAttack();
        boolean oppIsSpecial = oppSpA > oppAtk * 1.15;
        boolean oppIsPhysical = oppAtk > oppSpA * 1.15;

        for (BattlePokemon candidate : canSwitchIn) {
            int benchMaxHp = Math.max(1, candidate.getMaxHealth());
            int benchHp = Math.max(1, candidate.getHealth());
            int incomingOnSwitch = estimateOpponentBestDamage(opponent, candidate, false, battle);
            boolean isImmune = incomingOnSwitch == 0;

            Pokemon candPkm = candidate.getEffectedPokemon();
            int candDef = candPkm.getDefence();
            int candSpD = candPkm.getSpecialDefence();

            boolean isSpecialWall = oppIsSpecial && (candSpD >= 110 || candSpD >= candDef * 1.20) && incomingOnSwitch <= benchMaxHp * 0.35;
            boolean isPhysicalWall = oppIsPhysical && (candDef >= 110 || candDef >= candSpD * 1.20) && incomingOnSwitch <= benchMaxHp * 0.35;
            boolean isMatchingWall = isSpecialWall || isPhysicalWall;

            int benchBestOut = estimatePokemonBestDamageFromTemplateMoves(candidate, opponent, battle);
            boolean benchFaster = getEffectiveSpeedWithExtraStage(candidate, 0, battle) >= getEffectiveSpeedWithExtraStage(opponent, 0, battle);

            boolean hasRecoveryOrStatus = false;
            if (candPkm.getMoveSet() != null) {
                for (var move : candPkm.getMoveSet().getMoves()) {
                    if (move != null) {
                        String mId = normalizeId(move.getName());
                        if (RECOVERY_MOVES.contains(mId) || "willowisp".equals(mId) || "thunderwave".equals(mId)
                            || "toxic".equals(mId) || "spore".equals(mId) || "glare".equals(mId) || "saltcure".equals(mId)) {
                            hasRecoveryOrStatus = true;
                            break;
                        }
                    }
                }
            }

            boolean viable = PracticeBattleMath.isSwitchCandidateViable(
                isImmune,
                incomingOnSwitch,
                benchHp,
                benchMaxHp,
                benchBestOut,
                oppHp,
                benchFaster,
                isMatchingWall,
                hasRecoveryOrStatus
            );

            if (!viable) {
                continue;
            }

            double score = PracticeBattleMath.evaluateSwitchScore(
                isImmune,
                benchBestOut,
                incomingOnSwitch,
                oppHp,
                benchHp,
                benchMaxHp,
                benchFaster,
                isSpecialWall,
                isPhysicalWall
            );

            if (score > bestBenchScore) {
                bestBenchScore = score;
                bestBench = candidate;
            }
        }

        // If active Pokemon is at low HP (<= 20%), only switch if the bench counter is IMMUNE or takes <= 15% chip;
        // otherwise let the low HP active mon deal whatever damage it can before fainting to give a clean entry.
        if (bestBench != null && userHp <= userMaxHp * 0.20) {
            int incoming = estimateOpponentBestDamage(opponent, bestBench, false, battle);
            if (incoming > bestBench.getMaxHealth() * 0.15) {
                return null; // Sacrifice low HP mon instead of chipping bench
            }
        }

        return bestBench;
    }

    private BattlePokemon chooseBestSwitchIn(
        List<BattlePokemon> canSwitchIn,
        BattlePokemon opponent,
        PokemonBattle battle
    ) {
        if (canSwitchIn.size() == 1 || opponent == null) {
            return canSwitchIn.get(0);
        }

        BattlePokemon best = canSwitchIn.get(0);
        double bestScore = Double.NEGATIVE_INFINITY;

        for (BattlePokemon candidate : canSwitchIn) {
            double score = evaluateSwitchCandidateScore(candidate, opponent, battle);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private double evaluateSwitchCandidateScore(BattlePokemon candidate, BattlePokemon opponent, PokemonBattle battle) {
        if (opponent == null) {
            return candidate.getHealth();
        }
        int candHp = Math.max(1, candidate.getHealth());
        int candMaxHp = Math.max(1, candidate.getMaxHealth());
        int oppHp = Math.max(1, opponent.getHealth());

        int ourDmg = estimatePokemonBestDamageFromTemplateMoves(candidate, opponent, battle);
        int oppDmg = estimateOpponentBestDamage(opponent, candidate, false, battle);
        boolean faster = getEffectiveSpeedWithExtraStage(candidate, 0, battle) >= getEffectiveSpeedWithExtraStage(opponent, 0, battle);

        Pokemon oppPkm = opponent.getEffectedPokemon();
        Pokemon candPkm = candidate.getEffectedPokemon();
        int oppAtk = oppPkm.getAttack();
        int oppSpA = oppPkm.getSpecialAttack();
        boolean oppIsSpecial = oppSpA > oppAtk * 1.15;
        boolean oppIsPhysical = oppAtk > oppSpA * 1.15;

        int candDef = candPkm.getDefence();
        int candSpD = candPkm.getSpecialDefence();

        boolean isSpecialWall = oppIsSpecial && (candSpD >= 110 || candSpD >= candDef * 1.20) && oppDmg <= candMaxHp * 0.35;
        boolean isPhysicalWall = oppIsPhysical && (candDef >= 110 || candDef >= candSpD * 1.20) && oppDmg <= candMaxHp * 0.35;

        return PracticeBattleMath.evaluateSwitchScore(
            oppDmg == 0,
            ourDmg,
            oppDmg,
            oppHp,
            candHp,
            candMaxHp,
            faster,
            isSpecialWall,
            isPhysicalWall
        );
    }

    private int estimateOpponentBestDamage(
        BattlePokemon opponent,
        BattlePokemon defender,
        boolean defenderTerastallized,
        PokemonBattle battle
    ) {
        int best = 0;
        Pokemon effOpp = opponent.getEffectedPokemon();
        if (effOpp.getMoveSet() != null) {
            for (var move : effOpp.getMoveSet().getMoves()) {
                if (move != null) {
                    int dmg = estimateMoveDamage(opponent, defender, move.getName(), false, defenderTerastallized, battle);
                    if (dmg > best) {
                        best = dmg;
                    }
                }
            }
        }
        if (best == 0) {
            // Fallback: estimate STAB 85-power attack from opponent's primary/secondary types
            for (ElementalType t : effOpp.getTypes()) {
                int est = estimateSyntheticTypeDamage(opponent, defender, t, 85, defenderTerastallized);
                if (est > best) {
                    best = est;
                }
            }
        }
        return best;
    }

    private int estimatePokemonBestDamageFromTemplateMoves(
        BattlePokemon attacker,
        BattlePokemon defender,
        PokemonBattle battle
    ) {
        int best = 0;
        Pokemon effAttacker = attacker.getEffectedPokemon();
        if (effAttacker.getMoveSet() != null) {
            for (var move : effAttacker.getMoveSet().getMoves()) {
                if (move != null) {
                    int dmg = estimateMoveDamage(attacker, defender, move.getName(), false, false, battle);
                    if (dmg > best) {
                        best = dmg;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Accurate Level-50 Gen 9 Damage Estimator using real IV/EV/Nature stats from {@link BattlePokemon#getEffectedPokemon()}.
     */
    public int estimateMoveDamage(
        BattlePokemon attacker,
        BattlePokemon defender,
        String rawMoveId,
        boolean attackerTerastallized,
        boolean defenderTerastallized,
        PokemonBattle battle
    ) {
        String moveId = normalizeId(rawMoveId);
        MoveTemplate template = Moves.INSTANCE.getByName(moveId);
        if (template == null || template.getDamageCategory().equals(DamageCategories.INSTANCE.getSTATUS())) {
            return 0;
        }

        // Fixed / special damage moves
        if ("seismictoss".equals(moveId) || "nightshade".equals(moveId)) {
            double eff = getTypeEffectiveness(attacker, defender, moveId, template.getElementalType(), defenderTerastallized);
            return eff == 0.0 ? 0 : attacker.getEffectedPokemon().getLevel();
        }
        if ("ruination".equals(moveId) || "superfang".equals(moveId) || "naturesmadness".equals(moveId)) {
            double eff = getTypeEffectiveness(attacker, defender, moveId, template.getElementalType(), defenderTerastallized);
            return eff == 0.0 ? 0 : Math.max(1, defender.getHealth() / 2);
        }
        if ("endeavor".equals(moveId)) {
            double eff = getTypeEffectiveness(attacker, defender, moveId, template.getElementalType(), defenderTerastallized);
            return eff == 0.0 ? 0 : Math.max(0, defender.getHealth() - attacker.getHealth());
        }
        if ("finalgambit".equals(moveId)) {
            double eff = getTypeEffectiveness(attacker, defender, moveId, template.getElementalType(), defenderTerastallized);
            return eff == 0.0 ? 0 : attacker.getHealth();
        }
        if ("counter".equals(moveId) || "mirrorcoat".equals(moveId) || "metalburst".equals(moveId) || "comeuppance".equals(moveId)) {
            return 0;
        }
        if (OHKO_MOVES.contains(moveId)) {
            return canOhkoMoveHit(attacker, defender, moveId, template) ? defender.getHealth() : 0;
        }

        ElementalType moveType = resolveEffectiveMoveType(attacker, moveId, template.getElementalType(), attackerTerastallized);

        // Check ability immunities (unless attacker has Mold Breaker / Teravolt / Turboblaze / Mycelium Might)
        if (!ignoresDefenderAbility(attacker, moveId) && isImmuneByAbility(defender, moveType, moveId)) {
            return 0;
        }

        double typeEffectiveness = getTypeEffectiveness(attacker, defender, moveId, moveType, defenderTerastallized);
        if (typeEffectiveness <= 0.0) {
            return 0;
        }

        double basePower = resolveEffectiveBasePower(attacker, defender, moveId, template, battle);
        if (basePower <= 0.0) {
            return 0;
        }

        boolean isPhysical = template.getDamageCategory().equals(DamageCategories.INSTANCE.getPHYSICAL());
        boolean alwaysCrit = ALWAYS_CRIT_MOVES.contains(moveId);
        boolean ignoreAttackerStages = hasAbility(defender, "unaware");
        boolean ignoreDefenderStages = hasAbility(attacker, "unaware") || "sacredsword".equals(moveId) || "darkestlariat".equals(moveId);

        Pokemon attPkm = attacker.getEffectedPokemon();
        Pokemon defPkm = defender.getEffectedPokemon();

        // Determine offensive stat
        double atkStat;
        if ("bodypress".equals(moveId)) {
            atkStat = attPkm.getDefence() * stageMultiplier(ignoreAttackerStages ? 0 : getStage(attacker, Stats.DEFENCE));
        } else if ("foulplay".equals(moveId)) {
            atkStat = defPkm.getAttack() * stageMultiplier(ignoreAttackerStages ? 0 : getStage(defender, Stats.ATTACK));
        } else if (isPhysical) {
            int stage = ignoreAttackerStages ? 0 : getStage(attacker, Stats.ATTACK);
            if (alwaysCrit && stage < 0) {
                stage = 0;
            }
            atkStat = attPkm.getAttack() * stageMultiplier(stage);
        } else {
            int stage = ignoreAttackerStages ? 0 : getStage(attacker, Stats.SPECIAL_ATTACK);
            if (alwaysCrit && stage < 0) {
                stage = 0;
            }
            atkStat = attPkm.getSpecialAttack() * stageMultiplier(stage);
        }

        // Determine defensive stat (Psyshock / Psystrike / Secret Sword target physical Defense)
        boolean targetsPhysicalDef = isPhysical || "psyshock".equals(moveId) || "psystrike".equals(moveId) || "secretsword".equals(moveId);
        double defStat;
        if (targetsPhysicalDef) {
            int stage = ignoreDefenderStages ? 0 : getStage(defender, Stats.DEFENCE);
            if (alwaysCrit && stage > 0) {
                stage = 0;
            }
            defStat = defPkm.getDefence() * stageMultiplier(stage);
        } else {
            int stage = ignoreDefenderStages ? 0 : getStage(defender, Stats.SPECIAL_DEFENCE);
            if (alwaysCrit && stage > 0) {
                stage = 0;
            }
            defStat = defPkm.getSpecialDefence() * stageMultiplier(stage);
        }

        // Offensive ability & item modifiers
        String attAbility = getAbilityId(attacker);
        String attItem = getHeldItemId(attacker);
        String defAbility = ignoresDefenderAbility(attacker, moveId) ? "" : getAbilityId(defender);
        String defItem = getHeldItemId(defender);

        if (isPhysical) {
            if ("hugepower".equals(attAbility) || "purepower".equals(attAbility)) {
                atkStat *= 2.0;
            } else if ("gorillatactics".equals(attAbility) || "hustle".equals(attAbility)) {
                atkStat *= 1.5;
            } else if ("guts".equals(attAbility) && attPkm.getStatus() != null) {
                atkStat *= 1.5;
            } else if ("swordofruin".equals(attAbility)) {
                defStat *= 0.75;
            }
            if ("choiceband".equals(attItem)) {
                atkStat *= 1.5;
            }
            // Burn halves physical damage unless Guts or Facade
            if (isBurned(attPkm) && !"guts".equals(attAbility) && !"facade".equals(moveId)) {
                atkStat *= 0.5;
            }
        } else {
            if ("beadsofruin".equals(attAbility)) {
                defStat *= 0.75;
            }
            if ("choicespecs".equals(attItem)) {
                atkStat *= 1.5;
            }
        }

        if ("vesselofruin".equals(defAbility) && !isPhysical) {
            atkStat *= 0.75;
        } else if ("tabletsofruin".equals(defAbility) && isPhysical) {
            atkStat *= 0.75;
        } else if ("marvelscale".equals(defAbility) && defPkm.getStatus() != null && targetsPhysicalDef) {
            defStat *= 1.5;
        } else if ("furcoat".equals(defAbility) && targetsPhysicalDef) {
            defStat *= 2.0;
        } else if ("icescales".equals(defAbility) && !isPhysical) {
            defStat *= 2.0;
        }

        if ("assaultvest".equals(defItem) && !targetsPhysicalDef) {
            defStat *= 1.5;
        } else if ("eviolite".equals(defItem)) {
            defStat *= 1.5;
        }

        int level = Math.max(1, attPkm.getLevel());
        double baseDamage = Math.floor(Math.floor(Math.floor(2.0 * level / 5.0 + 2.0) * basePower * atkStat / Math.max(1.0, defStat)) / 50.0) + 2.0;

        if (alwaysCrit) {
            baseDamage *= 1.5;
        }

        // STAB & Tera STAB calculation
        double stab = computeStabMultiplier(attacker, moveType, attackerTerastallized);
        double damage = baseDamage * stab * typeEffectiveness;

        // Item & Ability damage modifiers
        if ("lifeorb".equals(attItem)) {
            damage *= 1.3;
        } else if ("expertbelt".equals(attItem) && typeEffectiveness > 1.0) {
            damage *= 1.2;
        }

        if ("supremeoverlord".equals(attAbility)) {
            int faintedAllies = battle == null ? 0 : countFaintedPokemon(attacker.getActor().getPokemonList());
            damage *= (1.0 + 0.1 * Math.min(5, faintedAllies));
        }

        if (("multiscale".equals(defAbility) || "shadowshield".equals(defAbility)) && defender.getHealth() >= defender.getMaxHealth()) {
            damage *= 0.5;
        } else if (("solidrock".equals(defAbility) || "filter".equals(defAbility) || "prismarmor".equals(defAbility)) && typeEffectiveness > 1.0) {
            damage *= 0.75;
        } else if ("thickfat".equals(defAbility)
            && (moveType.equals(ElementalTypes.INSTANCE.getFIRE()) || moveType.equals(ElementalTypes.INSTANCE.getICE()))) {
            damage *= 0.5;
        } else if ("purifyingsalt".equals(defAbility) && moveType.equals(ElementalTypes.INSTANCE.getGHOST())) {
            damage *= 0.5;
        }

        // Use conservative 0.91x average roll so the AI doesn't rely on max damage rolls for KOs
        int finalDamage = (int) Math.max(1, Math.floor(damage * 0.91));

        // Sturdy / Focus Sash / Disguise survival check at full HP (for single-hit moves)
        boolean isMultiHit = MULTI_HIT_2_TO_5.contains(moveId) || "surgingstrikes".equals(moveId)
            || "tripleaxel".equals(moveId) || "dualwingbeat".equals(moveId) || "populationbomb".equals(moveId)
            || "tachyoncutter".equals(moveId) || "twinbeam".equals(moveId) || "doubleironbash".equals(moveId);
        if (!isMultiHit && defender.getHealth() >= defender.getMaxHealth()) {
            if ("sturdy".equals(defAbility) || "focus_sash".equals(defItem) || "focussash".equals(defItem)) {
                finalDamage = Math.min(finalDamage, Math.max(1, defender.getHealth() - 1));
            } else if ("disguise".equals(defAbility)) {
                finalDamage = Math.min(finalDamage, Math.max(1, defender.getMaxHealth() / 8));
            }
        }

        return finalDamage;
    }

    private int estimateSyntheticTypeDamage(
        BattlePokemon attacker,
        BattlePokemon defender,
        ElementalType type,
        int power,
        boolean defenderTerastallized
    ) {
        double eff = getTypeEffectiveness(attacker, defender, "", type, defenderTerastallized);
        if (eff <= 0.0) {
            return 0;
        }
        Pokemon attPkm = attacker.getEffectedPokemon();
        Pokemon defPkm = defender.getEffectedPokemon();
        double atk = Math.max(attPkm.getAttack(), attPkm.getSpecialAttack());
        double def = Math.min(defPkm.getDefence(), defPkm.getSpecialDefence());
        int level = Math.max(1, attPkm.getLevel());
        double base = Math.floor(Math.floor(Math.floor(2.0 * level / 5.0 + 2.0) * power * atk / Math.max(1.0, def)) / 50.0) + 2.0;
        return (int) Math.max(1, Math.floor(base * 1.5 * eff * 0.92));
    }

    private double resolveEffectiveBasePower(
        BattlePokemon attacker,
        BattlePokemon defender,
        String moveId,
        MoveTemplate template,
        PokemonBattle battle
    ) {
        double power = template.getPower();
        String attAbility = getAbilityId(attacker);
        String attItem = getHeldItemId(attacker);

        if ("terablast".equals(moveId)) {
            power = 80.0;
        } else if ("gyroball".equals(moveId)) {
            int userSpe = Math.max(1, getEffectiveSpeedWithExtraStage(attacker, 0, battle));
            int targetSpe = Math.max(1, getEffectiveSpeedWithExtraStage(defender, 0, battle));
            power = Math.min(150.0, Math.floor(25.0 * targetSpe / userSpe) + 1.0);
        } else if ("electroball".equals(moveId)) {
            int userSpe = Math.max(1, getEffectiveSpeedWithExtraStage(attacker, 0, battle));
            int targetSpe = Math.max(1, getEffectiveSpeedWithExtraStage(defender, 0, battle));
            double ratio = (double) userSpe / targetSpe;
            power = ratio >= 4.0 ? 150 : ratio >= 3.0 ? 120 : ratio >= 2.0 ? 80 : ratio >= 1.0 ? 60 : 40;
        } else if ("lowkick".equals(moveId) || "grassknot".equals(moveId)) {
            float weightHg = defender.getEffectedPokemon().getForm().getWeight();
            float weightKg = weightHg / 10.0f;
            power = weightKg >= 200f ? 120 : weightKg >= 100f ? 100 : weightKg >= 50f ? 80 : weightKg >= 25f ? 60 : weightKg >= 10f ? 40 : 20;
        } else if ("heavyslam".equals(moveId) || "heatcrash".equals(moveId)) {
            float userW = Math.max(1f, attacker.getEffectedPokemon().getForm().getWeight());
            float targetW = Math.max(1f, defender.getEffectedPokemon().getForm().getWeight());
            float r = userW / targetW;
            power = r >= 5f ? 120 : r >= 4f ? 100 : r >= 3f ? 80 : r >= 2f ? 60 : 40;
        } else if ("storedpower".equals(moveId) || "powertrip".equals(moveId)) {
            int totalPositiveStages = 0;
            for (Integer stage : attacker.getStatChanges().values()) {
                if (stage != null && stage > 0) {
                    totalPositiveStages += stage;
                }
            }
            power = 20.0 + 20.0 * totalPositiveStages;
        } else if ("acrobatics".equals(moveId) && attItem.isEmpty()) {
            power = 110.0;
        } else if ("knockoff".equals(moveId) && hasRemovableItem(defender)) {
            power = 97.5;
        } else if ("facade".equals(moveId) && attacker.getEffectedPokemon().getStatus() != null) {
            power = 140.0;
        } else if (("hex".equals(moveId) || "infernalparade".equals(moveId) || "bittermalice".equals(moveId))
            && defender.getEffectedPokemon().getStatus() != null) {
            power *= 2.0;
        } else if (("fishiousrend".equals(moveId) || "boltbeak".equals(moveId))
            && isFasterForMove(attacker, defender, moveId, battle)) {
            power = 170.0;
        } else if ("waterspout".equals(moveId) || "eruption".equals(moveId) || "dragonenergy".equals(moveId)) {
            power = Math.max(1.0, Math.floor(150.0 * attacker.getHealth() / Math.max(1.0, attacker.getMaxHealth())));
        } else if ("lastrespects".equals(moveId)) {
            int fainted = battle == null ? 0 : countFaintedPokemon(attacker.getActor().getPokemonList());
            power = 50.0 + 50.0 * fainted;
        } else if ("ragefist".equals(moveId)) {
            power = 100.0;
        } else if (MULTI_HIT_2_TO_5.contains(moveId)) {
            double hits = "skilllink".equals(attAbility) ? 5.0
                : ("loaded_dice".equals(attItem) || "loadeddice".equals(attItem)) ? 4.25
                : 3.1;
            power = power * hits;
        } else if ("surgingstrikes".equals(moveId) || "tripleaxel".equals(moveId) || "watershuriken".equals(moveId)) {
            power = "tripleaxel".equals(moveId) ? 90.0 : power * 3.0;
        } else if ("dualwingbeat".equals(moveId) || "doubleironbash".equals(moveId) || "tachyoncutter".equals(moveId) || "twinbeam".equals(moveId)) {
            power = power * 2.0;
        } else if ("populationbomb".equals(moveId)) {
            power = 160.0;
        }

        // Ability power boosts (Technician, Strong Jaw, Iron Fist, Sharpness, Tough Claws, Sheer Force)
        if ("technician".equals(attAbility) && template.getPower() <= 60.0) {
            power *= 1.5;
        }
        if ("strongjaw".equals(attAbility) && BITING_MOVES.contains(moveId)) {
            power *= 1.5;
        }
        if ("ironfist".equals(attAbility) && PUNCH_MOVES.contains(moveId)) {
            power *= 1.2;
        }
        if ("sharpness".equals(attAbility) && SLICING_MOVES.contains(moveId)) {
            power *= 1.5;
        }
        if ("punkrock".equals(attAbility) && SOUND_MOVES.contains(moveId)) {
            power *= 1.3;
        }

        return power;
    }

    private ElementalType resolveEffectiveMoveType(
        BattlePokemon attacker,
        String moveId,
        ElementalType templateType,
        boolean attackerTerastallized
    ) {
        if ("terablast".equals(moveId) && attackerTerastallized) {
            ElementalType teraType = resolveTeraElementalType(attacker);
            if (teraType != null) {
                return teraType;
            }
        }
        String ability = getAbilityId(attacker);
        if (templateType.equals(ElementalTypes.INSTANCE.getNORMAL())) {
            if ("pixilate".equals(ability)) {
                return ElementalTypes.INSTANCE.getFAIRY();
            }
            if ("aerilate".equals(ability)) {
                return ElementalTypes.INSTANCE.getFLYING();
            }
            if ("refrigerate".equals(ability)) {
                return ElementalTypes.INSTANCE.getICE();
            }
            if ("galvanize".equals(ability)) {
                return ElementalTypes.INSTANCE.getELECTRIC();
            }
        }
        return templateType;
    }

    private double computeStabMultiplier(
        BattlePokemon attacker,
        ElementalType moveType,
        boolean attackerTerastallized
    ) {
        boolean hasOriginalStab = hasElementalType(attacker, moveType);
        boolean adaptability = "adaptability".equals(getAbilityId(attacker));

        if (attackerTerastallized) {
            ElementalType teraType = resolveTeraElementalType(attacker);
            boolean matchesTera = teraType != null && teraType.equals(moveType);
            if (matchesTera && hasOriginalStab) {
                return adaptability ? 2.25 : 2.0;
            }
            if (matchesTera || hasOriginalStab) {
                return adaptability ? 2.0 : 1.5;
            }
            return 1.0;
        }

        if (hasOriginalStab) {
            return adaptability ? 2.0 : 1.5;
        }
        return 1.0;
    }

    private double getTypeEffectiveness(
        BattlePokemon attacker,
        BattlePokemon defender,
        String moveId,
        ElementalType moveType,
        boolean defenderTerastallized
    ) {
        List<ElementalType> defTypes;
        if (defenderTerastallized) {
            ElementalType tera = resolveTeraElementalType(defender);
            defTypes = tera != null ? List.of(tera) : toList(defender.getEffectedPokemon().getTypes());
        } else {
            defTypes = toList(defender.getEffectedPokemon().getTypes());
        }

        boolean scrappyOrMindEye = "scrappy".equals(getAbilityId(attacker)) || "mindseye".equals(getAbilityId(attacker));
        double multiplier = 1.0;

        for (ElementalType defType : defTypes) {
            if ("freezedry".equals(moveId) && defType.equals(ElementalTypes.INSTANCE.getWATER())) {
                multiplier *= 2.0;
                continue;
            }
            if (scrappyOrMindEye && defType.equals(ElementalTypes.INSTANCE.getGHOST())
                && (moveType.equals(ElementalTypes.INSTANCE.getNORMAL()) || moveType.equals(ElementalTypes.INSTANCE.getFIGHTING()))) {
                continue;
            }
            if ("thousandarrows".equals(moveId) && defType.equals(ElementalTypes.INSTANCE.getFLYING())) {
                continue;
            }
            double eff = getSingleTypeEffectiveness(moveType, defType);
            multiplier *= eff;
        }
        return multiplier;
    }

    public static double getSingleTypeEffectiveness(ElementalType attackType, ElementalType defendType) {
        if (attackType == null || defendType == null) {
            return 1.0;
        }
        return getSingleTypeEffectiveness(attackType.getShowdownId(), defendType.getShowdownId());
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

    private boolean canOhkoMoveHit(BattlePokemon attacker, BattlePokemon defender, String moveId, MoveTemplate template) {
        if (attacker.getEffectedPokemon().getLevel() < defender.getEffectedPokemon().getLevel()) {
            return false;
        }
        if (!ignoresDefenderAbility(attacker, moveId) && hasAbility(defender, "sturdy")) {
            return false;
        }
        if ("sheercold".equals(moveId) && hasElementalType(defender, ElementalTypes.INSTANCE.getICE())) {
            return false;
        }
        ElementalType moveType = template.getElementalType();
        if (!ignoresDefenderAbility(attacker, moveId) && isImmuneByAbility(defender, moveType, moveId)) {
            return false;
        }
        return getTypeEffectiveness(attacker, defender, moveId, moveType, false) > 0.0;
    }

    private boolean ignoresDefenderAbility(BattlePokemon attacker, String moveId) {
        String ability = getAbilityId(attacker);
        return "moldbreaker".equals(ability) || "teravolt".equals(ability) || "turboblaze".equals(ability)
            || "sunsteelstrike".equals(moveId) || "moongeistbeam".equals(moveId) || "photongeyser".equals(moveId);
    }

    private boolean isImmuneByAbility(BattlePokemon defender, ElementalType moveType, String moveId) {
        String defAbility = getAbilityId(defender);
        String defItem = getHeldItemId(defender);

        if (moveType.equals(ElementalTypes.INSTANCE.getGROUND()) && !"thousandarrows".equals(moveId)) {
            if ("levitate".equals(defAbility) || "eartheater".equals(defAbility) || "airballoon".equals(defItem)) {
                return true;
            }
        }
        if (moveType.equals(ElementalTypes.INSTANCE.getWATER())
            && ("waterabsorb".equals(defAbility) || "stormdrain".equals(defAbility) || "dryskin".equals(defAbility))) {
            return true;
        }
        if (moveType.equals(ElementalTypes.INSTANCE.getELECTRIC())
            && ("voltabsorb".equals(defAbility) || "lightningrod".equals(defAbility) || "motordrive".equals(defAbility))) {
            return true;
        }
        if (moveType.equals(ElementalTypes.INSTANCE.getFIRE())
            && ("flashfire".equals(defAbility) || "wellbakedbody".equals(defAbility))) {
            return true;
        }
        if (moveType.equals(ElementalTypes.INSTANCE.getGRASS()) && "sapsipper".equals(defAbility)) {
            return true;
        }
        if ("soundproof".equals(defAbility) && SOUND_MOVES.contains(moveId)) {
            return true;
        }
        if ("bulletproof".equals(defAbility) && ("shadowball".equals(moveId) || "sludgebomb".equals(moveId)
            || "focusblast".equals(moveId) || "aurasphere".equals(moveId) || "energyball".equals(moveId)
            || "gyroball".equals(moveId) || "electroball".equals(moveId) || "pyroball".equals(moveId)
            || "pollenpuff".equals(moveId) || "seedbomb".equals(moveId) || "bulletseed".equals(moveId))) {
            return true;
        }
        return false;
    }

    private boolean isFasterForMove(BattlePokemon user, BattlePokemon opponent, String moveId, PokemonBattle battle) {
        int myPriority = getMovePriority(user, normalizeId(moveId));
        if (myPriority > 0) {
            return true;
        }
        if (myPriority < 0) {
            return false;
        }
        return getEffectiveSpeedWithExtraStage(user, 0, battle) >= getEffectiveSpeedWithExtraStage(opponent, 0, battle);
    }
    int plannerSpeed(BattlePokemon pokemon,PokemonBattle battle){return getEffectiveSpeedWithExtraStage(pokemon,0,battle);}
    int plannerPriority(BattlePokemon pokemon,String move){return getMovePriority(pokemon,move);}

    private int getMovePriority(BattlePokemon user, String moveId) {
        MoveTemplate template = Moves.INSTANCE.getByName(moveId);
        int priority = template != null ? template.getPriority() : 0;
        String ability = getAbilityId(user);
        if (template != null && template.getDamageCategory().equals(DamageCategories.INSTANCE.getSTATUS()) && "prankster".equals(ability)) {
            priority += 1;
        } else if (template != null && template.getElementalType().equals(ElementalTypes.INSTANCE.getFLYING())
            && "galewings".equals(ability) && user.getHealth() >= user.getMaxHealth()) {
            priority += 1;
        } else if ("triage".equals(ability) && (RECOVERY_MOVES.contains(moveId) || "drainingkiss".equals(moveId)
            || "gigadrain".equals(moveId) || "drainpunch".equals(moveId) || "hornleech".equals(moveId))) {
            priority += 3;
        } else if ("grassyglide".equals(moveId)) {
            priority += 1;
        }
        return priority;
    }

    private int getEffectiveSpeedWithExtraStage(BattlePokemon bp, int extraStage, PokemonBattle battle) {
        Pokemon pkm = bp.getEffectedPokemon();
        int baseSpe = pkm.getSpeed();
        int stage = Math.max(-6, Math.min(6, getStage(bp, Stats.SPEED) + extraStage));
        double spe = baseSpe * stageMultiplier(stage);

        String item = getHeldItemId(bp);
        if ("choicescarf".equals(item)) {
            spe *= 1.5;
        } else if ("ironball".equals(item) || "machobrace".equals(item)) {
            spe *= 0.5;
        }

        if (isParalyzed(pkm) && !"quickfeet".equals(getAbilityId(bp))) {
            spe *= 0.5;
        }
        return (int) Math.max(1, Math.round(spe));
    }

    private static double stageMultiplier(int stage) {
        int clamped = Math.max(-6, Math.min(6, stage));
        return clamped >= 0 ? (2.0 + clamped) / 2.0 : 2.0 / (2.0 - clamped);
    }

    private static int getStage(BattlePokemon bp, Stat stat) {
        Integer val = bp.getStatChanges().get(stat);
        return val != null ? val : 0;
    }

    private static Stat resolveStat(String shortId) {
        return switch (shortId) {
            case "atk" -> Stats.ATTACK;
            case "def" -> Stats.DEFENCE;
            case "spa" -> Stats.SPECIAL_ATTACK;
            case "spd" -> Stats.SPECIAL_DEFENCE;
            case "spe" -> Stats.SPEED;
            default -> null;
        };
    }

    private static ElementalType resolveTeraElementalType(BattlePokemon bp) {
        TeraType tera = bp.getEffectedPokemon().getTeraType();
        if (tera instanceof ElementalTypeTeraType elementalTera) {
            return elementalTera.getType();
        }
        if (tera != null) {
            ElementalType byName = ElementalTypes.INSTANCE.get(tera.showdownId());
            if (byName != null) {
                return byName;
            }
        }
        for (ElementalType t : bp.getEffectedPokemon().getTypes()) {
            return t;
        }
        return null;
    }

    private static boolean hasElementalType(BattlePokemon bp, ElementalType targetType) {
        if (targetType == null) {
            return false;
        }
        for (ElementalType t : bp.getEffectedPokemon().getTypes()) {
            if (targetType.equals(t)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAbility(BattlePokemon bp, String abilityId) {
        return abilityId.equals(getAbilityId(bp));
    }

    private static String getAbilityId(BattlePokemon bp) {
        var ability = bp.getEffectedPokemon().getAbility();
        return ability != null && ability.getName() != null ? normalizeId(ability.getName()) : "";
    }

    private static String getHeldItemId(BattlePokemon bp) {
        var stack = bp.getEffectedPokemon().heldItem();
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String path = stack.getItem().builtInRegistryHolder().key().location().getPath();
        return normalizeId(path);
    }

    private static boolean hasRemovableItem(BattlePokemon bp) {
        String item = getHeldItemId(bp);
        if (item.isEmpty()) {
            return false;
        }
        return !item.endsWith("ite") && !item.endsWith("iumz") && !item.contains("memory") && !item.contains("plate");
    }

    private static boolean isBurned(Pokemon pkm) {
        PersistentStatusContainer status = pkm.getStatus();
        return status != null && status.getStatus() != null && "brn".equalsIgnoreCase(status.getStatus().getShowdownName());
    }

    private static boolean isParalyzed(Pokemon pkm) {
        PersistentStatusContainer status = pkm.getStatus();
        return status != null && status.getStatus() != null && "par".equalsIgnoreCase(status.getStatus().getShowdownName());
    }

    private static boolean isOpponentBadlyDamagedPassively(BattlePokemon opponent) {
        PersistentStatusContainer status = opponent.getEffectedPokemon().getStatus();
        if (status != null && status.getStatus() != null) {
            String s = status.getStatus().getShowdownName();
            if ("tox".equalsIgnoreCase(s) || "psn".equalsIgnoreCase(s) || "brn".equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }

    private static boolean targetsOpponent(InBattleMove move) {
        String target = move.getTarget() != null ? move.getTarget().name().toLowerCase(Locale.ROOT) : "normal";
        return !target.contains("self") && !target.contains("allyside") && !target.contains("field");
    }

    private static String pickValidTargetPnx(ActiveBattlePokemon activeBattlePokemon, InBattleMove move) {
        List<Targetable> targets = move.getTargets(activeBattlePokemon);
        if (targets == null || targets.isEmpty()) {
            return null;
        }
        for (Targetable t : targets) {
            if (t instanceof ActiveBattlePokemon abp && !abp.isAllied(activeBattlePokemon) && !abp.isGone()) {
                return abp.getPNX();
            }
        }
        return targets.get(0).getPNX();
    }

    private String pickBestTargetPnx(
        ActiveBattlePokemon activeBattlePokemon,
        InBattleMove move,
        BattlePokemon user,
        BattleSide aiSide,
        boolean userTerastallized
    ) {
        List<Targetable> targets = move.getTargets(activeBattlePokemon);
        if (targets == null || targets.isEmpty()) {
            return null;
        }
        Targetable best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Targetable t : targets) {
            if (t instanceof ActiveBattlePokemon abp && !abp.isAllied(activeBattlePokemon) && abp.getBattlePokemon() != null && !abp.isGone()) {
                BattlePokemon opp = abp.getBattlePokemon();
                int dmg = estimateMoveDamage(user, opp, move.getId(), userTerastallized, false, activeBattlePokemon.getBattle());
                int score = dmg >= opp.getHealth() ? 10000 + dmg : dmg;
                if (score > bestScore) {
                    bestScore = score;
                    best = abp;
                }
            }
        }
        if (best != null) {
            return best.getPNX();
        }
        return targets.get(0).getPNX();
    }

    private static int countAlivePokemon(Iterable<BattlePokemon> list) {
        int count = 0;
        for (BattlePokemon bp : list) {
            if (bp != null && bp.getHealth() > 0 && !bp.getGone()) {
                count++;
            }
        }
        return count;
    }

    private static int countFaintedPokemon(Iterable<BattlePokemon> list) {
        int count = 0;
        for (BattlePokemon bp : list) {
            if (bp != null && (bp.getHealth() <= 0 || bp.getGone())) {
                count++;
            }
        }
        return count;
    }

    private static <T> List<T> toList(Iterable<T> iterable) {
        if (iterable == null) {
            return List.of();
        }
        if (iterable instanceof List<T> list) {
            return list;
        }
        return StreamSupport.stream(iterable.spliterator(), false).toList();
    }

    private static String normalizeId(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private record ScoredMove(InBattleMove move, double score, int damage) {
    }

    private record MoveDecision(InBattleMove move, double score, String gimmickId, boolean useTera) {
    }
}
