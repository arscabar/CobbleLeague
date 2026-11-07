package dev.cobbleleague.client

import com.cobblemon.mod.common.client.gui.interact.battleRequest.BattleConfigureGUI
import com.google.gson.JsonObject
import dev.cobbleleague.*
import dev.cobbleleague.mixin.BattleScreenAccessor
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

/** Extend the actual R -> Battle rule carousel and its original challenge button. */
object NativeBattleRules {
    private fun access(screen: BattleConfigureGUI) = screen as BattleScreenAccessor
    private fun selectedOption(screen: BattleConfigureGUI): String? {
        val options = BattleScreenAccessor.`cobbleleague$getOptions`()
        return options.getOrNull(access(screen).`cobbleleague$getPage`())?.name
    }
    private fun format(screen: BattleConfigureGUI) = when (selectedOption(screen)) {
        "SINGLE_BATTLE" -> LeagueFormat.SINGLE
        "DOUBLE_BATTLE" -> LeagueFormat.DOUBLE
        "TRIPLE_BATTLE" -> LeagueFormat.TRIPLE
        else -> null
    }
    private fun selectedMode(screen: BattleConfigureGUI) = NativeLeagueMode.fromSelector(
        BattleConfigureGUI.levelRulesetOption.getOrNull(access(screen).`cobbleleague$getLevelIndex`()))

    @JvmStatic fun prepare(screen: BattleConfigureGUI) {
        val a = access(screen)
        val enabled = ClientPlayNetworking.canSend(LeaguePayload.TYPE) && format(screen) != null &&
            a.`cobbleleague$getActiveRequest`() == null && a.`cobbleleague$getActiveTeamRequest`() == null
        updateNativeRuleChoices(BattleConfigureGUI.levelRulesetOption, enabled)
        if (a.`cobbleleague$getLevelIndex`() !in BattleConfigureGUI.levelRulesetOption.indices)
            a.`cobbleleague$setLevelIndex`(0)
    }

    fun reset() = updateNativeRuleChoices(BattleConfigureGUI.levelRulesetOption, false)

    @JvmStatic fun caption(screen: BattleConfigureGUI, nativeKey: String): String =
        (NativeBattleInvites.mode(screen) ?: selectedMode(screen))?.translationKey ?: nativeKey

    /** Return true for every custom ID, including failures, so no ID reaches vanilla battle packets. */
    @JvmStatic fun submit(screen: BattleConfigureGUI): Boolean {
        val mode = selectedMode(screen) ?: return false
        val client = Minecraft.getInstance()
        runCatching {
            require(ClientPlayNetworking.canSend(LeaguePayload.TYPE)) { "서버에 CobbleLeague가 설치되어 있지 않습니다." }
            val battleFormat = requireNotNull(format(screen)) { "이 모드는 싱글·더블·트리플 대전에서 이용할 수 있습니다." }
            val teams = if (mode.requiresSavedTeams) QuickTeamsBridge.snapshot().also {
                require(!it.isEmpty) { "Quick Teams에 저장된 덱이 없습니다." }
            } else null
            LeagueClient.send(JsonObject().apply {
                addProperty("action", "challenge")
                addProperty("target", access(screen).`cobbleleague$getPacket`().targetId.toString())
                addProperty("mode", mode.wireName)
                addProperty("format", battleFormat.name)
                addProperty("level", 50)
                if (teams != null) add("teams", teams)
            })
            client.setScreen(null)
        }.onFailure { client.player?.displayClientMessage(Component.literal(it.message ?: "대전 신청에 실패했습니다."), false) }
        return true
    }

    @JvmStatic fun tooltip(screen: BattleConfigureGUI, g: GuiGraphics, mouseX: Int, mouseY: Int) {
        val mode = NativeBattleInvites.mode(screen) ?: selectedMode(screen) ?: return
        val client = Minecraft.getInstance()
        val left = (screen.width - 113) / 2
        val top = (screen.height - 113) / 2
        if (mouseX !in left + 10..left + 103 || mouseY !in top + 72..top + 85) return
        g.renderComponentTooltip(client.font, listOf(Component.translatable("cobbleleague.rule.${mode.wireName}.description"),
            Component.translatable("cobbleleague.rule.level50")), mouseX, mouseY)
    }
}
