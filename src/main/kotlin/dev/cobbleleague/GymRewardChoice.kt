package dev.cobbleleague

import java.util.UUID

enum class GymPrize(val label:String) {
    MYTHICAL("환상 뽑기권 1장"), LEGENDARY("전설 포켓몬"), MONEY("돈"),
    CRYSTAL("Z크리스탈"), EQUIPMENT("고급 도구"), LESSON("무료 기술 학습 1회")
}

/** One victory, one fixed offer, one successful claim. Failed delivery is retryable. */
class GymRewardChoice(val options:List<GymPrize>,val token:String=UUID.randomUUID().toString()) {
    init{require(options.isNotEmpty()&&options.distinct().size==options.size)}
    companion object {
        fun offer(available:List<GymPrize>,random:kotlin.random.Random=kotlin.random.Random.Default):GymRewardChoice {
            val others=available.distinct().filter{it!=GymPrize.MONEY}
            require(others.size>=2)
            return GymRewardChoice(listOf(GymPrize.MONEY)+others.shuffled(random).take(2))
        }
    }
    var selected:GymPrize?=null;private set
    fun choose(token:String,prize:GymPrize,deliver:()->Unit) {
        require(token==this.token&&selected==null&&prize in options){"이미 선택했거나 만료된 보상입니다."}
        deliver()
        selected=prize
    }
}

/** A retry is a vote for this attempt, never consent for its replacement. */
class BattleRestartVote(val players:Set<UUID>) {
    init{require(players.isNotEmpty())}
    private val votes=mutableSetOf<UUID>()
    fun voted(id:UUID)=id in votes
    fun request(id:UUID):Boolean {require(id in players);votes.add(id);return votes.containsAll(players)}
    val count get()=votes.size
}
