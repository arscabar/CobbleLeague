package dev.cobbleleague.client
import com.google.gson.JsonObject
import dev.cobbleleague.*
import net.minecraft.client.gui.GuiGraphics
class BattleControlScreen(data:JsonObject):LeaguePane(data) {
    override val caption="현재 대전 제어"
    override val maximumHeight=170
    override fun init(){
        button(px+12,py+62,pw-24,if(data.get("restartVoted")?.asBoolean==true)"상대의 재시작 동의 대기" else "이 대전 무효 후 다시 시작",data.get("restartVoted")?.asBoolean!=true){action("battle_restart"){addProperty("battleId",data.text("battleId"))}}
        button(px+12,py+88,pw-24,"이번 대전 항복"){LeagueClient.confirmSurrender(this)}
        button(px+12,py+ph-28,pw-24,"닫기"){onClose()}
    }
    override fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        text(g,"재시작 동의 ${data.number("restartVotes")} / ${data.number("battleParticipants")}명",px+12,py+29,0xFFE39A)
        text(g,short("두 사람 모두 동의하면 현재 대전만 다시 시작합니다.",pw-24),px+12,py+42)
    }
}
