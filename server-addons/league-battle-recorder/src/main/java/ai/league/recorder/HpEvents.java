package ai.league.recorder;

final class HpEvents {
    static void set(PokemonState mon, String condition) { HpCondition.set(mon, condition); }

    static boolean handle(BattleTimeline t, String[] p, String raw) {
        String tag = p[1];
        if (!tag.equals("-damage") && !tag.equals("-heal") && !tag.equals("-sethp")) return false;
        int count = tag.equals("-sethp") ? p.length : Math.min(4, p.length);
        for (int n = 2; n + 1 < count && !p[n].startsWith("["); n += 2) apply(t, p, n, raw);
        return true;
    }

    private static void apply(BattleTimeline t, String[] p, int n, String raw) {
        PokemonState target = t.resolve(p[n]);
        if (target == null) {
            t.add("HP 변화 (개체 미확인)", null, null, "", null, null, null, "", "", "대상 미확인", raw);
            return;
        }
        int before = target.hp, oldMax = target.maxHp;
        String oldStatus = target.status, tag = p[1];
        set(target, p[n + 1]);
        HpCause cause = HpCause.resolve(t, p, target, oldStatus);
        boolean resized = oldMax != target.maxHp;
        boolean damage = tag.equals("-damage"), heal = tag.equals("-heal");
        boolean inconsistent = !resized && (damage && target.hp > before || heal && target.hp < before);
        String type = resized ? "최대 HP 변경" : inconsistent ? "HP 검증 필요"
                : damage ? "피해" : heal ? "회복" : "HP 조정";
        String reason = resized ? "최대 HP " + oldMax + " → " + target.maxHp
                : inconsistent ? "엔진 HP 변화 방향 확인 필요" : cause.reason();
        Integer amount = damage && !resized && !inconsistent ? before - target.hp : null;
        if (amount != null) {
            t.damageCause.put(target.uuid, reason.isEmpty() ? "원인 미상 HP 감소" : reason);
            if (cause.source() != null) t.damageSource.put(target.uuid, cause.source());
            else t.damageSource.remove(target.uuid);
        }
        t.add(type, resized ? null : cause.source(), target, resized ? "" : cause.move(), amount,
                before, target.hp, "", "", reason, raw);
    }
}
