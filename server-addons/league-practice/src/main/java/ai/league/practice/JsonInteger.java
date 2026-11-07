package ai.league.practice;

import com.google.gson.JsonElement;

final class JsonInteger {
    static void check(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException(field + ": 정수 숫자가 필요합니다.");
        try { value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException error) { throw new IllegalArgumentException(field + ": int 범위의 정수가 필요합니다.", error); }
    }
}
