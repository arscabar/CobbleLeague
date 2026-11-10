package dev.cobbleleague.client
import com.google.gson.JsonObject
import dev.cobbleleague.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import java.util.Locale

class DungeonOddsScreen(data:JsonObject):LeaguePane(data){
    override val caption="뽑기 확률 · 후보 1마리 기준"
    private fun rows()=data.getAsJsonArray("drawOdds")?.map{it.asJsonObject}.orEmpty()
    private val rowHeight get()=((ph-116)/6).coerceAtMost(23)
    override fun init(){
        rows().forEachIndexed{i,row->if(row.text("id")!="normal"){
            button(px+pw-122,py+62+i*rowHeight,110,"1원 +${row.number("boost")/100.0}%p",editable()&&data.get("boostAvailable")?.asBoolean==true&&data.number("money")>=1&&data.get("oddsUsed")?.asBoolean!=true&&(rows().firstOrNull()?.number("basisPoints",0)?:0)>=row.number("boost")){action("odds_boost"){addProperty("category",row.text("id"))}}
        }}
        button(px+12,py+ph-28,pw-24,"던전 준비로 돌아가기"){action("status")}
    }
    override fun updateProgress(update:JsonObject){if(update.has("drawOdds")){val pool=data.getAsJsonObject("pool")?.deepCopy();data=update.deepCopy();if(!data.has("pool")&&pool!=null)data.add("pool",pool);rebuildWidgets()}}
    override fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        text(g,"보유 ${data.number("money")}원 · 라운드당 1회",px+12,py+28,0xFFE39A)
        val status=if(data.get("boostAvailable")?.asBoolean!=true)"선택 단계 전용" else if(data.get("oddsUsed")?.asBoolean==true)"강화 사용 완료" else "강화 가능";text(g,status,px+pw-font.width(status)-12,py+28,0xB5E6E9)
        data.getAsJsonObject("pool")?.let{pool->text(g,short("코블버스 서버 데이터 · 등록 ${pool.number("implemented")}종 / 던전 후보 ${pool.number("species")}종 · ${pool.number("forms")}폼",pw-24),px+12,py+43,0xBCCBD5)}
        rows().forEachIndexed{i,row->val y=py+62+i*rowHeight
            text(g,row.text("label"),px+12,y+5)
            text(g,String.format(Locale.ROOT,"%.1f%%",row.number("basisPoints")/100.0),px+pw-180,y+5,0xFFE39A)
        }
        text(g,short("현재 던전 누적 · 다음 생성·리롤부터 적용",pw-24),px+12,py+ph-49,0xB5E6E9)
    }
    override fun drawHints(g:GuiGraphics,mx:Int,my:Int){
        rows().forEachIndexed{i,row->val y=py+62+i*rowHeight;if(mx in px+12..px+pw-126&&my in y..y+rowHeight){
            val detail=when(row.text("id")){"normal"->"메가진화 가능한 일반 종의 기본형을 포함합니다.";"sublegendary"->"제한 전설이 아닌 전설·울트라비스트 분류입니다.";"mega"->"일반 종의 메가 후보입니다. 기본 폼 + 해당 메가 폼에 맞는 메가스톤을 지급합니다. 전설류 메가는 제외합니다.";else->"이 분류에서 종을 고른 뒤 폼을 고릅니다."}
            g.renderComponentTooltip(font,listOf(Component.literal(detail),Component.literal("선택 분류 +${row.number("boost")/100.0}%p · 일반에서 동일 차감")),mx,my)
        }}
    }
    override fun onClose(){action("status")}
}
