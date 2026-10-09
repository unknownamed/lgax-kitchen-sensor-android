package com.unknownamed.dishdry;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/** CSV storage only; the Activity schedules these operations away from the UI thread. */
final class RecordStore {
    static final String HEADER = "recorded_at_utc,temperature_c,humidity_percent,light_adc,phone_lux,phone_motion_m_s2,drying_index";
    private final File file;
    private Summary summary = new Summary(0, null);

    static final class Summary {
        final int count;
        final Instant lastRecord;
        Summary(int count, Instant lastRecord) {
            this.count = count;
            this.lastRecord = lastRecord;
        }
    }

    RecordStore(File file) { this.file = file; }

    synchronized Summary loadSummary() throws IOException {
        int count = 0;
        Instant lastRecord = null;
        if (file.exists() && file.length() > 0) {
            try (BufferedReader input = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8))) {
                if (!HEADER.equals(input.readLine())) throw new IOException("CSV 열 형식이 다릅니다.");
                String line;
                while ((line = input.readLine()) != null) {
                    String[] columns = line.split(",", -1);
                    if (columns.length != 7) continue;
                    try {
                        Instant time = Instant.parse(columns[0]);
                        count++;
                        lastRecord = time;
                    } catch (DateTimeParseException ignored) {
                        // An incomplete or damaged line does not count as a saved measurement.
                    }
                }
            }
        }
        summary = new Summary(count, lastRecord);
        return summary;
    }

    synchronized Summary append(ReadingSnapshot snapshot, Instant recordedAt) throws IOException {
        if (!snapshot.hasValues()) throw new IOException("측정 중인 유효한 값이 없습니다.");
        // Re-read before appending so a failed load never silently extends an unrelated file.
        loadSummary();
        boolean empty = !file.exists() || file.length() == 0;
        boolean needsNewline = false;
        if (!empty) {
            try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
                input.seek(input.length() - 1);
                needsNewline = input.read() != '\n';
            }
        }
        try (FileOutputStream output = new FileOutputStream(file, true)) {
            if (empty) output.write((HEADER + "\n").getBytes(StandardCharsets.UTF_8));
            else if (needsNewline) output.write('\n');
            output.write(snapshot.csvRow(recordedAt).getBytes(StandardCharsets.UTF_8));
        }
        summary = new Summary(summary.count + 1, recordedAt);
        return summary;
    }

    synchronized void copyTo(OutputStream output) throws IOException {
        if (output == null) throw new IOException("저장 위치를 열 수 없음");
        if (!file.exists() || file.length() == 0) throw new IOException("저장된 기록이 없습니다.");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }
}
