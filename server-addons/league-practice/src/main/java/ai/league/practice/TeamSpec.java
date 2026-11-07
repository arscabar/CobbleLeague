package ai.league.practice;

import java.util.List;

public record TeamSpec(String id, List<PokemonSpec> pokemon) {
    public TeamSpec { pokemon = pokemon == null ? null : List.copyOf(pokemon); }
    public void validateShape() {
        if (id == null || !id.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("팀 id는 영문 소문자·숫자·밑줄·하이픈 1~64자여야 합니다.");
        if (pokemon == null || pokemon.size() > 6)
            throw new IllegalArgumentException(id + ": pokemon은 0~6개 배열이어야 합니다.");
        for (PokemonSpec member : pokemon) {
            if (member == null) throw new IllegalArgumentException(id + ": 빈 포켓몬 항목입니다.");
            member.validate();
        }
    }
    public void validate() {
        validateShape();
        if (pokemon.isEmpty()) throw new IllegalArgumentException(id + ": 팀 준비 중입니다.");
    }
}
