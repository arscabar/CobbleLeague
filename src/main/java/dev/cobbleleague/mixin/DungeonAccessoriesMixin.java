package dev.cobbleleague.mixin;

import dev.cobbleleague.LeagueMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Supply session-only access; native config and once-per-battle checks still execute. */
@Pseudo
@Mixin(targets="com.github.yajatkaul.mega_showdown.utils.AccessoriesUtils",remap=false)
public abstract class DungeonAccessoriesMixin {
    @Inject(method="checkTagInAccessories",at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private static void cobbleleague$temporaryAccess(LivingEntity entity, TagKey<Item> tag, CallbackInfoReturnable<Boolean> cir){
        if(entity instanceof ServerPlayer player && tag.location().getNamespace().equals("mega_showdown")){
            var service=LeagueMod.Companion.getService();
            if(service!=null && service.battleAccessory(player,tag.location().getPath()))cir.setReturnValue(true);
        }
    }
}
