package ai.league.practice;

public record SpawnSite(double x, double y, double z, float yaw) {
    public void validate() {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)
                || Math.abs(x) > 29_999_984 || Math.abs(z) > 29_999_984)
            throw new IllegalArgumentException("NPC 생성 위치는 월드 범위의 유한 좌표여야 합니다.");
    }
}
