package dev.cobbleleague.client
import com.google.gson.JsonObject
import dev.cobbleleague.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen

class LobbyScreen(data:JsonObject):LeaguePane(data) {
    override val caption="던전 로비"
    override val maximumHeight=250
    override fun init(){
        val joined=data.get("joined")?.asBoolean==true;val ready=data.get("ready")?.asBoolean==true;val w=(pw-30)/2
        button(px+12,py+ph-57,w,if(!joined)"로비 입장" else if(ready)"대기 취소" else "대기 완료") {
            if(!joined)action("lobby_join")else action("lobby_ready"){addProperty("ready",!ready)}
        }
        button(px+18+w,py+ph-57,w,"로비 나가기",joined){action("lobby_leave")}
        button(px+12,py+ph-31,pw-24,"닫기"){onClose()}
        button(px+pw-87,py+7,75,"상태·핑"){action("ready_status")}
        if(false&&data.has("scores"))button(px+pw-87,py+7,75,"HP 점수판"){minecraft?.setScreen(ScoreboardScreen(data,this))}
    }
    fun accept(update:JsonObject){data=update;rebuildWidgets()}
    override fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        val members=data.getAsJsonArray("players")?.map{it.asJsonObject}.orEmpty();val done=members.count{it.get("ready")?.asBoolean==true}
        text(g,"${members.size}명 참가 · 대기 완료 $done/${members.size}",px+12,py+28,0xB5E6E9)
        val rh=((ph-128)/8).coerceAtMost(16)
        members.take(8).forEachIndexed{i,p->val y=py+47+i*rh;val r=p.get("ready")?.asBoolean==true
            text(g,p.text("name"),px+16,y)
            text(g,if(r)"대기 완료" else "준비 중",px+pw-75,y,if(r)0x8DDDAD else 0xD0CBC5)
        }
        val hint=if(data.get("busy")?.asBoolean==true)"진행 중인 게임 종료 후 시작합니다." else "2명 이상 · 전원이 대기 완료하면 시작"
        text(g,short(hint,pw-24),px+12,py+ph-75,0xFFE39A)
    }
}

