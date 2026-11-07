package dev.cobbleleague

/** A participant keeps the same private room through every round of one challenge. */
fun reusableSelectionRoom(current: Int, occupied: Set<Int>, count: Int): Int {
    require(count > 0)
    if (current in 0 until count && current !in occupied) return current
    return (0 until count).firstOrNull { it !in occupied }
        ?: error("선택방 두 개가 사용 중입니다. 진행 중인 도전이 끝난 뒤 이용하세요.")
}
