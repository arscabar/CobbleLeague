package ai.league.practice.mixin;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Used only while constructing a fresh, unowned NPC preset, before it has an entity. */
@Mixin(value = Pokemon.class, remap = false)
public interface PracticeHeldItemAccessor {
    @Accessor("heldItem") void leaguePractice$initializeHeldItem(ItemStack stack);
}
