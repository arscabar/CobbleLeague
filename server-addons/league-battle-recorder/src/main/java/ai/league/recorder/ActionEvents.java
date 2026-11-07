package ai.league.recorder;

final class ActionEvents {
    static boolean handle(BattleTimeline t, String[] p, String raw) {
        String tag = p[1], ref = BattleTimeline.part(p, 2);
        PokemonState mon = t.resolve(ref);
        switch (tag) {
            case "move" -> {
                t.pendingDamageCause.clear();
                t.lastUser = mon;
                t.lastMove = BattleTimeline.part(p, 3);
                t.lastTarget = t.resolve(BattleTimeline.part(p, 4));
                String type = raw.contains("ability: Magic Bounce") ? "기술 반사 (매직미러)" : "기술 사용";
                t.add(type, mon, t.lastTarget, t.lastMove, null, null, null, "", "", String.join(" | ", p).strip(), raw);
            }
            case "switch", "drag", "replace" -> {
                String slot = t.slot(ref);
                PokemonState previous = t.active.get(slot);
                Integer before = mon == null ? null : mon.hp;
                if (mon != null) {
                    if (!BattleTimeline.part(p, 4).isBlank()) HpEvents.set(mon, p[4]);
                    t.active.put(slot, mon);
                }
                String reason = previous == null ? "최초 등장" : tag.equals("drag") ? "강제 교체"
                        : tag.equals("replace") ? "환영/표시 변경" : previous.hp == 0 ? "기절 후 교체" : "자발적/기술 교체";
                String from = previous == null ? "" : previous.label() + " [" + previous.uuid + "] " + previous.hp + "/" + previous.maxHp;
                String to = mon == null ? ref : mon.label() + " [" + mon.uuid + "] " + mon.hp + "/" + mon.maxHp;
                t.add(tag.equals("replace") ? "모습 공개" : "포켓몬 교체", mon, mon, "", null, before,
                        mon == null ? null : mon.hp, tag.equals("replace") ? "" : from,
                        tag.equals("replace") ? "" : to, reason, raw);
            }
            case "faint" -> {
                Integer before = mon == null ? null : mon.hp;
                if (mon != null) mon.hp = 0;
                boolean fromDamage = before != null && before == 0;
                String cause = !fromDamage ? "직접 원인 미확인" : t.damageCause.getOrDefault(mon.uuid, "불명");
                PokemonState source = !fromDamage ? null : t.damageSource.get(mon.uuid);
                t.add("기절", source, mon, "", null, before, mon == null ? null : 0, "", "", cause, raw);
            }
            case "swap" -> {
                String slot = t.slot(ref);
                String otherSlot = slot.substring(0, 2) + (char) ('a' + Integer.parseInt(p[3]));
                PokemonState other = t.active.get(otherSlot);
                if (mon != null) t.active.put(otherSlot, mon);
                if (other != null) t.active.put(slot, other); else t.active.remove(slot);
                t.add("위치 교환", mon, other, "", null, null, null, "", "", slot + " ↔ " + otherSlot, raw);
            }
            default -> { return false; }
        }
        return true;
    }
}
