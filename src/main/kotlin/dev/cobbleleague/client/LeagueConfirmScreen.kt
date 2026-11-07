package dev.cobbleleague.client

import com.cobblemon.mod.common.client.gui.interact.battleRequest.BattleResponseButton
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** Same accept/decline controls as Cobblemon's battle request and forfeit UI. */
internal class LeagueConfirmScreen(private val parent: Screen?, private val wholeGame: Boolean,
                                   private val confirmed: () -> Unit) : Screen(Component.literal("항복 확인")) {
    private val panelWidth get() = (width - 16).coerceAtMost(276)
    private val left get() = (width - panelWidth) / 2
    private val top get() = (height - 132) / 2
    override fun init() {
        addRenderableWidget(BattleResponseButton(width / 2 - 35, top + 58, true) { confirmed(); onClose() }
            .also { it.message = Component.literal("항복 확인") })
        addRenderableWidget(BattleResponseButton(width / 2, top + 58, false) { onClose() }
            .also { it.message = Component.literal("취소") })
    }
    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        LeagueSkin.panel(g, left, top, panelWidth, 132)
        LeagueSkin.text(g, if (wholeGame) "게임 항복" else "라운드 항복", width / 2, top + 4, panelWidth - 28)
        LeagueSkin.text(g, "항복하시겠습니까?", width / 2, top + 17, panelWidth - 28)
        LeagueSkin.text(g, if (wholeGame) "도전 종료 · 상대 최종 승리" else "이번 라운드 패배", width / 2, top + 37, panelWidth - 28)
        LeagueSkin.text(g, if (wholeGame) "임시 파티가 삭제됩니다." else "던전: 상대 3점 · 다음 포켓몬 선택", width / 2, top + 95, panelWidth - 28)
        super.render(g, mouseX, mouseY, delta)
    }
    override fun renderBlurredBackground(delta: Float) {}
    override fun renderMenuBackground(g: GuiGraphics) {}
    override fun isPauseScreen() = false
    override fun onClose() { minecraft?.setScreen(parent) }
}
