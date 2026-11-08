package dev.cobbleleague.client
import com.google.gson.JsonObject
import dev.cobbleleague.*
import net.minecraft.client.gui.GuiGraphics
class SpectatorScreen(data:JsonObject):LeaguePane(data) {
    override val caption="진행 중인 경기 관전"
    override val maximumHeight=274
    override fun init(){
        val battles=data.getAsJsonArray("battles")?.map{it.asJsonObject}.orEmpty()
        battles.take(8).forEachIndexed{i,b->button(px+12,py+42+i*22,pw-24,short("${b.text("kind")} · ${b.text("label")}",pw-36)){
            action("watch_battle"){addProperty("battleId",b.text("id"))}
        }}
        val w=(pw-30)/2
        button(px+12,py+ph-28,w,"관전 종료 · 원래 방으로",data.get("watching")?.asBoolean==true){action("watch_stop")}
        button(px+18+w,py+ph-28,w,"준비 창으로"){action("status")}
    }
    override fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float){text(g,"자기 전투가 없는 대기·완료 참가자만 관전할 수 있습니다.",px+12,py+27,0xB5E6E9)}
}
