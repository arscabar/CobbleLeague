package ai.league.practice;

import com.google.gson.*;

final class TeamShape {
    static void check(JsonElement root) {
        require(root != null && root.isJsonArray(), "파티 파일은 배열이어야 합니다.");
        for (JsonElement team : root.getAsJsonArray()) {
            require(team.isJsonObject(), "파티 항목은 객체여야 합니다.");
            JsonElement members = team.getAsJsonObject().get("pokemon");
            require(members != null && members.isJsonArray(), "pokemon은 배열이어야 합니다.");
            for (JsonElement member : members.getAsJsonArray()) {
                require(member.isJsonObject(), "포켓몬 항목은 객체여야 합니다.");
                for (String field : new String[]{"ivs", "evs"}) {
                    JsonElement stats = member.getAsJsonObject().get(field);
                    require(stats != null && stats.isJsonArray() && stats.getAsJsonArray().size() == 6,
                            field + ": 정수 6개가 필요합니다.");
                    for (JsonElement value : stats.getAsJsonArray()) JsonInteger.check(value, field);
                }
            }
        }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalArgumentException(message);
    }
}
