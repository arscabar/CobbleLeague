package ai.league.practice;

public record BlockSite(int x, int y, int z) {
    public void validate() {
        if (Math.abs((long) x) > 29_999_984 || Math.abs((long) z) > 29_999_984)
            throw new IllegalArgumentException("버튼 좌표가 월드 범위를 벗어났습니다.");
    }
}
