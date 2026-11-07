package ai.league.recorder;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.google.gson.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

public final class LeagueRecorder implements ModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("league-battle-recorder");
    private static SessionManager sessions;

    @Override public void onInitialize() {
        var loader = FabricLoader.getInstance();
        var arena = ArenaConfig.load(loader.getConfigDir().resolve("league-battle-recorder.json"), LOG);
        sessions = new SessionManager(arena, loader.getGameDir().resolve("league-battle-records"));
        CobblemonEvents.BATTLE_STARTED_POST.subscribe((Consumer<BattleStartedEvent.Post>)
                event -> guard(() -> sessions.start(event.getBattle())));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> sessions.resume());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> guard(() -> sessions.shutdown()));
    }

    public static void observe(PokemonBattle battle, String raw) {
        if (sessions != null) guard(() -> sessions.accept(battle, raw));
    }

    public static void disconnected(UUID player) {
        if (sessions != null) guard(() -> sessions.disconnected(player));
    }

    static void guard(Runnable action) {
        try { action.run(); }
        catch (Exception error) { LOG.error("전투 기록 중 오류가 발생했습니다. 게임은 계속됩니다.", error); }
    }

    static void save(Path directory, BattleRecord record) {
        try {
            Path file = XlsxExport.write(directory, record);
            LOG.info("전투 기록 저장: {}", file);
        } catch (Exception error) {
            LOG.error("Excel 저장 실패. 전투 기록을 JSON으로 보존합니다: {}", record.id, error);
            try {
                Files.createDirectories(directory);
                var gson = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(Instant.class,
                        (JsonSerializer<Instant>) (value, type, context) -> new JsonPrimitive(value.toString())).create();
                Path file = directory.resolve(record.id + ".json");
                Files.writeString(file, gson.toJson(record), StandardOpenOption.CREATE_NEW);
                LOG.warn("복구용 전투 기록 저장: {}", file);
            } catch (Exception fallback) { LOG.error("JSON 보존도 실패했습니다: {}", record.id, fallback); }
        }
    }
}
