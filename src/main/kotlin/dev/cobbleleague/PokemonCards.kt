package dev.cobbleleague

import com.cobblemon.mod.common.pokemon.Pokemon
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.core.registries.BuiltInRegistries
import com.cobblemon.mod.common.api.pokemon.stats.Stats

fun pokemonCard(mon: Pokemon) = JsonObject().apply {
    addProperty("tera",mon.teraType.id.toString())
    addProperty("uuid", mon.uuid.toString())
    addProperty("species", mon.species.resourceIdentifier.toString())
    addProperty("nickname", mon.nickname?.string.orEmpty())
    addProperty("form", if (mon.form == mon.species.standardForm) "" else mon.form.name)
    add("aspects", JsonArray().also { a -> mon.aspects.forEach { a.add(it) } })
    addProperty("item", BuiltInRegistries.ITEM.getKey(mon.heldItem().item).toString())
    addProperty("ability", mon.ability.name)
    addProperty("level", mon.level)
    addProperty("nature", mon.effectiveNature.name.path)
    add("stats", JsonObject().apply {
        listOf("HP" to Stats.HP, "공격" to Stats.ATTACK, "방어" to Stats.DEFENCE,
            "특공" to Stats.SPECIAL_ATTACK, "특방" to Stats.SPECIAL_DEFENCE, "스피드" to Stats.SPEED)
            .forEach { (label, stat) -> addProperty(label, mon.getStat(stat)) }
    })
    add("ivs",JsonArray().also { a -> DungeonTraining.stats.forEach{a.add(mon.ivs.getOrDefault(it))} })
    add("moves", JsonArray().also { a -> mon.moveSet.forEach { a.add(it.name) } })
}
