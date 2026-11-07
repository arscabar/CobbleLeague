package dev.cobbleleague.client

import com.google.gson.JsonObject
import dev.cobbleleague.number
import dev.cobbleleague.text
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft

internal object DraftHud {
    var data: JsonObject? = null
    private var received = 0L
    fun update(value: JsonObject) { data = value; received = System.currentTimeMillis() }
    fun remaining(): Int {
        val seconds = data?.number("remainingSeconds", -1) ?: -1
        return if (seconds < 0) -1 else (seconds - (System.currentTimeMillis() - received).toInt() / 1000).coerceAtLeast(0)
    }
    fun register() {
        HudRenderCallback.EVENT.register { g, _ ->
            val value = data
            val client = Minecraft.getInstance()
            if (value != null && client.player != null && !LeagueClient.hasLeagueBattle && (client.screen == null || client.screen is com.cobblemon.mod.common.client.gui.summary.Summary)) {
                val time = if(value.text("stage")=="selection") {if(value.number("selectionSeconds",-1)>=0)"선택 ${value.number("selectionSeconds")}초" else "선택 차례 대기"} else remaining().let { if (it < 0) "라운드 대기" else "준비 ${it}초" }
                val health=if(value.has("health"))" · ${value.number("health")}점" else ""
                val text = "F4 준비$health · ${value.number("money")}원 · 리롤 ${value.number("rerolls")}회 · $time"
                g.fill(4, 4, 12 + client.font.width(text), 20, 0xAA111722.toInt())
                g.drawString(client.font, text, 8, 8, 0xFFFFFF)
            }
        }
    }
}
