package ai.league.recorder;

import com.google.gson.*;
import java.util.*;
import java.util.regex.Pattern;

final class RequestSync {
    private static final Pattern UUID = Pattern.compile("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");
    private final BattleTimeline timeline;
    private final Map<String, String> pending = new LinkedHashMap<>();

    RequestSync(BattleTimeline timeline) { this.timeline = timeline; }

    void accept(String json) {
        JsonElement data = JsonParser.parseString(json);
        if (!data.isJsonObject() || !data.getAsJsonObject().has("side")) return;
        JsonObject side = data.getAsJsonObject().getAsJsonObject("side");
        if (!side.has("pokemon")) return;
        for (JsonElement element : side.getAsJsonArray("pokemon")) {
            JsonObject mon = element.getAsJsonObject();
            if (!mon.has("details") || !mon.has("condition")) continue;
            var id = UUID.matcher(mon.get("details").getAsString());
            if (id.find()) pending.put(id.group(), mon.get("condition").getAsString());
        }
    }

    // Requests can describe the final HP before the corresponding battle update arrives.
    void flush() {
        for (var entry : pending.entrySet()) {
            PokemonState mon = timeline.record.pokemon(entry.getKey());
            if (mon == null) continue;
            int before = mon.hp, oldMax = mon.maxHp;
            String oldStatus = mon.status;
            HpCondition.set(mon, entry.getValue());
            if (before != mon.hp || oldMax != mon.maxHp) {
                String type = oldMax == mon.maxHp ? "HP 동기화" : "최대 HP 변경";
                timeline.add(type, null, mon, "", null, before, mon.hp, "", "",
                        "엔진 상태 갱신 (원인 미확인)", "");
            } else if (!oldStatus.equals(mon.status)) {
                timeline.add("상태 동기화", null, mon, "", null, null, null, "", "",
                        mon.status.isEmpty() ? "상태이상 해제" : EffectLabels.status(mon.status), "");
            }
        }
        pending.clear();
    }
}
