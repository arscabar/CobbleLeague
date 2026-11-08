package dev.cobbleleague.client
import com.google.gson.JsonObject
import dev.cobbleleague.*
import net.minecraft.client.gui.GuiGraphics
class DungeonTeraScreen(data:JsonObject):DungeonCatalogScreen(data) {
    override val view="tera"
    override val caption="테라피스 · 테라타입 변경"
    override val maximumHeight=244
    private var selected=""
    private val names=mapOf("normal" to "노말","fire" to "불꽃","water" to "물","grass" to "풀","electric" to "전기","ice" to "얼음","fighting" to "격투","poison" to "독","ground" to "땅","flying" to "비행","psychic" to "에스퍼","bug" to "벌레","rock" to "바위","ghost" to "고스트","dragon" to "드래곤","dark" to "악","steel" to "강철","fairy" to "페어리","stellar" to "스텔라")
    private fun name(id:String)=names[id.substringAfter(':')]?:id
    override fun init(){
        button(px+12,py+41,pw-24,"대상: ${target()?.let(LeagueCards::name).orEmpty()} · 변경"){minecraft?.setScreen(DungeonTargetScreen(this))}
        val types=data.getAsJsonArray("teraTypes")?.map{it.asJsonObject}.orEmpty();val w=(pw-24)/7
        types.forEachIndexed{i,t->val id=t.text("id");button(px+12+i%7*w,py+84+i/7*22,w-2,name(id)) {selected=id;rebuildWidgets()}.message=net.minecraft.network.chat.Component.literal(name(id)).withStyle(if(selected==id)net.minecraft.ChatFormatting.AQUA else net.minecraft.ChatFormatting.WHITE)}
        val current=target()?.text("tera").orEmpty()
        button(px+12,py+ph-55,pw-24,"테라피스 사용 ${data.number("teraPrice",3)}원 · 잔액 ${(data.number("money")-data.number("teraPrice",3)).coerceAtLeast(0)}원",editable()&&selected.isNotBlank()&&selected!=current&&data.number("money")>=data.number("teraPrice",3)){
            action("buy"){addProperty("operation","tera");addProperty("pokemon",targetId);addProperty("tera",selected);addProperty("view",view)}
        }
        button(px+12,py+ph-29,pw-24,"장비 관리로"){action("shop"){addProperty("view","equipment");addProperty("pokemon",targetId)}}
    }
    override fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        text(g,wallet(),px+12,py+26,0xFFE39A)
        text(g,"현재 테라타입: ${name(target()?.text("tera").orEmpty())}",px+12,py+66,0xB5E6E9)
        text(g,if(selected.isBlank())"변경할 타입을 고르세요 · 해당 던전에서만 유지" else "${name(target()?.text("tera").orEmpty())} → ${name(selected)}",px+12,py+155,0xFFE39A)
    }
}
