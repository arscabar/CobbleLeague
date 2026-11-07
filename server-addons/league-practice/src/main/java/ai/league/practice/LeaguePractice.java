package ai.league.practice;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.*;
import java.util.UUID;

public final class LeaguePractice implements ModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("league-practice");
    static PracticeController controller;
    @Override public void onInitialize() {
        PracticeButtons.register();
        PracticeExchange.register();
        PracticeCommands.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                controller = new PracticeController(server,
                        FabricLoader.getInstance().getConfigDir().resolve("league-practice"),
                        server.getWorldPath(LevelResource.ROOT).resolve("league-practice"));
                LOG.info("NPC 훈련 기능을 불러왔습니다. /leaguepractice status 로 상태를 확인하세요.");
            } catch (Exception error) { LOG.error("설정 또는 보상 파일 오류로 훈련 기능을 비활성화했습니다. 원본 파일을 확인하세요.", error); }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            try { if (controller != null) controller.sessions.shutdown(); }
            catch (Exception error) { LOG.error("서버 종료 중 훈련 전투 정리 실패", error); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> controller = null);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            PracticeBattleOutput.tick();
            PracticeNpcCleanup.tick(server);
            if (controller == null) return;
            try { controller.sessions.tick(); }
            catch (Exception error) { LOG.error("훈련 전투 정리 실패", error); }
            if (server.getTickCount() % 100 == 0) for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                try { controller.rewards.claim(player); }
                catch (Exception error) { LOG.error("보류 증표 지급을 재시도할 수 없습니다.", error); }
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (PracticeNpcTags.stale(entity)) entity.discard();
        });
    }
    public static void disconnected(UUID player) {
        try { if (controller != null) controller.sessions.cancel(player, "접속 종료"); }
        catch (Exception error) { LOG.error("접속 종료 중 훈련 전투 정리 실패", error); }
    }
    public static void observe(UUID battle, String raw) {
        if (controller != null && ProtocolSignals.tie(raw)) controller.tie(battle);
    }
}
