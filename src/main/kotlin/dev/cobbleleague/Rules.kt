package dev.cobbleleague

import java.util.UUID

enum class LeagueFormat(val label: String, val slots: Int) {
    SINGLE("싱글", 1), DOUBLE("더블", 2), TRIPLE("트리플", 3), MULTI("멀티 2 대 2", 1);
    companion object { fun parse(value: String) = entries.firstOrNull { it.name == value } ?: SINGLE }
}

enum class RoundOutcome(val label: String, val points: Int) {
    WIN("승리", 3), DRAW("무승부", 1), LOSS("패배", 0)
}

/** A tied total has no winner and must not trigger a tiebreak round. */
fun pointsWinner(first: Int, second: Int, forfeitedPlayer: Int? = null): Int? = when {
    forfeitedPlayer != null -> { require(forfeitedPlayer in 0..1); 1-forfeitedPlayer }
    first > second -> 0
    second > first -> 1
    else -> null
}

/** Each round grants only its own picks before one battle can begin. */
class DraftState(val minimumTeam: Int, val rounds: Int = 12) {
    init { require(minimumTeam in 1..6 && rounds > 0) }
    var round = 0; private set
    var version = UUID.randomUUID().toString(); private set
    var ready = false; private set
    var inBattle = false; private set
    var teamSize = 0; private set
    var picksThisRound = 0; private set
    private val history = mutableListOf<RoundOutcome>()
    val requiredPicks get() = if (round == 0) minimumTeam else 1
    val remainingPicks get() = if (completed) 0 else (requiredPicks - picksThisRound).coerceAtLeast(0)
    val points get() = history.sumOf { it.points }
    val wins get() = history.count { it == RoundOutcome.WIN }
    val draws get() = history.count { it == RoundOutcome.DRAW }
    val losses get() = history.count { it == RoundOutcome.LOSS }
    val lastOutcome get() = history.lastOrNull()
    val completed get() = round >= rounds
    fun refreshChoices() {
        require(!ready && !inBattle && !completed)
        version = UUID.randomUUID().toString()
    }
    fun choose(token: String, replaceSlot: Int = -1) {
        require(!ready && !inBattle && !completed && token == version) { "만료된 선택입니다." }
        require(teamSize < 6 || replaceSlot in 0..5) { "교체할 포켓몬을 선택하세요." }
        if (teamSize < 6) teamSize++
        picksThisRound++
        ready = remainingPicks == 0
        version = UUID.randomUUID().toString()
    }
    fun skip(token:String) {
        require(token==version && !ready && !inBattle && !completed && teamSize>=minimumTeam) { "필요한 파티를 먼저 구성하세요." }
        picksThisRound=requiredPicks
        ready=true
        version=UUID.randomUUID().toString()
    }
    fun fight() { require(ready && !inBattle && !completed); inBattle = true }
    fun retryBattle(){require(inBattle && !completed);inBattle=false}
    fun syncTeamSize(size:Int){require(size in 0..6&&!inBattle);teamSize=size;version=UUID.randomUUID().toString()}
    fun finishBye() {
        require(ready && !inBattle && !completed)
        round++;ready=false;picksThisRound=0;version=UUID.randomUUID().toString()
    }
    fun finishRound(outcome: RoundOutcome) {
        require(inBattle && !completed)
        history.add(outcome)
        inBattle = false; round++; ready = false; picksThisRound = 0
        version = UUID.randomUUID().toString()
    }
}

class ArenaPool(private val count: Int) {
    private val owners = mutableMapOf<Int, UUID>()
    fun available() = owners.size < count
    fun acquire(owner: UUID): Int? {
        require(owner !in owners.values)
        val free = (0 until count).firstOrNull { it !in owners } ?: return null
        owners[free] = owner
        return free
    }
    fun release(owner: UUID) { owners.entries.removeIf { it.value == owner } }
}

enum class Rarity(val rgb: Int) {
    LEGENDARY(0xFFD45C), MYTHICAL(0xF397E6), PARADOX(0x62EDFF);
    companion object {
        fun fromLabels(labels: Set<String>): Rarity? {
            val normalized = labels.map { it.lowercase() }.toSet()
            return when {
                "mythical" in normalized -> MYTHICAL
                "legendary" in normalized -> LEGENDARY
                "paradox" in normalized -> PARADOX
                else -> null
            }
        }
    }
}

data class SpawnWeights(val normal: Int = 95, val legendary: Int = 1, val mythical: Int = 2, val paradox: Int = 2) {
    fun validate() { require(listOf(normal, legendary, mythical, paradox).all { it >= 0 } && normal + legendary + mythical + paradox == 100) { "출현 확률의 합은 100이어야 합니다." } }
    fun category(roll: Int): Rarity? {
        validate(); require(roll in 0..99)
        return when {
            roll < normal -> null
            roll < normal + legendary -> Rarity.LEGENDARY
            roll < normal + legendary + mythical -> Rarity.MYTHICAL
            else -> Rarity.PARADOX
        }
    }
}
