package dev.cobbleleague.client
import com.google.gson.JsonObject
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft

internal object LobbyHud {
    var data:JsonObject?=null
    fun update(value:JsonObject){data=if(value.get("joined")?.asBoolean==true)value else null}
    fun register(){HudRenderCallback.EVENT.register{g,_->val d=data;val c=Minecraft.getInstance()
        if(d!=null&&c.player!=null&&c.screen==null&&DraftHud.data==null){val list=d.getAsJsonArray("players");val ready=list?.count{it.asJsonObject.get("ready")?.asBoolean==true}?:0
            val text="F4 로비 · ${list?.size()?:0}명 · 대기 완료 $ready/${list?.size()?:0}"
            g.fill(4,4,12+c.font.width(text),20,0xAA111722.toInt());g.drawString(c.font,text,8,8,0xFFFFFF)
        }
    }}
}
