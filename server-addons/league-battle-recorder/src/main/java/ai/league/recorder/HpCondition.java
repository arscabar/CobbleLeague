package ai.league.recorder;

import java.util.regex.*;

final class HpCondition {
    private static final Pattern HP = Pattern.compile("^(\\d+)(?:/(\\d+))?(?: (.*))?$");

    static void set(PokemonState mon, String condition) {
        Matcher match = HP.matcher(condition.strip());
        if (!match.matches()) throw new IllegalArgumentException("Invalid HP: " + condition);
        int hp = Integer.parseInt(match.group(1));
        int max = match.group(2) == null ? mon.maxHp : Integer.parseInt(match.group(2));
        if (max <= 0 || hp > max) throw new IllegalArgumentException("Invalid HP range");
        mon.hp = hp;
        mon.maxHp = max;
        String status = match.group(3) == null ? "" : match.group(3).strip().split("\\s+")[0];
        mon.status = status.matches("brn|psn|tox|par|slp|frz") ? status : "";
    }
}
