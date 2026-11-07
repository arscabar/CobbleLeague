package ai.league.practice;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

final class RewardDisk {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    record Data(int version, List<RewardStore.Grant> grants) {}

    static List<RewardStore.Grant> read(Path file) throws IOException {
        if (!Files.exists(file)) return List.of();
        try {
            Data data = GSON.fromJson(Files.readString(file), Data.class);
            if (data == null || data.version != 1 || data.grants == null)
                throw new IllegalArgumentException("보상 파일 형식 오류");
            return data.grants;
        } catch (RuntimeException error) {
            throw new IOException("보상 파일을 읽지 못했습니다. 원본을 유지합니다: " + file, error);
        }
    }

    static void write(Path file, Collection<RewardStore.Grant> grants) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), "rewards-", ".tmp");
        try {
            Files.writeString(temp, GSON.toJson(new Data(1, List.copyOf(grants))));
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }
}
