package ai.league.practice;

import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.battles.*;
import net.fabricmc.loader.api.FabricLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One upstream AI instance per actor; upstream keeps battle-specific mutable state. */
public final class PracticeRunBunAI implements BattleAI {
    private static final Map<UUID, Map<UUID, PracticeRunBunAI>> BATTLES = new HashMap<>();
    private final BattleAI delegate;
    private int choices;
    private int failures;

    public PracticeRunBunAI() {
        try {
            Class<?> config = Class.forName("com.gitlab.surilexa.rbrctai.api.ai.config.RunBunAIConfig");
            Object settings = config.getConstructor(Boolean.class, String.class).newInstance(true, "");
            delegate = (BattleAI) Class.forName("com.gitlab.surilexa.rbrctai.api.ai.RunBunAI")
                    .getConstructor(config).newInstance(settings);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Run & Bun 0.15.4 AI를 연결하지 못했습니다.", error);
        }
    }

    public static BattleAI forActor(AIBattleActor actor) {
        return BATTLES.computeIfAbsent(actor.getBattle().getBattleId(), id -> new HashMap<>())
                .computeIfAbsent(actor.getUuid(), id -> {
                    var ai = new PracticeRunBunAI();
                    LeaguePractice.LOG.info("[Run & Bun] Connected practice actor {} in battle {}", id, actor.getBattle().getBattleId());
                    return ai;
                });
    }

    public static void release(UUID battle) { BATTLES.remove(battle); }

    public static String status() {
        String version = FabricLoader.getInstance().getModContainer("rbrctai")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("설치 안 됨");
        return "Run & Bun " + version + " / 모든 난이도 연결 / 테라 허용";
    }

    public int choices() { return choices; }
    public int failures() { return failures; }

    @Override public ShowdownActionResponse choose(ActiveBattlePokemon active, PokemonBattle battle,
            BattleSide side, ShowdownMoveset moveset, boolean forceSwitch) {
        // Simultaneous knockouts can leave the opposing slot empty. Use the existing
        // validated replacement selector until an opponent is active again.
        if (forceSwitch || side.getOppositeSide().getActivePokemon().stream()
                .noneMatch(p -> p.getBattlePokemon() != null && p.getBattlePokemon().getHealth() > 0)) {
            return PracticeTacticalBattleAI.INSTANCE.choose(active, battle, side, moveset, forceSwitch);
        }
        try {
            choices++;
            return delegate.choose(active, battle, side, moveset, false);
        } catch (RuntimeException | LinkageError error) {
            failures++;
            LeaguePractice.LOG.error("[Run & Bun] Choice failed in battle {}; using recovery AI for this choice", battle.getBattleId(), error);
            return PracticeTacticalBattleAI.INSTANCE.choose(active, battle, side, moveset, false);
        }
    }
}
