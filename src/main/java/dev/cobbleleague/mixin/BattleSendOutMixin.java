package dev.cobbleleague.mixin;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.entity.pokemon.effects.IllusionEffect;
import dev.cobbleleague.BattleAnimationRecovery;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Mixin(Pokemon.class)
public abstract class BattleSendOutMixin {
    @Inject(method="sendOutWithAnimation", at=@At("RETURN"), remap=false)
    private void leagueWatchSendOut(LivingEntity source, ServerLevel world, Vec3 pos, UUID battleId,
            boolean cry, IllusionEffect illusion, Function1<? super PokemonEntity, Unit> mutation,
            CallbackInfoReturnable<CompletableFuture<PokemonEntity>> cir) {
        BattleAnimationRecovery.watchSendOut((Pokemon)(Object)this,world,pos,battleId,illusion,mutation,cir.getReturnValue());
    }
}
