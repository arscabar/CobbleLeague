package dev.cobbleleague.client

import com.cobblemon.mod.common.client.gui.pc.PCGUI
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/** UI feedback only. The authoritative storage lock is enforced independently on the server. */
object ClientStorageLock {
    var isLocked = false
        private set
    private var lastNoticeTick = -100

    fun setLocked(value: Boolean) {
        isLocked = value
        if (value && Minecraft.getInstance().screen is PCGUI) Minecraft.getInstance().setScreen(null)
    }
    fun reset() { isLocked = false; lastNoticeTick = -100 }
    fun notifyBlocked() {
        val player = Minecraft.getInstance().player ?: return
        if (player.tickCount - lastNoticeTick >= 40) {
            player.displayClientMessage(Component.literal("도전 중에는 PC 박스와 Quick Teams 파티 교체를 사용할 수 없습니다."), true)
            lastNoticeTick = player.tickCount
        }
    }
}
