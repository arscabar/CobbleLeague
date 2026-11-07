package dev.cobbleleague.mixin;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Dungeon-only virtual equipment; no inventory item is created or consumed. */
@Mixin(value=Pokemon.class,remap=false)
public interface DungeonHeldItemAccessor {
 @Accessor("heldItem") void cobbleleague$setVirtualItem(ItemStack stack);
}
