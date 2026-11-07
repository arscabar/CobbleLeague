package dev.cobbleleague

/** A session wallet, cumulative rerolls and a server-owned preparation deadline. */
class DraftPreparation {
    var money = 0; private set
    var freeLessons = 0; private set
    var rerolls = 1; private set
    var revision = 0; private set
    var confirmed = false; private set
    var deadline = 0L; private set
    fun begin(now: Long) { if (deadline == 0L) { deadline = now + 60_000; revision++ } }
    fun seconds(now: Long) = if (deadline == 0L) -1 else ((deadline - now + 999) / 1000).coerceAtLeast(0).toInt()
    fun ready(now: Long) = confirmed || deadline != 0L && now >= deadline
    fun editable(now: Long) = !confirmed && (deadline == 0L || now < deadline)
    fun check(token: Int, now: Long) { require(token == revision) { "화면이 갱신되었습니다. 다시 선택하세요." }; require(editable(now)) { "준비를 취소하거나 다음 라운드에 변경하세요." } }
    fun confirm(value: Boolean, token: Int, now: Long) {
        require(token == revision); require(deadline == 0L || now < deadline) { "준비 시간이 끝났습니다." }
        confirmed = value; revision++
    }
    fun reroll(token: Int, now: Long) { check(token, now); require(rerolls > 0) { "남은 리롤이 없습니다." }; rerolls--; revision++ }
    fun pay(cost: Int, token: Int, now: Long, apply: () -> Unit) {
        check(token, now); require(cost > 0 && money >= cost) { "돈이 부족합니다." }
        apply(); money -= cost; revision++
    }
    fun awardFreeLesson() { freeLessons++; revision++ }
    fun learn(token:Int,now:Long,apply:()->Unit) {
        if(freeLessons==0){ pay(DungeonShop.TM_PRICE,token,now,apply); return }
        check(token,now)
        apply()
        freeLessons--
        revision++
    }
    fun changed() { revision++ }
    fun awardLoss() = credit(1)
    fun credit(amount:Int) { require(amount>=0);money+=amount;revision++ }
    fun unlockWaiting() {confirmed=false;deadline=0L;revision++}
    fun nextRound(loss: Boolean) {
        rerolls++
        if (loss) money++
        confirmed = false; deadline = 0L; revision++
    }
}

data class DungeonProduct(val id: String, val price: Int, val tier: String, val consumable: Boolean = false)
object DungeonShop {
    val products = listOf(
        DungeonProduct("minecraft:gold_nugget", 3, "육성 · 금뚜껑"),
        DungeonProduct("cobblemon:ability_capsule", 2, "육성 · 특성 변경"),
        DungeonProduct("cobblemon:sitrus_berry", 1, "배틀 소모품", true),
        DungeonProduct("cobblemon:lum_berry", 1, "배틀 소모품", true),
        DungeonProduct("cobblemon:chesto_berry", 1, "배틀 소모품", true),
        DungeonProduct("cobblemon:air_balloon", 2, "배틀 소모품", true),
        DungeonProduct("cobblemon:white_herb", 1, "배틀 소모품", true),
        DungeonProduct("cobblemon:mental_herb", 1, "배틀 소모품", true),
        DungeonProduct("cobblemon:power_herb", 2, "배틀 소모품", true),
        DungeonProduct("cobblemon:weakness_policy", 2, "배틀 소모품", true),
        DungeonProduct("cobblemon:eject_button", 1, "배틀 소모품", true),
        DungeonProduct("cobblemon:red_card", 1, "배틀 소모품", true),
        DungeonProduct("cobblemon:eject_pack", 1, "배틀 소모품", true),
        DungeonProduct("cobblemon:throat_spray", 1, "배틀 소모품", true),
        DungeonProduct("cobblemon:blunder_policy", 1, "배틀 소모품", true),
        DungeonProduct("cobblemon:light_clay", 2, "배틀 장비"),
        DungeonProduct("cobblemon:eviolite", 2, "배틀 장비"),
        DungeonProduct("cobblemon:covert_cloak", 2, "배틀 장비"),
        DungeonProduct("cobblemon:protective_pads", 2, "배틀 장비"),
        DungeonProduct("cobblemon:loaded_dice", 2, "배틀 장비"),
        DungeonProduct("cobblemon:safety_goggles", 2, "배틀 장비"),
        DungeonProduct("cobblemon:terrain_extender", 2, "배틀 장비"),
        DungeonProduct("cobblemon:damp_rock", 1, "배틀 장비"),
        DungeonProduct("cobblemon:heat_rock", 1, "배틀 장비"),
        DungeonProduct("cobblemon:smooth_rock", 1, "배틀 장비"),
        DungeonProduct("cobblemon:icy_rock", 1, "배틀 장비"),
        DungeonProduct("cobblemon:black_sludge", 2, "배틀 장비"),
        DungeonProduct("cobblemon:flame_orb", 2, "배틀 장비"),
        DungeonProduct("cobblemon:toxic_orb", 2, "배틀 장비"),
        DungeonProduct("cobblemon:clear_amulet", 2, "배틀 장비"),
        DungeonProduct("cobblemon:punching_glove", 2, "배틀 장비"),
        DungeonProduct("cobblemon:muscle_band", 2, "장비"),
        DungeonProduct("cobblemon:wise_glasses", 2, "장비"),
        DungeonProduct("cobblemon:expert_belt", 2, "장비"),
        DungeonProduct("cobblemon:shell_bell", 2, "장비"),
        DungeonProduct("cobblemon:leftovers", 3, "고급 장비"),
        DungeonProduct("cobblemon:life_orb", 3, "고급 장비"),
        DungeonProduct("cobblemon:focus_sash", 3, "고급 장비", true),
        DungeonProduct("cobblemon:choice_band", 3, "고급 장비"),
        DungeonProduct("cobblemon:choice_specs", 3, "고급 장비"),
        DungeonProduct("cobblemon:choice_scarf", 3, "고급 장비"),
        DungeonProduct("cobblemon:assault_vest", 3, "고급 장비"),
        DungeonProduct("cobblemon:rocky_helmet", 3, "고급 장비"),
        DungeonProduct("cobblemon:heavy_duty_boots", 3, "고급 장비")
    )
    const val TM_PRICE = 1
}
