package dev.cobbleleague.client

import com.google.gson.JsonObject
import dev.cobbleleague.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

class GymRewardsScreen(data:JsonObject):LeaguePane(data) {
    override val caption get()="${data.number("round")}라운드 관장전 · 보상 선택"
    override val maximumHeight=254
    private fun ready()=data.get("rewardReady")?.asBoolean==true
    private fun offer()=data.getAsJsonObject("gymReward")
    private fun label(prize:GymPrize):String {
        val r=offer()?:return prize.label
        return when(prize){
            GymPrize.MONEY->"${r.number("money")}원"
            GymPrize.LEGENDARY->"전설 · ${r.text("legendary")}"
            GymPrize.EQUIPMENT->r.text("prize")
            GymPrize.CRYSTAL->LeagueCards.item(r.text("crystal"))
            else->prize.label
        }
    }
    override fun init() {
        val r=offer();val selected=r?.text("selected").orEmpty();val w=(pw-30)/2
        val options=r?.getAsJsonArray("options")?.map{GymPrize.valueOf(it.asString)}.orEmpty()
        options.forEachIndexed{i,prize->
            val picked=selected==prize.name
            button(px+12,py+54+i*23,pw-24,short((if(picked)"✓ " else "")+label(prize),pw-36),!ready()&&selected.isBlank()){
                action("gym_reward_choose"){addProperty("token",r!!.text("token"));addProperty("prize",prize.name)}
            }
        }
        val pending=data.text("pendingReward")
        button(px+12,py+128,w,if(pending.isNotBlank())"포켓몬 결과 보기" else "환상권 사용",!ready()&&(pending.isNotBlank()||data.number("mythicalTickets")>0)){
            if(pending.isNotBlank())action("reward_view")else action("reward_draw"){addProperty("kind","mythical")}
        }
        button(px+18+w,py+128,w,"상점 · 학습 · 보관함",!ready()){action("shop"){addProperty("view",if(selected==GymPrize.LESSON.name)"tms" else "items")}}
        button(px+12,py+ph-52,pw-24,if(ready())"확인 완료 취소" else "보상 확인 완료 · 다음 라운드 대기",ready()||r==null||selected.isNotBlank()){
            action(if(ready())"reward_resume" else "reward_done")
        }
        button(px+12,py+ph-27,pw-24,"닫기 · F4로 다시 보기"){onClose()}
    }
    override fun updateProgress(update:JsonObject) {
        val redraw=listOf("gymReward","rewardReady","pendingReward","mythicalTickets","revision").any{data[it]!=update[it]}
        data=update;if(redraw)rebuildWidgets()
    }
    override fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        val r=offer();val selected=r?.text("selected").orEmpty()
        text(g,if(r==null)"관장전 종료 · 전원 확인 후 함께 진행합니다." else if(selected.isBlank())"돈 + 무작위 보상 2개 중 하나를 선택하세요." else "선택 완료 · ${short(label(GymPrize.valueOf(selected)),pw-100)}",px+12,py+26,0xFFE39A)
        text(g,short("${wallet()} · 무료 학습 ${data.number("freeLessons")}회",pw-24),px+12,py+39,0xB5E6E9)
        text(g,"보상 확인 완료 ${data.number("rewardReadyCount")} / ${data.number("participantCount")}명",px+12,py+153,0xB5E6E9)
        text(g,short("선택하지 않은 보상은 지급되지 않습니다.",pw-24),px+12,py+165)
    }
    override fun drawHints(g:GuiGraphics,mx:Int,my:Int) {
        val options=offer()?.getAsJsonArray("options")?.map{GymPrize.valueOf(it.asString)}.orEmpty();val w=(pw-30)/2
        options.forEachIndexed{i,prize->val x=px+12;val y=py+54+i*23
            if(mx in x until x+pw-24&&my in y until y+20)g.renderTooltip(font,Component.literal(label(prize)+" · 다른 보상과 중복 수령할 수 없습니다."),mx,my)
        }
    }
}
