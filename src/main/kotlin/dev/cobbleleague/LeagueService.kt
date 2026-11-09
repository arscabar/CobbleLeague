package dev.cobbleleague

import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore
import com.cobblemon.mod.common.api.storage.pc.link.PCLinkManager
import com.cobblemon.mod.common.battles.*
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty
import com.cobblemon.mod.common.util.party
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.core.BlockPos
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.abs
import com.cobblemon.mod.common.api.moves.Moves
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import dev.cobbleleague.mixin.DungeonHeldItemAccessor

private data class Challenge(
    val id: UUID, val sender: UUID, val receiver: UUID, val mode: String, val format: LeagueFormat,
    val level: Int, var expires: Long, val senderDecks: List<SavedDeck>,
    var receiverDecks: List<SavedDeck> = emptyList(), var accepted: Boolean = false,
    var selection: DeckSelection? = null
)
private class Run(val player: UUID, val format: LeagueFormat, val returnPoint: ReturnPoint, roundLimit:Int=12,startingOdds:DrawOdds=DrawOdds()) {
    val id = UUID.randomUUID()
    val state = DraftState(format.slots,roundLimit)
    val party = PlayerPartyStore(player)
    val preparation = DraftPreparation()
    val authorizedMoves = mutableMapOf<UUID, List<String>>()
    val equipment = mutableMapOf<String, Int>()
    var lastOrder = emptyList<UUID>()
    var originalParty: PlayerPartyStore? = null
    var originalIds = emptyList<UUID?>()
    var choices: List<String> = emptyList()
    val recentOffers=ArrayDeque<List<String>>()
    var room = -1
    var replacementSlot = -1
    var pendingPick:String?=null
    var armed = false
    var opponent: UUID? = null
    var pairId = id
    var waiting = false
    var lastButtonPress = -100
    var lobbyGame:UUID?=null
    val rewardWallet=CreepRewardWallet()
    val drawOdds=DrawOddsBoosts(startingOdds)
    var pendingRewardPokemon:Pokemon?=null
    var gymReward:GymRewardSummary?=null
    val displays = mutableListOf<Pair<PokemonEntity, Vec3>>()
    val choiceLabels=mutableListOf<net.minecraft.world.entity.Display.TextDisplay>()
    val displayMegaForms = mutableMapOf<UUID, RandomRoster.Candidate>()
    val partyMegaForms = mutableMapOf<UUID, RandomRoster.Candidate>()
}
private data class GymRewardSummary(val legendary:String,val money:Int,val prize:String,val crystal:String?,val legendaryKey:String,val prizeId:String,val choice:GymRewardChoice)
private data class Match(
    val id: UUID, val battle: com.cobblemon.mod.common.api.battles.model.PokemonBattle,
    val players: List<UUID>, val arena: Int, val origins: Map<UUID, ReturnPoint>, val run: UUID? = null,
    val createdAt: Int,
    var finishedAt: Long? = null, var surrenderedBy: UUID? = null, var surrenderedAt: Int? = null,
    val creepKind:DungeonRoundKind?=null, val battleCenter:Pair<Int,Int>?=null,
    val restartVotes:BattleRestartVote=BattleRestartVote(players.toSet()),val restartFailed:Boolean=false
)
private data class WatchVisit(val battle:UUID,val origin:ReturnPoint,val anchor:Vec3,val invulnerable:Boolean)
private enum class LobbyGamePhase { BUILDING, SELECTION, PREPARATION, BATTLES, RESULTS, REWARDS }
private data class ActiveCreep(val player:UUID,val matchId:UUID,val trainer:GymTrainer,val handle:CreepNpcHandle)
private class LobbyGameRuntime(val id:UUID,val model:EliminationGame,val names:Map<UUID,String>) {
    var phase=LobbyGamePhase.SELECTION
    val selections=ArrayDeque<UUID>()
    val selectionEnds=mutableMapOf<UUID,Long>()
    val duels=ArrayDeque<LeagueDuel>()
    var current:LeagueDuel?=null
    var resultEnds=0L
    val creepQueue=ArrayDeque<UUID>()
    val currentCreeps=linkedMapOf<UUID,ActiveCreep>()
    var trainer:GymTrainer?=null
    val creepOutcomes=mutableMapOf<UUID,String>()
    val creepDefeated=mutableMapOf<UUID,Int>()
    val history=RankHistory().also{it.record(0,model.standings.mapValues{it.value.health})}
    val offeredThisRound=mutableSetOf<String>()
    var rewardRound=0
    val rewardReady=mutableSetOf<UUID>()
}

class LeagueService(private val server: MinecraftServer) {
    private val dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("cobbleleague", "league"))
    private val arenas = ArenaPool(1)
    private val centers = listOf(0 to 38)
    private val legacyRooms=listOf(-42 to -50,42 to -50)
    private var rooms=legacyRooms
    private val privateRooms=PrivateSelectionRooms()
    private val challenges = mutableMapOf<UUID, Challenge>()
    private val runs = mutableMapOf<UUID, Run>()
    private val matches = mutableMapOf<UUID, Match>()
    private val lastRequest = mutableMapOf<UUID, Long>()
    private val watchVisits=mutableMapOf<UUID,WatchVisit>()
    private val pingTimes=mutableMapOf<UUID,Int>()
    private val clientProtocols = mutableMapOf<UUID, Int>()
    private val displayIds = mutableSetOf<UUID>()
    private val lastPcNotice = mutableMapOf<UUID, Int>()
    private val duelOriginalParties = mutableMapOf<UUID, List<UUID?>>()
    private val returnFile = server.getWorldPath(LevelResource.ROOT).resolve("cobbleleague-returns.json")
    private val returns = runCatching { ReturnJournal(returnFile) }.onFailure {
        LeagueMod.LOGGER.error("Return journal unavailable; new league entries are disabled and original file retained", it)
    }.getOrNull()
    private val pendingReturns = mutableSetOf<UUID>()
    private val pendingCompletions = mutableSetOf<UUID>()
    private val roster = RandomRoster()
    private val lobbyQueue=ReadyLobby()
    private val lobbyOptOut=mutableSetOf<UUID>()
    private var lobbyGame:LobbyGameRuntime?=null
    private val lobbyBalance=LobbyBalance.load()
    private val creepNpcs=CreepNpcBridge()
    fun ownsDisplay(id: UUID) = id in displayIds
    fun draftParty(id: UUID): PlayerPartyStore? = runs[id]?.party
    fun mayReorderDraft(p: ServerPlayer): Boolean {
        val run = runs[p.uuid] ?: return true
        val allowed = !run.state.inBattle && run.preparation.editable(System.currentTimeMillis())
        if (!allowed) note(p, "전투 중이거나 준비가 확정되었습니다. 준비를 취소한 뒤 순서를 바꾸세요.")
        return allowed
    }
    fun rejectDraftMoveChange(p: ServerPlayer) { note(p, "던전 기술은 상점에서 구매한 기술머신으로만 교체할 수 있습니다."); runs[p.uuid]?.party?.let { syncVisibleParty(p, it) } }
    fun isDraftPokemon(mon: Pokemon) = runs.values.any { run -> run.party.any { it.uuid == mon.uuid } }
    fun battleAccessory(p:ServerPlayer,tag:String):Boolean {
        val run=runs[p.uuid]?:return false
        if(!leagueArea(p)||!run.state.inBattle)return false
        return when(tag){
            "mega_bracelet"->run.party.any{!it.heldItem().isEmpty&&DungeonForms.isMegaStone(it.heldItem().item)&&DungeonForms.eligible(it.heldItem().item,it)}
            "dynamax_band","power_spot"->run.party.any{it.dmaxLevel>0||it.gmaxFactor}
            else->false
        }
    }
    fun leagueArea(p:ServerPlayer)=p.level().dimension()==dimension
    fun rescueLeaguePlayer(p:ServerPlayer){
        val m=matches.values.firstOrNull{p.uuid in it.players}
        val center=m?.let{it.battleCenter?:centers[it.arena]} ?: runs[p.uuid]?.let{rooms.getOrNull(it.room)} ?: (0 to -100)
        teleport(p,center.first+0.5,center.second+0.5)
    }
    fun protectedSelectionArea(p:ServerPlayer)=p.level().dimension()==dimension&&(0..7).any{privateRooms.contains(it,p.x,p.y,p.z)}
    private fun participantInside(p:ServerPlayer,run:Run):Boolean =
        p.level().dimension()==dimension&&(watchVisits.containsKey(p.uuid)||insideLeague(true,p.x,p.y,p.z)||run.lobbyGame!=null&&privateRooms.contains(lobbyGame?.model?.standings?.keys?.indexOf(p.uuid)?:-100,p.x,p.y,p.z))
    fun useRerollControl(p:ServerPlayer,pos:BlockPos):net.minecraft.world.InteractionResult? {
        if(p.level().dimension()!=dimension || rooms.none{DungeonRerollControl.position(it)==pos})return null
        val run=runs[p.uuid]
        if(run==null || run.room !in rooms.indices || DungeonRerollControl.position(rooms[run.room])!=pos) {
            note(p,"자신의 선택방 리롤 버튼을 사용하세요.");return net.minecraft.world.InteractionResult.FAIL
        }
        if(p.position().distanceToSqr(Vec3.atCenterOf(pos))>64)return net.minecraft.world.InteractionResult.FAIL
        val state=p.serverLevel().getBlockState(pos)
        if(!state.`is`(Blocks.STONE_BUTTON))return net.minecraft.world.InteractionResult.FAIL
        if(server.tickCount-run.lastButtonPress<5 || state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED))return net.minecraft.world.InteractionResult.CONSUME
        return try {
            reroll(p,JsonObject().apply {addProperty("version",run.state.version);addProperty("revision",run.preparation.revision)},false)
            run.lastButtonPress=server.tickCount
            net.minecraft.world.InteractionResult.PASS
        } catch(e:IllegalArgumentException) {note(p,e.message?:"지금은 리롤할 수 없습니다.");net.minecraft.world.InteractionResult.FAIL}
    }
    fun prepareBattleForms(battle:com.cobblemon.mod.common.api.battles.model.PokemonBattle) { /* Held stones and native battle limits determine transformations. */ }
    fun isStorageLocked(id: UUID) = id in runs || id in duelOriginalParties
    private fun syncStorageLock(p: ServerPlayer, locked: Boolean) {
        if (locked) PCLinkManager.removeLink(p.uuid)
        LeagueMod.send(p, packet("storage_lock") { addProperty("locked", locked) })
    }
    fun rejectPcAccess(p: ServerPlayer) {
        PCLinkManager.removeLink(p.uuid)
        val previous = lastPcNotice[p.uuid]
        if (previous == null || server.tickCount - previous >= 40) {
            lastPcNotice[p.uuid] = server.tickCount
            LeagueMod.send(p, packet("pc_denied"))
            note(p, "도전 중에는 PC 박스와 Quick Teams 파티 교체를 사용할 수 없습니다.")
        }
    }

    private fun player(id: UUID) = server.playerList.getPlayer(id)
    private fun attempt(label: String, operation: () -> Unit): Boolean = try { operation(); true }
        catch (e: Exception) { LeagueMod.LOGGER.error("League recovery step failed: $label", e); false }
    private fun note(p: ServerPlayer, text: String) { attempt("message ${p.uuid}") { p.sendSystemMessage(Component.literal("[리그] $text")) } }
    private fun packet(kind: String, body: JsonObject.() -> Unit = {}) = JsonObject().apply { addProperty("kind", kind); body() }
    private fun removeChallenge(id: UUID) {
        val c = challenges.remove(id) ?: return
        listOf(c.sender, c.receiver).mapNotNull(::player).forEach {
            LeagueMod.send(it, packet("challenge_removed") { addProperty("id", id.toString()) })
        }
    }
    private fun point(p: ServerPlayer) = ReturnPoint(p.level().dimension().location().toString(), p.x, p.y, p.z, p.yRot, p.xRot)
    private fun remember(points: Map<UUID, ReturnPoint>) = requireNotNull(returns) { "귀환 기록 파일을 읽지 못해 새 대전을 시작할 수 없습니다. 운영자 확인이 필요합니다." }.record(points)
    private fun teleport(p: ServerPlayer, x: Double, z: Double, yaw: Float = 0f) {
        p.teleportTo(requireNotNull(server.getLevel(dimension)), x, 65.0, z, yaw, 0f)
        p.fallDistance = 0f
        check(p.level().dimension() == dimension && p.position().distanceToSqr(Vec3(x,65.0,z)) < 4) { "리그장 이동에 실패했습니다." }
    }
    private fun restore(p: ServerPlayer, pos: ReturnPoint) {
        val key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(pos.dimension))
        pos.validate()
        val target = server.getLevel(key)
        val world = target ?: server.overworld()
        val spawn = world.sharedSpawnPos
        val x = if (target != null) pos.x else spawn.x + 0.5
        val z = if (target != null) pos.z else spawn.z + 0.5
        val y = if (target != null) pos.y else world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.x, spawn.z).toDouble()
        p.teleportTo(world, x, y, z, pos.yaw, pos.pitch)
        p.fallDistance = 0f
        check(p.level() == world && p.position().distanceToSqr(Vec3(x,y,z)) < 4) { "귀환 이동이 완료되지 않았습니다." }
    }
    private fun restoreRecorded(p: ServerPlayer, point: ReturnPoint, complete: Boolean = true) {
        pendingReturns.add(p.uuid)
        if (complete) performRecordedReturn(returns,p.uuid) {
            restore(p,point)
            pendingReturns.remove(p.uuid); pendingCompletions.add(p.uuid)
        }
        else restore(p,point)
        pendingReturns.remove(p.uuid)
        pendingCompletions.remove(p.uuid)
    }
    private fun ensureMap() {
        val world = requireNotNull(server.getLevel(dimension)) { "배틀 맵 데이터팩을 설치하고 서버를 다시 시작하세요." }
        require(world.getBlockState(BlockPos(0, 64, 0)).`is`(Blocks.SEA_LANTERN)) { "운영자가 /function cobbleleague:install 로 배틀 맵을 먼저 생성해야 합니다." }
    }
    private fun available(p: ServerPlayer) {
        require(p.isAlive && !p.isSpectator) { "현재 상태에서는 입장할 수 없습니다." }
        require(p.uuid !in pendingReturns && p.uuid !in pendingCompletions) { "이전 귀환 기록을 정리 중입니다. 잠시 후 다시 시도하세요." }
        require(BattleRegistry.getBattleByParticipatingPlayer(p) == null && matches.values.none { p.uuid in it.players }) { "진행 중인 전투를 먼저 끝내세요." }
        require(p.uuid !in duelOriginalParties) { "원래 파티 복귀 검사를 먼저 완료해야 합니다." }
    }
    fun recover(p: ServerPlayer) {
        if (p.uuid in runs || matches.values.any { p.uuid in it.players }) abortParticipant(p.uuid, "도전을 종료하고 PC 잠금을 해제했습니다.")
        attempt("recover original party ${p.uuid}") { restoreDuelParty(p) }
        syncStorageLock(p, isStorageLocked(p.uuid))
        if (p.uuid in pendingCompletions) {
            if (attempt("reconnect journal completion ${p.uuid}") { returns?.complete(p.uuid) }) pendingCompletions.remove(p.uuid)
            return
        }
        returns?.get(p.uuid)?.let { origin ->
            if (attempt("reconnect return ${p.uuid}") { restoreRecorded(p, origin) }) note(p, "종료된 도전의 임시 파티를 정리하고 원래 위치로 복귀했습니다.")
        }
    }
    fun lobby(p: ServerPlayer) {
        require((clientProtocols[p.uuid]?:0)>=11){"친구용 CobbleLeague 0.8.0으로 업데이트하세요."}
        runs[p.uuid]?.let { sendRun(p,it); return }
        available(p); ensureMap()
        if (p.level().dimension() != dimension) remember(mapOf(p.uuid to point(p)))
        teleport(p, 0.5, -100.5)
        lobbyOptOut.remove(p.uuid);lobbyQueue.join(p.uuid);sendLobby(p)
    }
    private fun inLobby(p:ServerPlayer)=p.level().dimension()==dimension&&abs(p.x)<=20&&p.z in -113.0..-85.0&&abs(p.y-65)<8
    private fun standings(g:LobbyGameRuntime)=JsonArray().also{a->g.model.standings.values.sortedWith(compareBy<PlayerStanding>{g.model.rank(it.id)}.thenBy{g.model.standings.keys.indexOf(it.id)}).forEach{row->a.add(JsonObject().apply{
        addProperty("id",row.id.toString());addProperty("name",g.names[row.id]?:"플레이어")
        addProperty("health",row.health);addProperty("maximum",g.model.startingHealth);addProperty("points",row.health)
        addProperty("wins",row.wins);addProperty("losses",row.losses);addProperty("byes",row.byes);addProperty("rank",g.model.rank(row.id));addProperty("finalPlace",g.model.finalPlacements()[row.id]?:0);addProperty("colorIndex",g.model.standings.keys.indexOf(row.id))
    })}}
    private fun rankHistory(g:LobbyGameRuntime)=JsonArray().also{a->g.history.rounds.forEach{round->a.add(JsonObject().apply{
        addProperty("round",round.round);add("players",JsonArray().also{rows->round.rows.forEach{rank->rows.add(JsonObject().apply{addProperty("id",rank.player.toString());addProperty("rank",rank.rank);addProperty("health",rank.health)})}})
    })}}
    private fun creepProgress(g:LobbyGameRuntime)=JsonArray().also{a->
        val kind=g.trainer?.kind?:return@also
        val participants=g.model.standings.keys.filter{it in (g.model.plan?.players?:g.creepOutcomes.keys)}
        participants.forEach{id->
            val active=g.currentCreeps[id]
            val enemy=active?.handle?.battle?.actors?.filter{id !in it.getPlayerUUIDs()}?.flatMap{it.pokemonList}.orEmpty()
            val outcome=g.creepOutcomes[id]
            val defeated=enemy.count{it.health<=0}.let{if(outcome=="win")kind.teamSize else if(outcome!=null)g.creepDefeated[id]?:0 else it}
            val status=outcome?:if(active!=null)"battling" else if(id !in g.model.alive)"left" else "waiting"
            a.add(JsonObject().apply{
                addProperty("id",id.toString());addProperty("name",g.names[id]?:"플레이어");addProperty("status",status)
                addProperty("defeated",defeated);addProperty("total",kind.teamSize);addProperty("turn",active?.handle?.battle?.turn?:0)
                addProperty("health",g.model.standings[id]?.health?:0)
            })
        }
    }
    private fun sendCreepProgress(p:ServerPlayer,g:LobbyGameRuntime,open:Boolean=false) {
        if(g.trainer==null)return
        LeagueMod.send(p,packet("creep_progress"){
            addProperty("open",open);addProperty("trainer",g.trainer!!.name);addProperty("round",g.model.plan?.number?:g.model.round)
            addProperty("inBattle",runs[p.uuid]?.state?.inBattle==true);addProperty("roundComplete",g.model.roundComplete||g.phase!=LobbyGamePhase.BATTLES)
            add("creepProgress",creepProgress(g));add("scores",standings(g));add("rankHistory",rankHistory(g))
        })
    }
    private fun broadcastCreepProgress(g:LobbyGameRuntime){g.model.standings.keys.mapNotNull(::player).forEach{sendCreepProgress(it,g)}}
    private fun sendLobby(p:ServerPlayer,open:Boolean=true) {
        require((clientProtocols[p.uuid]?:0)>=11){"친구용 CobbleLeague 0.8.0으로 업데이트하세요."}
        LeagueMod.send(p,packet(if(open)"lobby" else "lobby_update"){
            addProperty("joined",p.uuid in lobbyQueue.members());addProperty("ready",lobbyQueue.isReady(p.uuid))
            addProperty("busy",lobbyGame!=null||runs.isNotEmpty()||matches.isNotEmpty())
            addProperty("startingHealth",lobbyBalance.startingHealth);addProperty("lossDamage",lobbyBalance.lossDamage)
            add("players",JsonArray().also{a->lobbyQueue.members().forEach{id->a.add(JsonObject().apply{
                addProperty("id",id.toString());addProperty("name",player(id)?.name?.string?:"플레이어");addProperty("ready",lobbyQueue.isReady(id))
            })}})
            lobbyGame?.let{add("scores",standings(it))}
        })
    }
    private fun broadcastLobby(){lobbyQueue.members().mapNotNull(::player).forEach{sendLobby(it,false)}}
    private fun leaveLobby(p:ServerPlayer) {
        lobbyQueue.leave(p.uuid);lobbyOptOut.add(p.uuid)
        returns?.get(p.uuid)?.let{restoreRecorded(p,it)}
        LeagueMod.send(p,packet("close"));broadcastLobby()
    }
    private fun startLobbyGame(ids:List<UUID>) {
        val people=ids.map{requireNotNull(player(it))};people.forEach{available(it)};ensureMap()
        creepNpcs.verify();CreepRewardKind.entries.forEach{require(roster.rewardPool(it).isNotEmpty()){ "보상 포켓몬 풀이 비어 있습니다." }}
        val g=LobbyGameRuntime(UUID.randomUUID(),EliminationGame(ids,lobbyBalance.startingHealth,lobbyBalance.lossDamage,lobbyBalance.escalateFromGym,lobbyBalance.maxLossDamage),people.associate{it.uuid to it.name.string})
        val created=people.map{p->Run(p.uuid,LeagueFormat.SINGLE,returns?.get(p.uuid)?:point(p),Int.MAX_VALUE,roster.baseOdds).also{r->
            r.originalParty=p.party();r.originalIds=p.party().toGappyList().map{it?.uuid};r.lobbyGame=g.id;r.pairId=g.id
        }}
        remember(created.associate{it.player to it.returnPoint})
        try {
            created.forEach{r->r.originalParty!!.forEach{it.recall()};r.originalParty!!.observerUUIDs.remove(r.player);r.party.initialize();runs[r.player]=r}
            lobbyGame=g
            rooms=ids.indices.map{privateRooms.center(it)}
            people.forEach{p->syncStorageLock(p,true);syncVisibleParty(p,requireNotNull(runs[p.uuid]).party);note(p,"로비 전원이 대기 완료했습니다. ${ids.size}인 던전을 시작합니다. 보유 ${g.model.startingHealth}점 · 0점이면 탈락")}
            beginLobbyRound(g)
        }catch(e:Exception){finishLobbyGame(g,"시작 오류로 던전을 취소했습니다.",true);throw e}
    }
    private fun lobbySpot(p:ServerPlayer,g:LobbyGameRuntime) {
        val index=g.model.standings.keys.indexOf(p.uuid).coerceAtLeast(0)
        teleport(p,-7.5+(index%8)*2.0,-100.5)
    }
    private fun beginLobbyRound(g:LobbyGameRuntime) {
        watchVisits.keys.toList().mapNotNull(::player).forEach{stopWatching(it)}
        g.offeredThisRound.clear()
        val plan=g.model.beginRound();g.phase=LobbyGamePhase.BUILDING;g.current=null
        g.selections.clear();g.selections.addAll(plan.players);g.selectionEnds.clear();g.duels.clear();g.duels.addAll(plan.duels)
        g.creepQueue.clear();if(plan.kind!=DungeonRoundKind.PVP)g.creepQueue.addAll(plan.players)
        g.currentCreeps.clear();g.trainer=if(plan.kind==DungeonRoundKind.PVP)null else GymTrainers.draw(plan.kind);g.creepOutcomes.clear();g.creepDefeated.clear();g.rewardRound=0;g.rewardReady.clear()
        plan.players.forEach{id->runs[id]?.let{r->r.room=-1;r.opponent=null;r.waiting=false;r.replacementSlot=-1;r.pendingPick=null;r.gymReward=null
            r.preparation.credit(lobbyBalance.roundIncome)
            privateRooms.request(requireNotNull(server.getLevel(dimension)),g.model.standings.keys.indexOf(id))
            player(id)?.let{p->sendRun(p,r);note(p,if(plan.bye==id)"이번 라운드는 순환 대기입니다. 자신의 격리 선택방에서 동일하게 영입하고 리롤 보상을 받습니다." else "${plan.number}라운드 · 참가자별 격리 선택방을 준비합니다.")}
        }}
    }
    private fun restoreLobbyMember(id:UUID,message:String,disconnected:UUID?=null) {
        player(id)?.let{stopWatching(it)}
        val r=runs[id]?:return;r.opponent=null;r.pairId=r.id;lobbyOptOut.add(id)
        endRunGroup(id,message,disconnected)
    }
    private fun finishLobbyGame(g:LobbyGameRuntime,message:String="",cancelled:Boolean=false) {
        if(lobbyGame!==g)return
        matches.remove(g.id)?.let{arenas.release(it.id);stopBattle(it)}
        g.currentCreeps.values.toList().forEach{active->matches.remove(active.matchId)?.let(::stopBattle);active.handle.abort()};g.currentCreeps.clear()
        val finalRound=g.model.plan?.number?:g.model.round
        if(!cancelled)g.history.recordFinal(finalRound,g.model.standings.mapValues{it.value.health},g.model.finalPlacements())
        val rows=standings(g);val winner=g.model.winner
        val verdict=if(cancelled)message else if(winner!=null)"${player(winner)?.name?.string?:"플레이어"}님 최종 승리" else "남은 참가자가 없어 종료했습니다."
        lobbyGame=null
        g.model.standings.keys.toList().forEach{id->restoreLobbyMember(id,verdict)}
        rooms=legacyRooms
        g.model.standings.keys.mapNotNull(::player).forEach{p->LeagueMod.send(p,packet("result"){addProperty("verdict",verdict);addProperty("mode","elimination");add("scores",rows.deepCopy());add("rankHistory",rankHistory(g))})}
        broadcastLobby()
    }
    private fun finishLobbyDuel(g:LobbyGameRuntime,duel:LeagueDuel,winner:UUID?,battle:com.cobblemon.mod.common.api.battles.model.PokemonBattle?=null,disconnected:UUID?=null) {
        if(g.model.isCompleted(duel))return
        g.model.complete(duel,winner)
        listOf(duel.first,duel.second).forEach{id->runs[id]?.let{r->
            val outcome=if(winner==null)RoundOutcome.DRAW else if(winner==id)RoundOutcome.WIN else RoundOutcome.LOSS
            if(!r.state.inBattle&&r.state.ready)r.state.fight()
            if(r.state.inBattle)r.state.finishRound(outcome)
            if(outcome==RoundOutcome.LOSS)r.preparation.credit(lobbyBalance.lossBonus)
            r.preparation.unlockWaiting();r.opponent=null;r.room=-1
            battle?.actors?.filter{id in it.getPlayerUUIDs()}?.forEach{actor->actor.pokemonList.forEach{bm->
                val original=r.party.firstOrNull{it.uuid==bm.originalPokemon.uuid}
                if(original!=null&&DungeonShop.products.any{it.consumable&&it.id==BuiltInRegistries.ITEM.getKey(original.heldItem().item).toString()}&&bm.effectedPokemon.heldItem().isEmpty)
                    (original as DungeonHeldItemAccessor).`cobbleleague$setVirtualItem`(ItemStack.EMPTY)
            }}
            if(g.model.standings.getValue(id).health==0)restoreLobbyMember(id,"보유 점수가 0점이 되어 탈락했습니다.",disconnected)
            else player(id)?.let{p->r.party.forEach{it.heal()};r.room=g.model.standings.keys.indexOf(id);moveRoom(p,r);syncVisibleParty(p,r.party);sendRun(p,r);note(p,"이번 대전 ${outcome.label} · 남은 ${g.model.standings.getValue(id).health}점${if(outcome==RoundOutcome.LOSS)" · 패배 보상 ${lobbyBalance.lossBonus}원" else ""}")}
        }}
        g.current=null
        g.model.alive.mapNotNull(::player).forEach{p->runs[p.uuid]?.let{sendRun(p,it,false)}}
    }
    private fun unfinishedCreepRound(g:LobbyGameRuntime):Boolean = g.model.plan?.let{it.kind!=DungeonRoundKind.PVP&&!g.model.roundComplete}?:false
    private fun startCreepBattle(g:LobbyGameRuntime,id:UUID,kind:DungeonRoundKind) {
        val p=requireNotNull(player(id));stopWatching(p);val r=runs.getValue(id);available(p)
        val center=privateRooms.center(g.model.standings.keys.indexOf(id))
        val trainer=requireNotNull(g.trainer);require(trainer.kind==kind)
        val matchId=UUID.randomUUID()
        var started:CreepNpcHandle?=null
        try {
            protectDraftMoves(r);clearDisplays(r);r.party.forEach{it.recall()}
            val origin=point(p);teleport(p,center.first-12.5,center.second+0.5,-90f)
            val handle=creepNpcs.start(p,requireNotNull(server.getLevel(dimension)),trainer,format(LeagueFormat.SINGLE,50),center.first.toDouble(),center.second.toDouble())
            started=handle
            g.currentCreeps[id]=ActiveCreep(id,matchId,trainer,handle)
            matches[matchId]=Match(matchId,handle.battle,listOf(id),-1,mapOf(id to origin),id,server.tickCount,creepKind=kind,battleCenter=center)
            r.state.fight();r.waiting=false
            LeagueMod.send(p,packet("battle_started"));note(p,"${trainer.name} 관장전 · 상대 ${kind.teamSize}마리 · 싱글 · 레벨 50 · 모두 같은 관장과 동시 대전")
        } catch(e:Exception){matches.remove(matchId);g.currentCreeps.remove(id);started?.abort();throw e}
    }
    private fun finishCreepBattle(g:LobbyGameRuntime,match:Match) {
        val id=match.players.single();val active=requireNotNull(g.currentCreeps.remove(id))
        require(active.player==id&&active.handle.battle.battleId==match.battle.battleId)
        val won=match.surrenderedBy==null&&match.battle.winners.any{id in it.getPlayerUUIDs()}
        g.creepDefeated[id]=active.handle.battle.actors.filter{id !in it.getPlayerUUIDs()}.flatMap{it.pokemonList}.count{it.health<=0}
        active.handle.abort();stopBattle(match)
        if(g.model.isCreepCompleted(id))return
        g.model.completeCreep(id,won);g.creepOutcomes[id]=if(won)"win" else "loss"
        runs[id]?.let{r->
            match.battle.actors.filter{id in it.getPlayerUUIDs()}.forEach{actor->actor.pokemonList.forEach{bm->
                val original=r.party.firstOrNull{it.uuid==bm.originalPokemon.uuid}
                if(original!=null&&DungeonShop.products.any{it.consumable&&it.id==BuiltInRegistries.ITEM.getKey(original.heldItem().item).toString()}&&bm.effectedPokemon.heldItem().isEmpty)
                    (original as DungeonHeldItemAccessor).`cobbleleague$setVirtualItem`(ItemStack.EMPTY)
            }}
            if(r.state.inBattle)r.state.finishRound(if(won)RoundOutcome.WIN else RoundOutcome.LOSS)
            if(!won)r.preparation.credit(lobbyBalance.lossBonus)
            r.preparation.unlockWaiting();r.opponent=null
            if(won){
                val legendary=roster.drawReward(CreepRewardKind.LEGENDARY)
                val cash=(lobbyBalance.gymMoneyMin..lobbyBalance.gymMoneyMax).random()
                val prize=DungeonTraining.reward()
                val crystal=DungeonTraining.choices(DungeonTraining.Z_CHOICE).randomOrNull()
                val prizeName=when(prize){
                    DungeonTraining.Z_CHOICE->"Z크리스탈 선택권"
                    DungeonTraining.MEGA_CHOICE->"메가스톤 선택권"
                    DungeonTraining.GOLD->"금뚜껑"
                    else->BuiltInRegistries.ITEM.get(ResourceLocation.parse(prize)).description.string
                }
                r.gymReward=GymRewardSummary(legendary.name,cash,prizeName,crystal,legendary.key,prize,
                    GymRewardChoice.offer(GymPrize.entries.filter{it!=GymPrize.CRYSTAL||crystal!=null}))
                player(id)?.let{note(it,"관장전 승리 · 전원 전투 종료 후 F4에서 보상 하나를 선택하세요. 선택 전에는 지급되지 않습니다.")}
            }
            if(g.model.standings.getValue(id).health<=0)restoreLobbyMember(id,"관장전에서 0점이 되어 탈락했습니다.")
            else player(id)?.let{p->r.party.forEach{it.heal()};moveRoom(p,r);syncVisibleParty(p,r.party);sendRun(p,r,false);if(!won)note(p,"관장전 패배 · 점수 ${g.model.damageAt(g.model.plan?.number?:g.model.round)}점 차감 · ${lobbyBalance.lossBonus}원 지급")}
        }
        g.model.alive.mapNotNull(::player).forEach{p->runs[p.uuid]?.let{sendRun(p,it,false)}}
        player(id)?.let{sendCreepProgress(it,g,true)}
        broadcastCreepProgress(g)
    }
    private fun withdrawLobbyPlayer(id:UUID,message:String,disconnected:UUID?=null) {
        val g=lobbyGame?:return;g.model.withdraw(id)
        g.currentCreeps.remove(id)?.let{active->
            matches.remove(active.matchId)?.let(::stopBattle);active.handle.abort()
        }
        if(g.trainer!=null)g.creepOutcomes[id]="left"
        val duel=g.current
        if(duel!=null&&id in listOf(duel.first,duel.second)) {
            matches.remove(g.id)?.let{arenas.release(it.id);stopBattle(it)}
            val other=if(duel.first==id)duel.second else duel.first
            finishLobbyDuel(g,duel,other.takeIf{it in g.model.alive},disconnected=disconnected)
        }
        restoreLobbyMember(id,message,disconnected)
        if(g.model.finished&&!unfinishedCreepRound(g))finishLobbyGame(g)
    }
    private fun tickLobby() {
        val inArea=server.playerList.players.filter{inLobby(it)}
        lobbyOptOut.retainAll(inArea.map{it.uuid}.toSet())
        lobbyQueue.members().toList().forEach{id->val p=player(id);if(p==null||!p.isAlive||p.level().dimension()!=dimension)lobbyQueue.leave(id)}
        inArea.filter{it.uuid !in runs&&it.uuid !in lobbyOptOut&&(clientProtocols[it.uuid]?:0)>=11&&it.isAlive&&!it.isSpectator}.forEach{p->
            if(p.uuid !in lobbyQueue.members())runCatching{lobbyQueue.join(p.uuid);sendLobby(p)}
        }
        val g=lobbyGame
        if(g==null){
            if(lobbyQueue.canStart&&runs.isEmpty()&&matches.isEmpty()){
                val ids=lobbyQueue.takeReady()
                try{startLobbyGame(ids)}catch(e:Exception){LeagueMod.LOGGER.error("Lobby start failed",e);ids.mapNotNull(::player).forEach{note(it,e.message?:"시작 실패")}}
            }
            return
        }
        if(g.model.finished&&!unfinishedCreepRound(g)){finishLobbyGame(g);return}
        val now=System.currentTimeMillis()
        when(g.phase){
            LobbyGamePhase.BUILDING->{
                privateRooms.tick(requireNotNull(server.getLevel(dimension)))
                if(g.model.alive.all{privateRooms.ready(g.model.standings.keys.indexOf(it))})g.phase=LobbyGamePhase.SELECTION
            }
            LobbyGamePhase.SELECTION->{
                g.model.alive.toList().forEach{id->val r=runs[id]?:return@forEach;val p=player(id)?:return@forEach
                    if(r.room>=0&&r.state.ready){g.selectionEnds.remove(id)}
                    else if(r.room>=0&&(g.selectionEnds[id]?:Long.MAX_VALUE)<=now&&r.displays.isNotEmpty()){
                        if(r.party.count()>0)skipPick(p,r,r.state.version,true)
                        else r.displays.indexOfFirst{!it.first.isRemoved}.takeIf{it>=0}?.let{index->
                            pick(p,JsonObject().apply{addProperty("pokemon",r.choices[index]);addProperty("version",r.state.version)},true)
                            note(p,"선택 시간이 지나 첫 후보를 자동 영입했습니다.")
                        }
                    }
                }
                while(g.selections.isNotEmpty()&&runs.values.count{it.lobbyGame==g.id&&it.room>=0}<rooms.size){
                    val id=g.selections.removeFirst();val r=runs[id]?:continue;val p=player(id)?:continue
                    if(id !in g.model.alive)continue
                    moveRoom(p,r);offer(r);g.selectionEnds[id]=now+lobbyBalance.selectionSeconds*1000L;sendRun(p,r)
                }
                if(g.selections.isEmpty()&&g.model.alive.all{runs[it]?.state?.ready==true}){
                    g.phase=LobbyGamePhase.PREPARATION
                    g.model.alive.forEach{id->runs[id]?.let{r->r.preparation.begin(now);player(id)?.let{sendRun(it,r)}}}
                }
            }
            LobbyGamePhase.PREPARATION->if(g.model.alive.all{runs[it]?.preparation?.ready(now)==true}){g.phase=LobbyGamePhase.BATTLES;g.model.alive.mapNotNull(::player).forEach{p->runs[p.uuid]?.let{sendRun(p,it,false)}}}
            LobbyGamePhase.BATTLES->{
                val plan=requireNotNull(g.model.plan)
                if(plan.kind!=DungeonRoundKind.PVP){
                    g.currentCreeps.values.toList().forEach{it.handle.tick()}
                    if(server.tickCount%20==0)broadcastCreepProgress(g)
                    while(g.creepQueue.isNotEmpty()){
                        val id=g.creepQueue.removeFirst()
                        if(id in g.model.alive&&id in runs)startCreepBattle(g,id,plan.kind)
                    }
                } else if(g.current==null&&g.duels.isNotEmpty()&&arenas.available()){
                    val duel=g.duels.removeFirst();val healthy=listOf(duel.first,duel.second).filter{it in g.model.alive&&it in runs}
                    if(healthy.size<2)finishLobbyDuel(g,duel,healthy.singleOrNull())
                    else {g.current=duel;val a=runs.getValue(duel.first);val b=runs.getValue(duel.second);a.opponent=b.player;b.opponent=a.player;a.waiting=true;b.waiting=true;fight(requireNotNull(player(a.player)),true)}
                }
                if(g.current==null&&g.currentCreeps.isEmpty()&&g.duels.isEmpty()&&g.creepQueue.isEmpty()&&g.model.roundComplete){
                    plan.players.forEach{id->runs[id]?.let{r->if(plan.bye==id&&r.state.ready)r.state.finishBye();r.preparation.nextRound(false);r.replacementSlot=-1;player(id)?.let{p->note(p,"라운드 종료 · 모두 리롤 1회 지급");syncVisibleParty(p,r.party)}}}
                    g.rewardRound=if(plan.kind==DungeonRoundKind.PVP)0 else plan.number
                    g.model.closeRound();g.history.record(g.model.round,g.model.standings.mapValues{it.value.health},g.model.finalPlacements());g.phase=LobbyGamePhase.RESULTS;g.resultEnds=now+5000
                    g.model.standings.keys.mapNotNull(::player).forEach{p->LeagueMod.send(p,packet("round_result"){addProperty("verdict","${g.model.round}라운드 결과");addProperty("mode","elimination");add("scores",standings(g));add("rankHistory",rankHistory(g))})}
                }
            }
            LobbyGamePhase.RESULTS->if(now>=g.resultEnds){
                if(g.rewardRound>0){
                    g.phase=LobbyGamePhase.REWARDS
                    g.model.alive.forEach{id->runs[id]?.let{r->player(id)?.let{sendRun(it,r)}}}
                }else beginLobbyRound(g)
            }
            LobbyGamePhase.REWARDS->if(g.model.alive.all{it in g.rewardReady})beginLobbyRound(g)
        }
    }
    private fun readyRows():JsonArray=JsonArray().also{a->
        val g=lobbyGame
        if(g!=null)g.model.alive.forEach{id->runs[id]?.let{run->a.add(JsonObject().apply{
            addProperty("id",id.toString());addProperty("name",player(id)?.name?.string?:"플레이어")
            val ready=run.state.ready&&run.preparation.ready(System.currentTimeMillis())
            addProperty("ready",ready);addProperty("status",if(run.state.inBattle)"전투 중" else if(!run.state.ready)"선택 중" else if(ready)"준비 완료" else "준비 중")
        })}}else lobbyQueue.members().forEach{id->a.add(JsonObject().apply{addProperty("id",id.toString());addProperty("name",player(id)?.name?.string?:"플레이어");addProperty("ready",lobbyQueue.isReady(id));addProperty("status",if(lobbyQueue.isReady(id))"대기 완료" else "준비 중")})}
    }
    private fun sendReadyPanel(p:ServerPlayer){require(p.uuid in runs||p.uuid in lobbyQueue.members());LeagueMod.send(p,packet("ready_status"){add("players",readyRows())})}
    private fun ping(p:ServerPlayer,target:String){
        require(server.tickCount-(pingTimes[p.uuid]?:-100)>40){"핑은 2초 간격으로 보낼 수 있습니다."}
        val ids=readyRows().map{it.asJsonObject.text("id")};require(p.uuid.toString() in ids&&target in ids){"같은 던전 참가자에게만 보낼 수 있습니다."}
        val recipient=requireNotNull(player(UUID.fromString(target)));pingTimes[p.uuid]=server.tickCount
        LeagueMod.send(recipient,packet("ping"){addProperty("sender",p.name.string)})
    }
    fun menu(p: ServerPlayer) {
        runs[p.uuid]?.let { sendRun(p, it); return }
        matches.values.firstOrNull{p.uuid in it.players}?.let{sendBattleControls(p,it);return}
        challenges.values.firstOrNull { it.accepted && p.uuid in listOf(it.sender, it.receiver) }?.let { c ->
            val owner = requireNotNull(player(if (p.uuid == c.sender) c.receiver else c.sender))
            sendDecks(p, c, owner, if (p.uuid == c.sender) c.receiverDecks else c.senderDecks); return
        }
        sendLobby(p)
    }
    fun handle(p: ServerPlayer, json: JsonObject) {
        if (json.text("action") == "hello") { clientProtocols[p.uuid] = json.number("protocol"); return }
        val now = server.tickCount.toLong()
        require(now - (lastRequest[p.uuid] ?: -100L) >= 3) { "잠시 후 다시 시도하세요." }
        lastRequest[p.uuid] = now
        when (json.text("action")) {
            "lobby_status" -> sendLobby(p)
            "ui_close" -> { /* Screen close never withdraws or clears ready state. */ }
            "ready_status" -> sendReadyPanel(p)
            "ping" -> ping(p,json.text("target"))
            "odds" -> sendOdds(p)
            "odds_boost" -> boostOdds(p,json)
            "creep_status" -> {val g=requireNotNull(lobbyGame);require(p.uuid in g.model.standings);sendCreepProgress(p,g,true)}
            "gym_reward_choose" -> chooseGymReward(p,json)
            "battle_restart" -> restartBattle(p,json.text("battleId"))
            "reward_draw" -> drawCreepReward(p,json)
            "reward_view" -> showCreepReward(p)
            "reward_accept" -> acceptCreepReward(p,json)
            "reward_decline" -> declineCreepReward(p,json)
            "reward_done", "reward_resume" -> {
                val g=requireNotNull(lobbyGame);require(g.phase==LobbyGamePhase.REWARDS&&p.uuid in g.model.alive)
                if(json.text("action")=="reward_done"){
                    require(runs[p.uuid]?.gymReward?.choice?.selected!=null||runs[p.uuid]?.gymReward==null){"승리 보상을 하나 선택하세요."}
                    g.rewardReady.add(p.uuid)
                } else g.rewardReady.remove(p.uuid)
                runs[p.uuid]?.let{r->r.preparation.changed();sendRun(p,r)}
            }
            "watch_list" -> sendWatchList(p)
            "watch_battle" -> {watchBattle(p,UUID.fromString(json.text("battleId")))}
            "watch_stop" -> {note(p,stopWatching(p));menu(p)}
            "tera" -> shop(p,JsonObject().apply{addProperty("view","tera")})
            "equipment" -> shop(p,JsonObject().apply{addProperty("view","equipment")})
            "sync_party" -> runs[p.uuid]?.let{syncVisibleParty(p,it.party);sendRun(p,it,false)}
            "lobby_join" -> lobby(p)
            "lobby_ready" -> {
                available(p)
                require(com.cobblemon.mod.common.Cobblemon.playerDataManager.getGenericData(p).starterSelected){"스타팅 포켓몬을 먼저 선택하세요."}
                lobbyQueue.setReady(p.uuid,json.get("ready")?.asBoolean?:true);broadcastLobby()
            }
            "lobby_leave" -> leaveLobby(p)
            "menu" -> menu(p)
            "lobby" -> lobby(p)
            "leave" -> leave(p)
            "challenge" -> challenge(p, json)
            "accept" -> accept(p, json)
            "decline" -> decline(p, json)
            "deck" -> selectDeck(p, json)
            "deck_confirm" -> confirmDeck(p, json)
            "run" -> error("상대 플레이어에게 R키로 던전 도전을 신청하세요.")
            "status" -> runs[p.uuid]?.let { sendRun(p, it) } ?: menu(p)
            "pick" -> pick(p, json)
            "pick_skip" -> skipPick(p,requireNotNull(runs[p.uuid]),json.text("version"))
            "pick_cancel" -> {
                val r=requireNotNull(runs[p.uuid])
                require(json.text("version")==r.state.version)
                r.pendingPick=null
                sendRun(p,r)
            }
            "replace" -> {
                val run = requireNotNull(runs[p.uuid]); require(!run.state.ready && run.party.count() == 6)
                val slot = json.number("slot", -1); require(slot in 0..5)
                run.replacementSlot = slot; run.armed = false; sendRun(p, run)
            }
            "fight", "ready" -> setPreparationReady(p, json, true)
            "unready" -> setPreparationReady(p, json, false)
            "reroll" -> reroll(p, json)
            "shop" -> shop(p, json)
            "buy" -> buy(p, json)
            "reorder" -> {
                val run = requireNotNull(runs[p.uuid]); require(mayReorderDraft(p))
                run.preparation.check(json.number("revision", -1), System.currentTimeMillis())
                val from = run.party.toList().indexOfFirst { it.uuid.toString() == json.text("pokemon") }
                val to = json.number("target", -1); require(from >= 0 && to in 0 until run.party.count())
                run.party.swap(from, to); run.preparation.changed(); syncVisibleParty(p, run.party); sendRun(p, run, false)
            }
            "surrender" -> surrender(p)
            "surrender_game" -> surrenderGame(p)
            else -> error("알 수 없는 리그 요청입니다.")
        }
    }
    private fun challenge(p: ServerPlayer, json: JsonObject) {
        if(json.text("mode")=="draft"&&!java.lang.Boolean.getBoolean("league.smoke.enabled")){lobby(p);return}
        require((clientProtocols[p.uuid] ?: 0) >= 2) { "친구용 CobbleLeague 0.2.0 설치파일로 업데이트해 주세요." }
        available(p); ensureMap(); require(p.uuid !in runs)
        require(challenges.values.none { p.uuid == it.sender || p.uuid == it.receiver }) { "진행 중인 대전 신청이 있습니다." }
        val other = requireNotNull(player(UUID.fromString(json.text("target")))) { "상대가 접속 중이지 않습니다." }
        require((clientProtocols[other.uuid] ?: 0) >= 2) { "상대도 친구용 CobbleLeague 0.2.0으로 업데이트해야 합니다." }
        require(other != p && other.uuid !in runs)
        available(other)
        require(p.level() == other.level() && p.distanceToSqr(other) <= 1024) { "상대가 너무 멀리 있습니다." }
        require(challenges.values.none { other.uuid == it.sender || other.uuid == it.receiver }) { "상대에게 진행 중인 신청이 있습니다." }
        require(net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(other, LeaguePayload.TYPE)) { "상대에게도 CobbleLeague 애드온이 필요합니다." }
        val mode = json.text("mode"); require(mode in listOf("borrow", "pick", "draft"))
        val format = LeagueFormat.parse(json.text("format"))
        require(format != LeagueFormat.MULTI) { "추가 덱 모드는 싱글·더블·트리플을 지원합니다. 기존 멀티 대전은 원래 화면에서 이용하세요." }
        val decks = if (mode != "draft") QuickTeams.readAndValidate(p, json.getAsJsonArray("teams")) else emptyList()
        require(mode == "draft" || decks.isNotEmpty()) { "Quick Teams에 유효한 덱을 먼저 저장하세요." }
        val level = json.number("level", 50); require(level in listOf(-1, 5, 50, 100))
        val c = Challenge(UUID.randomUUID(), p.uuid, other.uuid, mode, format, level, server.tickCount + 1200L, decks)
        challenges[c.id] = c
        LeagueMod.send(other, packet("challenge") {
            addProperty("id", c.id.toString()); addProperty("sender", p.name.string); addProperty("mode", mode)
            addProperty("format", format.label); addProperty("level", level)
            addProperty("senderId", p.uuid.toString()); addProperty("senderEntityId", p.id)
            addProperty("selectedPokemonId", (other.party().firstOrNull()?.uuid ?: UUID(0, 0)).toString())
            addProperty("formatKey", format.name)
        })
        note(p, "${other.name.string}님에게 ${format.label} 리그 대전을 신청했습니다. 60초 동안 유효합니다.")
    }
    private fun pending(p: ServerPlayer, json: JsonObject) = requireNotNull(challenges[UUID.fromString(json.text("id"))]) { "만료된 대전 신청입니다." }.also {
        require(server.tickCount <= it.expires && p.uuid in listOf(it.sender, it.receiver))
    }
    private fun decline(p: ServerPlayer, json: JsonObject) {
        val c = pending(p, json); removeChallenge(c.id)
        listOf(c.sender, c.receiver).mapNotNull(::player).forEach { note(it, "대전 신청을 취소했습니다.") }
    }
    private fun accept(p: ServerPlayer, json: JsonObject) {
        val c = pending(p, json); require(p.uuid == c.receiver && !c.accepted)
        available(p); available(requireNotNull(player(c.sender)))
        if (c.mode == "draft") {
            startPairRun(c)
            removeChallenge(c.id)
        } else {
            c.receiverDecks = QuickTeams.readAndValidate(p, json.getAsJsonArray("teams"))
            require(c.receiverDecks.isNotEmpty()) { "Quick Teams에 유효한 덱을 먼저 저장하세요." }
            c.accepted = true
            c.expires = server.tickCount + 6000L
            c.selection = DeckSelection(c.sender, c.receiver, if (c.mode == "borrow") NativeLeagueMode.BORROW else NativeLeagueMode.PICK,
                mapOf(c.sender to c.senderDecks.map { it.id }.toSet(), c.receiver to c.receiverDecks.map { it.id }.toSet()))
            sendDecks(requireNotNull(player(c.sender)), c, p, c.receiverDecks)
            sendDecks(p, c, requireNotNull(player(c.sender)), c.senderDecks)
        }
    }
    private fun sendDecks(p: ServerPlayer, c: Challenge, owner: ServerPlayer, decks: List<SavedDeck>) {
        val selection = requireNotNull(c.selection)
        val selected = decks.firstOrNull { it.id == selection.selected(p.uuid) }
        LeagueMod.send(p, packet("decks") {
            addProperty("id", c.id.toString()); addProperty("owner", owner.name.string); addProperty("mode", c.mode)
            add("decks", QuickTeams.describe(decks, owner)); addProperty("selectedDeck", selected?.id.orEmpty())
            addProperty("selectedName", selected?.name.orEmpty()); addProperty("confirmed", selection.isConfirmed(p.uuid))
            addProperty("selectedSummary", selected?.let { QuickTeams.describe(listOf(it), owner).first().asJsonObject.text("summary") }.orEmpty())
        })
    }
    private fun selectDeck(p: ServerPlayer, json: JsonObject) {
        val c = pending(p, json); require(c.accepted && c.mode != "draft")
        requireNotNull(c.selection).select(p.uuid, json.text("deck"))
        val owner = requireNotNull(player(if (p.uuid == c.sender) c.receiver else c.sender))
        sendDecks(p, c, owner, if (p.uuid == c.sender) c.receiverDecks else c.senderDecks)
    }
    private fun confirmDeck(p: ServerPlayer, json: JsonObject) {
        val c = pending(p, json); require(c.accepted && c.mode != "draft")
        val selection = requireNotNull(c.selection)
        selection.confirm(p.uuid)
        if (selection.ready) {
            try { startPvp(c) }
            catch (e: Exception) {
                if (c.id in challenges) {
                    selection.reopenAfterStartFailure(p.uuid)
                    val owner = requireNotNull(player(selection.opponent(p.uuid)))
                    sendDecks(p, c, owner, if (p.uuid == c.sender) c.receiverDecks else c.senderDecks)
                }
                throw e
            }
        }
        else {
            val owner = requireNotNull(player(selection.opponent(p.uuid)))
            sendDecks(p, c, owner, if (p.uuid == c.sender) c.receiverDecks else c.senderDecks)
        }
    }
    private fun restoreDuelParty(p: ServerPlayer) {
        val expected = duelOriginalParties[p.uuid] ?: return
        val store = p.party()
        restoreOriginalPartyOrder(expected, { store.toGappyList().map { it?.uuid } }, store::swap)
        syncVisibleParty(p, store)
        duelOriginalParties.remove(p.uuid)
        syncStorageLock(p, p.uuid in runs)
        note(p, "원래 파티 복귀 확인 완료 · 구성·순서 확인 후 파티 정보를 다시 전송했습니다.")
    }
    private fun party(p: ServerPlayer, source: List<Pokemon>): PlayerPartyStore = PlayerPartyStore(p.uuid, UUID.randomUUID()).also { store ->
        source.forEachIndexed { i, pokemon -> UncatchableProperty.uncatchable().apply(pokemon); store.set(i, pokemon) }
    }
    private fun format(type: LeagueFormat, level: Int): BattleFormat {
        val base = when (type) { LeagueFormat.DOUBLE -> BattleFormat.GEN_9_DOUBLES; LeagueFormat.TRIPLE -> BattleFormat.GEN_9_TRIPLES; LeagueFormat.MULTI -> BattleFormat.GEN_9_MULTI; else -> BattleFormat.GEN_9_SINGLES }
        return base.copy(ruleSet = base.ruleSet + BattleRules.BAG_CLAUSE, adjustLevel = level)
    }
    private fun startPvp(c: Challenge) {
        val a = requireNotNull(player(c.sender)); val b = requireNotNull(player(c.receiver))
        available(a); available(b); ensureMap()
        val selection = requireNotNull(c.selection)
        val assignmentA = selection.assignment(a.uuid); val assignmentB = selection.assignment(b.uuid)
        fun clone(assignment: DeckAssignment) = QuickTeams.cloneDeck(requireNotNull(player(assignment.owner)),
            (if (assignment.owner == c.sender) c.senderDecks else c.receiverDecks).first { it.id == assignment.deckId })
        val aa = clone(assignmentA); val bb = clone(assignmentB)
        require(aa.size >= c.format.slots && bb.size >= c.format.slots) { "선택한 대전 형식에 필요한 포켓몬이 부족합니다." }
        val arena = requireNotNull(arenas.acquire(c.id)) { "모든 리그장이 사용 중입니다. 잠시 후 덱 선택 또는 신청을 다시 진행하세요." }
        val origins = mapOf(a.uuid to point(a), b.uuid to point(b))
        val center = centers[arena]
        try {
            remember(origins)
            listOf(a, b).forEach { p ->
                duelOriginalParties[p.uuid] = p.party().toGappyList().map { it?.uuid }
                syncStorageLock(p, true)
            }
            val pa = party(a, aa); val pb = party(b, bb)
            teleport(a, center.first - 20.5, center.second + 0.5, -90f)
            teleport(b, center.first + 20.5, center.second + 0.5, 90f)
            val result = BattleBuilder.pvp1v1(a, b, battleFormat = format(c.format, c.level), cloneParties = true, healFirst = true,
                partyAccessor = { if (it.uuid == a.uuid) pa else pb })
            require(result is SuccessfulBattleStart) { "전투를 시작할 수 없습니다. 포켓몬과 대전 형식을 확인하세요." }
            matches[c.id] = Match(c.id, result.battle, listOf(a.uuid, b.uuid), arena, origins, createdAt = server.tickCount)
            val expected = mapOf(a.uuid to aa.map { it.uuid }, b.uuid to bb.map { it.uuid })
            val actual = listOf(a, b).associate { p -> p.uuid to requireNotNull(result.battle.getActor(p)).pokemonList.map { it.originalPokemon.uuid } }
            verifyBattleParties(expected, actual)
            listOf(a, b).forEach { p ->
                check(p.party().toGappyList().map { it?.uuid } == duelOriginalParties[p.uuid]) { "원래 파티 보존 검사가 실패했습니다." }
                note(p, "선택 덱 적용 확인 완료 · ${requireNotNull(result.battle.getActor(p)).pokemonList.joinToString(" · ") { it.effectedPokemon.species.translatedName.string }}")
            }
            removeChallenge(c.id)
            listOf(a, b).forEach { LeagueMod.send(it, packet("battle_started")) }
        } catch (e: Exception) {
            matches[c.id]?.let(::detachMatch) ?: arenas.release(c.id)
            removeChallenge(c.id)
            listOf(a, b).forEach { p -> attempt("failed PVP original party ${p.uuid}") { restoreDuelParty(p) } }
            origins.forEach { (id, pos) ->
                player(id)?.takeIf { it.isAlive }?.let { attempt("failed PVP return $id") { restoreRecorded(it, pos, pos.dimension != dimension.location().toString()) } }
            }
            throw e
        }
    }
    private fun startPairRun(c: Challenge) {
        val a = requireNotNull(player(c.sender)); val b = requireNotNull(player(c.receiver))
        lobbyQueue.leave(a.uuid);lobbyQueue.leave(b.uuid)
        available(a); available(b); ensureMap()
        require(runs.size + 2 <= rooms.size) { "두 사람이 사용할 선택방이 부족합니다." }
        val ra = Run(a.uuid, c.format, returns?.get(a.uuid) ?: point(a),startingOdds=roster.baseOdds)
        val rb = Run(b.uuid, c.format, returns?.get(b.uuid) ?: point(b),startingOdds=roster.baseOdds)
        ra.originalParty = a.party(); rb.originalParty = b.party()
        ra.originalIds = a.party().toGappyList().map { it?.uuid }; rb.originalIds = b.party().toGappyList().map { it?.uuid }
        ra.opponent = b.uuid; rb.opponent = a.uuid; ra.pairId = c.id; rb.pairId = c.id
        try {
            remember(mapOf(a.uuid to ra.returnPoint, b.uuid to rb.returnPoint))
            listOf(ra, rb).forEach { run ->
                run.originalParty!!.forEach { it.recall() }
                run.originalParty!!.observerUUIDs.remove(run.player)
                run.party.initialize()
            }
            runs[a.uuid] = ra; runs[b.uuid] = rb
            syncVisibleParty(a, ra.party); syncVisibleParty(b, rb.party)
            syncStorageLock(a, true); syncStorageLock(b, true)
            moveRoom(a, ra); offer(ra)
            moveRoom(b, rb); offer(rb)
            sendRun(a, ra); sendRun(b, rb)
        } catch (e: Exception) {
            abortParticipant(a.uuid, "도전 입장을 취소했습니다."); removeChallenge(c.id)
            listOf(ra, rb).forEach { run -> player(run.player)?.let { p ->
                run.originalParty?.let { original ->
                    if (p.uuid !in original.observerUUIDs) original.observerUUIDs.add(p.uuid)
                    syncVisibleParty(p, original)
                }
            } }
            throw e
        }
    }
    private fun clearDisplays(run: Run) {
        run.choiceLabels.toList().forEach{displayIds.remove(it.uuid);it.discard()};run.choiceLabels.clear()
        val old = run.displays.toList()
        run.displays.clear()
        run.displayMegaForms.clear()
        old.forEach { (entity, _) -> displayIds.remove(entity.uuid); attempt("discard display ${entity.uuid}") { entity.discard() } }
    }
    private fun draftOwnershipCounts(run:Run):Map<String,Int> {
        val game=lobbyGame?.takeIf{it.id==run.lobbyGame}
        val members=if(game!=null)runs.values.filter{it.lobbyGame==game.id}else runs.values.filter{it.pairId==run.pairId}
        return members.flatMap{it.party.toList()}.groupingBy{it.species.resourceIdentifier.toString()}.eachCount()
    }
    private fun offer(run: Run, excluded: Set<String> = emptySet(),type:String="") {
        clearDisplays(run)
        val game=lobbyGame?.takeIf{it.id==run.lobbyGame}
        val shared=game?.offeredThisRound?:runs.values.filter{it.pairId==run.pairId&&it!==run}.flatMap{it.choices}.map{it.substringBefore('|')}.toSet()
        val recent=run.recentOffers.flatten().toSet()
        val partyCounts=draftOwnershipCounts(run)
        run.choices=roster.chooseDiverse(3,excluded,run.drawOdds.odds,shared,recent,partyCounts,run.state.round==0,type).map{it.key};run.armed=false
        val offered=run.choices.map{it.substringBefore('|')}
        game?.offeredThisRound?.addAll(offered)
        run.recentOffers.addLast(offered);while(run.recentOffers.size>4)run.recentOffers.removeFirst()
        val room = rooms[run.room]
        val world = requireNotNull(server.getLevel(dimension))
        DungeonRerollControl.ensure(world,room,run.room,run.preparation.rerolls)
        run.choices.forEachIndexed { index, species ->
            val anchor = Vec3(room.first + (index - 1) * 14 + 0.5, 65.0, room.second + 12.5)
            val candidate = roster.resolve(species)
            val pokemon = candidate.create()
            UncatchableProperty.uncatchable().apply(pokemon)
            val entity=spawnChoice(world,pokemon,anchor)
            run.displays.add(entity to anchor)
            if(candidate.isMega)run.displayMegaForms[entity.uuid]=candidate
            val kind=roster.category(candidate)
            val rgb=when(kind){DrawCategory.NORMAL->0xE7EDF1;DrawCategory.LEGENDARY->0xFFD45C;DrawCategory.SUBLEGENDARY->0xFFAD66;DrawCategory.MYTHICAL->0xF397E6;DrawCategory.PARADOX->0x62EDFF;DrawCategory.MEGA->0xBD9CFF}
            val percent=String.format(java.util.Locale.ROOT,"%.1f",run.drawOdds.odds.values.getValue(kind)/100.0)
            val stars=DungeonTraining.stars(pokemon).coerceIn(0,5)
            val title=Component.literal("${index+1}. ${if(run.state.round==0)"스타팅" else kind.label} · ${if(run.state.round==0)"기본 폼" else "$percent%"}\n").withStyle{it.withColor(rgb)}
                .append(candidate.species.translatedName).append("\n")
            pokemon.types.forEachIndexed { i,type->
                if(i>0)title.append(" / ")
                title.append(type.displayName.copy().withStyle{it.withColor(type.hue)})
            }
            title.append("\n").append(Component.literal("★".repeat(stars)+"☆".repeat(5-stars)).withStyle{it.withColor(0xFFD45C)})
            val label=net.minecraft.world.entity.Display.TextDisplay(net.minecraft.world.entity.EntityType.TEXT_DISPLAY,world)
            val tag=net.minecraft.nbt.CompoundTag().apply{
                putString("text",Component.Serializer.toJson(title,server.registryAccess()));putString("billboard","center");putString("alignment","center")
                put("transformation",net.minecraft.nbt.CompoundTag().apply{
                    fun vector(vararg values:Float)=net.minecraft.nbt.ListTag().apply{values.forEach{add(net.minecraft.nbt.FloatTag.valueOf(it))}}
                    put("translation",vector(0f,0f,0f));put("scale",vector(1f,1f,1f));put("left_rotation",vector(0f,0f,0f,1f));put("right_rotation",vector(0f,0f,0f,1f))
                })
                putBoolean("shadow",true);putBoolean("see_through",true);putByte("text_opacity",(-1).toByte());putInt("background",0xA0101820.toInt());putInt("line_width",240);putFloat("view_range",1.5f)
                put("brightness",net.minecraft.nbt.CompoundTag().apply{putInt("block",15);putInt("sky",15)})
            }
            label.load(tag);label.addTag("cobbleleague_display");label.addTag("cobbleleague_choice_label");label.setNoGravity(true)
            label.moveTo(anchor.x,66.7,anchor.z-3.5,0f,0f);displayIds.add(label.uuid);run.choiceLabels.add(label)
            if(!world.addFreshEntity(label)){displayIds.remove(label.uuid);label.discard()}
        }
    }
    private fun spawnChoice(world:net.minecraft.server.level.ServerLevel,pokemon:Pokemon,anchor:Vec3):PokemonEntity {
        world.getChunkAt(BlockPos.containing(anchor))
        val entity=PokemonEntity(world,pokemon)
        entity.addTag("cobbleleague_display")
        entity.isNoAi=true
        entity.setNoGravity(true)
        entity.isInvulnerable=true
        entity.isSilent=true
        entity.setPersistenceRequired()
        entity.moveTo(anchor.x,anchor.y,anchor.z,180f,0f)
        displayIds.add(entity.uuid)
        if(!world.addFreshEntity(entity)){
            displayIds.remove(entity.uuid)
            entity.discard()
        }
        return entity
    }
    private fun moveRoom(p: ServerPlayer, run: Run) {
        val occupied = runs.values.filter { it !== run }.map { it.room }.toSet()
        run.room = if(run.lobbyGame!=null)requireNotNull(lobbyGame).model.standings.keys.indexOf(p.uuid)
            else reusableSelectionRoom(run.room, occupied, rooms.size)
        val room = rooms[run.room]
        teleport(p, room.first + 0.5, room.second + 0.5)
    }
    private fun sendRun(p: ServerPlayer, run: Run, open: Boolean = true) {
        LeagueMod.send(p, packet(if (open) "run" else "run_update") {
            add("readyPlayers",readyRows())
            addProperty("playerId",p.uuid.toString());addProperty("opponentId",run.opponent?.toString().orEmpty())
            addProperty("round", run.state.round + 1); addProperty("version", run.state.version)
            addProperty("ready", run.state.ready); addProperty("inBattle", run.state.inBattle)
            addProperty("format", run.format.label)
            addProperty("replacement", run.replacementSlot)
            add("rerollTypes",JsonArray().also{a->roster.availableDraftTypes(run.state.round==0).forEach{a.add(it)}})
            addProperty("canSkipPick",run.lobbyGame!=null && run.party.count()>0 && selectionActionsAvailable(p,run))
            addProperty("waiting", run.waiting)
            matches.values.firstOrNull{p.uuid in it.players}?.let{m->
                addProperty("battleId",m.battle.battleId.toString());addProperty("restartVotes",m.restartVotes.count)
                addProperty("restartVoted",m.restartVotes.voted(p.uuid));addProperty("battleParticipants",m.players.size)
            }
            addProperty("money", run.preparation.money)
            addProperty("rerolls", run.preparation.rerolls)
            addProperty("revision", run.preparation.revision)
            addProperty("freeLessons",run.preparation.freeLessons)
            addProperty("canShop",canUseGymIntermission(run))
            addProperty("watching",p.uuid in watchVisits);addProperty("watchCount",watchableMatches(p.uuid).size)
            addProperty("zChoices",run.equipment[DungeonTraining.Z_CHOICE]?:0)
            addProperty("megaChoices",run.equipment[DungeonTraining.MEGA_CHOICE]?:0)
            run.gymReward?.let{reward->add("gymReward",JsonObject().apply{
                addProperty("legendary",reward.legendary)
                addProperty("money",reward.money)
                addProperty("prize",reward.prize)
                addProperty("crystal",reward.crystal.orEmpty())
                addProperty("token",reward.choice.token);addProperty("selected",reward.choice.selected?.name.orEmpty())
                add("options",JsonArray().also{a->reward.choice.options.forEach{a.add(it.name)}})
            })}
            addProperty("mythicalTickets",run.rewardWallet.count(CreepRewardKind.MYTHICAL))
            addProperty("legendaryTickets",run.rewardWallet.count(CreepRewardKind.LEGENDARY))
            addProperty("pendingReward",run.rewardWallet.pending?.kind?.id.orEmpty())
            addProperty("boostAvailable",selectionActionsAvailable(p,run));addProperty("oddsRound",oddsRound(run));addProperty("oddsUsed",!run.drawOdds.available(oddsRound(run)));add("drawOdds",oddsRows(run))
            addProperty("confirmed", run.preparation.confirmed)
            addProperty("remainingSeconds", run.preparation.seconds(System.currentTimeMillis()))
            add("scores", JsonArray().also { scores -> runs.values.filter { it.pairId == run.pairId }.forEach { member -> scores.add(JsonObject().apply {
                addProperty("id", member.player.toString()); addProperty("name", player(member.player)?.name?.string ?: "플레이어")
                addProperty("points", member.state.points); addProperty("wins", member.state.wins); addProperty("losses", member.state.losses)
            }) } })
            addProperty("opponentReady", run.opponent?.let { runs[it]?.preparation?.ready(System.currentTimeMillis()) } ?: false)
            addProperty("arenaWaiting", run.state.ready && run.preparation.ready(System.currentTimeMillis()) && !arenas.available() && !run.state.inBattle)
            addProperty("wins", run.state.wins)
            addProperty("points", run.state.points)
            addProperty("draws", run.state.draws)
            addProperty("losses", run.state.losses)
            addProperty("lastResult", run.state.lastOutcome?.label ?: "")
            addProperty("lastPoints", run.state.lastOutcome?.points ?: 0)
            addProperty("remainingPicks", run.state.remainingPicks)
            addProperty("requiredPicks", run.state.requiredPicks)
            addProperty("opponent", run.opponent?.let(::player)?.name?.string ?: "상대")
            addProperty("opponentWins", run.opponent?.let { runs[it]?.state?.wins } ?: 0)
            addProperty("opponentPoints", run.opponent?.let { runs[it]?.state?.points } ?: 0)
            add("choices", JsonArray().also { arr -> run.choices.forEach { species ->
                arr.add(JsonObject().also { it.addProperty("id", species); it.addProperty("name", roster.resolve(species).name) })
            } })
            add("party", JsonArray().also { arr -> run.party.forEach { pokemon -> arr.add(pokemon.species.translatedName.string) } })
            add("cards", JsonArray().also { arr -> run.party.forEach { arr.add(pokemonCard(it)) } })
            lobbyGame?.takeIf{it.id==run.lobbyGame}?.let{g->
                addProperty("mode","elimination");addProperty("round",g.model.plan?.number?:g.model.round)
                addProperty("health",g.model.standings[run.player]?.health?:0);addProperty("maximumHealth",g.model.startingHealth)
                addProperty("lossDamage",g.model.damageAt(g.model.plan?.number?:g.model.round))
                addProperty("stage",g.phase.name.lowercase());addProperty("bye",g.model.plan?.bye==run.player)
                val kind=g.model.plan?.kind?:DungeonRoundKind.at(g.rewardRound)
                addProperty("roundKind",kind.name.lowercase());addProperty("trainer",if(kind==DungeonRoundKind.PVP)"" else "${g.trainer?.name?:kind.label} 관장전")
                addProperty("creepDone",g.model.isCreepCompleted(run.player))
                addProperty("rewardReady",run.player in g.rewardReady)
                addProperty("rewardReadyCount",g.model.alive.count{it in g.rewardReady})
                addProperty("roomNumber",g.model.standings.keys.indexOf(run.player)+1);addProperty("roomCount",g.model.standings.size)
                addProperty("readyCount",g.model.alive.count{runs[it]?.preparation?.ready(System.currentTimeMillis())==true})
                addProperty("participantCount",g.model.alive.size)
                addProperty("selectionSeconds",g.selectionEnds[run.player]?.let{((it-System.currentTimeMillis()+999)/1000).coerceAtLeast(0).toInt()}?:-1)
                addProperty("assigned",run.room>=0);add("scores",standings(g));add("rankHistory",rankHistory(g));add("creepProgress",creepProgress(g))
            }
        })
    }
    private fun pick(p: ServerPlayer, json: JsonObject, automatic:Boolean=false) {
        val run = requireNotNull(runs[p.uuid]) { "진행 중인 도전이 없습니다." }
        compactDraftParty(run)
        require(p.level().dimension() == dimension)
        val species = json.text("pokemon"); require(species in run.choices)
        val display = requireNotNull(run.displays.getOrNull(run.choices.indexOf(species)))
        require(json.text("version")==run.state.version && !run.state.ready && !run.state.inBattle) { "만료된 선택입니다." }
        val replacement=json.text("replace")
        val confirming=run.lobbyGame!=null && run.pendingPick==species && replacement.isNotBlank()
        require(automatic || confirming || p.boundingBox.inflate(0.15).intersects(display.first.boundingBox)) { "원하는 포켓몬에 직접 닿아 선택하세요." }
        if(run.lobbyGame!=null&&!automatic)require(selectionActionsAvailable(p,run)){"포켓몬 선택 중에만 영입할 수 있습니다."}
        if(run.lobbyGame!=null && run.party.count()>=6 && !confirming && !automatic){
            run.pendingPick=species
            LeagueMod.send(p,packet("creep_reward"){
                addProperty("draftPick",true)
                addProperty("candidate",species)
                addProperty("version",run.state.version)
                addProperty("rewardLabel","후보 영입")
                add("offer",pokemonCard(display.first.pokemon))
                add("cards",JsonArray().also{a->run.party.forEach{a.add(pokemonCard(it))}})
            })
            return
        }
        require(!display.first.isRemoved){"후보를 복구 중입니다. 잠시 후 다시 선택하세요."}
        val slot=if(confirming) (0..5).firstOrNull{run.party.get(it)?.uuid.toString()==replacement}
            ?: error("교체할 포켓몬을 다시 선택하세요.")
        else if(run.party.count()>=6)json.number("slot",5).coerceIn(0,5) else -1
        val pokemon = display.first.pokemon.clone(registryAccess = server.registryAccess())
        pokemon.tradeable = false
        UncatchableProperty.uncatchable().apply(pokemon)
        val index = if (run.party.count() < 6) run.party.count() else slot
        run.state.choose(json.text("version"), slot)
        try {
            run.pendingPick=null
            run.party.get(index)?.let { old->
                old.recall()
                run.partyMegaForms.remove(old.uuid)
                run.authorizedMoves.remove(old.uuid)
                if(!old.heldItem().isEmpty){
                    val item=BuiltInRegistries.ITEM.getKey(old.heldItem().item).toString()
                    run.equipment[item]=(run.equipment[item]?:0)+1
                }
            }
            val candidate = roster.resolve(species)
            if (candidate.isMega) { run.partyMegaForms[pokemon.uuid] = candidate }
            clearDisplays(run)
            run.party.set(index, pokemon)
            syncVisibleParty(p, run.party)
            run.authorizedMoves[pokemon.uuid] = pokemon.moveSet.map { it.name }
            announceRare(p,run,pokemon)
            if (!run.state.ready) offer(run)
            run.waiting = run.state.ready
            if(run.state.ready&&run.room in rooms.indices)DungeonRerollControl.ensure(p.serverLevel(),rooms[run.room],run.room,run.preparation.rerolls,false)
            if (run.state.ready) note(p, if(run.lobbyGame!=null)"영입 완료 · 참가자 모두 선택하면 1분 준비 시간이 시작됩니다. C로 순서·선봉, F4로 준비·상점을 여세요." else "선택 완료 · 두 사람 모두 선택하면 1분 준비 시간이 시작됩니다.")
            sendRun(p, run)
        } catch (e: Exception) { abortParticipant(p.uuid, "포켓몬 영입 중 오류로 도전을 종료했습니다."); throw e }
    }
    private fun skipPick(p:ServerPlayer,run:Run,version:String,automatic:Boolean=false) {
        require(run.lobbyGame!=null && (automatic || selectionActionsAvailable(p,run))) { "후보 선택 중에만 건너뛸 수 있습니다." }
        run.state.skip(version)
        run.pendingPick=null
        clearDisplays(run)
        DungeonRerollControl.ensure(p.serverLevel(),rooms[run.room],run.room,run.preparation.rerolls,false)
        note(p,"이번 영입을 건너뛰었습니다. 현재 파티를 유지합니다.")
        sendRun(p,run)
    }
    private fun setPreparationReady(p: ServerPlayer, json: JsonObject, value: Boolean) {
        val run = requireNotNull(runs[p.uuid])
        if(run.lobbyGame!=null)require(lobbyGame?.phase==LobbyGamePhase.PREPARATION){"참가자 모두 영입을 마친 뒤 준비 완료할 수 있습니다."}
        require(run.state.ready && !run.state.inBattle) { "이번 포켓몬을 먼저 선택하세요." }
        run.preparation.confirm(value, json.number("revision", run.preparation.revision), System.currentTimeMillis())
        sendRun(p, run)
        run.opponent?.let { id -> player(id)?.let { other -> runs[id]?.let { sendRun(other, it, false) } } }
    }
    private fun reroll(p: ServerPlayer, json: JsonObject, open:Boolean=true) {
        val run = requireNotNull(runs[p.uuid])
        if(run.lobbyGame!=null)require(lobbyGame?.phase==LobbyGamePhase.SELECTION&&run.displays.isNotEmpty()){ "후보 선택 중에만 리롤할 수 있습니다." }
        require(!run.state.ready && !run.state.inBattle && json.text("version") == run.state.version) {"포켓몬 선택을 완료하기 전에 리롤하세요."}
        val requestedType=json.text("type")
        require(requestedType.isBlank()||requestedType in roster.availableDraftTypes(run.state.round==0)){"이 뽑기 풀에서 사용할 수 없는 타입입니다."}
        run.preparation.reroll(json.number("revision", -1), System.currentTimeMillis())
        val previous = run.choices.toSet()
        run.pendingPick=null
        run.state.refreshChoices(); offer(run, previous,requestedType); sendRun(p, run, open)
    }
    private fun protectDraftMoves(run: Run) {
        run.party.forEach { mon ->
            val allowed = run.authorizedMoves[mon.uuid] ?: return@forEach
            val actual = mon.moveSet.map { it.name }
            // Reordering the same four moves is harmless; learning/relearning requires a purchase.
            if (actual.sorted() == allowed.sorted()) run.authorizedMoves[mon.uuid] = actual
            else { mon.moveSet.clear(); allowed.forEach { name -> mon.moveSet.add(Moves.getByName(name)!!.create()) } }
        }
    }
    private fun compactDraftParty(run: Run) {
        val current=run.party.toGappyList().map { it?.uuid }
        val filled=current.filterNotNull()
        val expected=filled + List<UUID?>(6-filled.size) { null }
        if(current != expected) restoreOriginalPartyOrder(expected, { run.party.toGappyList().map { it?.uuid } }, run.party::swap)
    }
    private fun canUseGymIntermission(run:Run):Boolean {
        val game=lobbyGame?.takeIf{it.id==run.lobbyGame} ?: return true
        if(game.phase==LobbyGamePhase.REWARDS)return run.player !in game.rewardReady
        val gym=game.model.plan?.kind?.let{it!=DungeonRoundKind.PVP} ?: (game.rewardRound>0)
        return !gym || game.phase !in setOf(LobbyGamePhase.BATTLES,LobbyGamePhase.RESULTS)
    }

    private fun checkGymIntermission(run:Run) {
        require(canUseGymIntermission(run)){"관장전과 보상 확인이 끝날 때까지 기다리거나, 보상 완료를 취소하세요."}
    }

    private fun shop(p: ServerPlayer, json: JsonObject) {
        val run = requireNotNull(runs[p.uuid]); require(!run.state.inBattle && run.party.count() > 0)
        checkGymIntermission(run)
        val mon = run.party.firstOrNull { it.uuid.toString() == json.text("pokemon") } ?: run.party.first()
        LeagueMod.send(p, packet("shop") {
            addProperty("view", json.text("view", "items"))
            addProperty("category",json.number("category",0)); addProperty("selected", mon.uuid.toString()); addProperty("money", run.preparation.money)
            addProperty("rerolls", run.preparation.rerolls); addProperty("revision", run.preparation.revision)
            addProperty("remainingSeconds", run.preparation.seconds(System.currentTimeMillis()))
            addProperty("confirmed", run.preparation.confirmed)
            addProperty("freeLessons",run.preparation.freeLessons)
            addProperty("zChoices",run.equipment[DungeonTraining.Z_CHOICE]?:0)
            addProperty("megaChoices",run.equipment[DungeonTraining.MEGA_CHOICE]?:0)
            add("abilities",JsonArray().also{a->DungeonTraining.abilityOptions(mon).forEach{a.add(it)}})
            add("cards", JsonArray().also { a -> run.party.forEach { a.add(pokemonCard(it)) } })
            lobbyGame?.takeIf{it.id==run.lobbyGame}?.let{g->addProperty("mode","elimination");addProperty("stage",g.phase.name.lowercase());addProperty("health",g.model.standings[run.player]?.health?:0)}
            add("products", JsonArray().also { products ->
                val selectable=rewardSelections(run)
                val catalog=(DungeonShop.products + DungeonForms.products(mon) + (run.equipment.keys+selectable.keys).filter { ':' in it }
                    .map { DungeonProduct(it,0,"관장 보상") }).distinctBy { it.id }
                catalog.forEach { product ->
                    if(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(product.id)) && DungeonForms.eligible(BuiltInRegistries.ITEM.get(ResourceLocation.parse(product.id)),mon) && DungeonForms.trainingEligible(product.id,mon)) products.add(JsonObject().apply {
                        addProperty("id",product.id)
                        addProperty("price",product.price)
                        addProperty("tier",product.tier)
                        addProperty("owned",run.equipment[product.id]?:0)
                        addProperty("choice",selectable[product.id].orEmpty())
                        addProperty("consumable",DungeonTraining.consumable(product.id)||DungeonForms.consumable(BuiltInRegistries.ITEM.get(ResourceLocation.parse(product.id))))
                        addProperty("name",when(product.id){
                            DungeonTraining.GOLD->"금뚜껑"
                            DungeonTraining.ABILITY->"특성 변경 캡슐"
                            DungeonTraining.SOUP->"다이맥스 사탕"
                            DungeonTraining.GMAX->"거다이스프"
                            else->""
                        })
                    })
                }
            })
            val learnable = DungeonLearnset.entries(mon)
            addProperty("teraPrice",DungeonTera.PRICE)
            add("teraTypes",JsonArray().also{a->DungeonTera.types.forEach{t->a.add(JsonObject().apply{addProperty("id",t.id.toString());addProperty("type",t.id.path)})}})
            add("tms", JsonArray().also { a -> learnable.keys.forEach { a.add(it.name) } })
            add("moveSources", JsonObject().also { obj -> learnable.forEach { (move, sources) -> obj.addProperty(move.name, sources.joinToString(" · ")) } })
        })
    }
    private fun rewardSelections(run:Run):Map<String,String> = buildMap {
        for(token in listOf(DungeonTraining.Z_CHOICE,DungeonTraining.MEGA_CHOICE))
            if((run.equipment[token]?:0)>0)DungeonTraining.choices(token).forEach{put(it,token)}
    }

    private fun applyShopItem(run:Run,mon:Pokemon,id:String,item:ItemStack,ability:String) {
        DungeonForms.validate(mon,item.item)
        if(DungeonForms.consumable(item.item))DungeonForms.use(mon,item)
        else if(DungeonTraining.consumable(id))DungeonTraining.apply(mon,id,ability)
        else equipVirtual(run,mon,item)
        run.authorizedMoves[mon.uuid]=mon.moveSet.map{it.name}
    }

    private fun equipVirtual(run: Run, mon: Pokemon, item: ItemStack) {
        if(!item.isEmpty)DungeonForms.validate(mon,item.item)
        val old = mon.heldItem().copy()
        if(!old.isEmpty)DungeonForms.nativeHeld(mon,old.item,false)
        if (!old.isEmpty) {
            val id = BuiltInRegistries.ITEM.getKey(old.item).toString()
            run.equipment[id] = (run.equipment[id] ?: 0) + 1
        }
        (mon as DungeonHeldItemAccessor).`cobbleleague$setVirtualItem`(item)
        if(!item.isEmpty)DungeonForms.nativeHeld(mon,item.item,true)
        run.authorizedMoves[mon.uuid]=mon.moveSet.map{it.name}
    }
    private fun buy(p: ServerPlayer, json: JsonObject) {
        val run = requireNotNull(runs[p.uuid]); require(!run.state.inBattle)
        val mon = requireNotNull(run.party.firstOrNull { it.uuid.toString() == json.text("pokemon") }) { "파티의 포켓몬을 선택하세요." }
        checkGymIntermission(run)
        val revision = json.number("revision", -1); val now = System.currentTimeMillis()
        when (json.text("operation")) {
            "tera" -> {
                val id=json.text("tera")
                require(DungeonTera.types.any{it.id.toString()==id}){"사용할 수 없는 테라타입입니다."}
                require(mon.teraType.id.toString()!=id){"이미 같은 테라타입입니다."}
                run.preparation.pay(DungeonTera.PRICE,revision,now){DungeonTera.apply(mon,id)}
                note(p,"테라피스 사용 완료 · 이번 던전의 테라타입을 변경했습니다.")
            }
            "tm" -> {
                val template = requireNotNull(DungeonLearnset.entries(mon).keys.firstOrNull { it.name == json.text("move") }) { "이 포켓몬의 현재 폼이 배울 수 없는 기술입니다." }
                require(mon.moveSet.none { it.name == template.name }) { "이미 알고 있는 기술입니다." }
                val slot = json.number("slot", -1); require(slot in 0..3)
                run.preparation.learn(revision,now) {
                    mon.moveSet.setMove(slot, template.create())
                    run.authorizedMoves[mon.uuid] = mon.moveSet.map { it.name }
                }
                note(p, "기술머신 사용 완료 · 해당 기술 한 칸을 교체했습니다.")
            }
            "unequip_all" -> {
                run.preparation.check(revision,now)
                require(run.party.any{!it.heldItem().isEmpty}){"장착 중인 도구가 없습니다."}
                run.party.filter{!it.heldItem().isEmpty}.forEach{equipVirtual(run,it,ItemStack.EMPTY)}
                run.preparation.changed()
            }
            "swap" -> {
                run.preparation.check(revision,now)
                val other=requireNotNull(run.party.firstOrNull{it.uuid.toString()==json.text("other")&&it!==mon})
                val held=mon.heldItem().copy();val second=other.heldItem().copy()
                if(!second.isEmpty)DungeonForms.validate(mon,second.item)
                if(!held.isEmpty)DungeonForms.validate(other,held.item)
                if(!held.isEmpty)DungeonForms.nativeHeld(mon,held.item,false)
                if(!second.isEmpty)DungeonForms.nativeHeld(other,second.item,false)
                (mon as DungeonHeldItemAccessor).`cobbleleague$setVirtualItem`(second)
                (other as DungeonHeldItemAccessor).`cobbleleague$setVirtualItem`(held)
                if(!second.isEmpty)DungeonForms.nativeHeld(mon,second.item,true)
                if(!held.isEmpty)DungeonForms.nativeHeld(other,held.item,true)
                run.authorizedMoves[mon.uuid]=mon.moveSet.map{it.name};run.authorizedMoves[other.uuid]=other.moveSet.map{it.name}
                run.preparation.changed()
            }
            "unequip" -> {
                run.preparation.check(revision, now); require(!mon.heldItem().isEmpty)
                equipVirtual(run, mon, ItemStack.EMPTY); run.preparation.changed()
            }
            "owned", "redeem" -> {
                run.preparation.check(revision, now)
                val id=json.text("product")
                val stock=if(json.text("operation")=="redeem")rewardSelections(run)[id]
                    ?: error("사용할 선택권이 없습니다.") else id
                require((run.equipment[stock]?:0)>0){"보관 도구가 없습니다."}
                val item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))
                require(item!=net.minecraft.world.item.Items.AIR)
                applyShopItem(run,mon,id,ItemStack(item),json.text("ability"))
                run.equipment[stock]=run.equipment.getValue(stock)-1
                run.preparation.changed()
            }
            else -> {
                val product = requireNotNull((DungeonShop.products+DungeonForms.products(mon)).firstOrNull { it.id == json.text("product") })
                val item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(product.id)); require(item != net.minecraft.world.item.Items.AIR)
                run.preparation.pay(product.price, revision, now) { applyShopItem(run,mon,product.id,ItemStack(item),json.text("ability")) }
            }
        }
        syncVisibleParty(p, run.party); sendRun(p, run, false)
        shop(p, JsonObject().apply { addProperty("pokemon", mon.uuid.toString()); addProperty("view", json.text("view", "items")) })
    }
    private fun selectionActionsAvailable(p:ServerPlayer,r:Run):Boolean {
        if(p.level().dimension()!=dimension||r.room !in rooms.indices||r.state.ready||r.state.inBattle||r.displays.isEmpty())return false
        val center=rooms[r.room]
        if(abs(p.x-center.first)>28||abs(p.z-center.second)>22||p.y !in 60.0..88.0)return false
        return r.lobbyGame==null||lobbyGame?.let{it.id==r.lobbyGame&&it.phase==LobbyGamePhase.SELECTION}==true
    }
    private fun oddsRound(r:Run)=lobbyGame?.takeIf{it.id==r.lobbyGame}?.let{it.model.plan?.number?:it.model.round}?:r.state.round+1
    private fun oddsRows(r:Run)=JsonArray().also{a->DrawCategory.entries.forEach{kind->a.add(JsonObject().apply{addProperty("id",kind.id);addProperty("label",kind.label);addProperty("basisPoints",r.drawOdds.odds.values.getValue(kind));addProperty("boost",kind.boostBasisPoints)})}}
    private fun sendOdds(p:ServerPlayer){
        val r=requireNotNull(runs[p.uuid]);require(!r.state.inBattle)
        LeagueMod.send(p,packet("odds"){
            addProperty("money",r.preparation.money);addProperty("rerolls",r.preparation.rerolls);addProperty("revision",r.preparation.revision);addProperty("confirmed",r.preparation.confirmed)
            addProperty("remainingSeconds",r.preparation.seconds(System.currentTimeMillis()));addProperty("boostAvailable",selectionActionsAvailable(p,r));addProperty("oddsRound",oddsRound(r));addProperty("oddsUsed",!r.drawOdds.available(oddsRound(r)));add("drawOdds",oddsRows(r))
        })
    }
    private fun boostOdds(p:ServerPlayer,json:JsonObject){
        val r=requireNotNull(runs[p.uuid]);require(selectionActionsAvailable(p,r)){"던전의 포켓몬 선택 중에만 강화할 수 있습니다."};val kind=DrawCategory.parse(json.text("category"));val round=oddsRound(r)
        require(r.drawOdds.available(round)){"이번 라운드에는 이미 강화했습니다."}
        r.preparation.pay(1,json.number("revision",-1),System.currentTimeMillis()){r.drawOdds.buy(kind,round)}
        note(p,"${kind.label} 뽑기 확률 +${kind.boostBasisPoints/100.0}%p · 해당 던전에서 누적 · 다음 후보 생성·리롤부터 적용")
        sendRun(p,r,false);sendOdds(p)
    }
    private fun announceRare(p:ServerPlayer,r:Run,mon:Pokemon){
        if(p.level().dimension()!=dimension||runs[p.uuid]!==r)return
        val kind=DrawCategory.classify(mon.species.labels)
        if(kind !in setOf(DrawCategory.LEGENDARY,DrawCategory.SUBLEGENDARY,DrawCategory.MYTHICAL))return
        val message=Component.literal("[던전] ${p.name.string}님이 ${kind.label} 포켓몬 ‘").append(mon.species.translatedName).append("’ 영입!")
        val ids=lobbyGame?.takeIf{it.id==r.lobbyGame}?.model?.standings?.keys?:runs.values.filter{it.pairId==r.pairId}.map{it.player}
        ids.mapNotNull(::player).forEach{it.sendSystemMessage(message.copy())}
    }
    private fun chooseGymReward(p:ServerPlayer,json:JsonObject) {
        val r=requireNotNull(runs[p.uuid]);checkGymIntermission(r)
        val g=requireNotNull(lobbyGame);require(g.phase==LobbyGamePhase.REWARDS&&p.uuid !in g.rewardReady)
        val reward=requireNotNull(r.gymReward){"선택할 승리 보상이 없습니다."}
        val prize=GymPrize.valueOf(json.text("prize"))
        reward.choice.choose(json.text("token"),prize){
            when(prize){
                GymPrize.MYTHICAL->check(r.rewardWallet.grant(CreepRewardKind.MYTHICAL,reward.choice.token))
                GymPrize.LEGENDARY->r.rewardWallet.reveal(reward.legendaryKey)
                GymPrize.MONEY->r.preparation.credit(reward.money)
                GymPrize.CRYSTAL->{val id=requireNotNull(reward.crystal);r.equipment[id]=(r.equipment[id]?:0)+1}
                GymPrize.EQUIPMENT->r.equipment[reward.prizeId]=(r.equipment[reward.prizeId]?:0)+1
                GymPrize.LESSON->r.preparation.awardFreeLesson()
            }
        }
        r.preparation.changed();note(p,"선택한 승리 보상 · ${prize.label}");sendRun(p,r)
    }
    private fun drawCreepReward(p:ServerPlayer,json:JsonObject) {
        val r=requireNotNull(runs[p.uuid]);require(!r.state.inBattle);checkGymIntermission(r);r.preparation.check(json.number("revision",-1),System.currentTimeMillis())
        val kind=CreepRewardKind.parse(json.text("kind"))
        require(kind==CreepRewardKind.MYTHICAL){"전설 보상은 결과 보기에서 직접 확인하세요."}
        r.rewardWallet.draw(kind){
            val candidate=roster.drawReward(kind)
            r.pendingRewardPokemon=candidate.create().also{it.tradeable=false;UncatchableProperty.uncatchable().apply(it)}
            candidate.key
        }
        r.preparation.changed();sendRun(p,r,false);showCreepReward(p)
    }
    private fun showCreepReward(p:ServerPlayer) {
        val r=requireNotNull(runs[p.uuid]);require(!r.state.inBattle)
        checkGymIntermission(r)
        val offer=requireNotNull(r.rewardWallet.pending){"대기 중인 뽑기 결과가 없습니다."}
        val mon=r.pendingRewardPokemon ?: roster.resolve(offer.candidate).create().also{
            it.tradeable=false
            UncatchableProperty.uncatchable().apply(it)
            r.pendingRewardPokemon=it
        }
        LeagueMod.send(p,packet("creep_reward"){
            addProperty("token",offer.token);addProperty("rewardKind",offer.kind.id);addProperty("rewardLabel",offer.kind.label)
            addProperty("revision",r.preparation.revision);addProperty("money",r.preparation.money);addProperty("rerolls",r.preparation.rerolls)
            addProperty("confirmed",r.preparation.confirmed);add("offer",pokemonCard(mon))
            add("cards",JsonArray().also{a->r.party.forEach{a.add(pokemonCard(it))}})
        })
    }
    private fun acceptCreepReward(p:ServerPlayer,json:JsonObject) {
        val r=requireNotNull(runs[p.uuid]);require(!r.state.inBattle);checkGymIntermission(r);r.preparation.check(json.number("revision",-1),System.currentTimeMillis())
        val mon=requireNotNull(r.pendingRewardPokemon)
        require(r.party.none{it.uuid==mon.uuid}){"이미 영입한 포켓몬입니다."}
        val slot=(0..5).firstOrNull{r.party.get(it)==null}
            ?: (0..5).firstOrNull{r.party.get(it)?.uuid.toString()==json.text("replace")}
            ?: error("교체할 포켓몬을 선택하세요.")
        r.rewardWallet.finish(json.text("token")){
            r.party.get(slot)?.let{old->old.recall();val held=old.heldItem();if(!held.isEmpty){val key=BuiltInRegistries.ITEM.getKey(held.item).toString();r.equipment[key]=(r.equipment[key]?:0)+1};r.partyMegaForms.remove(old.uuid);r.authorizedMoves.remove(old.uuid)}
            r.party.set(slot,mon);r.authorizedMoves[mon.uuid]=mon.moveSet.map{it.name};r.state.syncTeamSize(r.party.count())
        }
        r.pendingRewardPokemon=null;r.preparation.changed();announceRare(p,r,mon);syncVisibleParty(p,r.party);note(p,"보상 포켓몬을 던전 임시 파티에 영입했습니다.");sendRun(p,r)
    }
    private fun declineCreepReward(p:ServerPlayer,json:JsonObject) {
        val r=requireNotNull(runs[p.uuid]);require(!r.state.inBattle);checkGymIntermission(r);r.preparation.check(json.number("revision",-1),System.currentTimeMillis())
        r.rewardWallet.finish(json.text("token"));r.pendingRewardPokemon=null;r.preparation.changed();note(p,"이번 뽑기 결과를 포기했습니다.");sendRun(p,r)
    }
    private fun fight(p: ServerPlayer, automatic: Boolean = false) {
        if (runs[p.uuid]?.state?.inBattle == true) return
        available(p); ensureMap()
        val run = requireNotNull(runs[p.uuid]); require(run.state.ready && !run.state.inBattle)
        require(p.level().dimension() == dimension)
        val centerOfRoom = rooms.getOrNull(run.room)
        require(automatic || (centerOfRoom!=null&&abs(p.x - centerOfRoom.first) <= 40 && abs(p.z - centerOfRoom.second) <= 40)) { "현재 선택방에서 도전을 시작하세요." }
        val other = requireNotNull(run.opponent?.let(::player)) { "상대가 접속 중이지 않습니다." }
        val opponentRun = requireNotNull(runs[other.uuid])
        require(opponentRun.opponent == p.uuid)
        run.waiting = true
        if (!opponentRun.waiting || !opponentRun.state.ready) {
            note(p, "상대의 선택과 준비 완료를 기다리고 있습니다."); sendRun(p, run); return
        }
        available(other)
        val otherRoom = rooms.getOrNull(opponentRun.room)
        require(other.level().dimension() == dimension && (automatic || (otherRoom!=null&&abs(other.x-otherRoom.first) <= 40 && abs(other.z-otherRoom.second) <= 40))) { "상대가 자신의 선택방으로 돌아와야 합니다." }
        val arena = requireNotNull(arenas.acquire(run.pairId)) { "모든 리그장이 사용 중입니다. 잠시 후 다시 준비 버튼을 누르세요." }
        val center = centers[arena]
        val origins = mapOf(p.uuid to point(p), other.uuid to point(other))
        try {
            protectDraftMoves(run); protectDraftMoves(opponentRun)
            run.party.forEach { it.recall() }; opponentRun.party.forEach { it.recall() }
            stopWatching(p);stopWatching(other)
            teleport(p, center.first - 20.5, center.second + 0.5, -90f)
            teleport(other, center.first + 20.5, center.second + 0.5, 90f)
            // Native level-adjusted battle stores keep send-out ownership separate from
            // the player's real party and from the persistent draft roster.
            val result = BattleBuilder.pvp1v1(p, other, battleFormat = format(run.format, 50), cloneParties = true,
                healFirst = true, partyAccessor = { if (it.uuid == p.uuid) run.party else opponentRun.party })
            require(result is SuccessfulBattleStart) { "던전 전투를 시작하지 못했습니다." }
            matches[run.pairId] = Match(run.pairId, result.battle, listOf(p.uuid, other.uuid), arena, origins, p.uuid, server.tickCount)
            run.state.fight(); opponentRun.state.fight()
            run.waiting = false; opponentRun.waiting = false
            LeagueMod.send(p, packet("battle_started")); LeagueMod.send(other, packet("battle_started"))
        } catch (e: Exception) {
            abortParticipant(p.uuid, "전투 시작 중 오류로 도전을 종료했습니다.")
            arenas.release(run.pairId)
            throw e
        }
    }
    private fun endRunGroup(id: UUID, message: String, disconnected: UUID? = null) {
        val first = runs[id] ?: return
        val group = runs.values.filter { it.pairId == first.pairId }
        cleanupParticipants(group,
            detach = { run -> runs.remove(run.player); lastPcNotice.remove(run.player); run.waiting = false; arenas.release(run.pairId) },
            discard = { run ->
                clearDisplays(run)
                run.party.forEach { mon -> attempt("discard temporary party ${mon.uuid}") { mon.entity?.discard() } }
                run.partyMegaForms.clear()
                run.party.observerUUIDs.clear()
                if(run.room in rooms.indices)server.getLevel(dimension)?.let{DungeonRerollControl.ensure(it,rooms[run.room],run.room,0,false)}
            },
            unlock = { run -> player(run.player)?.takeIf { it.uuid != disconnected }?.let { p ->
                check(p.party().toGappyList().map { it?.uuid } == run.originalIds) { "원래 파티 복귀 확인에 실패했습니다." }
                if (p.uuid !in p.party().observerUUIDs) p.party().observerUUIDs.add(p.uuid)
                syncStorageLock(p,false); syncVisibleParty(p, p.party())
            } },
            restore = { run -> player(run.player)?.takeIf { it.uuid != disconnected && it.isAlive }?.let { restoreRecorded(it, run.returnPoint) } },
            notify = { run -> player(run.player)?.takeIf { it.uuid != disconnected }?.let { LeagueMod.send(it,packet("close")); note(it,message) } },
            failed = { step, error -> LeagueMod.LOGGER.error("Paired cleanup step failed: $step",error) })
    }
    private fun detachMatch(match: Match) {
        if (matches.remove(match.id) == null) return
        arenas.release(match.id)
        stopBattle(match)
    }
    private fun stopBattle(match: Match) {
        watchVisits.filterValues{it.battle==match.battle.battleId}.keys.toList().mapNotNull(::player).forEach{stopWatching(it)}
        BattleAnimationRecovery.forget(match.battle.battleId)
        if (!match.battle.ended) {
            attempt("stop battle ${match.id}") { match.battle.stop() }
            if (!match.battle.ended) attempt("end battle ${match.id}") { match.battle.end() }
        }
        match.battle.actors.forEach { actor -> actor.pokemonList.forEach { mon ->
            attempt("discard battle entity ${mon.uuid}") {
                mon.entity?.let { it.battleId=null; it.discard() }
                mon.effectedPokemon.state = com.cobblemon.mod.common.pokemon.activestate.InactivePokemonState()
                mon.originalPokemon.state = com.cobblemon.mod.common.pokemon.activestate.InactivePokemonState()
            }
        } }
        match.players.mapNotNull(::player).forEach { p -> attempt("close battle client ${p.uuid}") {
            com.cobblemon.mod.common.net.messages.client.battle.BattleEndPacket().sendToPlayer(p)
        } }
        attempt("unregister battle ${match.id}") { if(BattleRegistry.getBattle(match.battle.battleId)!=null)BattleRegistry.closeBattle(match.battle) }
    }
    /** Spectator access is scoped to this lobby; no game-mode or persistent-party changes. */
    fun spectatorParticipants():Set<UUID> = lobbyGame?.model?.alive?.toSet().orEmpty()
    private fun watchableMatches(id:UUID):List<Match> {
        val r=runs[id]?:return emptyList();val g=lobbyGame?:return emptyList()
        if(r.lobbyGame!=g.id||g.phase!=LobbyGamePhase.BATTLES||r.state.inBattle)return emptyList()
        return matches.values.filter{m->!m.battle.ended&&!m.restartFailed&&id !in m.players&&m.players.any{runs[it]?.lobbyGame==g.id}}
    }
    fun isWatching(id:UUID)=id in watchVisits
    fun canSpectate(id:UUID)=watchableMatches(id).isNotEmpty()
    fun watchNextBattle(p:ServerPlayer):String {
        val list=watchableMatches(p.uuid);require(list.isNotEmpty()){"관전할 경기가 없습니다. 자기 전투를 마치거나 대기 중일 때 사용할 수 있습니다."}
        val current=watchVisits[p.uuid]?.battle;val next=list[(list.indexOfFirst{it.battle.battleId==current}+1)%list.size]
        return watchBattle(p,next.battle.battleId)
    }
    fun openWatchMenu(p:ServerPlayer)=sendWatchList(p)
    private fun sendWatchList(p:ServerPlayer) {
        LeagueMod.send(p,packet("watch_list"){
            addProperty("watching",p.uuid in watchVisits)
            add("battles",JsonArray().also{a->watchableMatches(p.uuid).forEach{m->a.add(JsonObject().apply{
                addProperty("id",m.battle.battleId.toString());addProperty("label",m.battle.actors.joinToString(" vs "){it.getName().string})
                addProperty("kind",if(m.creepKind!=null)"관장전" else "플레이어전")
            })}})
        })
    }
    fun watchBattle(p:ServerPlayer,id:UUID):String {
        val m=requireNotNull(watchableMatches(p.uuid).firstOrNull{it.battle.battleId==id}){"참여 중인 같은 던전의 다른 진행 중 경기만 관전할 수 있습니다."}
        require(com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(p)==null){"자기 전투 중에는 관전할 수 없습니다."}
        val origin=watchVisits[p.uuid]?.origin?:point(p);val invulnerable=watchVisits[p.uuid]?.invulnerable?:p.isInvulnerable
        stopWatching(p)
        val center=m.battleCenter?:centers[m.arena];val world=requireNotNull(server.getLevel(dimension))
        // A small observation platform is separate from Pokemon/player send-out positions.
        for(x in -1..1)for(z in -1..1){val pos=BlockPos(center.first+x,70,center.second+17+z);if(world.getBlockState(pos).isAir)world.setBlock(pos,Blocks.GLASS.defaultBlockState(),3)}
        val anchor=Vec3(center.first+0.5,71.0,center.second+17.5)
        watchVisits[p.uuid]=WatchVisit(id,origin,anchor,invulnerable);p.isInvulnerable=true
        p.teleportTo(world,anchor.x,anchor.y,anchor.z,180f,20f)
        m.battle.spectators.add(p.uuid)
        LeagueMod.send(p,packet("watch_started"))
        com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket(m.battle,m.battle.side1).sendToPlayer(p)
        com.cobblemon.mod.common.net.messages.client.battle.BattleMessagePacket(m.battle.chatLog).sendToPlayer(p)
        return "관전 중 · "+m.battle.actors.joinToString(" vs "){it.getName().string}+" · F4에서 관전 종료"
    }
    fun stopWatching(p:ServerPlayer):String {
        val visit=watchVisits.remove(p.uuid)?:return "관전 중이 아닙니다."
        BattleRegistry.getBattle(visit.battle)?.spectators?.remove(p.uuid)
        p.isInvulnerable=visit.invulnerable
        val own=BattleRegistry.getBattleByParticipatingPlayer(p)
        if(own==null){
            com.cobblemon.mod.common.net.messages.client.battle.BattleEndPacket().sendToPlayer(p)
            val world=server.allLevels.firstOrNull{it.dimension().location().toString()==visit.origin.dimension}
            if(world!=null)p.teleportTo(world,visit.origin.x,visit.origin.y,visit.origin.z,visit.origin.yaw,visit.origin.pitch)
            LeagueMod.send(p,packet("watch_stopped"))
        }
        return "관전을 종료하고 원래 위치로 돌아왔습니다."
    }
    private fun tickSpectators() {
        watchVisits.toMap().forEach{(id,visit)->
            val p=player(id)
            if(p==null){BattleRegistry.getBattle(visit.battle)?.spectators?.remove(id);watchVisits.remove(id);return@forEach}
            if(watchableMatches(id).none{it.battle.battleId==visit.battle}||!p.isAlive||p.level().dimension()!=dimension){stopWatching(p);return@forEach}
            if(p.position().distanceToSqr(visit.anchor)>16||p.y<70.5)p.teleportTo(p.serverLevel(),visit.anchor.x,visit.anchor.y,visit.anchor.z,180f,20f)
        }
    }
    private fun sendBattleControls(p:ServerPlayer,match:Match) {
        LeagueMod.send(p,packet("battle_control"){
            addProperty("battleId",match.battle.battleId.toString());addProperty("restartVoted",match.restartVotes.voted(p.uuid))
            addProperty("restartVotes",match.restartVotes.count);addProperty("battleParticipants",match.players.size)
        })
    }
    fun restartBattle(p:ServerPlayer,attempt:String="") {
        val match=requireNotNull(matches.values.firstOrNull{p.uuid in it.players}){"참여 중인 대전이 없습니다."}
        require(attempt.isBlank()||attempt==match.battle.battleId.toString()){"이전 대전의 재시작 요청입니다."}
        require((!match.battle.ended||match.restartFailed)&&match.finishedAt==null&&match.surrenderedBy==null){"종료된 대전은 재시작할 수 없습니다."}
        require(server.tickCount-match.createdAt>=100){"전투 시작 후 5초가 지나면 재시작할 수 있습니다."}
        require(match.players.all{player(it)?.isAlive==true}){"접속 중인 참가자만 재시작할 수 있습니다."}
        if(!match.restartVotes.request(p.uuid)){
            match.players.mapNotNull(::player).forEach{other->
                note(other,"${p.name.string}님이 이 대전의 재시작을 요청했습니다. 두 사람 모두 F4에서 동의하면 무효 후 재시작합니다.")
                runs[other.uuid]?.let{sendRun(other,it,false)}?:sendBattleControls(other,match)
            };return
        }
        // Detach this attempt before stopping it, so no old callback can count its result.
        val teams=match.players.associateWith{id->runs[id]?.party?:party(requireNotNull(player(id)),
            requireNotNull(match.battle.getActor(requireNotNull(player(id)))).pokemonList.map{it.originalPokemon.clone(registryAccess=server.registryAccess())})}
        val g=lobbyGame
        val creep=if(match.creepKind!=null)requireNotNull(g?.currentCreeps?.get(p.uuid)) else null
        matches.remove(match.id)
        try {
            if(creep!=null)g!!.currentCreeps.remove(p.uuid)
            stopBattle(match);creep?.handle?.abort()
            teams.values.forEach{store->store.forEach{it.recall();it.heal()}}
            if(creep!=null){
                val r=runs.getValue(p.uuid);if(r.state.inBattle)r.state.retryBattle()
                startCreepBattle(requireNotNull(g),p.uuid,requireNotNull(match.creepKind))
            }else{
                val a=requireNotNull(player(match.players[0]));val b=requireNotNull(player(match.players[1]))
                val result=BattleBuilder.pvp1v1(a,b,battleFormat=match.battle.format,cloneParties=true,healFirst=true,partyAccessor={teams.getValue(it.uuid)})
                require(result is SuccessfulBattleStart){"대전을 다시 시작하지 못했습니다."}
                matches[match.id]=match.copy(battle=result.battle,createdAt=server.tickCount,finishedAt=null,
                    restartVotes=BattleRestartVote(match.players.toSet()),restartFailed=false)
                match.players.mapNotNull(::player).forEach{LeagueMod.send(it,packet("battle_started"))}
            }
            match.players.mapNotNull(::player).forEach{note(it,"이 대전만 무효로 하고 같은 파티로 다시 시작했습니다. HP·승패·돈·보상은 정산하지 않습니다.")}
        }catch(e:Exception){
            LeagueMod.LOGGER.error("Battle restart failed for {}",match.id,e)
            // Keep only this duel waiting for retry/explicit surrender. Do not withdraw a
            // participant or settle an ended attempt: that could terminate parallel battles.
            val waiting=match.copy(createdAt=server.tickCount,restartVotes=BattleRestartVote(match.players.toSet()),restartFailed=true)
            matches[match.id]=waiting
            if(creep!=null){g!!.currentCreeps[p.uuid]=creep;runs[p.uuid]?.let{if(!it.state.inBattle&&it.state.ready)it.state.fight()}}
            match.players.mapNotNull(::player).forEach{member->
                note(member,"이 대전의 재시작에 실패했습니다. 5초 후 다시 요청하거나 항복할 수 있습니다. 다른 전투와 점수는 유지됩니다.")
                runs[member.uuid]?.let{sendRun(member,it)}?:sendBattleControls(member,waiting)
            }
        }
    }
    fun surrender(p: ServerPlayer) {
        val match = matches.values.firstOrNull { p.uuid in it.players }
        if (match==null || match.battle.ended&&!match.restartFailed) { note(p,"항복할 수 있는 진행 중 대전이 없습니다."); return }
        if (match.surrenderedBy!=null) { stopBattle(match); finish(match,true); return }
        try {
            val actor = requireNotNull(match.battle.getActor(p))
            require(actor.showdownId in setOf("p1","p2","p3","p4"))
            match.surrenderedBy=p.uuid; match.surrenderedAt=server.tickCount
            match.battle.writeShowdownAction(">forcelose ${actor.showdownId}")
            match.players.mapNotNull(::player).forEach { note(it,"${p.name.string}님이 이번 대전에 항복했습니다.") }
            // Closing locally does not depend on the animation/Showdown output queue.
            stopBattle(match); finish(match,true)
        } catch (e: Exception) {
            LeagueMod.LOGGER.error("Surrender failed for {}",match.id,e)
            abortParticipant(p.uuid,"항복 처리 중 전투 엔진 오류로 대전을 종료했습니다.")
        }
    }
    fun surrenderGame(p: ServerPlayer) {
        val run = runs[p.uuid]
        if(run?.lobbyGame!=null){withdrawLobbyPlayer(p.uuid,"던전을 나가 탈락 처리했습니다.");return}
        if (run==null) { surrender(p); return }
        val otherRun=run.opponent?.let { runs[it] }
        val other=otherRun?.let { player(it.player) }
        if (otherRun==null || other==null || !other.isAlive) {
            abortParticipant(p.uuid,"상대가 없어 도전을 종료했습니다."); return
        }
        val group=listOf(run,otherRun)
        val names=listOf(p.name.string,other.name.string)
        val winner=pointsWinner(run.state.points,otherRun.state.points,0)!!
        val result=packet("result") {
            addProperty("verdict","${names[winner]}님 최종 승리")
            addProperty("gameSurrender",true); addProperty("reason","${p.name.string}님 게임 항복")
            group.forEachIndexed { i,member ->
                addProperty("name$i",names[i]); addProperty("points$i",member.state.points)
                addProperty("record$i","${member.state.wins}승 ${member.state.draws}무 ${member.state.losses}패")
            }
        }
        matches.values.firstOrNull { p.uuid in it.players }?.takeIf { !it.battle.ended }?.let { match ->
            attempt("game surrender command ${match.id}") {
                val actor=requireNotNull(match.battle.getActor(p))
                require(actor.showdownId in setOf("p1","p2","p3","p4"))
                match.battle.writeShowdownAction(">forcelose ${actor.showdownId}")
            }
        }
        abortParticipant(p.uuid,"${p.name.string}님 게임 항복 · ${other.name.string}님 최종 승리")
        group.mapNotNull { player(it.player) }.forEach { LeagueMod.send(it,result) }
    }
    private fun restorePvp(match: Match, disconnected: UUID? = null) {
        match.players.mapNotNull(::player).filter { it.uuid != disconnected }.forEach { p ->
            attempt("restore PVP original party ${p.uuid}") { restoreDuelParty(p) }
        }
        match.origins.forEach { (id, origin) ->
            player(id)?.takeIf { it.uuid != disconnected && it.isAlive }?.let { p ->
                attempt("PVP return $id") { restoreRecorded(p,origin,origin.dimension != dimension.location().toString()) }
            }
        }
    }
    private fun abortParticipant(id: UUID, message: String, disconnected: UUID? = null) {
        if(runs[id]?.lobbyGame!=null){
            val g=lobbyGame
            if(g!=null&&(message.contains("오류")||message.contains("엔진")))finishLobbyGame(g,message,true)
            else withdrawLobbyPlayer(id,message,disconnected)
            return
        }
        matches.values.filter { id in it.players }.toList().forEach { match ->
            detachMatch(match)
            if (match.run == null) restorePvp(match,disconnected)
            else match.players.firstOrNull { it in runs }?.let { endRunGroup(it,message,disconnected) }
        }
        endRunGroup(id,message,disconnected)
        challenges.values.filter { id in listOf(it.sender,it.receiver) }.toList().forEach { c ->
            removeChallenge(c.id)
            listOf(c.sender,c.receiver).filter { it != disconnected }.mapNotNull(::player).forEach { note(it,message) }
        }
    }
    fun leave(p: ServerPlayer) {
        if(p.uuid in lobbyQueue.members()){leaveLobby(p);return}
        if (p.uuid in runs) { surrenderGame(p); return }
        abortParticipant(p.uuid,"도전을 종료하고 임시 파티를 정리했습니다.")
        if (p.isAlive) returns?.get(p.uuid)?.let { origin -> attempt("leave return ${p.uuid}") { restoreRecorded(p,origin) } }
        syncStorageLock(p,isStorageLocked(p.uuid)); LeagueMod.send(p,packet("close"))
    }
    fun disconnect(id: UUID) {
        player(id)?.let{stopWatching(it)}
        player(id)?.let{stopWatching(it)}
        lobbyQueue.leave(id);lobbyOptOut.remove(id)
        clientProtocols.remove(id)
        abortParticipant(id,"상대가 접속을 종료하여 도전을 마쳤습니다.",id)
        lastRequest.remove(id); lastPcNotice.remove(id)
    }
    fun shutdown() {
        watchVisits.keys.toList().mapNotNull(::player).forEach{stopWatching(it)}
        lobbyGame?.let{finishLobbyGame(it,"서버 종료로 던전을 마쳤습니다.",true)}
        lobbyQueue.members().mapNotNull(::player).forEach{leaveLobby(it)}
        BattleAnimationRecovery.prune(matches.values.map{it.battle.battleId}.toSet())
        matches.values.toList().forEach { match ->
            detachMatch(match)
            if (match.run == null) restorePvp(match)
        }
        runs.keys.toList().forEach { endRunGroup(it,"서버 종료로 도전을 마쳤습니다.") }
        challenges.keys.toList().forEach(::removeChallenge); lastPcNotice.clear()
    }
    fun tick() {
        tickSpectators()
        runs.values.toList().forEach { run ->
            if (run.player !in runs) return@forEach
            try {
                val p = player(run.player)
                if (p == null) { abortParticipant(run.player,"상대 접속이 끊겨 도전을 종료했습니다.",run.player); return@forEach }
                if (!p.isAlive || p.isSpectator || !participantInside(p,run)) {
                    abortParticipant(p.uuid,"사망 또는 도전 구역 이탈로 도전을 종료했습니다."); return@forEach
                }
                val automaticOpponent = run.opponent?.let { runs[it] }
                val now = System.currentTimeMillis()
                if (run.lobbyGame==null&&automaticOpponent != null && autoDraftStartReady(run.state, automaticOpponent.state, true)) {
                    if (run.preparation.deadline == 0L) {
                        run.preparation.begin(now); automaticOpponent.preparation.begin(now)
                        sendRun(p, run, false); player(automaticOpponent.player)?.let { sendRun(it, automaticOpponent, false) }
                    }
                    if (run.preparation.ready(now) && automaticOpponent.preparation.ready(now) && arenas.available()) fight(p, automatic = true)
                }
                if (!run.state.inBattle) { compactDraftParty(run); protectDraftMoves(run) }
                val order = run.party.map { it.uuid }
                if (order != run.lastOrder) { run.lastOrder = order; if (!run.state.inBattle) sendRun(p, run, false) }
                if (server.tickCount % 20 == 0) sendRun(p, run, false)
                run.displays.indices.forEach { index->
                    val (entity,anchor)=run.displays[index]
                    if(entity.isRemoved){
                        if(server.tickCount%20==0){
                            val replacement=spawnChoice(p.serverLevel(),entity.pokemon,anchor)
                            displayIds.remove(entity.uuid)
                            run.displayMegaForms.remove(entity.uuid)?.let{run.displayMegaForms[replacement.uuid]=it}
                            run.displays[index]=replacement to anchor
                        }
                        return@forEach
                    }
                    entity.deltaMovement = Vec3.ZERO; entity.moveTo(anchor.x,anchor.y,anchor.z,180f,0f)
                }
                if (!run.state.ready && !run.state.inBattle) {
                    val contact = run.displays.indexOfFirst { !it.first.isRemoved && p.boundingBox.inflate(0.15).intersects(it.first.boundingBox) }
                    if (contact < 0) run.armed = true
                    else if (run.armed) {
                        run.armed = false
                        if(run.party.count()>=6&&run.replacementSlot<0)run.replacementSlot=5
                        pick(p,JsonObject().apply {
                            addProperty("pokemon",run.choices[contact]); addProperty("version",run.state.version); addProperty("slot",run.replacementSlot)
                        })
                    }
                }
            } catch (e: Exception) {
                LeagueMod.LOGGER.error("Draft tick failed for {}",run.player,e)
                abortParticipant(run.player,"도전 처리 오류로 임시 파티를 정리했습니다.")
            }
        }
        challenges.values.filter { server.tickCount>it.expires }.toList().forEach { c ->
            removeChallenge(c.id); listOf(c.sender,c.receiver).mapNotNull(::player).forEach { note(it,"리그 대전 신청이 만료되었습니다.") }
        }
        BattleAnimationRecovery.prune(matches.values.map{it.battle.battleId}.toSet())
        matches.values.toList().forEach { match ->
            if (match.id !in matches || match.restartFailed) return@forEach
            try {
                BattleAnimationRecovery.tick(match.battle,server.tickCount)
                val center = match.battleCenter ?: centers[match.arena]
                val invalid = match.players.firstOrNull { id ->
                    val p = player(id)
                    p==null || !p.isAlive || p.isSpectator || p.level().dimension()!=dimension ||
                        abs(p.x-center.first)>80 || abs(p.z-center.second)>55 || abs(p.y-65)>20
                }
                if (invalid!=null) abortParticipant(invalid,"전투 참가자가 이탈하여 대전을 종료했습니다.",if(player(invalid)==null)invalid else null)
                else if (!match.battle.ended && match.surrenderedAt?.let { server.tickCount-it>200 }==true) {
                    stopBattle(match); finish(match,true)
                } else if (!match.battle.started && !match.battle.ended && server.tickCount-match.createdAt>1200)
                    abortParticipant(match.players.first(),"전투 엔진이 시작되지 않아 도전을 종료했습니다.")
                else if (match.battle.ended) {
                    if (match.finishedAt==null) match.finishedAt=server.tickCount+60L
                    if (server.tickCount>=match.finishedAt!!) finish(match,true)
                }
            } catch (e: Exception) {
                LeagueMod.LOGGER.error("Battle lifecycle failed for {}",match.id,e)
                abortParticipant(match.players.first(),"전투 처리 오류로 도전을 종료했습니다.")
            }
        }
        try {tickLobby()}catch(e:Exception){LeagueMod.LOGGER.error("Lobby progression failed",e);lobbyGame?.let{finishLobbyGame(it,"로비 진행 오류로 던전을 종료했습니다.",true)}}
        if(server.tickCount%20==0&&lobbyQueue.members().isNotEmpty())broadcastLobby()
        if (server.tickCount%100==0) pendingReturns.toList().forEach { id ->
            if (id !in runs && matches.values.none { id in it.players }) player(id)?.takeIf { it.isAlive }?.let { p ->
                returns?.get(id)?.let { point -> attempt("retry return $id") { restoreRecorded(p,point) } }
            }
        }
        if (server.tickCount%100==0) pendingCompletions.toList().forEach { id ->
            if (attempt("complete returned position $id") { returns?.complete(id) }) pendingCompletions.remove(id)
        }
        if (server.tickCount%100==0) duelOriginalParties.keys.toList().forEach { id ->
            if (matches.values.none { id in it.players }) player(id)?.let { p ->
                attempt("retry original party synchronization $id") { restoreDuelParty(p) }
            }
        }
    }
    private fun finish(match: Match, normal: Boolean, disconnected: UUID? = null) {
        if (matches.remove(match.id) == null) return
        arenas.release(match.id)
        val run = match.run?.let { runs[it] } ?: match.players.mapNotNull { runs[it] }.firstOrNull()
        try {
        val lobby=lobbyGame
        if(run?.lobbyGame!=null&&lobby!=null&&run.lobbyGame==lobby.id){
            if(match.creepKind!=null){finishCreepBattle(lobby,match);return}
            val duel=requireNotNull(lobby.current)
            val winner=if(match.surrenderedBy!=null)match.players.firstOrNull{it!=match.surrenderedBy} else match.players.firstOrNull{id->match.battle.winners.any{id in it.getPlayerUUIDs()}}
            stopBattle(match);finishLobbyDuel(lobby,duel,winner,match.battle);return
        }
        if (run != null) {
            val group = listOfNotNull(run, run.opponent?.let { runs[it] })
            if (!normal || group.size != 2 || group.any { player(it.player)?.isAlive != true }) {
                endRunGroup(run.player, "도전이 종료되어 양쪽 임시 파티를 정리했습니다.", disconnected)
            } else {
                group.forEach { member ->
                    match.battle.actors.filter { member.player in it.getPlayerUUIDs() }.forEach { actor -> actor.pokemonList.forEach { battleMon ->
                        val original = member.party.firstOrNull { it.uuid == battleMon.originalPokemon.uuid }
                        if (original != null && DungeonShop.products.any { it.consumable && it.id == BuiltInRegistries.ITEM.getKey(original.heldItem().item).toString() } &&
                            battleMon.effectedPokemon.heldItem().isEmpty) (original as DungeonHeldItemAccessor).`cobbleleague$setVirtualItem`(ItemStack.EMPTY)
                    } }
                    val outcome = when {
                        match.surrenderedBy==member.player -> RoundOutcome.LOSS
                        match.surrenderedBy!=null -> RoundOutcome.WIN
                        match.battle.winners.any { member.player in it.getPlayerUUIDs() } -> RoundOutcome.WIN
                        match.battle.winners.isEmpty() -> RoundOutcome.DRAW
                        else -> RoundOutcome.LOSS
                    }
                    member.state.finishRound(outcome)
                    member.preparation.nextRound(outcome == RoundOutcome.LOSS)
                    player(member.player)?.let { note(it, if (outcome == RoundOutcome.LOSS) "라운드 보상 · 리롤 1회와 패배 보상 1원을 받았습니다." else "라운드 보상 · 리롤 1회를 받았습니다.") }
                    player(member.player)?.let { note(it, "${member.state.round}라운드 ${outcome.label} · +${outcome.points}점 · 누적 ${member.state.points}점") }
                }
                if (group.all { it.state.completed }) {
                    val names = group.map { player(it.player)?.name?.string ?: "플레이어" }
                    val winner = pointsWinner(group[0].state.points, group[1].state.points)
                    val verdict = if (winner == null) "무승부" else "${names[winner]}님 최종 승리"
                    val result = packet("result") {
                        addProperty("verdict", verdict)
                        group.forEachIndexed { i, member ->
                            addProperty("name$i", names[i]); addProperty("points$i", member.state.points)
                            addProperty("record$i", "${member.state.wins}승 ${member.state.draws}무 ${member.state.losses}패")
                        }
                    }
                    endRunGroup(run.player, "12라운드 종료 · $verdict · ${group[0].state.points}:${group[1].state.points}점 · 임시 파티를 정리했습니다.")
                    group.mapNotNull { player(it.player) }.forEach { LeagueMod.send(it, result) }
                } else group.forEach { member ->
                    val p = requireNotNull(player(member.player))
                    member.party.forEach { it.heal() }; member.replacementSlot = -1
                    moveRoom(p, member); offer(member); syncVisibleParty(p, member.party); sendRun(p, member)
                }
            }
        } else { stopBattle(match); restorePvp(match,disconnected) }
        } catch (e: Exception) {
            LeagueMod.LOGGER.error("Post-battle progression failed for {}",match.id,e)
            if(run?.lobbyGame!=null&&lobbyGame!=null)finishLobbyGame(requireNotNull(lobbyGame),"라운드 진행 오류로 도전을 종료했습니다.",true)
            else if (run!=null) endRunGroup(run.player,"라운드 진행 오류로 도전을 종료했습니다.",disconnected)
            else restorePvp(match,disconnected)
        }
    }
}

