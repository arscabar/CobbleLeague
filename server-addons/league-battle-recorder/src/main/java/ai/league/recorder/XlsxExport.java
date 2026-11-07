package ai.league.recorder;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipOutputStream;

public final class XlsxExport {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withZone(ZONE);

    public static synchronized Path write(Path directory, BattleRecord record) throws IOException {
        Files.createDirectories(directory);
        String stem = "battle_" + safe(record.player1) + "_" + safe(record.player2) + "_" + STAMP.format(record.startedAt);
        Path target = directory.resolve(stem + ".xlsx");
        for (int n = 2; Files.exists(target); n++) target = directory.resolve(stem + "_" + n + ".xlsx");
        Path temp = Files.createTempFile(directory, ".battle-", ".tmp");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp))) {
                XlsxPackage.write(zip, SheetXml.create(XlsxRows.parties(record), 11, new double[]{18, 8, 24, 22, 8, 45, 12, 12, 12, 12}),
                        SheetXml.create(XlsxRows.events(record), 1, new double[]{8, 7, 32, 24, 32, 50, 16, 26, 20, 20}));
            }
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, target); }
            return target;
        } finally { Files.deleteIfExists(temp); }
    }

    private static String safe(String s) {
        String name = s == null ? "player" : s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip().replaceAll("[. ]+$", "_");
        return name.isEmpty() ? "player" : name.substring(0, Math.min(name.length(), 64));
    }
}
