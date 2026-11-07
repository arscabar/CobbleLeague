package ai.league.recorder;

import java.time.Instant;

public final class ProtocolLog {
    private final BattleTimeline timeline;
    private final RequestSync requests;
    private int splitPhase;
    private int parseErrors;
    private boolean tied;
    private boolean finished;

    public ProtocolLog(BattleRecord record) {
        timeline = new BattleTimeline(record);
        requests = new RequestSync(timeline);
    }

    public void accept(String raw) {
        if (finished) return;
        boolean header = true;
        for (String line : raw.split("\\R", -1)) {
            if (header && !line.startsWith("|") && !line.isEmpty()) continue;
            header = false;
            if (line.startsWith("|split|")) { splitPhase = 1; continue; }
            if (splitPhase == 2) { splitPhase = 0; continue; }
            if (splitPhase == 1) splitPhase = 2;
            if (!line.startsWith("|") || line.equals("|")) continue;
            try { consume(line); }
            catch (RuntimeException error) {
                parseErrors++;
                timeline.add("해석 오류 (원문 보존)", null, null, "", null, null, null,
                        "", "", error.getClass().getSimpleName(), line);
            }
        }
        if (raw.startsWith("update\n") || raw.startsWith("update\r\n")) requests.flush();
    }

    private void consume(String line) {
            String[] p = line.split("\\|", -1);
            String tag = BattleTimeline.part(p, 1);
            if (tag.equals("request")) { requests.accept(line.substring("|request|".length())); return; }
            if (tag.equals("sentchoice")) return;
            if (tag.equals("tie")) tied = true;
            if (tag.equals("turn")) {
                requests.flush();
                timeline.turn = Integer.parseInt(p[2]);
                timeline.pendingDamageCause.clear();
                timeline.lastUser = timeline.lastTarget = null;
                timeline.lastMove = "";
            }
            if (ActionEvents.handle(timeline, p, line)) return;
            if (HpEvents.handle(timeline, p, line)) return;
            EffectEvents.handle(timeline, p, line);
    }

    public boolean isTie() { return tied; }

    public void finish(String result, String reason) {
        if (finished) return;
        requests.flush();
        finished = true;
        timeline.record.endedAt = Instant.now();
        String outcome = tied && (reason.isBlank() || reason.equals("전투 중단")) ? "무승부" : result;
        String endCause = outcome.equals("무승부") ? "엔진 무승부" : reason;
        String ending = parseErrors == 0 ? endCause : endCause + "; 해석 오류 " + parseErrors + "건 (원문 보존)";
        timeline.record.result = outcome;
        timeline.record.endReason = ending;
        timeline.add("전투 종료", null, null, "", null, null, null, "", "", outcome + " / " + ending, "");
    }
}
