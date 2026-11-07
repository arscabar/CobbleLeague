package ai.league.recorder;

final class DisplayEffects {
    static DisplayRow row(BattleEvent e, DisplayText text) {
        DisplayRow row = new DisplayRow(e, text);
        row.action = e.type();
        row.result = text.detail(e);
        switch (e.type()) {
            case "피해" -> {
                String cause = DisplayText.cause(text.detail(e));
                row.action = e.move().isBlank() ? (cause.isBlank() ? "HP 피해" : cause + " 피해") : KoreanNames.move(e.move());
                row.damage = e.damage(); row.result = "";
            }
            case "포켓몬 교체" -> {
                row.action = "교체"; row.target = ""; row.targetHp = "";
                String from = text.switched(e.withdrawn()), to = text.switched(e.sentOut());
                row.actor = e.player();
                row.result = from.isBlank() ? to + " 등장" : from + " → " + to;
                if (e.reason().contains("기절")) row.add("기절 후 교체");
                else if (e.reason().contains("강제")) row.add("강제 교체");
            }
            case "변신" -> row.result = "대상 모습으로 변신";
            case "모습 공개" -> row.targetHp = "";
            case "회복" -> row.result = DisplayText.cause(row.result);
            case "기절" -> { row.action = "기절"; row.result = "기절"; }
            case "상태이상" -> row.result += " 부여";
            case "해석 오류 (원문 보존)" -> { row.action = "기록 확인"; row.result = "일부 이벤트 해석 확인 필요"; }
            default -> {
                if (e.type().startsWith("엔진 이벤트")) row.action = "기타 효과";
            }
        }
        return row;
    }
}
