package ai.league.recorder;

record HpCause(PokemonState source, String move, String reason) {
    static HpCause resolve(BattleTimeline t, String[] fields, PokemonState target, String oldStatus) {
        String cause = "", move = "";
        PokemonState source = null;
        for (String field : fields) {
            if (field.startsWith("[from] ")) cause = field.substring(7);
            if (field.startsWith("[of] ")) source = t.resolve(field.substring(5));
        }
        String delayed = fields[1].equals("-damage") ? t.pendingDamageCause.remove(target.uuid) : null;
        if (cause.isEmpty() && delayed != null) {
            cause = move = delayed;
            source = null;
        } else if (cause.isEmpty() && fields[1].equals("-damage")) {
            cause = move = t.lastMove;
            source = t.lastUser;
        }
        if (cause.equals("psn") || cause.equals("tox"))
            cause = cause.equals("tox") || oldStatus.equals("tox") || target.status.equals("tox") ? "맹독" : "독";
        else cause = switch (cause) {
            case "brn" -> "화상";
            case "Sandstorm" -> "모래바람";
            case "Hail" -> "싸라기눈";
            case "Spikes" -> "압정뿌리기";
            case "Stealth Rock" -> "스텔스록";
            case "recoil" -> "반동";
            default -> cause;
        };
        return new HpCause(source, move, cause);
    }
}
