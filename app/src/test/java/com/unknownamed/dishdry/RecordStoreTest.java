package com.unknownamed.dishdry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Locale;

import static org.junit.Assert.*;

public class RecordStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final Instant TIME = Instant.parse("2026-10-10T01:02:03Z");

    @Test public void appendWritesOneHeaderAndRestoresSummaryAfterReopen() throws Exception {
        File file = temporary.newFile();
        RecordStore store = new RecordStore(file);
        store.append(new ReadingSnapshot(null, null, 0.5f), TIME);
        store.append(new ReadingSnapshot(null, 20f, null), TIME.plusSeconds(1));
        assertEquals(3, Files.readAllLines(file.toPath()).size());
        RecordStore.Summary reopened = new RecordStore(file).loadSummary();
        assertEquals(2, reopened.count);
        assertEquals(TIME.plusSeconds(1), reopened.lastRecord);
    }

    @Test public void missingSensorsStayBlankAndDecimalFormatDoesNotFollowPhoneLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            assertEquals(TIME + ",,,,,0.500,\n", new ReadingSnapshot(null, null, 0.5f).csvRow(TIME));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test public void exportIsAnExactCopyAndKeepsInternalFile() throws Exception {
        File file = temporary.newFile();
        RecordStore store = new RecordStore(file);
        store.append(new ReadingSnapshot(null, null, 0.5f), TIME);
        byte[] original = Files.readAllBytes(file.toPath());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        store.copyTo(output);
        assertArrayEquals(original, output.toByteArray());
        assertArrayEquals(original, Files.readAllBytes(file.toPath()));
    }

    @Test public void incompleteLinesDoNotBecomeSavedRecords() throws Exception {
        File file = temporary.newFile();
        Files.write(file.toPath(), (RecordStore.HEADER + "\npartial\nbad-time,,,,,0.500,\n"
                + new ReadingSnapshot(null, null, 0.5f).csvRow(TIME)).getBytes(StandardCharsets.UTF_8));
        assertEquals(1, new RecordStore(file).loadSummary().count);
    }

    @Test public void emptySnapshotAndDifferentHeaderCannotBeAppended() throws Exception {
        File file = temporary.newFile();
        RecordStore store = new RecordStore(file);
        assertThrows(IOException.class, () -> store.append(new ReadingSnapshot(null, null, null), TIME));
        Files.write(file.toPath(), "unrelated-file\n".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> store.append(new ReadingSnapshot(null, null, 0.5f), TIME));
        assertEquals("unrelated-file\n", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    @Test public void missingTrailingNewlineDoesNotMergeTwoRows() throws Exception {
        File file = temporary.newFile();
        String first = new ReadingSnapshot(null, null, 0.5f).csvRow(TIME).trim();
        Files.write(file.toPath(), (RecordStore.HEADER + "\n" + first).getBytes(StandardCharsets.UTF_8));
        RecordStore store = new RecordStore(file);
        assertEquals(2, store.append(new ReadingSnapshot(null, null, 1f), TIME.plusSeconds(1)).count);
        assertEquals(3, Files.readAllLines(file.toPath()).size());
    }
}
