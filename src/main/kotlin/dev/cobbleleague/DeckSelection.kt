package dev.cobbleleague

import java.util.UUID

data class DeckAssignment(val owner: UUID, val deckId: String)

/** Both modes choose from the opponent's catalogue; the eventual user of the deck differs. */
class DeckSelection(val first: UUID, val second: UUID, val mode: NativeLeagueMode,
                    private val catalogues: Map<UUID, Set<String>>) {
    init { require(first != second && mode != NativeLeagueMode.DRAFT); require(listOf(first, second).all { !catalogues[it].isNullOrEmpty() }) }
    private val choices = mutableMapOf<UUID, String>()
    private val confirmed = mutableSetOf<UUID>()
    fun opponent(player: UUID): UUID = when (player) { first -> second; second -> first; else -> error("대전 참가자가 아닙니다.") }
    fun selected(player: UUID): String? { opponent(player); return choices[player] }
    fun isConfirmed(player: UUID): Boolean { opponent(player); return player in confirmed }
    val ready get() = confirmed.size == 2
    fun select(player: UUID, deckId: String) {
        val owner = opponent(player)
        require(player !in confirmed) { "이미 덱 선택을 확정했습니다." }
        require(deckId in catalogues.getValue(owner)) { "상대의 유효한 등록 덱을 선택하세요." }
        choices[player] = deckId
    }
    fun confirm(player: UUID) {
        opponent(player)
        require(choices[player] != null) { "덱을 먼저 선택하세요." }
        require(confirmed.add(player)) { "이미 선택을 확정했습니다." }
    }
    fun assignment(player: UUID): DeckAssignment {
        // No team can be applied before both players explicitly confirm.
        val other = opponent(player)
        check(ready) { "양쪽 모두 덱 선택을 확정해야 합니다." }
        return if (mode == NativeLeagueMode.BORROW) DeckAssignment(other, choices.getValue(player))
        else DeckAssignment(player, choices.getValue(other))
    }
    fun reopenAfterStartFailure(player: UUID) { opponent(player); confirmed.remove(player) }
}

fun verifyBattleParties(expected: Map<UUID, List<UUID>>, actual: Map<UUID, List<UUID>>) {
    check(expected.keys == actual.keys) { "전투 참가자와 선택 덱의 참가자가 일치하지 않습니다." }
    expected.forEach { (player, ids) ->
        check(ids.isNotEmpty() && ids.distinct().size == ids.size && ids == actual[player]) {
            "선택 덱과 실제 전투 파티가 일치하지 않아 대전을 중단합니다."
        }
    }
}

/** Reorder only existing originals, without overwriting or duplicating a Pokemon. */
fun restoreOriginalPartyOrder(expected: List<UUID?>, read: () -> List<UUID?>, swap: (Int, Int) -> Unit) {
    val current = read()
    check(current.size == expected.size && current.groupingBy { it }.eachCount() == expected.groupingBy { it }.eachCount()) {
        "원래 파티의 포켓몬 구성이 바뀌어 자동 복귀 검사를 완료하지 못했습니다."
    }
    for (slot in expected.indices) {
        if (read()[slot] == expected[slot]) continue
        val target = (slot + 1 until expected.size).first { read()[it] == expected[slot] }
        swap(slot, target)
    }
    check(read() == expected) { "원래 파티 순서 복귀 검사가 실패했습니다." }
}
