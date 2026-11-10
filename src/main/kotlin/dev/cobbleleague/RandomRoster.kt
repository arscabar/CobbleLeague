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
        fun create(): Pokemon {
            return PokemonProperties.parse("${species.resourceIdentifier} level=50").create().also {
                val initial=if(isMega)species.standardForm else form
                it.forcedAspects = initial.aspects.toSet()
                it.form = initial
                DungeonForms.grantDrawStone(this,it)
                it.initializeMoveset(preferLatest = true)
                DungeonRandomMoves.apply(it)
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
    private fun all():List<Candidate> = PokemonSpecies.species.filter{it.implemented}.flatMap{species->
        (listOf(species.standardForm)+species.forms).distinctBy{it.name}.filter{form->
            (form.evolutions.isEmpty() || form==species.standardForm&&species.forms.any{mega(it)&&DrawCategory.classify(species.labels+it.labels)==DrawCategory.NORMAL&&DungeonForms.megaStoneFor(Candidate(species,it))!=null}) &&
                "gmax" !in form.labels&&form.aspects.none{it=="gmax"||it=="core-percent"}
        }.map{Candidate(species,it)}.filter{c->!c.isMega ||
            DrawCategory.classify(c.species.labels+c.form.labels)==DrawCategory.NORMAL&&DungeonForms.megaStoneFor(c)!=null}
    }
    fun coverageAudit():Map<String,Int> {
        val pool=all()
        return mapOf("registered" to PokemonSpecies.species.size,"implemented" to PokemonSpecies.species.count{it.implemented},"species" to pool.map{it.species.resourceIdentifier}.distinct().size,"forms" to pool.size)
    }
    fun megaBasePool():List<Candidate> = all().filter{it.isMega}.map{Candidate(it.species,it.species.standardForm)}.distinctBy{it.species.resourceIdentifier}
    fun category(candidate:Candidate)=DrawCategory.classify(candidate.species.labels+candidate.form.labels,candidate.isMega)
    fun poolAudit():Map<String,Int>{val candidates=all();return DrawCategory.entries.associate{kind->kind.id to candidates.filter{category(it)==kind}.map{it.species.resourceIdentifier}.distinct().size}}
    fun megaBasesPresent():Boolean{val candidates=all();return candidates.filter{it.isMega}.all{m->candidates.any{it.species==m.species&&it.form==m.species.standardForm&&!it.isMega}}}
    fun choose(count:Int,excluded:Set<String> = emptySet(),odds:DrawOdds=baseOdds):List<Candidate> =
        chooseDiverse(count,excluded,odds,emptySet(),emptySet(),emptyMap())
    fun availableDraftTypes(starters:Boolean):Set<String> = (if(starters)megaBasePool() else all()).flatMap{(if(it.isMega)it.species.standardForm else it.form).types.map{t->t.name}}.toSet()
    private val starterIds=setOf("venusaur","charizard","blastoise","meganium","typhlosion","feraligatr","sceptile","blaziken","swampert","torterra","infernape","empoleon","serperior","emboar","samurott","chesnaught","delphox","greninja","decidueye","incineroar","primarina","rillaboom","cinderace","inteleon","meowscarada","skeledirge","quaquaval")
    fun starterPool():List<Candidate> = megaBasePool().filter{it.species.resourceIdentifier.path in starterIds}.distinctBy{it.species.resourceIdentifier}
    private fun draftPool(starters:Boolean)=if(starters)starterPool().also{require(it.size>=3){"메가 스타팅 데이터가 부족합니다."}}else all()
    fun chooseDiverse(count:Int,excluded:Set<String>,odds:DrawOdds,roundAvoid:Set<String>,recent:Set<String>,partyCounts:Map<String,Int>,starters:Boolean=false,type:String=""):List<Candidate>{
        val candidates=if(starters&&type.isNotBlank())megaBasePool()else draftPool(starters);val previous=excluded.map{it.substringBefore('|')}.toSet()
        require(candidates.map{it.species.resourceIdentifier}.distinct().size>=count){"등록된 최종진화체 데이터가 부족합니다."}
        val selected=mutableSetOf<String>()
        return List(count){
            val kind=if(starters)DrawCategory.NORMAL else odds.category(Random.nextInt(10000))
            val available=candidates.filter{it.species.resourceIdentifier.toString() !in selected}
            val typed=if(type.isBlank())available else available.filter{c->(if(c.isMega)c.species.standardForm else c.form).types.any{it.name==type}}.ifEmpty{available}
            val group=typed.filter{category(it)==kind}.ifEmpty{typed.filter{category(it)==DrawCategory.NORMAL}.ifEmpty{typed}}
            val fresh=DraftDiversity.preferred(group,{it.species.resourceIdentifier.toString()},listOf(previous))
            val perSpecies=fresh.groupBy{it.species.resourceIdentifier.toString()}
            val species=DraftSpeciesWeight.pick(perSpecies.keys.toList(),{id->DraftSpeciesWeight.value(partyCounts[id]?:0,id in roundAvoid,id in recent)})
            val choice=perSpecies.getValue(species).random()
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
