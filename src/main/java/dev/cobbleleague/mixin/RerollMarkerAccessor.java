package dev.cobbleleague.mixin;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(ArmorStand.class)
public interface RerollMarkerAccessor {
 @Invoker("setMarker") void cobbleleague$makeMarker(boolean marker);
}
