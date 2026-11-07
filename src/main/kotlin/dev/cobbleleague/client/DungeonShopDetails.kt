package dev.cobbleleague.client

/** Shop explanations, separate from language-pack item names. */
internal object DungeonShopDetails {
    val items = mapOf(
        "gold_nugget" to "선택한 임시 포켓몬의 개체값 6개를 모두 31로 올립니다. 사용하면 소모됩니다.",
        "ability_capsule" to "현재 폼에서 가능한 특성 중 하나를 직접 선택합니다. 사용하면 소모됩니다.",
        "dynamax_candy" to "선택한 임시 포켓몬의 다이맥스 레벨을 10으로 올립니다.",
        "max_soup" to "거다이맥스가 가능한 포켓몬에 거다이맥스 인자를 부여합니다.",
        "sitrus_berry" to "HP가 절반 이하가 되면 최대 HP의 1/4을 회복합니다. 사용하면 소모됩니다.",
        "lum_berry" to "주요 상태이상 또는 혼란을 한 번 회복합니다. 사용하면 소모됩니다.",
        "chesto_berry" to "잠듦 상태를 한 번 회복합니다. 사용하면 소모됩니다.",
        "muscle_band" to "물리 기술의 위력이 10% 증가합니다. 기술 선택 제한은 없습니다.",
        "wise_glasses" to "특수 기술의 위력이 10% 증가합니다. 기술 선택 제한은 없습니다.",
        "expert_belt" to "효과가 굉장한 공격의 위력이 20% 증가합니다.",
        "shell_bell" to "공격으로 준 피해량의 1/8만큼 HP를 회복합니다.",
        "leftovers" to "매 턴 종료 시 최대 HP의 1/16을 회복합니다.",
        "life_orb" to "공격 피해량이 30% 증가하지만, 공격 후 최대 HP의 1/10을 잃습니다.",
        "focus_sash" to "HP가 가득 찼을 때 일격에 쓰러질 공격을 받아도 HP 1로 버팁니다. 발동하면 소모됩니다.",
        "choice_band" to "공격이 50% 증가하지만, 교체할 때까지 처음 선택한 기술만 사용할 수 있습니다.",
        "choice_specs" to "특수공격이 50% 증가하지만, 교체할 때까지 처음 선택한 기술만 사용할 수 있습니다.",
        "choice_scarf" to "스피드가 50% 증가하지만, 교체할 때까지 처음 선택한 기술만 사용할 수 있습니다.",
        "assault_vest" to "특수방어가 50% 증가하지만, 변화 기술을 선택할 수 없습니다.",
        "rocky_helmet" to "접촉 공격을 받으면 공격한 상대에게 최대 HP의 1/6 피해를 줍니다.",
        "heavy_duty_boots" to "교체로 나올 때 스텔스록·압정 등 설치 기술의 영향을 받지 않습니다."
    )
    fun item(id: String) = items[id.substringAfter(':')] ?: "선택한 포켓몬에게 장착하는 던전 전용 도구입니다."
}
