package dev.cobbleleague.client

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.google.gson.JsonObject
import dev.cobbleleague.text
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf

internal object LeagueCards {
    private val states = mutableMapOf<String, com.cobblemon.mod.common.client.render.models.blockbench.FloatingState>()
    fun name(card: JsonObject): String {
        val species = PokemonSpecies.getByIdentifier(ResourceLocation.parse(card.text("species")))
        val base = card.text("nickname").ifBlank { species?.translatedName?.string ?: card.text("species") }
        val form = card.text("form")
        return if (form.isBlank()) base else if (form.startsWith("Mega", true)) "메가 $base" else "$base ($form)"
    }
    fun move(id: String) = Moves.getByName(id)?.displayName?.string ?: id
    fun item(id: String) = runCatching { BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)).description.string }.getOrDefault(id)
    fun hint(card: JsonObject): List<String> = listOf(
        name(card),
        "기술: " + card.getAsJsonArray("moves").joinToString(" / ") { move(it.asString) },
        "특성: " + Component.translatable("cobblemon.ability.${card.text("ability")}").string,
        "도구: " + if (card.text("item") == "minecraft:air") "없음" else item(card.text("item"))
    )
    fun portrait(g: GuiGraphics, card: JsonObject, x: Int, y: Int) {
        val species = PokemonSpecies.getByIdentifier(ResourceLocation.parse(card.text("species"))) ?: return
        val aspects = card.getAsJsonArray("aspects")?.map { it.asString }?.toSet().orEmpty()
        if (states.size > 256) states.clear()
        g.pose().pushPose()
        try {
            g.pose().translate(x.toDouble(), y.toDouble(), 50.0)
            g.pose().scale(18f, 18f, 18f)
            val state=states.getOrPut(card.text("uuid").ifBlank { card.text("species") }) { com.cobblemon.mod.common.client.render.models.blockbench.FloatingState() }
            drawProfilePokemon(RenderablePokemon(species, aspects), g.pose(), Quaternionf(), state=state, partialTicks=0f, scale = 1f)
        } catch (_: Exception) {
            // Missing third-party models must not make the selection screen unusable.
        } finally { g.pose().popPose() }
    }
}
