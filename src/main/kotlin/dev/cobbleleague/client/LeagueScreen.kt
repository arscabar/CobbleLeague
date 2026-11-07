package dev.cobbleleague.client

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.cobbleleague.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen

/** Small entry/result windows only. Preparation and shops have dedicated screens. */
class LeagueScreen(data:JsonObject,parent:Screen?=null):LeaguePane(data,parent) {
    override val caption get()=if(data.text("kind")=="result")"대전 결과" else "리그"
    override val maximumHeight=220
    override fun init(){
        if(data.text("kind")=="result") {
            button(px+12,py+ph-29,pw-24,"닫기"){onClose()}
        } else {
            button(px+12,py+61,pw-24,"진행 중인 던전 준비"){action("status")}
            button(px+12,py+89,pw-24,"리그 로비로 이동"){action("lobby");onClose()}
            button(px+12,py+117,pw-24,"리그 / 던전에서 나가기"){action("leave")}
            button(px+12,py+ph-29,pw-24,"닫기"){onClose()}
        }
    }
    override fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        if(data.text("kind")=="result") {
            text(g,short(data.text("verdict"),pw-24),px+12,py+34,0xFFE39A)
            val chart=JsonObject().apply {add("scores",JsonArray().also {a->for(i in 0..1)a.add(JsonObject().apply {addProperty("name",data.text("name$i"));addProperty("points",data.number("points$i"))})})}
            drawScores(g,chart,px+12,py+59,pw-24,font)
            for(i in 0..1)text(g,short("${data.text("name$i")} · ${data.text("record$i")}",pw-24),px+12,py+98+i*18,0xCFD9E1)
        } else text(g,short("상대를 보고 R → 대전에서 모드를 선택하세요.",pw-24),px+12,py+34,0xB5E6E9)
    }
}
