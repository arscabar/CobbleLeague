package ai.league.recorder;

import java.util.*;

final class DisplayRow {
    final int turn;
    String targetId, actor, action, target, result = "", targetHp = "", p1Hp, p2Hp;
    Integer damage, hpAfter;

    DisplayRow(BattleEvent e, DisplayText text) {
        turn = e.turn(); targetId = e.targetId();
        actor = text.person(e.player(), e.pokemon()); action = KoreanNames.move(e.move());
        target = text.person(e.targetPlayer(), e.targetPokemon());
        update(e, text);
    }

    void update(BattleEvent e, DisplayText text) {
        snapshots(e, text);
        if (e.hpBefore() != null && e.hpAfter() != null) {
            targetHp = e.hpBefore() + " → " + e.hpAfter() + (e.hpMax() == null ? "" : " / " + e.hpMax());
            hpAfter = e.hpAfter();
        }
    }

    void snapshots(BattleEvent e, DisplayText text) {
        p1Hp = text.hp(e.player1Hp()); p2Hp = text.hp(e.player2Hp());
    }

    void add(String text) {
        if (text == null || text.isBlank() || Arrays.asList(result.split(" · ")).contains(text)) return;
        result += result.isBlank() ? text : " · " + text;
    }

    List<Object> cells(int order) {
        return Arrays.asList(order, turn, actor, action, target, result, damage, targetHp, p1Hp, p2Hp);
    }
}
