package dev.cobbleleague.client

import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattleChallenge
import com.cobblemon.mod.common.client.gui.interact.battleRequest.BattleConfigureGUI
import com.cobblemon.mod.common.client.gui.interact.battleRequest.BattleResponseButton
import com.cobblemon.mod.common.client.render.ClientPlayerIcon
import com.cobblemon.mod.common.net.messages.client.PlayerInteractOptionsPacket
import com.google.gson.JsonObject
import dev.cobbleleague.*
import dev.cobbleleague.mixin.BattleScreenAccessor
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import java.util.EnumMap
import java.util.UUID

/** Adapt league invitations into the real Cobblemon request inbox and BattleConfigureGUI. */
object NativeBattleInvites {
    private data class Invite(val id: UUID, val sender: UUID, val name: String, val mode: NativeLeagueMode,
                              val option: PlayerInteractOptionsPacket.Options, var awaiting: Boolean = false)
    private val invites = mutableMapOf<UUID, Invite>()
    private fun invite(screen: BattleConfigureGUI) =
        (screen as BattleScreenAccessor).`cobbleleague$getActiveRequest`()?.requestID?.let(invites::get)

    fun mode(screen: BattleConfigureGUI) = invite(screen)?.mode

    fun receive(data: JsonObject) {
        val client = Minecraft.getInstance()
        val id = UUID.fromString(data.text("id"))
        val sender = UUID.fromString(data.text("senderId"))
        val mode = requireNotNull(NativeLeagueMode.entries.firstOrNull { it.wireName == data.text("mode") })
        val type = LeagueFormat.parse(data.text("formatKey"))
        val option = when (type) {
            LeagueFormat.DOUBLE -> PlayerInteractOptionsPacket.Options.DOUBLE_BATTLE
            LeagueFormat.TRIPLE -> PlayerInteractOptionsPacket.Options.TRIPLE_BATTLE
            else -> PlayerInteractOptionsPacket.Options.SINGLE_BATTLE
        }
        val format = when (type) {
            LeagueFormat.DOUBLE -> BattleFormat.GEN_9_DOUBLES
            LeagueFormat.TRIPLE -> BattleFormat.GEN_9_TRIPLES
            else -> BattleFormat.GEN_9_SINGLES
        }.copy(adjustLevel = data.number("level", 50))
        val request = ClientBattleChallenge(id, sender, 60, format)
        invites[id] = Invite(id, sender, data.text("sender"), mode, option)
        // Preserve any unrelated ordinary request already owned by Cobblemon.
        if (CobblemonClient.requests.battleChallenges[sender] == null) {
            CobblemonClient.requests.battleChallenges[sender] = request
            ClientPlayerIcon.update(sender)
        }
        val options = EnumMap<PlayerInteractOptionsPacket.Options, PlayerInteractOptionsPacket.OptionStatus>(PlayerInteractOptionsPacket.Options::class.java)
        options[option] = PlayerInteractOptionsPacket.OptionStatus.AVAILABLE
        val packet = PlayerInteractOptionsPacket(options, sender, data.number("senderEntityId", -1),
            UUID.fromString(data.text("selectedPokemonId")))
        client.setScreen(BattleConfigureGUI(packet, request))
    }

    @JvmStatic fun prepare(screen: BattleConfigureGUI) {
        val i = invite(screen) ?: return
        val access = screen as BattleScreenAccessor
        // A saved team can be in the PC: do not hide its format using the physical party's size.
        access.`cobbleleague$getPacket`().options[i.option] = PlayerInteractOptionsPacket.OptionStatus.AVAILABLE
        access.`cobbleleague$setTargetName`(Component.literal(i.name).withStyle(net.minecraft.ChatFormatting.BOLD))
    }

    @JvmStatic fun decorate(screen: BattleConfigureGUI) {
        val i = invite(screen) ?: return
        Screens.getButtons(screen).filterIsInstance<BattleResponseButton>().forEachIndexed { index, button ->
            val label = if (index == 0) {
                if (i.mode.requiresSavedTeams) "수락 · 내 등록 덱 이름과 구성 목록 공유" else "대전 수락"
            } else "대전 거절"
            button.message = Component.literal(label)
            button.tooltip = Tooltip.create(Component.literal(label))
        }
    }

    @JvmStatic fun respond(screen: BattleConfigureGUI, id: UUID, accept: Boolean): Boolean {
        val i = invites[id] ?: return false
        if (invite(screen)?.id != id) return false
        if (i.awaiting) return true
        val client = Minecraft.getInstance()
        runCatching {
            LeagueClient.send(leagueChallengeResponse(id.toString(), i.mode, accept, QuickTeamsBridge::snapshot))
            i.awaiting = true
            client.setScreen(null)
        }.onFailure { client.player?.displayClientMessage(Component.literal(it.message ?: "대전 응답에 실패했습니다."), false) }
        return true
    }

    fun onError() { invites.values.forEach { it.awaiting = false } }

    fun remove(id: UUID, closeScreen: Boolean = true) {
        val i = invites.remove(id) ?: return
        val current = CobblemonClient.requests.battleChallenges[i.sender]
        if (current?.requestID == id) {
            CobblemonClient.requests.battleChallenges.remove(i.sender)
            ClientPlayerIcon.update(i.sender)
        }
        val client = Minecraft.getInstance()
        val screen = client.screen as? BattleConfigureGUI
        if (closeScreen && screen != null && (screen as BattleScreenAccessor).`cobbleleague$getActiveRequest`()?.requestID == id)
            client.setScreen(null)
    }
    fun clear() { invites.keys.toList().forEach { remove(it, false) } }
}
