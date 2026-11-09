package dev.cobbleleague.mixin;

import dev.cobbleleague.LeagueMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.github.yajatkaul.mega_showdown.utils.PlayerUtils",remap=false)
public abstract class DungeonPowerSpotMixin {
    @Inject(method="isBlockNearby",at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private static void cobbleleague$temporaryPowerSpot(ServerPlayer player,TagKey<Block> tag,int radius,CallbackInfoReturnable<Boolean> cir){
        var service=LeagueMod.Companion.getService();
        if(service!=null && tag.location().getNamespace().equals("mega_showdown") && tag.location().getPath().equals("power_spot") && service.battleAccessory(player,"power_spot"))cir.setReturnValue(true);
    }
}
