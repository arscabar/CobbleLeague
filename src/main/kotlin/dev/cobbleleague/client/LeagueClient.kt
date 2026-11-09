package dev.cobbleleague.client

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.client.gui.battle.BattleGUI
import com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelOption
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.cobbleleague.*
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class LeagueClient : ClientModInitializer {
    override fun onInitializeClient() {
        RarityGlow.register()
        DraftHud.register()
        LobbyHud.register()
        ReadyStatusHud.register()
        ClientReleaseUpdate.register()
        DungeonMenuKey.register()
        DraftPartyBinding.register()
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            ReadyStatusHud.reset(); RoundGraphSkin.reset(); ClientStorageLock.reset(); DraftHud.data=null;LobbyHud.data=null; hasLeagueBattle=false; NativeBattleRules.reset(); NativeBattleInvites.clear()
            if (ClientPlayNetworking.canSend(LeaguePayload.TYPE)) send(JsonObject().apply { addProperty("action","hello"); addProperty("protocol",11) })
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> ReadyStatusHud.reset(); RoundGraphSkin.reset(); ClientStorageLock.reset(); DraftHud.data=null;LobbyHud.data=null; hasLeagueBattle=false; NativeBattleRules.reset(); NativeBattleInvites.clear() }
        ClientPlayNetworking.registerGlobalReceiver(LeaguePayload.TYPE) { payload, context ->
            context.client().execute {
                val data = JsonParser.parseString(payload.json).asJsonObject
                if(data.text("kind")=="ping") {
                    ReadyStatusHud.ping(data.text("sender"))
                } else if (data.text("kind") == "storage_lock") {
                    ClientStorageLock.setLocked(data.get("locked")?.asBoolean == true)
                } else if (data.text("kind") == "pc_denied") {
                    ClientStorageLock.setLocked(true); ClientStorageLock.notifyBlocked()
                } else if (data.text("kind") == "error") {
                    NativeBattleInvites.onError()
                    val leagueScreen = context.client().screen as? LeaguePane
                    if (leagueScreen != null) leagueScreen.showError(data.text("message"))
                    else context.client().player?.displayClientMessage(Component.literal(data.text("message")), false)
                } else if (data.text("kind") == "challenge") {
                    runCatching { NativeBattleInvites.receive(data) }.onFailure {
                        context.client().player?.displayClientMessage(Component.literal(it.message ?: "대전 신청을 표시하지 못했습니다."), false)
                    }
                } else if (data.text("kind") == "challenge_removed") {
                    NativeBattleInvites.remove(java.util.UUID.fromString(data.text("id")))
                    (context.client().screen as? DeckSelectScreen)?.takeIf { it.belongsToChallenge(data.text("id")) }?.let { context.client().setScreen(null) }
                } else if (data.text("kind") == "battle_started" || data.text("kind")=="watch_started") {
                    NativeBattleInvites.clear()
                    hasLeagueBattle=true; context.client().setScreen(null)
                } else if(data.text("kind")=="watch_stopped"){
                    hasLeagueBattle=false;context.client().setScreen(null)
                } else if (data.text("kind") == "close") {
                    DraftHud.data=null;LobbyHud.data=null; hasLeagueBattle=false; context.client().setScreen(null)
                } else if(data.text("kind")=="lobby_update") {
                    LobbyHud.update(data);(context.client().screen as? LobbyScreen)?.accept(data)
                    data.getAsJsonArray("players")?.let{(context.client().screen as? ReadyStatusScreen)?.accept(it)}
                } else if(data.text("kind")=="creep_progress") {
                    if(data.get("open")?.asBoolean==true){
                        hasLeagueBattle=data.get("inBattle")?.asBoolean==true
                        context.client().setScreen(CreepProgressScreen(data))
                    } else (context.client().screen as? CreepProgressScreen)?.accept(data)
                } else if (data.text("kind") == "run_update") {
                    DraftHud.update(data)
                    (context.client().screen as? LeaguePane)?.updateProgress(data)
                } else {
                    if (data.text("kind") == "decks") NativeBattleInvites.remove(java.util.UUID.fromString(data.text("id")), false)
                    else if (data.text("kind") == "run") NativeBattleInvites.clear()
                    if (data.text("kind")=="run") { LobbyHud.data=null;hasLeagueBattle=data.get("inBattle")?.asBoolean==true; DraftHud.update(data) }
                    else if (data.text("kind")=="result") { DraftHud.data=null; hasLeagueBattle=false }
                    else if(data.text("kind")=="round_result")hasLeagueBattle=false
                    else if(data.text("kind")=="lobby")LobbyHud.update(data)
                    val screen=when(data.text("kind")) {
                        "run" -> if(data.text("stage")=="rewards") GymRewardsScreen(data) else DungeonPrepareScreen(data)
                        "ready_status" -> ReadyStatusScreen(data)
                        "watch_list" -> SpectatorScreen(data)
                        "battle_control" -> BattleControlScreen(data)
                        "lobby" -> LobbyScreen(data)
                        "round_result" -> ScoreboardScreen(data)
                        "creep_reward" -> CreepRewardScreen(data)
                        "odds" -> DungeonOddsScreen(data)
                        "result" -> if(data.text("mode")=="elimination")ScoreboardScreen(data) else LeagueScreen(data)
                        "decks" -> DeckSelectScreen(data)
                        "shop" -> {
                            val previous=(context.client().screen as? CatalogSource)?.catalogScreen
                            if(previous!=null&&previous.view==data.text("view","items")&&previous.targetId==data.text("selected")) {
                                previous.acceptShop(data);previous
                            } else if(data.text("view")=="tms") DungeonTmShopScreen(data) else if(data.text("view")=="tera") DungeonTeraScreen(data) else if(data.text("view")=="equipment") DungeonEquipmentScreen(data) else DungeonItemShopScreen(data)
                        }
                        else -> LeagueScreen(data)
                    }
                    context.client().setScreen(screen)
                }
            }
        }
        ScreenEvents.AFTER_INIT.register { client, screen, width, height ->
            if (screen is BattleGUI && hasLeagueBattle && ClientPlayNetworking.canSend(LeaguePayload.TYPE)) {
                Screens.getButtons(screen).add(LeagueButton(width/2-104, 6, 100, "라운드 항복") { confirmSurrender(screen) })
                if (ClientStorageLock.isLocked) Screens.getButtons(screen).add(LeagueButton(width/2+4, 6, 100, "게임 항복") { confirmSurrender(screen,true) })
            }
        }
        // R on one's own Pokemon reopens progress without having to retarget the opponent.
        CobblemonEvents.POKEMON_INTERACTION_GUI_CREATION.subscribe { event ->
            if (ClientPlayNetworking.canSend(LeaguePayload.TYPE) && ClientStorageLock.isLocked) event.addFillingOption(InteractWheelOption(
                iconResource = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/interact/interact_wheel_icon_battle.png"),
                tooltipText = "cobbleleague.menu",
                onPress = { send(JsonObject().apply { addProperty("action", "status") }) }
            ))
        }
    }
    companion object {
        var hasLeagueBattle=false
        fun confirmSurrender(parent: Screen?, wholeGame: Boolean = false) {
            val client=Minecraft.getInstance()
            client.setScreen(LeagueConfirmScreen(parent, wholeGame) {
                runCatching { send(JsonObject().apply { addProperty("action",if(wholeGame)"surrender_game" else "surrender") }) }
                    .onFailure { client.player?.displayClientMessage(Component.literal(it.message ?: "항복 요청에 실패했습니다."), false) }
            })
        }
        fun send(message: JsonObject) {
            require(ClientPlayNetworking.canSend(LeaguePayload.TYPE)) { "서버에 CobbleLeague가 설치되어 있지 않습니다." }
            ClientPlayNetworking.send(LeaguePayload(message.toString()))
        }
    }
}
