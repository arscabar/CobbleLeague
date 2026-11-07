package dev.cobbleleague.client

import com.google.gson.JsonObject
import dev.cobbleleague.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

/** Preview first; a full roster requires an explicit replacement, never an automatic overwrite. */
class CreepRewardScreen(data:JsonObject):LeaguePane(data) {
    private val draftPick get()=data.get("draftPick")?.asBoolean==true
    override val caption get()=data.text("rewardLabel")+" · 영입 선택"
    override val maximumHeight=246
    private var replacement=""
    private fun offer()=data.getAsJsonObject("offer")
    private val full get()=cards().size>=6
    private val cardX get()=px+120
    private val cw get()=(pw-134)/2
    private val ch get()=((ph-121)/3).coerceAtMost(38)
    override fun init(){
        button(px+12,py+ph-80,pw-24,if(full)"선택한 포켓몬과 교체" else "빈 파티 자리에 영입",editable()&&(!full||replacement.isNotBlank())){
            if(draftPick)action("pick"){addProperty("pokemon",data.text("candidate"));addProperty("version",data.text("version"));addProperty("replace",replacement)}
            else action("reward_accept"){addProperty("token",data.text("token"));addProperty("replace",replacement)}
        }
        val w=(pw-30)/2
        button(px+12,py+ph-54,w,if(draftPick)"현재 파티 유지" else "이번 결과 포기") {
            if(draftPick)action("pick_skip"){addProperty("version",data.text("version"))}
            else action("reward_decline"){addProperty("token",data.text("token"))}
        }
        button(px+18+w,py+ph-54,w,if(draftPick)"다른 후보 보기" else "나중에 결정") {onClose()}
        button(px+12,py+ph-28,pw-24,"준비 화면으로") {onClose()}
    }
    override fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        val mon=offer()
        text(g,short(LeagueCards.name(mon),105),px+12,py+33,0xFFE39A)
        LeagueCards.portrait(g,mon,px+55,py+76)
        text(g,if(full)"교체 대상 선택" else "영입은 선택입니다",px+12,py+103,0xB5E6E9)
        cards().take(6).forEachIndexed{i,c->val x=cardX+i%2*cw;val y=py+31+i/2*ch
            g.fill(x,y,x+cw-3,y+ch-3,if(c.text("uuid")==replacement)0xDD46746E.toInt() else 0xCC1E3444.toInt())
            LeagueCards.portrait(g,c,x+13,y+20)
            text(g,short("${i+1}. ${LeagueCards.name(c)}",cw-30),x+27,y+9)
        }
    }
    override fun drawHints(g:GuiGraphics,mx:Int,my:Int){
        if(mx in px+10..px+112&&my in py+30..py+100)g.renderComponentTooltip(font,LeagueCards.hint(offer()).map(Component::literal),mx,my)
        cards().take(6).forEachIndexed{i,c->val x=cardX+i%2*cw;val y=py+31+i/2*ch;if(mx in x..x+cw-3&&my in y..y+ch-3)g.renderComponentTooltip(font,LeagueCards.hint(c).map(Component::literal),mx,my)}
    }
    override fun mouseClicked(mx:Double,my:Double,b:Int):Boolean{
        if(b==0&&full)cards().take(6).forEachIndexed{i,c->val x=cardX+i%2*cw;val y=py+31+i/2*ch;if(mx>=x&&mx<x+cw-3&&my>=y&&my<y+ch-3){replacement=c.text("uuid");rebuildWidgets();return true}}
        return super.mouseClicked(mx,my,b)
    }
    override fun onClose(){if(draftPick)action("pick_cancel"){addProperty("version",data.text("version"))}else action("status")}
}
