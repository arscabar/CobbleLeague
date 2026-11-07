package dev.cobbleleague.mixin;

import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import dev.cobbleleague.client.ClientStorageLock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blocks direct PCGUI openings as well as normal and portable PC requests. */
@Mixin(Minecraft.class)
public abstract class MinecraftPCScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void cobbleleague$denyPCScreen(Screen screen, CallbackInfo callback) {
        if (screen instanceof PCGUI && ClientStorageLock.INSTANCE.isLocked()) {
            ClientStorageLock.INSTANCE.notifyBlocked();
            callback.cancel();
        }
    }
}
