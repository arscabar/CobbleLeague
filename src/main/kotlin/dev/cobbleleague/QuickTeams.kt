package dev.cobbleleague

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.party
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

data class SavedDeck(val id: String, val name: String, val members: List<UUID>)

/** The client supplies identifiers; all Pokemon and their actual stats come from server storage. */
object QuickTeams {
    fun readAndValidate(player: ServerPlayer, value: JsonArray?): List<SavedDeck> {
        if (value == null) return emptyList()
        require(value.size() <= 24) { "등록 덱 수가 너무 많습니다." }
        val owned = owned(player)
        return value.map { entry ->
            val obj = entry.asJsonObject
            val id = obj.text("id").take(32)
            val name = obj.text("name").take(48)
            val ids = obj.getAsJsonArray("members").map { UUID.fromString(it.asString) }
            require(ids.isNotEmpty() && ids.size <= 6 && ids.distinct().size == ids.size)
            require(ids.all { it in owned }) { "등록 덱의 포켓몬을 현재 보유하고 있지 않습니다." }
            SavedDeck(id, name, ids)
        }.also { require(it.map { d -> d.id }.distinct().size == it.size) }
    }
    fun owned(player: ServerPlayer): Map<UUID, Pokemon> =
        (player.party().toList() + Cobblemon.storage.getPC(player).toList()).associateBy { it.uuid }

    fun cloneDeck(player: ServerPlayer, deck: SavedDeck): List<Pokemon> {
        val owned = owned(player)
        return deck.members.map { id ->
            val pokemon = requireNotNull(owned[id]) { "선택한 덱의 포켓몬이 이동되었거나 없어졌습니다." }
            require(pokemon.entity?.isBusy != true) { "등록 덱의 포켓몬이 사용 중입니다." }
            pokemon.clone(registryAccess = player.registryAccess()).also { it.heal() }
        }
    }
    fun describe(decks: List<SavedDeck>, owner: ServerPlayer): JsonArray {
        val owned = owned(owner)
        return JsonArray().also { result -> decks.forEach { deck ->
            result.add(JsonObject().also { j ->
                j.addProperty("id", deck.id); j.addProperty("name", deck.name)
                j.addProperty("summary", deck.members.joinToString(" · ") { owned[it]?.species?.translatedName?.string ?: "없음" })
                j.add("cards", JsonArray().also { cards -> deck.members.mapNotNull { owned[it] }.forEach { cards.add(pokemonCard(it)) } })
            })
        } }
    }
}
