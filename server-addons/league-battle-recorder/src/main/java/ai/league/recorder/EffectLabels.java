package ai.league.recorder;

final class EffectLabels {
    static String type(String tag) {
        return switch (tag) {
            case "-status" -> "상태이상";
            case "-curestatus", "-cureteam" -> "상태이상 해제";
            case "-boost" -> "능력치 상승";
            case "-unboost" -> "능력치 하락";
            case "-setboost", "-swapboost", "-invertboost", "-clearboost", "-clearallboost",
                    "-clearpositiveboost", "-clearnegativeboost", "-copyboost" -> "능력치 랭크 변경";
            case "-start", "-end", "-singlemove", "-singleturn" -> "지속 효과 변경";
            case "-sidestart" -> "장판/벽 효과 시작";
            case "-sideend" -> "장판/벽 효과 종료";
            case "-swapsideconditions" -> "양측 장판/벽 교환";
            case "-prepare" -> "기술 준비";
            case "-mustrecharge" -> "재충전";
            case "cant" -> "행동 불가";
            case "-item", "-enditem", "bagitem" -> "아이템 변화/사용";
            case "-ability", "-endability", "-activate" -> "특성/효과 발동";
            case "-weather" -> "날씨 변화";
            case "-fieldstart", "-fieldend", "-fieldactivate" -> "필드 효과 변경";
            case "-transform" -> "변신";
            case "-mega", "-primal", "-burst", "-formechange", "detailschange" -> "진화/폼 변화";
            case "-terastallize" -> "테라스탈";
            case "-zpower", "-zbroken" -> "Z기술 효과";
            case "-crit" -> "급소";
            case "-supereffective", "-resisted", "-immune" -> "상성/무효";
            case "-fail", "-block", "-miss", "-notarget" -> "실패/방어/빗나감";
            case "-hitcount" -> "연속 공격 횟수";
            case "turn" -> "턴 시작";
            case "start" -> "전투 시작";
            case "win", "tie" -> "엔진 전투 결과";
            case "player", "teamsize", "poke", "teampreview" -> "참가자/파티 정보";
            case "-message", "-hint", "message" -> "전투 안내";
            default -> "엔진 이벤트 (" + tag + ")";
        };
    }

    static String stat(String id) {
        return switch (id) {
            case "atk" -> "공격"; case "def" -> "방어";
            case "spa" -> "특수공격"; case "spd" -> "특수방어";
            case "spe" -> "스피드"; case "accuracy" -> "명중률";
            case "evasion" -> "회피율"; default -> id;
        };
    }

    static String status(String id) {
        return switch (id) {
            case "brn" -> "화상"; case "psn" -> "독"; case "tox" -> "맹독";
            case "par" -> "마비"; case "slp" -> "잠듦"; case "frz" -> "얼음";
            default -> id;
        };
    }
}
