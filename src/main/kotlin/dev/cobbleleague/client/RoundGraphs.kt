package dev.cobbleleague.client

import com.google.gson.JsonObject
import dev.cobbleleague.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil

internal object RoundGraphSkin {
    private val rememberedSkins=mutableMapOf<UUID,net.minecraft.resources.ResourceLocation>()
    fun reset(){rememberedSkins.clear()}
    val colors=intArrayOf(0x56D7CE,0xFFB367,0xBD9CFF,0xFF829B,0x8FD772,0x79B9FF,0xF2DB76,0xD899DD)
    fun head(g:GuiGraphics,id:String,name:String,x:Int,y:Int,size:Int,color:Int){
        val uuid=runCatching{UUID.fromString(id)}.getOrNull()
        val skin=uuid?.let{Minecraft.getInstance().connection?.getPlayerInfo(it)?.skin}
        val texture=skin?.texture()?.also{if(uuid!=null)rememberedSkins[uuid]=it}?:rememberedSkins[uuid]
        if(texture!=null)PlayerFaceRenderer.draw(g,texture,x,y,size)
        else{g.fill(x,y,x+size,y+size,0xFF000000.toInt() or color);g.drawCenteredString(Minecraft.getInstance().font,name.take(1),x+size/2,y+(size-8)/2,0x132433)}
    }
    fun line(g:GuiGraphics,x0:Int,y0:Int,x1:Int,y1:Int,color:Int){
        var x=x0;var y=y0;val dx=abs(x1-x0);val sx=if(x0<x1)1 else -1;val dy=-abs(y1-y0);val sy=if(y0<y1)1 else -1;var error=dx+dy
        while(true){g.fill(x,y,x+2,y+2,color);if(x==x1&&y==y1)break;val e=2*error;if(e>=dy){error+=dy;x+=sx};if(e<=dx){error+=dx;y+=sy}}
    }
}

/** Completed global rounds only; live NPC defeat progress is a separate screen. */
class ScoreboardScreen(data:JsonObject,parent:Screen?=null):LeaguePane(data,parent) {
    override val caption get()=data.text("verdict","라운드별 HP 점수 변화")
    override val maximumHeight=300
    override fun init(){button(px+12,py+ph-29,pw-24,"닫기"){onClose()}}
    private fun rounds()=data.getAsJsonArray("rankHistory")?.map{it.asJsonObject}.orEmpty()
    private fun entry(round:JsonObject,id:String)=round.getAsJsonArray("players")?.firstOrNull{it.asJsonObject.text("id")==id}?.asJsonObject
    private fun cell(i:Int):Pair<Int,Int>{val columns=scoreRows(data).size.coerceIn(1,4);return px+12+i%columns*((pw-24)/columns) to py+ph-118+i/columns*39}
    override fun updateProgress(update:JsonObject){if(update.has("rankHistory")){data.add("rankHistory",update.get("rankHistory").deepCopy());data.add("scores",update.get("scores").deepCopy())}}
    override fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        text(g,"남은 던전 HP 점수 · 0점이면 탈락",px+12,py+28,0xB5E6E9)
        val rows=scoreRows(data).take(8);val history=rounds();val maximum=(rows.maxOfOrNull{it.number("maximum",10)}?:10).coerceAtLeast(1)
        val left=px+37;val right=px+pw-20;val top=py+47;val bottom=(py+ph-139).coerceAtLeast(top+25)
        fun yy(health:Int)=bottom-health.coerceIn(0,maximum)*(bottom-top)/maximum
        fun xx(index:Int)=if(history.size<=1)left else left+index*(right-left)/(history.size-1)
        val tick=ceil(maximum/5.0).toInt().coerceAtLeast(1)
        (listOf(0,maximum)+(0..maximum step tick)).distinct().forEach{value->val y=yy(value);g.fill(left,y,right+2,y+1,0xFF294352.toInt());text(g,"$value",px+12,y-3,0xA7BBC7)}
        val step=ceil(history.size/8.0).toInt().coerceAtLeast(1)
        history.forEachIndexed{i,r->if(i%step==0||i==history.lastIndex)text(g,if(r.number("round")==0)"시작" else "R${r.number("round")}",xx(i)-7,bottom+10,0xA7BBC7)}
        val cw=(pw-24)/rows.size.coerceIn(1,4)
        val focus=rows.indices.firstOrNull{i->val(x,y)=cell(i);mx in x until x+cw&&my in y until y+37}
        rows.forEachIndexed{i,row->
            val color=RoundGraphSkin.colors[row.number("colorIndex",i).coerceIn(0,7)];val argb=(if(focus==null||focus==i)0xFF000000.toInt() else 0x50000000) or color
            var previous:Pair<Int,Int>?=null
            history.forEachIndexed{j,r->entry(r,row.text("id"))?.let{e->val point=xx(j) to yy(e.number("health"));previous?.let{RoundGraphSkin.line(g,it.first,it.second,point.first,point.second,argb)};g.fill(point.first-1,point.second-1,point.first+3,point.second+3,argb);previous=point}}
            val(x,y)=cell(i);val center=x+cw/2;val current=history.lastOrNull()?.let{entry(it,row.text("id"))};val rank=row.number("rank",current?.number("rank")?:1)
            RoundGraphSkin.head(g,row.text("id"),row.text("name"),center-8,y,16,color)
            text(g,short(row.text("name"),cw-6),center-font.width(short(row.text("name"),cw-6))/2,y+18,color)
            val value=row.number("health",row.number("points"));val label=if(value<=0)"${rank}등 · 탈락" else "${rank}등 · HP $value/${row.number("maximum",maximum)}"
            text(g,label,center-font.width(label)/2,y+29,0xCFDCE3)
        }
        if(history.isEmpty())text(g,"첫 라운드가 끝나면 HP 변화가 표시됩니다.",left,top+10,0xCFDCE3)
    }
    override fun drawHints(g:GuiGraphics,mx:Int,my:Int){
        val rows=scoreRows(data);val cw=(pw-24)/rows.size.coerceIn(1,4)
        rows.take(8).forEachIndexed{i,row->val(x,y)=cell(i);if(mx in x until x+cw&&my in y until y+37)g.renderComponentTooltip(font,listOf(Component.literal(row.text("name")),Component.literal("이름 위에 마우스를 올리면 해당 선을 강조합니다.")),mx,my)}
    }
}

class CreepProgressScreen(data:JsonObject):LeaguePane(data) {
    override val caption get()="${data.number("round")}라운드 · ${data.text("trainer")} 관장전 현황"
    override val maximumHeight=300
    override fun init(){
        val w=(pw-30)/2
        button(px+12,py+ph-29,w,"HP 변화"){minecraft?.setScreen(ScoreboardScreen(data,this))}
        button(px+18+w,py+ph-29,w,if(data.get("inBattle")?.asBoolean==true)"전투로 돌아가기" else "닫기"){onClose()}
    }
    fun accept(update:JsonObject){data=update}
    override fun updateProgress(update:JsonObject){listOf("creepProgress","scores","rankHistory","inBattle").forEach{k->update[k]?.let{data.add(k,it.deepCopy())}}}
    override fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        val rows=data.getAsJsonArray("creepProgress")?.map{it.asJsonObject}.orEmpty()
        val done=rows.count{it.text("status") in setOf("win","loss","left")}
        text(g,"완료 $done/${rows.size}명 · 남은 ${rows.size-done}명",px+12,py+29,0xFFE39A)
        val overallWidth=pw-24;g.fill(px+12,py+42,px+12+overallWidth,py+47,0xFF253B4A.toInt());if(rows.isNotEmpty())g.fill(px+12,py+42,px+12+overallWidth*done/rows.size,py+47,0xFF56D7CE.toInt())
        val columns=if(rows.size>4)2 else 1;val cells=(rows.size+columns-1)/columns
        val cw=(pw-24-(columns-1)*6)/columns;val h=((ph-91)/cells.coerceAtLeast(1)).coerceAtMost(42)
        rows.take(8).forEachIndexed{i,row->
            val x=px+12+(i%columns)*(cw+6);val y=py+57+(i/columns)*h
            val colorIndex=scoreRows(data).firstOrNull{it.text("id")==row.text("id")}?.number("colorIndex",i)?.coerceIn(0,7)?:i
            val color=RoundGraphSkin.colors[colorIndex];val total=row.number("total",1).coerceAtLeast(1);val defeated=row.number("defeated").coerceIn(0,total)
            val left=x+103;val right=x+cw-6;val status=when(row.text("status")){"win"->"승리";"loss"->"패배";"left"->"퇴장";"waiting"->"준비 중";else->"전투 중 · ${row.number("turn")}턴"}
            RoundGraphSkin.head(g,row.text("id"),row.text("name"),x+36,y,12,color)
            text(g,short(row.text("name"),88),x+42-font.width(short(row.text("name"),88))/2,y+13,color)
            text(g,short(if(columns==1)"$status · ${defeated}/${total}마리" else "${defeated}/${total}마리 · $status",right-left),left,y,0xD9E4EA)
            g.fill(left,y+13,right,y+19,0xFF253B4A.toInt());g.fill(left,y+13,left+(right-left)*defeated/total,y+19,0xFF000000.toInt() or color)
        }
        text(g,"모두 끝나면 라운드 결과와 HP 변화가 표시됩니다.",px+12,py+ph-44,0xB5E6E9)
    }
}
