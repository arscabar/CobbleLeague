package dev.cobbleleague

fun autoDraftStartReady(first: DraftState, second: DraftState, arenaAvailable: Boolean): Boolean =
    arenaAvailable && first.ready && second.ready && !first.inBattle && !second.inBattle &&
        !first.completed && !second.completed
