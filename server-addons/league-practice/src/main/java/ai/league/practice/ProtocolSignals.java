package ai.league.practice;

final class ProtocolSignals {
    static boolean tie(String raw) {
        return raw.lines().anyMatch(line -> line.equals("|tie") || line.equals("|tie|"));
    }
}
