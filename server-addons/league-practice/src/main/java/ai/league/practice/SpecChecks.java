package ai.league.practice;

final class SpecChecks {
    static void id(String value, String field) {
        if (value == null || !value.matches("[a-z0-9_.-]+(?::[a-z0-9_./-]+)?"))
            throw new IllegalArgumentException(field + ": 공백 없는 영문 소문자 ID를 입력하세요.");
    }
    static int stats(int[] values, int maximum, String field) {
        if (values == null || values.length != 6)
            throw new IllegalArgumentException(field + ": hp,atk,def,spatk,spdef,speed 6개가 필요합니다.");
        int total = 0;
        for (int value : values) {
            if (value < 0 || value > maximum)
                throw new IllegalArgumentException(field + ": 각 값은 0~" + maximum + "이어야 합니다.");
            total += value;
        }
        return total;
    }
}
