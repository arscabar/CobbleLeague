package dev.cobbleleague

data class SpawnProtection(
    val owned: Boolean = false,
    val stored: Boolean = false,
    val battle: Boolean = false,
    val tethered: Boolean = false,
    val display: Boolean = false,
    val busy: Boolean = false,
    val age: Int = 0
)

fun shouldRemoveLeagueWild(state: SpawnProtection): Boolean = state.age >= 100 &&
    !state.owned && !state.stored && !state.battle && !state.tethered && !state.display && !state.busy
