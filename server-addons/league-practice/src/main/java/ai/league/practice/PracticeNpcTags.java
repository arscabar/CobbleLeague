package ai.league.practice;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;

public final class PracticeNpcTags {
    public static final String MARKER = "league_practice_npc";
    public static final String TEAM_PREFIX = "league_practice_team:";
    public static final String AUTO_BATTLE = "league_practice_auto_battle";
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

    private PracticeNpcTags() {
    }

    public static void mark(Entity entity) {
        if (entity != null) {
            entity.addTag(MARKER);
            ACTIVE.add(entity.getUUID());
        }
    }

    public static void unmark(Entity entity) {
        if (entity != null) {
            ACTIVE.remove(entity.getUUID());
        }
    }

    public static boolean stale(Entity entity) {
        return isPracticeNpc(entity) && !ACTIVE.contains(entity.getUUID());
    }

    public static String teamTag(String teamId) {
        return TEAM_PREFIX + teamId;
    }

    public static boolean isPracticeNpc(Entity entity) {
        return entity != null && entity.getTags().contains(MARKER);
    }
}
