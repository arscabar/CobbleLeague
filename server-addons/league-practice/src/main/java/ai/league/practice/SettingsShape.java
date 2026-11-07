package ai.league.practice;

import com.google.gson.*;

final class SettingsShape {
    static void check(JsonElement value) {
        require(value != null && value.isJsonObject(), "설정은 JSON 객체여야 합니다.");
        JsonObject root = value.getAsJsonObject();
        if (root.has("enabled")) require(root.get("enabled").isJsonPrimitive()
                && root.get("enabled").getAsJsonPrimitive().isBoolean(), "enabled는 true 또는 false여야 합니다.");
        if (root.has("spawn") && !root.get("spawn").isJsonNull())
            coordinates(root.get("spawn"), "x", "y", "z", "yaw");
        if (root.has("buttons")) {
            require(root.get("buttons").isJsonObject(), "buttons는 JSON 객체여야 합니다.");
            for (var entry : root.getAsJsonObject("buttons").entrySet()) {
                coordinates(entry.getValue(), "x", "y", "z");
                for (String field : new String[]{"x", "y", "z"})
                    JsonInteger.check(entry.getValue().getAsJsonObject().get(field), field);
            }
        }
    }

    private static void coordinates(JsonElement value, String... fields) {
        require(value != null && value.isJsonObject(), "좌표는 JSON 객체여야 합니다.");
        JsonObject point = value.getAsJsonObject();
        for (String field : fields) {
            JsonElement number = point.get(field);
            require(number != null && number.isJsonPrimitive() && number.getAsJsonPrimitive().isNumber(),
                    "좌표 필드에 숫자가 필요합니다: " + field);
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
