package ai.league.practice;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.phys.*;

final class SetupActions {
    static void spawn(CommandSourceStack source, PracticeController control) throws Exception {
        ServerPlayer player = source.getPlayerOrException();
        String dimension = player.serverLevel().dimension().location().toString();
        control.change(settings -> {
            sameWorld(settings, dimension);
            settings.dimension = dimension;
            settings.spawn = new SpawnSite(player.getX(), player.getY(), player.getZ(), player.getYRot());
        });
    }
    static void bind(CommandSourceStack source, PracticeController control, Tier tier) throws Exception {
        ServerPlayer player = source.getPlayerOrException();
        HitResult hit = player.pick(6, 0, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK ||
                !(player.serverLevel().getBlockState(block.getBlockPos()).getBlock() instanceof ButtonBlock))
            throw new IllegalArgumentException("등록할 버튼을 가까이에서 바라봐 주세요.");
        var pos = block.getBlockPos();
        String dimension = player.serverLevel().dimension().location().toString();
        control.change(settings -> {
            sameWorld(settings, dimension);
            settings.dimension = dimension;
            settings.buttons.put(tier.id(), new BlockSite(pos.getX(), pos.getY(), pos.getZ()));
        });
    }
    static void status(CommandSourceStack source, PracticeController control) {
        source.sendSuccess(() -> Component.literal("[훈련] " + (control.settings.enabled ? "활성" : "비활성") +
                " / " + (control.sessions.busy() ? "전투 진행 또는 보상 보관 중" : "대기 중")), false);
        source.sendSuccess(() -> Component.literal("[훈련 AI] " + PracticeTacticalBattleAI.status()), false);
        if (control.settings.exchange != null) source.sendSuccess(() -> Component.literal("[교환소] 중급 3 → 고급 1 / 고급 3 → 최고급 1 / 최고급 2 → 메가스톤 1 · " + PracticeExchange.catalog().size() + "종"), false);
        for (Tier tier : Tier.values()) {
            String state;
            try {
                var teams = control.files.readyTeams(tier);
                teams.forEach(NpcPartyFactory::validate);
                state = teams.size() + "개 준비됨";
            }
            catch (Exception error) { state = "준비 중: " + error.getMessage(); }
            String line = tier.label() + " - " + state;
            source.sendSuccess(() -> Component.literal(line), false);
        }
    }
    private static void sameWorld(PracticeSettings settings, String dimension) {
        if ((settings.spawn != null || !settings.buttons.isEmpty()) && !settings.dimension.equals(dimension))
            throw new IllegalArgumentException("NPC와 모든 버튼은 같은 차원에 등록해 주세요.");
    }
}
