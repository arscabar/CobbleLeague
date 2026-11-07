package ai.league.practice;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.level.block.ButtonBlock;

final class PracticeButtons {
    static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            var control = LeaguePractice.controller;
            if (control == null || !(player instanceof ServerPlayer serverPlayer) || hand != InteractionHand.MAIN_HAND)
                return InteractionResult.PASS;
            if (!world.dimension().location().toString().equals(control.settings.dimension)) return InteractionResult.PASS;
            var pos = hit.getBlockPos();
            for (Tier tier : Tier.values()) {
                BlockSite site = control.settings.buttons.get(tier.id());
                if (site == null || site.x() != pos.getX() || site.y() != pos.getY() || site.z() != pos.getZ()) continue;
                try {
                    if (!(world.getBlockState(pos).getBlock() instanceof ButtonBlock))
                        throw new IllegalStateException("등록된 위치에 버튼이 없습니다.");
                    if (player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 36)
                        throw new IllegalStateException("버튼 가까이에서 눌러 주세요.");
                    control.start(serverPlayer, tier);
                } catch (Exception error) {
                    serverPlayer.sendSystemMessage(Component.literal("[훈련] " + error.getMessage()));
                    LeaguePractice.LOG.debug("훈련 시작 거부", error);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }
}
