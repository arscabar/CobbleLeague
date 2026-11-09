package dev.cobbleleague.client

import com.google.gson.JsonObject
import dev.cobbleleague.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen

class DungeonPrepareScreen(data:JsonObject):LeaguePane(data) {
    override val caption get()=if(data.text("mode")=="elimination")"던전 · ${data.number("round")}라운드${if(data.text("trainer").isNotBlank())" · 관장전" else ""}" else "던전 준비 · ${data.number("round")}/12 라운드"
    override val maximumHeight=228
    private fun hasRewards()=data.number("mythicalTickets")>0||data.number("legendaryTickets")>0||data.text("pendingReward").isNotBlank()
    override fun init() {
        val gap=6;val bw=(pw-24-gap)/2;val shopWidth=(pw-24-2*gap)/3;val x=px+12;val y=py+86
        val inBattle=data.get("inBattle")?.asBoolean==true
        val confirmed=data.get("confirmed")?.asBoolean==true
        val canShop=data.get("canShop")?.asBoolean!=false
        val picked=data.get("ready")?.asBoolean==true
        val preparation=data.text("mode")!="elimination"||data.text("stage")=="preparation"
        button(px+pw-87,py+7,75,"진행·리롤"){minecraft?.setScreen(DungeonQuickScreen(data,this))}
        button(x,y,shopWidth,"장비·상점",cards().isNotEmpty()&&!inBattle&&!confirmed&&canShop) { action("equipment") }
        button(x+shopWidth+gap,y,shopWidth,"기술 학습",cards().isNotEmpty()&&!inBattle&&!confirmed&&canShop) { action("shop") { addProperty("view","tms") } }
        button(x+2*(shopWidth+gap),y,shopWidth,"뽑기 확률",!inBattle&&!confirmed){action("odds")}
        if(hasRewards()&&!inBattle&&data.number("watchCount")==0&&data.get("watching")?.asBoolean!=true){
            for((index,kind) in listOf(CreepRewardKind.MYTHICAL,CreepRewardKind.LEGENDARY).withIndex()){
                val pending=data.text("pendingReward");val count=data.number(if(kind==CreepRewardKind.MYTHICAL)"mythicalTickets" else "legendaryTickets")
                val label=if(pending==kind.id)if(index==0)"환상 뽑기 결과" else "전설 후보 보기" else if(index==0)"환상권 ${count}개" else "전설권 ${count}개"
                button(x+index*(bw+gap),y+25,bw,label,canShop&&!inBattle&&!confirmed&&(pending.isBlank()&&count>0||pending==kind.id)){
                    if(pending==kind.id)action("reward_view") else action("reward_draw"){addProperty("kind",kind.id)}
                }
            }
        }
        if(data.get("canSkipPick")?.asBoolean==true)button(x,if(hasRewards())y+50 else y+25,pw-24,"영입 건너뛰기 · 파티 유지"){action("pick_skip"){addProperty("version",data.text("version"))}}
        if(data.number("watchCount")>0||data.get("watching")?.asBoolean==true)button(x,y+25,pw-24,if(data.get("watching")?.asBoolean==true)"관전 중 · 경기 선택 / 종료" else "경기 관전 ${data.number("watchCount")}개"){action("watch_list")}
        if(inBattle)button(x,y+25,pw-24,if(data.get("restartVoted")?.asBoolean==true)"재시작 동의 대기 · ${data.number("restartVotes")}/${data.number("battleParticipants")}명" else "현재 대전만 무효 후 다시 시작",data.get("restartVoted")?.asBoolean!=true){
            action("battle_restart"){addProperty("battleId",data.text("battleId"))}
        }
        if(inBattle) button(x,py+ph-57,pw-24,"이번 대전 항복") { LeagueClient.confirmSurrender(this) }
        else if(data.text("stage")=="rewards")button(x,py+ph-57,pw-24,if(data.get("rewardReady")?.asBoolean==true)"보상 확인 완료" else "보상 확인 완료 / 나중에 사용",data.get("rewardReady")?.asBoolean!=true){action("reward_done")}
        else button(x,py+ph-57,pw-24,if(!preparation)"다른 참가자 진행 대기" else if(confirmed)"준비 취소" else "준비 완료",picked&&preparation) { action(if(confirmed)"unready" else "ready") }
        button(x,py+ph-31,bw,"던전 종료") { LeagueClient.confirmSurrender(this,true) }
        button(x+bw+gap,py+ph-31,bw,"맵으로 돌아가기") { onClose() }
    }
    override fun updateProgress(update:JsonObject) {
        val changed=data.number("revision")!=update.number("revision") || data.getAsJsonArray("cards")!=update.getAsJsonArray("cards") || data.text("stage")!=update.text("stage") || data["inBattle"]!=update["inBattle"] || data["ready"]!=update["ready"] || data["canSkipPick"]!=update["canSkipPick"] || data["canShop"]!=update["canShop"] || data["restartVotes"]!=update["restartVotes"] || data["battleId"]!=update["battleId"] || data["watchCount"]!=update["watchCount"] || data["watching"]!=update["watching"]
        data=update.deepCopy().apply { addProperty("kind","run") }
        if(changed)rebuildWidgets()
    }
    private fun inBattleView()=data.get("inBattle")?.asBoolean==true
    override fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        val balance=wallet()
        text(g,short(balance,pw-font.width(timeLabel())-32),px+12,py+27,0xFFE39A)
        text(g,timeLabel(),px+pw-font.width(timeLabel())-12,py+27,0xB5E6E9)
        val rows=scoreRows(data);val self=rows.firstOrNull{it.text("id")==data.text("playerId")};val other=rows.firstOrNull{it.text("id")==data.text("opponentId")}?:rows.firstOrNull{it!==self}
        val compact=data.deepCopy().apply{add("scores",com.google.gson.JsonArray().also{a->listOfNotNull(self,other).distinct().take(2).forEach{a.add(it)}})}
        drawScores(g,compact,px+12,py+47,pw-24,font)
        if(data.has("lossDamage"))text(g,"현재 ${data.number("health")}/${data.number("maximumHealth")}HP · 이번 패배 −${data.number("lossDamage")}HP",px+12,py+75,0xFFE39A)
        if(!inBattleView())text(g,"C: 파티 순서 / 선봉    F4: 준비 창",px+12,py+if(hasRewards() && data.get("canSkipPick")?.asBoolean==true)159 else if(hasRewards())137 else if(data.get("canSkipPick")?.asBoolean==true)137 else 119,0xB5E6E9)
        val instruction=when {
            data.text("mode")=="elimination"&&data.text("stage")=="building"->"참가자 수만큼 격리된 선택방을 준비합니다."
            data.text("stage")=="rewards"->"뽑기 결과를 보고 영입·교체·포기를 선택하세요."
            data.text("stage")=="results"->"라운드 결과를 확인하고 있습니다."
            data.text("stage")=="battles"&&data.text("trainer").isNotBlank()->"${data.text("trainer")} · ${if(data.get("creepDone")?.asBoolean==true)"내 관장전 완료 · 다른 참가자 대기" else "각자 격리된 방에서 동시 대전"}"
            data.text("mode")=="elimination"&&data.text("stage")=="battles"->if(data.get("bye")?.asBoolean==true)"이번 라운드 순환 대기 · 리롤은 동일 지급" else "다른 대전이 진행 중입니다. 잠시 기다려 주세요."
            data.text("mode")=="elimination"&&data.text("stage")=="selection"&&data.get("assigned")?.asBoolean!=true->"선택방 배정 순서를 기다려 주세요."
            cards().size>=6&&data.get("ready")?.asBoolean!=true->if(data.text("mode")=="elimination")"후보에 닿은 뒤 교체 대상을 선택하거나 영입을 건너뛰세요." else "C로 교체할 포켓몬을 6번 자리에 두세요."
            data.get("ready")?.asBoolean==true->"전원 준비 완료 또는 시간 종료 후 대전합니다."
            else->"선택방 포켓몬에 닿아 영입 · 옆 버튼으로 리롤"
        }
        if(!(hasRewards() && data.get("canSkipPick")?.asBoolean==true))text(g,short(instruction,pw-24),px+12,py+if(hasRewards() || data.get("canSkipPick")?.asBoolean==true)151 else 143,0xC1CBD5)
    }
}

/** Six choices fit one window; no cycling through individual Pokemon. */
class DungeonTargetScreen(private val catalog:DungeonCatalogScreen):LeaguePane(catalog.payload,catalog),CatalogSource {
    override val catalogScreen get()=catalog
    override val caption="적용할 포켓몬 선택"
    override fun init(){button(px+12,py+ph-29,pw-24,"취소") {onClose()}}
    private fun cellW()=(pw-30)/2
    private fun cellH()=((ph-75)/3).coerceAtMost(54)
    override fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float){cards().take(6).forEachIndexed {i,c->
        val x=px+12+(i%2)*(cellW()+6);val y=py+36+(i/2)*cellH()
        g.fill(x,y,x+cellW(),y+cellH()-4,if(mx in x..x+cellW()&&my in y..y+cellH()-4)0xCC38617A.toInt() else 0xBB1D3445.toInt())
        LeagueCards.portrait(g,c,x+21,y+27);text(g,short(LeagueCards.name(c),cellW()-48),x+43,y+12)
    }}
    override fun mouseClicked(x:Double,y:Double,button:Int):Boolean{
        if(button==0)cards().take(6).forEachIndexed {i,c->val xx=px+12+(i%2)*(cellW()+6);val yy=py+36+(i/2)*cellH();if(x>=xx&&x<xx+cellW()&&y>=yy&&y<yy+cellH()-4){catalog.selectTarget(c.text("uuid"));return true}}
        return super.mouseClicked(x,y,button)
    }
}
