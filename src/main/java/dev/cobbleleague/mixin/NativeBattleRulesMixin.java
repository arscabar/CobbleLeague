package dev.cobbleleague.mixin;

import com.cobblemon.mod.common.client.gui.interact.battleRequest.BattleConfigureGUI;
import dev.cobbleleague.client.NativeBattleRules;
import dev.cobbleleague.client.NativeBattleInvites;
import com.cobblemon.mod.common.client.requests.ClientPlayerActionRequest;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BattleConfigureGUI.class, remap = false)
public abstract class NativeBattleRulesMixin {
    @Inject(method = "init", at = @At("HEAD"), remap = true)
    private void cobbleleague$prepareInvite(CallbackInfo ci) {
        NativeBattleInvites.prepare((BattleConfigureGUI) (Object) this);
    }

    @Inject(method = "init", at = @At("TAIL"), remap = true)
    private void cobbleleague$updateChoices(CallbackInfo ci) {
        NativeBattleRules.prepare((BattleConfigureGUI) (Object) this);
        NativeBattleInvites.decorate((BattleConfigureGUI) (Object) this);
    }

    @Inject(method = "setCurrentPage", at = @At("TAIL"))
    private void cobbleleague$updateFormatChoices(CallbackInfo ci) {
        NativeBattleRules.prepare((BattleConfigureGUI) (Object) this);
    }

    @ModifyConstant(method = "render", constant = {@Constant(stringValue = "challenge.rule.anything_goes"), @Constant(stringValue = "challenge.rule.level")}, remap = true)
    private String cobbleleague$ruleCaption(String nativeKey) {
        return NativeBattleRules.caption((BattleConfigureGUI) (Object) this, nativeKey);
    }

    // Pinned to Cobblemon 1.7.3; contract tests check the real compiled callback and caption.
    @Inject(method = "init$lambda$4", at = @At("HEAD"), cancellable = true)
    private static void cobbleleague$challenge(BattleConfigureGUI screen, Button button, CallbackInfo ci) {
        if (NativeBattleRules.submit(screen)) ci.cancel();
    }

    @Inject(method = "init$lambda$2", at = @At("HEAD"), cancellable = true)
    private static void cobbleleague$accept(BattleConfigureGUI screen, ClientPlayerActionRequest request, Button button, CallbackInfo ci) {
        if (NativeBattleInvites.respond(screen, request.getRequestID(), true)) ci.cancel();
    }

    @Inject(method = "init$lambda$3", at = @At("HEAD"), cancellable = true)
    private static void cobbleleague$decline(BattleConfigureGUI screen, ClientPlayerActionRequest request, Button button, CallbackInfo ci) {
        if (NativeBattleInvites.respond(screen, request.getRequestID(), false)) ci.cancel();
    }

    @Inject(method = "render", at = @At("TAIL"), remap = true)
    private void cobbleleague$ruleTooltip(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        NativeBattleRules.tooltip((BattleConfigureGUI) (Object) this, g, mouseX, mouseY);
    }
}
