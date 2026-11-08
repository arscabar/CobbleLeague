package dev.cobbleleague.client

import com.cobblemon.mod.common.api.moves.Moves
import com.google.gson.JsonObject
import dev.cobbleleague.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

interface CatalogSource { val catalogScreen:DungeonCatalogScreen }

abstract class DungeonCatalogScreen(data:JsonObject):LeaguePane(data),CatalogSource {
    override val maximumHeight=320
    abstract val view:String
    override val catalogScreen get()=this
    val targetId get()=data.text("selected")
    protected fun target()=cards().firstOrNull {it.text("uuid")==targetId} ?: cards().firstOrNull()
    fun selectTarget(id:String){action("shop") {addProperty("view",view);addProperty("pokemon",id)}}
    fun purchase(operation:String,product:String="",move:String="",slot:Int=-1,ability:String="",other:String=""){
        action("buy"){addProperty("view",view);addProperty("pokemon",targetId);addProperty("operation",operation);addProperty("product",product);addProperty("move",move);addProperty("slot",slot);addProperty("ability",ability);addProperty("other",other)}
    }
    open fun acceptShop(update:JsonObject){data=update;rebuildWidgets()}
    protected fun buildHeader(){
        button(px+pw-89,py+7,77,"장비 관리"){action("equipment")}
        button(px+12,py+42,pw-24,"대상: ${target()?.let(LeagueCards::name).orEmpty()} Lv.${target()?.number("level") ?: 0} · 변경") {minecraft?.setScreen(DungeonTargetScreen(this))}
        button(px+12,py+ph-29,pw-24,"던전 준비로 돌아가기"){action("status")}
    }
    protected fun headerHint(g:GuiGraphics,mx:Int,my:Int):Boolean {
        if(mx !in px+12..px+pw-12 || my !in py+42..py+88)return false
        val mon=target() ?: return false
        val lines=LeagueCards.hint(mon)+listOf("능력치는 랭크 변화·도구 보정 전 실능력치입니다.","Lv.${mon.number("level")} · 성격: "+Component.translatable("cobblemon.nature.${mon.text("nature")}").string)
        g.renderTooltip(font,lines.flatMap { font.split(Component.literal(it),(width-24).coerceAtMost(280)) },mx,my)
        return true
    }
    protected fun drawHeader(g:GuiGraphics){
        target()?.let { mon ->
            val stats=mon.getAsJsonObject("stats")
            val names=listOf("HP","공격","방어","특공","특방","스피드")
            names.chunked(3).forEachIndexed { row, group ->
                text(g,group.joinToString("   ") { "$it ${stats?.get(it)?.asInt ?: 0}" },px+14,py+66+row*11,0xCFD9E1)
            }
        }
        text(g,wallet(),px+12,py+26,0xFFE39A)
        text(g,timeLabel(),px+pw-font.width(timeLabel())-12,py+26,0xB5E6E9)
    }
    override fun onClose(){action("status")}
}

class DungeonItemShopScreen(data:JsonObject):DungeonCatalogScreen(data) {
    override val caption="아이템 상점"
    override val view="items"
    private var category=data.number("category",0)
    private val scroll=PaneScroll()
    private val gridY get()=py+if(category==5)132 else 118
    private val gridH get()=(ph-(gridY-py)-37).coerceAtLeast(20)
    private val cellW get()=(pw-28)/3
    private val cellH=38
    private fun goods()=data.getAsJsonArray("products")?.map{it.asJsonObject}?.filter{category==0&&it.number("price")>0||category in 1..3&&it.number("price")==category||category==4&&it.number("owned")>0||category==5&&it.text("choice").isNotBlank()}.orEmpty()
    override fun init(){
        buildHeader()
        val labels=listOf("전체","1원","2원","3원","보관함","선택권","확률");val w=(pw-28)/7
        labels.forEachIndexed{i,label->button(px+12+i*w,py+94,w-2,if(category==i)"[$label]" else label){if(i==6)action("odds")else{category=i;scroll.reset();rebuildWidgets()}}}
    }
    override fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        drawHeader(g);val rows=goods();val total=((rows.size+2)/3)*cellH;scroll.clamp(total,gridH)
        if(category==5)text(g,short("Z 선택권 ${data.number("zChoices")}장 · 메가 선택권 ${data.number("megaChoices")}장 · 1장당 하나 선택",pw-24),px+12,py+118,0xFFE39A)
        g.enableScissor(px+10,gridY,px+pw-10,gridY+gridH)
        rows.forEachIndexed{i,p->val x=px+12+i%3*cellW;val y=gridY+i/3*cellH-scroll.offset
            if(y+cellH>=gridY&&y<=gridY+gridH){
                val hover=mx in x..x+cellW-3&&my in y..y+cellH-3
                g.fill(x,y,x+cellW-3,y+cellH-3,if(hover)0xDD365B70.toInt() else 0xCC1E3444.toInt())
                val item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(p.text("id")))
                g.renderItem(ItemStack(item),x+4,y+5)
                text(g,short(p.text("name").ifBlank{LeagueCards.item(p.text("id"))},cellW-29),x+24,y+5)
                text(g,if(category==5)"선택권 1장" else if(category==4)"보관 ${p.number("owned")}개" else "${p.number("price")}원",x+24,y+20,0xFFE39A)
            }
        }
        g.disableScissor();scroll.bar(g,px+pw-11,gridY,gridH,total)
        if(rows.isEmpty())text(g,"보관 중인 도구가 없습니다.",px+16,gridY+12,0xBCCBD6)
    }
    override fun drawHints(g:GuiGraphics,mx:Int,my:Int){if(headerHint(g,mx,my))return;val i=hit(mx.toDouble(),my.toDouble());if(i>=0){val p=goods()[i];g.renderTooltip(font,listOf(p.text("name").ifBlank{LeagueCards.item(p.text("id"))},DungeonShopDetails.item(p.text("id")),"${p.number("price")}원 · ${p.text("tier")} · 보관 ${p.number("owned")}개",if(p.get("consumable")?.asBoolean==true)"대상과 가격을 확인한 뒤 사용합니다." else "대상과 가격을 확인한 뒤 장착합니다.").flatMap { font.split(Component.literal(it),(width-24).coerceAtMost(280)) },mx,my)}}
    private fun hit(mx:Double,my:Double):Int{
        if(mx<px+12||mx>=px+pw-12||my<gridY||my>=gridY+gridH)return -1
        val col=(mx.toInt()-px-12)/cellW;val row=(my.toInt()-gridY+scroll.offset)/cellH
        val i=row*3+col;return if(col in 0..2&&i in goods().indices)i else -1
    }
    override fun mouseClicked(mx:Double,my:Double,b:Int):Boolean {val i=if(b==0)hit(mx,my)else -1;if(i>=0){minecraft?.setScreen(DungeonItemConfirmScreen(this,goods()[i],category==5));return true};return super.mouseClicked(mx,my,b)}
    override fun mouseScrolled(mx:Double,my:Double,h:Double,v:Double):Boolean{if(mx in px.toDouble()..(px+pw).toDouble()&&my in gridY.toDouble()..(gridY+gridH).toDouble()){scroll.wheel(v,((goods().size+2)/3)*cellH,gridH);return true};return super.mouseScrolled(mx,my,h,v)}
}

class DungeonItemConfirmScreen(
    private val root:DungeonItemShopScreen,
    private val product:JsonObject,
    private val redeem:Boolean
):LeaguePane(root.payload,root),CatalogSource {
    override val catalogScreen get()=root
    override val caption="도구 적용 확인"
    override val maximumHeight=244
    private var ability=""
    private val id get()=product.text("id")
    private val price get()=product.number("price")
    private fun target()=cards().firstOrNull{it.text("uuid")==root.targetId}
    private fun abilityName(id:String)=Component.translatable("cobblemon.ability.$id").string

    override fun init(){
        val canChoose=id==DungeonTraining.ABILITY
        val options=data.getAsJsonArray("abilities")?.map{it.asString}.orEmpty()
        if(canChoose && options.isNotEmpty()){
            val width=(pw-24)/options.size
            options.forEachIndexed{i,option->
                val name=(if(ability==option)"✓ " else "")+abilityName(option)
                button(px+12+i*width,py+95,width-2,short(name,width-9)){ability=option;rebuildWidgets()}
            }
        }
        val verb=if(product.get("consumable")?.asBoolean==true)"사용" else "장착"
        val enabled=editable() && (!canChoose || ability.isNotBlank())
        if(redeem){
            button(px+12,py+ph-103,pw-24,"선택권 1장 사용 · 잔액 ${data.number("money")}원",enabled){
                root.purchase("redeem",product=id)
            }
        }else{
            if(price>0)button(px+12,py+ph-103,pw-24,"${price}원 $verb · 잔액 ${(data.number("money")-price).coerceAtLeast(0)}원",
                enabled && data.number("money")>=price){root.purchase("buy",product=id,ability=ability)}
            if(product.number("owned")>0)button(px+12,py+ph-78,pw-24,"보관 도구 $verb · 잔액 ${data.number("money")}원",enabled){
                root.purchase("owned",product=id,ability=ability)
            }
        }
        val worn=target()?.text("item")?:"minecraft:air"
        button(px+12,py+ph-53,pw-24,"현재 장착 도구를 보관함으로",editable()&&worn!="minecraft:air"){root.purchase("unequip")}
        button(px+12,py+ph-28,pw-24,"상점으로 돌아가기"){onClose()}
    }

    override fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        g.renderItem(ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))),px+16,py+36)
        text(g,short(product.text("name").ifBlank{LeagueCards.item(id)},pw-64),px+43,py+39)
        val mon=target()
        text(g,short("적용 대상  ${mon?.let(LeagueCards::name).orEmpty()}",pw-24),px+12,py+61,0xB5E6E9)
        val preview=when(id){
            DungeonTraining.ABILITY -> "${abilityName(mon?.text("ability").orEmpty())} → ${if(ability.isBlank())"원하는 특성을 선택" else abilityName(ability)}"
            DungeonTraining.GOLD -> mon?.getAsJsonArray("ivs")?.joinToString("/"){it.asString}.orEmpty()+" → 31/31/31/31/31/31"
            else -> DungeonShopDetails.item(id)
        }
        text(g,short(preview,pw-24),px+12,py+78,0xFFE39A)
    }
}

class DungeonTmShopScreen(data:JsonObject):DungeonCatalogScreen(data) {
    override val caption get()="기술 학습 · ${if(data.number("freeLessons")>0)"무료 ${data.number("freeLessons")}회" else "${DungeonShop.TM_PRICE}원"} · ${moves().size}개"
    override val view="tms"
    private val scroll=PaneScroll()
    private var query=""
    private var search:EditBox?=null
    private val listY get()=py+119
    private val listH get()=(ph-156).coerceAtLeast(20)
    private val rowH=34
    private fun moves()=data.getAsJsonArray("tms")?.map{it.asString}?.filter{query.isBlank()||LeagueCards.move(it).contains(query,true)||it.contains(query,true)||data.getAsJsonObject("moveSources")?.get(it)?.asString?.contains(query,true)==true}.orEmpty().sortedBy(LeagueCards::move)
    override fun init(){
        buildHeader()
        search=addRenderableWidget(EditBox(font,px+12,py+94,pw-24,18,Component.literal("기술 검색")).also{
            it.value=query;it.setHint(Component.literal("기술 이름 · 알기술 · 자력기 검색"));it.setResponder{v->query=v.take(48);scroll.reset()}
        })
    }
    override fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        drawHeader(g);val rows=moves();val total=rows.size*rowH;scroll.clamp(total,listH)
        g.enableScissor(px+10,listY,px+pw-10,listY+listH)
        rows.forEachIndexed{i,id->val y=listY+i*rowH-scroll.offset;if(y+rowH>=listY&&y<=listY+listH){
            g.fill(px+12,y,px+pw-14,y+rowH-2,if(my in y..y+rowH-2&&mx in px+12..px+pw-14)0xDD365B70.toInt() else 0xCC1E3444.toInt())
            text(g,short(LeagueCards.move(id),pw-80),px+20,y+5)
            text(g,short(data.getAsJsonObject("moveSources")?.get(id)?.asString.orEmpty(),pw-62),px+20,y+19,0xB5E6E9)
            text(g,if(data.number("freeLessons")>0)"무료" else "${DungeonShop.TM_PRICE}원",px+pw-43,y+7,0xFFE39A)
        }}
        g.disableScissor();scroll.bar(g,px+pw-11,listY,listH,total)
        if(rows.isEmpty())text(g,"배울 수 있는 검색 결과가 없습니다.",px+16,listY+12,0xBCCBD6)
    }
    private fun hit(mx:Double,my:Double):Int{if(mx<px+12||mx>=px+pw-14||my<listY||my>=listY+listH)return -1;val i=(my.toInt()-listY+scroll.offset)/rowH;return if(i in moves().indices)i else -1}
    override fun drawHints(g:GuiGraphics,mx:Int,my:Int){if(headerHint(g,mx,my))return;val i=hit(mx.toDouble(),my.toDouble());if(i>=0){val move=Moves.getByName(moves()[i]);if(move!=null)g.renderTooltip(font,listOf(move.displayName.string,"${move.elementalType.displayName.string} · ${move.damageCategory.displayName.string}",move.description.string,"위력 ${move.power.toInt()} · 명중 ${if(move.accuracy<0) "필중" else move.accuracy.toInt().toString()} · PP ${move.pp}",data.getAsJsonObject("moveSources")?.get(move.name)?.asString.orEmpty(),"교체할 기술 한 칸을 선택해서 학습").flatMap { font.split(Component.literal(it),(width-24).coerceAtMost(280)) },mx,my)}}
    override fun mouseClicked(mx:Double,my:Double,b:Int):Boolean{val i=if(b==0)hit(mx,my)else -1;if(i>=0){minecraft?.setScreen(DungeonTmLearnScreen(this,moves()[i]));return true};return super.mouseClicked(mx,my,b)}
    override fun mouseScrolled(mx:Double,my:Double,h:Double,v:Double):Boolean{if(mx in px.toDouble()..(px+pw).toDouble()&&my in listY.toDouble()..(listY+listH).toDouble()){scroll.wheel(v,moves().size*rowH,listH);return true};return super.mouseScrolled(mx,my,h,v)}
}

class DungeonTmLearnScreen(private val root:DungeonTmShopScreen,private val moveId:String):LeaguePane(root.payload,root),CatalogSource {
    override val catalogScreen get()=root
    override val caption="기술 학습 확인"
    override val maximumHeight=230
    private var slot=-1
    private var confirm:Button?=null
    private fun mon()=cards().firstOrNull{it.text("uuid")==root.targetId}
    private fun cost()=if(data.number("freeLessons")>0)0 else DungeonShop.TM_PRICE
    override fun init(){
        val moves=mon()?.getAsJsonArray("moves")?.map{it.asString}.orEmpty();val w=(pw-30)/2
        for(i in 0..3)button(px+12+(i%2)*(w+6),py+78+(i/2)*26,w,(if(slot==i)"✓ " else "")+"${i+1}. "+(moves.getOrNull(i)?.let(LeagueCards::move)?:"빈 칸")){slot=i;rebuildWidgets()}
        confirm=button(px+12,py+ph-57,pw-24,"학습 ${cost()}원 · 잔액 ${(data.number("money")-cost()).coerceAtLeast(0)}원",slot>=0&&editable()&&data.number("money")>=cost()){root.purchase("tm",move=moveId,slot=slot)}
        button(px+12,py+ph-31,pw-24,"기술 목록으로"){onClose()}
    }
    override fun tick(){super.tick();confirm?.active=slot>=0&&editable()&&data.number("money")>=cost()}
    override fun drawContent(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        text(g,short(LeagueCards.move(moveId)+" · ${cost()}원",pw-24),px+12,py+31,0xFFE39A)
        text(g,short("대상: ${mon()?.let(LeagueCards::name).orEmpty()}",pw-24),px+12,py+47,0xB5E6E9)
        text(g,"교체할 기술 한 칸을 선택하세요.",px+12,py+63,0xCFD9E1)
        val old=mon()?.getAsJsonArray("moves")?.toList()?.getOrNull(slot)?.asString?.let(LeagueCards::move)?:"빈 칸"
        val preview=if(slot<0)"교체할 기술을 고르세요." else "$old → ${LeagueCards.move(moveId)}"
        text(g,short(preview,pw-24),px+12,py+137,0xFFE39A)
    }
}
