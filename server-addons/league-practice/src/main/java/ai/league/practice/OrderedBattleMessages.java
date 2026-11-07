package ai.league.practice;

import java.util.*;
import java.util.function.BiConsumer;

/** A snapshot drain keeps callback-produced outputs behind all existing outputs. */
final class OrderedBattleMessages {
    private record Entry(UUID battle, String message) {}
    private final Deque<Entry> messages = new ArrayDeque<>();
    private boolean draining;

    synchronized void add(UUID battle, String message) { messages.addLast(new Entry(battle, message)); }
    synchronized boolean pending(UUID battle) { return messages.stream().anyMatch(e -> e.battle.equals(battle)); }
    synchronized void clear(UUID battle) { messages.removeIf(e -> e.battle.equals(battle)); }

    void drain(BiConsumer<UUID, String> consumer) {
        int budget;
        synchronized (this) {
            if (draining) return;
            draining = true;
            budget = messages.size();
        }
        try {
            while (budget-- > 0) {
                Entry entry;
                synchronized (this) { entry = messages.pollFirst(); }
                if (entry == null) break;
                consumer.accept(entry.battle, entry.message);
            }
        } finally { synchronized (this) { draining = false; } }
    }
}
