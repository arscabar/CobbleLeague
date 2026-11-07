package ai.league.practice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RewardStoreTest {
    @TempDir Path directory;
    final UUID battle = UUID.randomUUID(), player = UUID.randomUUID();

    @Test void pendingSurvivesReloadAndPaidBattleCannotAwardAgain() throws Exception {
        Path file = directory.resolve("rewards.json");
        RewardStore first = new RewardStore(file);
        assertTrue(first.award(battle, player, "beginner"));
        assertFalse(first.award(battle, player, "beginner"));
        RewardStore loaded = new RewardStore(file);
        assertEquals(1, loaded.pending(player).size());
        assertTrue(loaded.pending(UUID.randomUUID()).isEmpty());
        loaded.delivered(battle);
        RewardStore paid = new RewardStore(file);
        assertTrue(paid.pending(player).isEmpty());
        assertFalse(paid.award(battle, player, "beginner"));
    }

    @Test void corruptFileIsNeverReplaced() throws Exception {
        Path file = directory.resolve("rewards.json");
        Files.writeString(file, "{bad");
        assertThrows(IOException.class, () -> new RewardStore(file));
        assertEquals("{bad", Files.readString(file));
    }

    @Test void failedWriteRollsBackMemoryAndCanRetry() throws Exception {
        Path parent = directory.resolve("blocked");
        RewardStore store = new RewardStore(parent.resolve("rewards.json"));
        Files.writeString(parent, "existing file");
        assertThrows(IOException.class, () -> store.award(battle, player, "beginner"));
        assertTrue(store.pending(player).isEmpty());
        Files.delete(parent);
        assertTrue(store.award(battle, player, "beginner"));
    }

    @Test void duplicateCannotRedirectReward() throws Exception {
        RewardStore store = new RewardStore(directory.resolve("rewards.json"));
        store.award(battle, player, "beginner");
        assertThrows(IllegalArgumentException.class,
                () -> store.award(battle, UUID.randomUUID(), "beginner"));
        assertThrows(IllegalArgumentException.class, () -> store.award(battle, player, "expert"));
        assertEquals(1, store.pending(player).size());
    }

    @Test void deliveryWriteFailurePreservesPending() throws Exception {
        Path file = directory.resolve("rewards.json");
        RewardStore store = new RewardStore(file);
        store.award(battle, player, "beginner");
        Files.delete(file);
        Files.createDirectory(file);
        Files.writeString(file.resolve("keep.txt"), "keep");
        assertThrows(IOException.class, () -> store.delivered(battle));
        assertEquals(1, store.pending(player).size());
        assertEquals("keep", Files.readString(file.resolve("keep.txt")));
    }

    @Test void unsupportedVersionFailsClosed() throws Exception {
        Path file = directory.resolve("rewards.json");
        String content = "{\"version\":2,\"grants\":[]}";
        Files.writeString(file, content);
        assertThrows(IOException.class, () -> new RewardStore(file));
        assertEquals(content, Files.readString(file));
    }
}
