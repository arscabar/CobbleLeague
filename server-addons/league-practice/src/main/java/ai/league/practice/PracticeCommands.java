package ai.league.practice;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import static net.minecraft.commands.Commands.literal;

final class PracticeCommands {
    @FunctionalInterface interface Action { void run(CommandSourceStack source, PracticeController control) throws Exception; }
    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            var root = literal("leaguepractice");
            root.then(literal("claim").executes(ctx -> run(ctx.getSource(), (s,c) -> c.rewards.claim(s.getPlayerOrException()))));
            root.then(literal("status").requires(s -> s.hasPermission(2)).executes(ctx -> run(ctx.getSource(), SetupActions::status)));
            root.then(literal("validate").requires(s -> s.hasPermission(2)).executes(ctx -> run(ctx.getSource(), (s,c) -> c.validatePresets(s))));
            root.then(literal("setspawn").requires(s -> s.hasPermission(2)).executes(ctx -> run(ctx.getSource(), SetupActions::spawn)));
            root.then(literal("reload").requires(s -> s.hasPermission(2)).executes(ctx -> run(ctx.getSource(), (s,c) -> c.reload())));
            for (boolean enabled : new boolean[]{true, false}) root.then(literal(enabled ? "enable" : "disable")
                    .requires(s -> s.hasPermission(2)).executes(ctx -> run(ctx.getSource(), (s,c) -> c.change(v -> v.enabled = enabled))));
            var bind = literal("bind").requires(s -> s.hasPermission(2));
            for (Tier tier : Tier.values()) bind.then(literal(tier.id()).executes(ctx ->
                    run(ctx.getSource(), (s,c) -> SetupActions.bind(s, c, tier))));
            root.then(bind);
            dispatcher.register(root);
        });
    }
    private static int run(CommandSourceStack source, Action action) {
        try {
            var control = LeaguePractice.controller;
            if (control == null) throw new IllegalStateException("훈련 기능을 불러오지 못했습니다. 서버 로그와 설정 파일을 확인해 주세요.");
            action.run(source, control);
            source.sendSuccess(() -> Component.literal("[훈련] 처리했습니다."), false);
            return 1;
        } catch (Exception error) { source.sendFailure(Component.literal("[훈련] " + error.getMessage())); return 0; }
    }
}
