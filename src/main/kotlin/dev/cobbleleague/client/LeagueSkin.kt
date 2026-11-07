package dev.cobbleleague.client

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/** Use the installed resource pack's Cobblemon textures; do not bundle copies. */
internal object LeagueSkin {
    private val panel = cobblemonResource("textures/gui/interact/request/battle_request.png")
    private val button = cobblemonResource("textures/gui/interact/request/button_request_battle.png")

    private fun slice(g: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, w: Int, h: Int,
                      u: Int, v: Int, sw: Int, sh: Int, tw: Int, th: Int) {
        if (w <= 0 || h <= 0) return
        g.pose().pushPose()
        g.pose().translate(x.toFloat(), y.toFloat(), 0F)
        g.pose().scale(w.toFloat() / sw, h.toFloat() / sh, 1F)
        g.blit(texture, 0, 0, u.toFloat(), v.toFloat(), sw, sh, tw, th)
        g.pose().popPose()
    }

    private fun frame(g: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, w: Int, h: Int,
                      sw: Int, sh: Int, left: Int, top: Int, right: Int, bottom: Int, v: Int = 0, th: Int = sh) {
        val sourceX = intArrayOf(0, left, sw - right, sw)
        val sourceY = intArrayOf(0, top, sh - bottom, sh)
        val targetX = intArrayOf(x, x + left, x + w - right, x + w)
        val targetY = intArrayOf(y, y + top, y + h - bottom, y + h)
        for (row in 0..2) for (col in 0..2) slice(g, texture,
            targetX[col], targetY[row], targetX[col + 1] - targetX[col], targetY[row + 1] - targetY[row],
            sourceX[col], sourceY[row] + v, sourceX[col + 1] - sourceX[col], sourceY[row + 1] - sourceY[row], sw, th)
    }

    fun panel(g: GuiGraphics, x: Int, y: Int, w: Int, h: Int) =
        frame(g, panel, x, y, w, h, 113, 126, 10, 30, 10, 46)

    fun button(g: GuiGraphics, x: Int, y: Int, w: Int, h: Int, hovered: Boolean) =
        frame(g, button, x, y, w, h, 69, 19, 3, 3, 3, 3, if (hovered) 19 else 0, 38)

    fun text(g: GuiGraphics, text: String, x: Int, y: Int, maxWidth: Int, color: Int = -1) =
        drawScaledText(g, font = CobblemonResources.DEFAULT_LARGE, text = Component.literal(text),
            x = x, y = y, centered = true, shadow = true, maxCharacterWidth = maxWidth, colour = color)
}

internal class LeagueButton(x: Int, y: Int, width: Int, text: String, onPress: OnPress) :
    Button(x, y, width, 19, Component.literal(text), onPress, DEFAULT_NARRATION) {
    override fun renderWidget(g: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        LeagueSkin.button(g, x, y, width, height, active && isHoveredOrFocused)
        LeagueSkin.text(g, message.string, x + width / 2, y + 5, width - 12, if (active) -1 else 0xFF999999.toInt())
    }
    override fun playDownSound(soundManager: SoundManager) {
        soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.PC_CLICK, 1F))
    }
}
