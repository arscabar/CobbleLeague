package dev.cobbleleague

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.registries.BuiltInRegistries

internal object DungeonTraining {
    const val GOLD = "minecraft:gold_nugget"
    const val ABILITY = "cobblemon:ability_capsule"
    const val SOUP = "mega_showdown:dynamax_candy"
    const val GMAX = "mega_showdown:max_soup"
    const val Z_CHOICE = "z_choice"
    const val MEGA_CHOICE = "mega_choice"
    val stats = listOf(Stats.HP,Stats.ATTACK,Stats.DEFENCE,Stats.SPECIAL_ATTACK,Stats.SPECIAL_DEFENCE,Stats.SPEED)

    fun stars(mon:Pokemon) = (stats.sumOf { mon.ivs.getOrDefault(it) } * 5 + 185) / 186
    fun consumable(id:String) = id in setOf(GOLD,ABILITY,SOUP,GMAX)

    private fun abilities(mon:Pokemon)=mon.form.abilities.toList().distinctBy { it.template.name }
    fun abilityOptions(mon:Pokemon)=abilities(mon).map{it.template.name}.filter{it!=mon.ability.name}

    fun apply(mon:Pokemon,id:String,ability:String="") {
        when(id) {
            GOLD -> {
                require(stats.any { mon.ivs.getOrDefault(it)<31 }) { "이미 개체값이 모두 31입니다." }
                stats.forEach { mon.ivs[it]=31 }
            }
            ABILITY -> {
                val next=abilities(mon).firstOrNull{it.template.name==ability && ability!=mon.ability.name}
                    ?: error("현재 폼에서 변경 가능한 특성을 선택하세요.")
                mon.updateAbility(next.template.create(false,next.priority))
            }
            SOUP -> {
                require(mon.dmaxLevel<10) { "이미 다이맥스 레벨이 최대입니다." }
                mon.dmaxLevel=10
            }
            GMAX -> {
                require(!mon.gmaxFactor && mon.species.forms.any { "gmax" in it.labels }) { "이 포켓몬에게 거다이스프를 적용할 수 없습니다." }
                mon.gmaxFactor=true
            }
        }
    }

    fun choices(token:String):List<String> {
        val type=when(token) {
            Z_CHOICE -> "com.github.yajatkaul.mega_showdown.item.custom.z.ElementalZCrystal"
            MEGA_CHOICE -> "com.github.yajatkaul.mega_showdown.item.custom.mega.MegaStone"
            else -> return emptyList()
        }
        val itemClass=runCatching { Class.forName(type) }.getOrNull() ?: return emptyList()
        return BuiltInRegistries.ITEM.filter { itemClass.isInstance(it) }
            .map { BuiltInRegistries.ITEM.getKey(it).toString() }.sorted()
    }

    fun reward():String {
        val z=choices(Z_CHOICE)
        val mega=choices(MEGA_CHOICE)
        return buildList {
            add(GOLD)
            listOf(SOUP,GMAX).filterTo(this) { BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.parse(it)) }
            if(z.isNotEmpty()){ add(Z_CHOICE) }
            if(mega.isNotEmpty())add(MEGA_CHOICE)
        }.random()
    }
}
