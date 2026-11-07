package dev.cobbleleague.client

import com.google.gson.JsonObject
import dev.cobbleleague.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

class DeckSelectScreen(data:JsonObject):LeaguePane(data) {
    override val caption get()=if(data.text("mode")=="borrow")"빌려 쓸 덱 선택" else "상대가 사용할 덱 선택"
    override val maximumWidth=560
    private val scroll=PaneScroll()
    private val decks get()=data.getAsJsonArray("decks")?.map{it.asJsonObject}.orEmpty()
    private val listW get()=(pw/3).coerceAtLeast(82)
    private val listY get()=py+40
    private val listH get()=(ph-80).coerceAtLeast(24)
    private val rowH=24
    private fun preview()=decks.firstOrNull{it.text("id")==data.text("selectedDeck")}?:decks.firstOrNull()
    fun belongsToChallenge(id:String)=data.text("id")==id
    override fun init(){
        val confirmed=data.get("confirmed")?.asBoolean==true
        button(px+12,py+ph-29,pw-126,if(confirmed)"선택 확정 · 상대 대기" else "선택한 덱으로 확정",!confirmed&&data.text("selectedDeck").isNotBlank()) {action("deck_confirm"){addProperty("id",data.text("id"))}}
        button(px+pw-108,py+ph-29,96,"신청 취소"){action("decline"){addProperty("id",data.text("id"))}}
    }
    override fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        text(g,short("${data.text("owner")}님의 등록 덱 · 목록을 클릭해 확인",pw-24),px+12,py+26,0xB5E6E9)
        val total=decks.size*rowH;scroll.clamp(total,listH)
        g.enableScissor(px+10,listY,px+12+listW,listY+listH)
        decks.forEachIndexed{i,d->val y=listY+i*rowH-scroll.offset;if(y+rowH>=listY&&y<=listY+listH){
            g.fill(px+12,y,px+listW+8,y+rowH-2,if(d.text("id")==data.text("selectedDeck"))0xDD386277.toInt() else 0xCC1E3444.toInt())
            text(g,short(d.text("name"),listW-12),px+17,y+8)
        }}
        g.disableScissor();scroll.bar(g,px+listW+8,listY,listH,total)
        val x=px+listW+20;val w=(pw-listW-36)/3;val h=((listH-20)/2).coerceAtMost(66)
        val cards=preview()?.getAsJsonArray("cards")?.map{it.asJsonObject}.orEmpty()
        cards.take(6).forEachIndexed{i,c->val xx=x+i%3*w;val yy=listY+i/3*h
            g.fill(xx,yy,xx+w-3,yy+h-3,0xAA1B303E.toInt())
            LeagueCards.portrait(g,c,xx+w/2,yy+32)
            text(g,short(LeagueCards.name(c),w-6),xx+3,yy+h-14)
        }
        if(cards.isEmpty())text(g,"왼쪽에서 덱을 선택하세요.",x,listY+15,0xCFD9E1)
    }
    override fun drawHints(g:GuiGraphics,mx:Int,my:Int){
        if(mx in px+12..px+listW+8&&my in listY..listY+listH){val i=(my-listY+scroll.offset)/rowH;if(i in decks.indices)g.renderComponentTooltip(font,listOf(Component.literal(decks[i].text("name"))),mx,my)}
        val x=px+listW+20;val w=(pw-listW-36)/3;val h=((listH-20)/2).coerceAtMost(66);val cards=preview()?.getAsJsonArray("cards")?.map{it.asJsonObject}.orEmpty()
        cards.take(6).forEachIndexed{i,c->val xx=x+i%3*w;val yy=listY+i/3*h;if(mx in xx..xx+w-3&&my in yy..yy+h-3)g.renderComponentTooltip(font,LeagueCards.hint(c).map(Component::literal),mx,my)}
    }
    override fun mouseClicked(mx:Double,my:Double,b:Int):Boolean{
        if(b==0&&mx>=px+12&&mx<px+listW+8&&my>=listY&&my<listY+listH&&data.get("confirmed")?.asBoolean!=true){val i=(my.toInt()-listY+scroll.offset)/rowH;if(i in decks.indices){action("deck"){addProperty("id",data.text("id"));addProperty("deck",decks[i].text("id"))};return true}}
        return super.mouseClicked(mx,my,b)
    }
    override fun mouseScrolled(mx:Double,my:Double,h:Double,v:Double):Boolean{if(mx>=px+10&&mx<=px+pw-10&&my>=listY&&my<=listY+listH){scroll.wheel(v,decks.size*rowH,listH);return true};return super.mouseScrolled(mx,my,h,v)}
}
