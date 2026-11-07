package dev.cobbleleague.mixin;

import com.cobblemon.mod.common.client.net.storage.pc.OpenPCHandler;
import dev.cobbleleague.client.ClientStorageLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OpenPCHandler.class, remap = false)
public abstract class OpenPCHandlerMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void cobbleleague$denyRemotePC(CallbackInfo callback) {
        if (ClientStorageLock.INSTANCE.isLocked()) {
            ClientStorageLock.INSTANCE.notifyBlocked();
            callback.cancel();
        }
    }
}
