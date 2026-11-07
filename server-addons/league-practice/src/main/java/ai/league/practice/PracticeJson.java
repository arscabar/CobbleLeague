package ai.league.practice;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;

final class PracticeJson {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    static <T> T read(Path file, Type type) throws IOException {
        try {
            String source = Files.readString(file);
            try (JsonReader reader = new JsonReader(new StringReader(source))) {
                reader.setLenient(false); reader.skipValue();
                if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("JSON 뒤에 추가 데이터가 있습니다.");
            }
            JsonElement tree = JsonParser.parseString(source);
            if (type == PracticeSettings.class) SettingsShape.check(tree);
            else TeamShape.check(tree);
            T value = JSON.fromJson(source, type);
            if (value == null) throw new IOException("JSON은 null일 수 없습니다.");
            return value;
        } catch (RuntimeException error) { throw new IOException("JSON 읽기 실패: " + file, error); }
    }

    static void create(Path file, Object value) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try { Files.writeString(file, JSON.toJson(value), StandardOpenOption.CREATE_NEW); }
        catch (FileAlreadyExistsException ignored) { }
    }

    static void save(Path file, Object value) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".practice-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(value));
            Files.move(temporary, file.toAbsolutePath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
