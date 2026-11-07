package dev.cobbleleague.client

import com.google.gson.JsonObject
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import dev.cobbleleague.LeaguePayload

object DungeonMenuKey {
    private val key=KeyMapping("key.cobbleleague.dungeon",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F4,"key.cobblemon.categories.cobblemon")
    private var lastToggle=0L
    private fun toggle(client:Minecraft) {
        if(client.player==null||!ClientPlayNetworking.canSend(LeaguePayload.TYPE))return
        if(LeagueClient.hasLeagueBattle&&DraftHud.data==null)return
        val now=System.currentTimeMillis();if(now-lastToggle<150)return;lastToggle=now
        if(client.screen is LeaguePane)client.setScreen(null)
        else LeagueClient.send(JsonObject().apply {addProperty("action","status")})
    }
    fun register() {
        KeyBindingHelper.registerKeyBinding(key)
        ClientTickEvents.END_CLIENT_TICK.register { client -> while(key.consumeClick()) {if(client.screen==null)toggle(client)} }
        ScreenEvents.AFTER_INIT.register { client,screen,_,_ ->
            ScreenKeyboardEvents.allowKeyPress(screen).register { _,code,scan,_ ->
                if(client.player!=null&&ClientPlayNetworking.canSend(LeaguePayload.TYPE)&&key.matches(code,scan)){toggle(client);false}else true
            }
        }
    }
}
