package ai.league.practice;

import java.util.UUID;

final class BattleGate {
    private UUID token;
    UUID acquire() {
        if (token != null) throw new IllegalStateException("리그장에서 이미 전투가 진행 중입니다.");
        return token = UUID.randomUUID();
    }
    boolean release(UUID expected) {
        if (token == null || !token.equals(expected)) return false;
        token = null;
        return true;
    }
    boolean busy() { return token != null; }
}
