package ai.league.recorder;

import java.time.*;
import java.util.*;

final class XlsxRows {
    static List<List<?>> parties(BattleRecord r) {
        List<List<?>> rows = new ArrayList<>();
        rows.add(List.of("리그장 전투 기록"));
        rows.add(Arrays.asList("기록 기준", "전투 중 실제 HP 변화 · 피해는 타격마다 구분"));
        rows.add(Arrays.asList("플레이어", r.player1, r.player2));
        rows.add(Arrays.asList("리그장", r.arena));
        rows.add(Arrays.asList("차원", r.dimension));
        rows.add(Arrays.asList("시작 시각 (한국)", time(r.startedAt)));
        rows.add(Arrays.asList("종료 시각 (한국)", time(r.endedAt)));
        rows.add(Arrays.asList("결과", r.result));
        rows.add(Arrays.asList("종료 사유", r.endReason));
        rows.add(List.of());
        rows.add(Arrays.asList("플레이어", "파티 순번", "포켓몬 종", "별명", "레벨", "보유 기술", "시작 HP", "시작 최대 HP", "종료 HP", "종료 최대 HP"));
        for (PokemonState p : r.party) rows.add(Arrays.asList(p.player, p.slot, KoreanNames.species(p.species), p.nickname, p.level, KoreanNames.moves(p.moves), p.startHp, p.startMaxHp, p.hp, p.maxHp));
        return rows;
    }

    static List<List<?>> events(BattleRecord r) {
        List<List<?>> rows = new ArrayList<>();
        rows.add(Arrays.asList("순서", "턴", "행동자", "기술·행동", "대상", "결과", "실제 HP 감소량", "대상 HP (전→후/최대)", r.player1 + " HP (직후)", r.player2 + " HP (직후)"));
        rows.addAll(new BattleDisplay(r).rows());
        return rows;
    }
    private static String time(Instant t) { return t == null ? "" : t.atZone(ZoneId.of("Asia/Seoul")).toString(); }
}
