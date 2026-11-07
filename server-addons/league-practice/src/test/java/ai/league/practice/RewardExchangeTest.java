package ai.league.practice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RewardExchangeTest {
    @TempDir Path directory;
    private final UUID player = UUID.randomUUID();

    private List<UUID> tickets(RewardStore store, String tier, int count) throws Exception {
        var ids = new ArrayList<UUID>();
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID(); ids.add(id);
            store.award(id, player, tier); store.delivered(id);
        }
        return ids;
    }

    @Test void upgradeAndPurchaseRemainSpentAfterReload() throws Exception {
        Path file = directory.resolve("rewards.json");
        var store = new RewardStore(file);
        var inputs = tickets(store, "intermediate", 3);
        UUID advanced = store.exchange(player, inputs, "intermediate", 3, "advanced", null);
        assertEquals("advanced", store.pending(player).getFirst().tier());
        store = new RewardStore(file);
        for (var id : inputs) assertFalse(store.currency(id, "intermediate"));
        store.delivered(advanced); assertTrue(store.currency(advanced, "advanced"));
        var master = tickets(store, "master", 2);
        UUID item = store.exchange(player, master, "master", 2, "master", "mega_showdown:metagrossite");
        assertEquals("mega_showdown:metagrossite", store.pending(player).getFirst().itemId());
        store.delivered(item);
        assertFalse(store.currency(item, "master"));
        var loaded = new RewardStore(file);
        assertTrue(loaded.pending(player).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> loaded.exchange(player, master, "master", 2, "master", "mega_showdown:metagrossite"));
    }

    @Test void duplicateForgedWrongTierAndUndeliveredTicketsCannotBeSpent() throws Exception {
        var store = new RewardStore(directory.resolve("rewards.json"));
        var inputs = tickets(store, "intermediate", 3);
        assertThrows(IllegalArgumentException.class, () -> store.exchange(player, List.of(inputs.getFirst(), inputs.getFirst(), inputs.get(1)), "intermediate", 3, "advanced", null));
        assertThrows(IllegalArgumentException.class, () -> store.exchange(player, inputs, "advanced", 3, "master", null));
        assertThrows(IllegalArgumentException.class, () -> store.exchange(player, List.of(UUID.randomUUID(), inputs.get(1), inputs.get(2)), "intermediate", 3, "advanced", null));
        UUID pending = UUID.randomUUID(); store.award(pending, player, "master");
        assertFalse(store.currency(pending, "master"));
        assertThrows(IllegalArgumentException.class, () -> store.exchange(player, inputs, "intermediate", 3, "advanced", "bad item"));
        assertTrue(store.currency(inputs.getFirst(), "intermediate"));
    }

    @Test void exchangeWriteFailureDoesNotConsumeInputsOrCreateOutput() throws Exception {
        Path file = directory.resolve("rewards.json");
        var store = new RewardStore(file);
        var inputs = tickets(store, "intermediate", 3);
        Files.delete(file); Files.createDirectory(file); Files.writeString(file.resolve("keep"), "keep");
        assertThrows(IOException.class, () -> store.exchange(player, inputs, "intermediate", 3, "advanced", null));
        for (var id : inputs) assertTrue(store.currency(id, "intermediate"));
        assertTrue(store.pending(player).isEmpty());
    }

    @Test void legacyTicketsLoadWithoutNewFields() throws Exception {
        Path file = directory.resolve("rewards.json");
        UUID id = UUID.randomUUID();
        Files.writeString(file, "{\"version\":1,\"grants\":[{\"battle\":\"" + id + "\",\"player\":\"" + player + "\",\"tier\":\"master\",\"delivered\":true}]}");
        assertTrue(new RewardStore(file).currency(id, "master"));
    }
}
