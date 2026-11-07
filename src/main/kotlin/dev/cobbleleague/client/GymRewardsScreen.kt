package dev.cobbleleague.client

import com.google.gson.JsonObject
import dev.cobbleleague.*
import net.minecraft.client.gui.GuiGraphics

class GymRewardsScreen(data:JsonObject):LeaguePane(data) {
    override val caption get()="${data.number("round")}라운드 관장전 · 보상 확인"
    override val maximumHeight=252
    private fun ready()=data.get("rewardReady")?.asBoolean==true

    override fun init() {
        val width=(pw-30)/2
        val pending=data.text("pendingReward")
        button(px+12,py+89,width,if(pending=="mythical")"환상 결과 보기" else "환상권 ${data.number("mythicalTickets")}장",
            !ready() && (pending=="mythical" || pending.isBlank() && data.number("mythicalTickets")>0)){
            if(pending=="mythical")action("reward_view")
            else action("reward_draw"){addProperty("kind","mythical")}
        }
        button(px+18+width,py+89,width,"전설 후보 보기",!ready() && pending=="legendary"){action("reward_view")}
        button(px+12,py+113,width,"아이템·선택권",!ready()){action("shop"){addProperty("view","items")}}
        button(px+18+width,py+113,width,"기술 학습",!ready()){action("shop"){addProperty("view","tms")}}
        button(px+12,py+ph-52,pw-24,if(ready())"확인 완료 취소" else "보상 확인 완료 · 다음 라운드 대기"){
            action(if(ready())"reward_resume" else "reward_done")
        }
        button(px+12,py+ph-27,pw-24,"닫기 · F4로 다시 보기"){onClose()}
    }

    override fun updateProgress(update:JsonObject) {
        val redraw=listOf("rewardReady","pendingReward","mythicalTickets","revision").any{data[it]!=update[it]}
        data=update
        if(redraw)rebuildWidgets()
    }

    override fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        val reward=data.getAsJsonObject("gymReward")
        val summary=if(reward==null)"관장전 종료 · 보상 확인을 완료해 주세요." else "승리 보상 · ${reward.number("money")}원 · 환상권 1장 · 무료 학습 1회"
        text(g,short(summary,pw-24),px+12,py+26,0xFFE39A)
        if(reward!=null){
            text(g,short("공개된 전설 후보  ${reward.text("legendary")}",pw-24),px+12,py+38)
            text(g,short("획득 도구  ${reward.text("prize")}",pw-24),px+12,py+50)
            val crystal=reward.text("crystal")
            if(crystal.isNotBlank())text(g,short("Z크리스탈  ${LeagueCards.item(crystal)}",pw-24),px+12,py+62)
        }
        text(g,short("잔액 ${data.number("money")}원 · 무료 학습 ${data.number("freeLessons")}회 · Z 선택권 ${data.number("zChoices")}장 · 메가 선택권 ${data.number("megaChoices")}장",pw-24),px+12,py+74,0xB5E6E9)
        text(g,"보상 확인 완료 ${data.number("rewardReadyCount")} / ${data.number("participantCount")}명",px+12,py+139,0xB5E6E9)
        text(g,short("전원이 완료하면 함께 시작합니다. 남은 보상은 이후 사용할 수 있습니다.",pw-24),px+12,py+152)
    }
}
