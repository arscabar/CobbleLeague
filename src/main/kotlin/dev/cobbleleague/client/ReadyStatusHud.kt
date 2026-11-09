package dev.cobbleleague.client

import dev.cobbleleague.*
import com.google.gson.JsonObject
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

internal object ReadyStatusHud {
    private var pingText=""
    private var pingUntil=0L
    fun reset(){pingUntil=0;pingText=""}
    fun ping(sender:String){
        pingText="$sender 님이 확인을 요청했습니다";pingUntil=System.currentTimeMillis()+3000
        Minecraft.getInstance().player?.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,0.18f,1.4f)
    }
    fun register(){HudRenderCallback.EVENT.register{g,_->
        val c=Minecraft.getInstance()
        if(c.player!=null){
            val d=DraftHud.data?:LobbyHud.data
            if(d!=null && c.screen==null){
                val rows=d.getAsJsonArray("readyPlayers")?:d.getAsJsonArray("players")
                rows?.take(8)?.forEachIndexed{i,e->val row=e.asJsonObject
                    val ready=row.get("ready")?.asBoolean==true
                    val label="${if(ready)"●" else "○"} ${row.text("name")} · ${row.text("status",if(ready)"대기 완료" else "준비 중")}"
                    val x=(c.window.guiScaledWidth-c.font.width(label)-10).coerceAtLeast(4);val y=26+i*13
                    g.fill(x-4,y-2,c.window.guiScaledWidth-4,y+10,0xAA111722.toInt())
                    g.drawString(c.font,label,x,y,if(ready)0x95E6AB else 0xD5DCE4)
                }
            }
            if(System.currentTimeMillis()<pingUntil && d!=null){
                val x=(c.window.guiScaledWidth-c.font.width(pingText))/2
                g.fill(x-8,23,x+c.font.width(pingText)+8,43,0xDD173D50.toInt())
                g.drawString(c.font,pingText,x,29,0xB5E6E9)
            }
        }
    }}
}
class ReadyStatusScreen(data:JsonObject):LeaguePane(data){
    override val caption="참가자 준비 상태 · 핑"
    private val rowHeight get()=((ph-76)/8).coerceAtMost(23)
    fun accept(rows:com.google.gson.JsonArray){data.add("players",rows.deepCopy());rebuildWidgets()}
    override fun updateProgress(update:JsonObject){update.getAsJsonArray("readyPlayers")?.let{accept(it)}}
    override fun init(){
        data.getAsJsonArray("players")?.take(8)?.forEachIndexed{i,e->val row=e.asJsonObject
            button(px+pw-78,py+33+i*rowHeight,66,"핑 보내기"){action("ping"){addProperty("target",row.text("id"))}}
        }
        button(px+12,py+ph-30,pw-24,"준비 창으로"){action("status")}
    }
    override fun drawContent(g:net.minecraft.client.gui.GuiGraphics,mx:Int,my:Int,delta:Float){
        data.getAsJsonArray("players")?.take(8)?.forEachIndexed{i,e->val row=e.asJsonObject
            text(g,short("${row.text("name")} · ${row.text("status")}",pw-108),px+12,py+39+i*rowHeight,if(row.get("ready")?.asBoolean==true)0x95E6AB else 0xD5DCE4)
        }
    }
}
class TypedRerollScreen(data:JsonObject,back:net.minecraft.client.gui.screens.Screen):LeaguePane(data,back){
    override val caption="타입별 리롤 · 남은 ${data.number("rerolls")}회"
    override fun init(){
        val types=data.getAsJsonArray("rerollTypes")?.map{it.asString}.orEmpty()
        types.forEachIndexed{i,type->button(px+12+(i%3)*((pw-24)/3),py+35+(i/3)*24,(pw-30)/3,Component.translatable("cobblemon.type.$type").string,data.number("rerolls")>0){action("reroll"){addProperty("type",type);addProperty("version",data.text("version"))}}}
        button(px+12,py+ph-30,pw-24,"돌아가기"){onClose()}
    }
    override fun drawContent(g:net.minecraft.client.gui.GuiGraphics,mx:Int,my:Int,delta:Float){text(g,"선택 타입 우선 · 후보가 부족하면 다른 타입 포함",px+12,py+ph-48,0xB5E6E9)}
}

class DungeonQuickScreen(data:JsonObject,back:net.minecraft.client.gui.screens.Screen):LeaguePane(data,back){
    override val caption="던전 진행 · 참가자 · 리롤"
    override fun init(){
        val x=px+12;val w=pw-24
        button(x,py+38,w,"준비 상태 확인 · 참가자에게 핑"){action("ready_status")}
        button(x,py+65,w,"HP 변화 그래프"){minecraft?.setScreen(ScoreboardScreen(data,this))}
        button(x,py+92,w,"타입별 리롤 · ${data.number("rerolls")}회",data.get("ready")?.asBoolean!=true&&data.get("inBattle")?.asBoolean!=true&&data.number("rerolls")>0){minecraft?.setScreen(TypedRerollScreen(data,this))}
        if(data.text("trainer").isNotBlank())button(x,py+119,w,"관장전 진행 현황"){action("creep_status")}
        button(x,py+ph-30,w,"돌아가기"){onClose()}
    }
}
