package dev.cobbleleague.client

import com.google.gson.JsonObject
import dev.cobbleleague.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** One purpose per window. Native party HUD remains visible on the left. */
abstract class LeaguePane(protected var data: JsonObject, val backScreen: Screen? = null) : Screen(Component.literal("CobbleLeague")) {
    protected open val caption = "리그"
    protected open val maximumWidth = 500
    protected open val maximumHeight = 260
    protected val pw get() = minOf(maximumWidth, width - if (width >= 360) 84 else 16)
    protected val ph get() = minOf(maximumHeight, height - 16)
    protected val px get() = if (width >= 360) maxOf(68, (width-pw)/2) else (width-pw)/2
    protected val py get() = (height-ph)/2
    val payload get() = data
    protected var error = ""
    protected fun action(name: String, body: JsonObject.() -> Unit = {}) {
        try { LeagueClient.send(JsonObject().apply { addProperty("action",name); addProperty("revision",data.number("revision",-1)); body() }) }
        catch(e:Exception) { showError(e.message ?: "요청에 실패했습니다.") }
    }
    protected fun button(x: Int,y: Int,w: Int,label: String,enabled: Boolean=true,click:()->Unit): Button =
        addRenderableWidget(LeagueButton(x,y,w,label) { click() }.also { it.active=enabled })
    fun showError(message: String) { error=message }
    open fun updateProgress(update: JsonObject) {
        listOf("money","rerolls","revision","confirmed","remainingSeconds","opponentReady","arenaWaiting","scores","mode","stage","health","selectionSeconds").forEach { key -> update[key]?.let { data.add(key,it.deepCopy()) } }
    }
    protected fun editable() = data.get("confirmed")?.asBoolean != true && DraftHud.remaining()!=0
    protected fun cards() = data.getAsJsonArray("cards")?.map { it.asJsonObject }.orEmpty()
    protected fun text(g:GuiGraphics,s:String,x:Int,y:Int,color:Int=0xFFFFFF) { g.drawString(font,s,x,y,color) }
    protected fun short(s:String,w:Int) = if(font.width(s)<=w)s else font.plainSubstrByWidth(s,(w-8).coerceAtLeast(0))+"…"
    protected fun wallet() = "${data.number("money")}원    리롤 ${data.number("rerolls")}회"
    protected fun timeLabel():String = when {
        data.text("stage")=="rewards" -> "전원 보상 확인 대기"
        data.text("mode")=="elimination"&&data.text("stage")=="building" -> "개인 선택방 생성 중"
        data.text("mode")=="elimination"&&data.text("stage")=="selection" -> if(data.number("selectionSeconds",-1)>=0)"선택 ${data.number("selectionSeconds")}초" else "선택 차례 대기"
        data.text("mode")=="elimination"&&data.text("stage")=="battles" -> "라운드 진행 중"
        data.text("mode")=="elimination"&&data.text("stage")=="results" -> "라운드 결과"
        data.get("arenaWaiting")?.asBoolean==true -> "경기장 대기 중"
        DraftHud.remaining()<0 -> "포켓몬 선택 중"
        else -> "준비 ${DraftHud.remaining()}초"
    }
    override fun render(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        LeagueSkin.panel(g,px,py,pw,ph)
        text(g,caption,px+12,py+10)
        drawContent(g,mx,my,delta)
        super.render(g,mx,my,delta)
        drawHints(g,mx,my)
        if(error.isNotBlank()) {
            val lines=font.split(Component.literal(error),pw-24).take(3)
            g.fill(px+8,py+ph-lines.size*10-8,px+pw-8,py+ph-3,0xF03A2020.toInt())
            lines.forEachIndexed { i,l -> g.drawString(font,l,px+12,py+ph-lines.size*10-5+i*10,0xFFB4A4) }
        }
    }
    protected open fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float) {}
    protected open fun drawHints(g:GuiGraphics,mx:Int,my:Int) {}
    override fun renderBlurredBackground(delta:Float) {}
    override fun renderMenuBackground(g:GuiGraphics) {}
    override fun isPauseScreen()=false
    override fun onClose() { minecraft?.setScreen(backScreen) }
}

/** Scroll a single list; no paging or hidden command buttons. */
internal class PaneScroll {
    var offset=0; private set
    fun reset(){offset=0}
    fun clamp(total:Int,visible:Int){offset=offset.coerceIn(0,(total-visible).coerceAtLeast(0))}
    fun wheel(amount:Double,total:Int,visible:Int){offset-= (amount*28).toInt();clamp(total,visible)}
    fun bar(g:GuiGraphics,x:Int,y:Int,h:Int,total:Int) {
        if(total<=h)return
        val thumb=(h.toDouble()*h/total).toInt().coerceAtLeast(12)
        val top=y+((h-thumb).toDouble()*offset/(total-h)).toInt()
        g.fill(x,y,x+3,y+h,0xFF182735.toInt());g.fill(x,top,x+3,top+thumb,0xFF86B7CB.toInt())
    }
}

internal fun scoreRows(data:JsonObject)=data.getAsJsonArray("scores")?.map { it.asJsonObject }.orEmpty()
internal fun drawScores(g:GuiGraphics,data:JsonObject,x:Int,y:Int,w:Int,font:net.minecraft.client.gui.Font) {
    val rows=scoreRows(data);val max=rows.maxOfOrNull { it.number("maximum",it.number("points")) }?.coerceAtLeast(1) ?: 1
    rows.take(4).forEachIndexed { i,row ->
        val yy=y+i*13;val points=row.number("points");val name=font.plainSubstrByWidth(row.text("name"),85)
        g.drawString(font,name,x,yy,0xE0E9EF)
        val bx=x+90;val bw=(w-124).coerceAtLeast(8)
        g.fill(bx,yy+2,bx+bw,yy+8,0xFF243746.toInt())
        g.fill(bx,yy+2,bx+(bw.toDouble()*points/max).toInt(),yy+8,if(i%2==0)0xFF4BBBC1.toInt() else 0xFFDC987C.toInt())
        g.drawString(font,if(row.has("health"))"$points/$max" else "${points}점",bx+bw+5,yy,0xFFFFFF)
    }
}
