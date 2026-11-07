package dev.cobbleleague

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.FormData
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import kotlin.random.Random

/** Random choices come from the actually loaded registry, including modpack additions and mega forms. */
class RandomRoster {
    data class Candidate(val species: Species, val form: FormData) {
        val key get() = "${species.resourceIdentifier}|${form.name}"
        val rarity get() = Rarity.fromLabels(species.labels + form.labels)
        val name get() = species.translatedName.string + if (form == species.standardForm) "" else " (${form.name})"
        val isMega get() = "mega" in form.labels || form.name.startsWith("Mega",true) || form.aspects.any { it.startsWith("mega") }
        fun enforceMega(pokemon: Pokemon) {
            if (!isMega) return
            val expected = pokemon.forcedAspects.filterNot { it.startsWith("mega") }.toSet() + form.aspects
            if (pokemon.forcedAspects != expected) pokemon.forcedAspects = expected
            if (pokemon.form != form) pokemon.form = form
        }
        fun create(): Pokemon {
            return PokemonProperties.parse("${species.resourceIdentifier} level=50").create().also {
                it.forcedAspects = form.aspects.toSet()
                it.form = form
                it.initializeMoveset(preferLatest = true)
                it.heal()
            }
        }
    }
    val baseOdds:DrawOdds
    init {
        val file=FabricLoader.getInstance().configDir.resolve("cobbleleague-random.json")
        baseOdds=if(Files.exists(file))runCatching{
            val json=com.google.gson.JsonParser.parseString(Files.readString(file)).asJsonObject
            if(json.has("mega")&&json.has("sublegendary"))DrawOdds(DrawCategory.entries.associateWith{category->
                json.get(category.id).asBigDecimal.multiply(java.math.BigDecimal(100)).intValueExact()
            }) else {
                val migrated=DrawOdds.migrateLegacy(json.get("normal").asInt,json.get("legendary").asInt,json.get("mythical").asInt,json.get("paradox").asInt)
                val backup=file.resolveSibling("cobbleleague-random.before-060-${System.currentTimeMillis()}.json");Files.copy(file,backup)
                Files.writeString(file,LeagueMod.GSON.toJson(migrated.values.mapKeys{it.key.id}.mapValues{it.value/100.0}));migrated
            }
        }.getOrElse{LeagueMod.LOGGER.warn("Invalid draw odds; using defaults without overwriting the configuration",it);DrawOdds()}
        else DrawOdds().also{Files.writeString(file,LeagueMod.GSON.toJson(it.values.mapKeys{it.key.id}.mapValues{it.value/100.0}))}
    }
    private fun mega(form:FormData)="mega" in form.labels||form.name.startsWith("Mega",true)||form.aspects.any{it.startsWith("mega")}
    private fun all():List<Candidate> = PokemonSpecies.implemented.filter{it.standardForm.evolutions.isEmpty()||it.forms.any(::mega)}.flatMap{species->
        (listOf(species.standardForm)+species.forms).distinctBy{it.name}.filter{form->
            (form==species.standardForm||form.evolutions.isEmpty())&&"gmax" !in form.labels&&form.aspects.none{it=="gmax"||it=="core-percent"}
        }.map{Candidate(species,it)}
    }
    fun category(candidate:Candidate)=DrawCategory.classify(candidate.species.labels+candidate.form.labels,candidate.isMega)
    fun poolAudit():Map<String,Int>{val candidates=all();return DrawCategory.entries.associate{kind->kind.id to candidates.filter{category(it)==kind}.map{it.species.resourceIdentifier}.distinct().size}}
    fun megaBasesPresent():Boolean{val candidates=all();return candidates.filter{it.isMega}.all{m->candidates.any{it.species==m.species&&it.form==m.species.standardForm&&!it.isMega}}}
    fun choose(count:Int,excluded:Set<String> = emptySet(),odds:DrawOdds=baseOdds):List<Candidate>{
        val excludedSpecies=excluded.map{it.substringBefore('|')}.toSet()
        val candidates=all().filter{it.species.resourceIdentifier.toString() !in excludedSpecies}
        require(candidates.map{it.species.resourceIdentifier}.distinct().size>=count){"등록된 최종진화체 데이터가 부족합니다."}
        val selected=mutableSetOf<String>()
        return List(count){
            val kind=odds.category(Random.nextInt(10000))
            val available=candidates.filter{it.species.resourceIdentifier.toString() !in selected}
            val group=available.filter{category(it)==kind}.ifEmpty{available.filter{category(it)==DrawCategory.NORMAL}.ifEmpty{available}}
            val perSpecies=group.groupBy{it.species.resourceIdentifier};val choice=perSpecies.values.random().random()
            selected.add(choice.species.resourceIdentifier.toString());choice
        }
    }
    fun resolve(key: String) = requireNotNull(all().firstOrNull { it.key == key }) { "후보 포켓몬 데이터가 변경되었습니다. 도전을 다시 시작하세요." }
    fun rewardPool(kind:CreepRewardKind):List<Candidate> = PokemonSpecies.implemented.filter { species ->
        species.standardForm.evolutions.isEmpty()&&when(kind){
            CreepRewardKind.MYTHICAL->"mythical" in species.labels
            CreepRewardKind.LEGENDARY->DrawCategory.classify(species.labels)==DrawCategory.LEGENDARY
        }
    }.map{Candidate(it,it.standardForm)}.distinctBy{it.species.resourceIdentifier}
    fun drawReward(kind:CreepRewardKind):Candidate=rewardPool(kind).also{require(it.isNotEmpty()){ "해당 보상 포켓몬 풀이 비어 있습니다." }}.random()
}
