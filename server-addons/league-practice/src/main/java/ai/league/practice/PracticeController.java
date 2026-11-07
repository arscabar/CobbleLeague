package ai.league.practice;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import com.cobblemon.mod.common.battles.BattleRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.UUID;

final class PracticeController {
    final PracticeFiles files;
    final Rewards rewards;
    final PracticeSessions sessions;
    PracticeSettings settings;
    private final MinecraftServer server;
    PracticeController(MinecraftServer server, Path config, Path data) throws IOException {
        this.server = server;
        files = new PracticeFiles(config);
        files.bootstrap();
        settings = files.loadSettings();
        rewards = new Rewards(data);
        sessions = new PracticeSessions(server, rewards);
    }
    void change(Consumer<PracticeSettings> change) throws IOException {
        if (sessions.busy()) throw new IllegalStateException("전투 종료 후 설정을 변경해 주세요.");
        PracticeSettings next = files.loadSettings();
        change.accept(next);
        next.validate();
        files.saveSettings(next);
        settings = next;
    }
    void reload() throws IOException {
        if (sessions.busy()) throw new IllegalStateException("전투 종료 후 다시 불러와 주세요.");
        settings = files.loadSettings();
    }
    void tie(UUID battle) { server.execute(() -> sessions.tie(battle)); }
    void validatePresets(net.minecraft.commands.CommandSourceStack source) throws IOException {
        if (sessions.busy()) throw new IllegalStateException("전투 종료 후 검증해 주세요.");
        var world = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(settings.dimension)));
        if (world == null || settings.spawn == null) throw new IllegalStateException("NPC 위치를 먼저 설정해 주세요.");
        for (Tier tier : Tier.values()) {
            var teams = files.loadTeams(tier);
            if (teams.stream().allMatch(t -> t.pokemon().isEmpty())) continue;
            int checked = 0;
            for (TeamSpec team : files.readyTeams(tier)) {
                var npc = NpcSpawn.validateDetached(world, settings.spawn, tier, team);
                try { checked++; } finally { PracticeNpcTags.unmark(npc); npc.discard(); }
            }
            final String result = "[훈련 검증] " + tier.label() + " " + checked + "팀 / NPC 세팅 확인 완료 (화면 소환 없음)";
            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(result), false);
        }
    }
    void start(ServerPlayer player, Tier tier) throws IOException {
        if (!settings.enabled) throw new IllegalStateException("훈련 리그장이 비활성 상태입니다.");
        if (player.isSpectator() || !player.isAlive()) throw new IllegalStateException("현재 상태에서는 도전할 수 없습니다.");
        if (sessions.busy()) throw new IllegalStateException("리그장에서 이미 전투가 진행 중입니다.");
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null)
            throw new IllegalStateException("진행 중인 전투를 먼저 마쳐 주세요.");
        if (!player.serverLevel().dimension().location().toString().equals(settings.dimension))
            throw new IllegalStateException("등록된 리그장 차원에서 도전해 주세요.");
        var world = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(settings.dimension)));
        if (world == null) throw new IllegalStateException("리그장 차원을 찾을 수 없습니다.");
        var teams = files.readyTeams(tier);
        teams.forEach(NpcPartyFactory::validate);
        var team = teams.get(ThreadLocalRandom.current().nextInt(teams.size()));
        sessions.start(player, world, settings.spawn, tier, team);
    }
}
