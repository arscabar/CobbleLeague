package dev.cobbleleague.client
import com.google.gson.JsonObject
import dev.cobbleleague.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
class DungeonEquipmentScreen(data:JsonObject):DungeonCatalogScreen(data) {
    override val caption="파티 장비 관리"
    override val view="equipment"
    private val cw get()=(pw-30)/2
    private val rh get()=((ph-102)/3).coerceAtMost(58)
    private fun empty(c:JsonObject)=c.text("item").let{it.isBlank()||it=="minecraft:air"}
    override fun init(){
        cards().take(6).forEachIndexed{i,c->val x=px+12+i%2*(cw+6);val y=py+48+i/2*rh
            button(x+cw-89,y+rh-23,41,"해제",editable()&&!empty(c)){
                action("buy"){addProperty("operation","unequip");addProperty("pokemon",c.text("uuid"));addProperty("view",view)}
            }
            button(x+cw-44,y+rh-23,40,"교환",editable()&&c.text("uuid")!=targetId){purchase("swap",other=c.text("uuid"))}
        }
        val w=(pw-36)/3
        button(px+12,py+ph-54,w,"보관함 장착"){action("shop"){addProperty("view","items");addProperty("pokemon",targetId);addProperty("category",4)}}
        button(px+18+w,py+ph-54,w,"아이템 구매"){action("shop"){addProperty("view","items");addProperty("pokemon",targetId)}}
        button(px+24+w*2,py+ph-54,w,"전체 해제",editable()&&cards().any{!empty(it)}){purchase("unequip_all")}
        button(px+12,py+ph-28,pw-24,"준비 창으로 돌아가기"){action("status")}
    }
    override fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        text(g,short("대상을 누르고 다른 포켓몬의 교환 버튼 · 해제한 도구는 보관함으로",pw-24),px+12,py+27,0xB5E6E9)
        cards().take(6).forEachIndexed{i,c->val x=px+12+i%2*(cw+6);val y=py+48+i/2*rh
            g.fill(x,y,x+cw,y+rh-3,if(c.text("uuid")==targetId)0xDD365B70.toInt() else 0xCC1E3444.toInt())
            LeagueCards.portrait(g,c,x+18,y+18)
            text(g,short(LeagueCards.name(c),cw-42),x+36,y+5)
            val id=c.text("item","minecraft:air")
            if(!empty(c))g.renderItem(ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))),x+4,y+rh-23)
            text(g,short(if(empty(c))"장비 없음" else LeagueCards.item(id),cw-112),x+23,y+rh-17,0xFFE39A)
        }
    }
    override fun mouseClicked(x:Double,y:Double,b:Int):Boolean{
        if(super.mouseClicked(x,y,b))return true
        if(b==0)cards().take(6).forEachIndexed{i,c->val xx=px+12+i%2*(cw+6);val yy=py+48+i/2*rh
            if(x>=xx&&x<xx+cw&&y>=yy&&y<yy+rh-3){selectTarget(c.text("uuid"));return true}
        };return false
    }
}
