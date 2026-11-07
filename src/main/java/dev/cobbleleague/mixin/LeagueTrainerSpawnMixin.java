package dev.cobbleleague.mixin;

import dev.cobbleleague.LeagueSpawnGuard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** RCT's direct world spawner does not use Cobblemon's natural-spawn event. */
@Mixin(ServerLevel.class)
public abstract class LeagueTrainerSpawnMixin {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void cobbleleague$blockAmbientTrainer(Entity entity, CallbackInfoReturnable<Boolean> result) {
        if (LeagueSpawnGuard.INSTANCE.rejectTrainerSpawn((ServerLevel)(Object)this, entity)) result.setReturnValue(false);
    }
}
