package ai.league.practice.mixin;

import ai.league.practice.PracticeBattleOutput;
import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GraalShowdownService.class, remap = false)
public abstract class PracticeBattleOutputMixin {
    @Inject(method = "sendFromShowdown", at = @At("HEAD"), cancellable = true, remap = false)
    private void leaguePractice$queueOutput(String battleId, String message, CallbackInfo ci) {
        if (PracticeBattleOutput.accept(battleId, message)) ci.cancel();
    }
}
