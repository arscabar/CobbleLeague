package dev.cobbleleague

/** Client selector IDs only. These values must never become a battle's adjusted level. */
enum class NativeLeagueMode(val selectorId: Int, val wireName: String, val translationKey: String) {
    BORROW(-10001, "borrow", "challenge.rule.cobbleleague.borrow"),
    PICK(-10002, "pick", "challenge.rule.cobbleleague.pick"),
    DRAFT(-10003, "draft", "challenge.rule.cobbleleague.draft");
    val requiresSavedTeams get() = this != DRAFT

    companion object {
        fun fromSelector(value: Int?) = entries.firstOrNull { it.selectorId == value }
    }
}

/** Preserve native and third-party rules and avoid duplicate additions on reinitialization. */
fun updateNativeRuleChoices(choices: MutableList<Int>, enabled: Boolean) {
    choices.removeAll { NativeLeagueMode.fromSelector(it) != null }
    if (enabled) choices.addAll(NativeLeagueMode.entries.map { it.selectorId })
}
