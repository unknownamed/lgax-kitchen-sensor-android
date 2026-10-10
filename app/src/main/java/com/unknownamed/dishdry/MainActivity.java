package com.unknownamed.dishdry;

import android.app.Activity;
import android.content.Intent;
import android.content.ContentValues;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.WindowInsets;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Coordinates the screen and Android document picker; measurements and CSV have their own owners. */
public class MainActivity extends Activity {
    private static final int EXPORT_CSV = 21;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private DashboardView screen;
    private MeasurementController measurements;
    private RecordStore records;
    private RecordStore.Summary summary = new RecordStore.Summary(0, null);
    private boolean fileBusy;
    private volatile boolean destroyed;
    private boolean examplePreview;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        applySystemInsets();
        screen = new DashboardView(this);
        examplePreview = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0
                && getIntent().getBooleanExtra("example_preview", false);
        findViewById(R.id.example_notice).setVisibility(examplePreview ? View.VISIBLE : View.GONE);
        findViewById(R.id.example_record_notice).setVisibility(examplePreview ? View.VISIBLE : View.GONE);
        measurements = new MeasurementController(this, this::render, examplePreview);
        records = new RecordStore(new File(getFilesDir(), examplePreview ? "example-readings.csv" : "readings.csv"));
        screen.endpoint.setText(examplePreview ? "http://192.168.4.1/readings"
                : getPreferences(MODE_PRIVATE).getString("endpoint", ""));
        screen.toggle.setOnClickListener(view -> toggleMeasurement());
        screen.save.setOnClickListener(view -> saveReading());
        screen.export.setOnClickListener(view -> exportCsv());
        fileBusy = true;
        render();
        files.execute(() -> {
            try {
                RecordStore.Summary loaded = records.loadSummary();
                if (examplePreview) prepareExampleDownloads();
                finishFileWork(() -> summary = loaded);
            } catch (Exception error) {
                finishFileWork(() -> toast(getString(R.string.record_failed, error.getMessage())));
            }
        });
    }

    private void prepareExampleDownloads() throws IOException {
        if (android.os.Build.VERSION.SDK_INT < 29) return;
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        for (int index = 1; index <= 3; index++) {
            String name = "dish-dry-example-0" + index + ".csv";
            try (Cursor existing = getContentResolver().query(collection,
                    new String[] {MediaStore.MediaColumns._ID},
                    MediaStore.MediaColumns.DISPLAY_NAME + " = ?", new String[] {name}, null)) {
                if (existing != null && existing.moveToFirst()) continue;
            }
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri created = getContentResolver().insert(collection, values);
            if (created == null) throw new IOException("샘플 파일을 만들 수 없습니다.");
            try {
                ReadingSnapshot sample = new ReadingSnapshot(
                        SensorReading.validated(25, 50, 1000, 0), 320f, 0.12f);
                String csv = RecordStore.HEADER + "\n" + sample.csvRow(Instant.now());
                try (OutputStream output = getContentResolver().openOutputStream(created, "w")) {
                    if (output == null) throw new IOException("샘플 파일을 열 수 없습니다.");
                    output.write(csv.getBytes(StandardCharsets.UTF_8));
                }
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContentResolver().update(created, values, null, null);
            } catch (IOException | RuntimeException error) {
                getContentResolver().delete(created, null, null);
                throw error;
            }
        }
    }

    private void applySystemInsets() {
        findViewById(R.id.root).setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
    }

    private void toggleMeasurement() {
        if (measurements.isRunning()) {
            measurements.stop();
            return;
        }
        try {
            if (examplePreview) {
                measurements.start("");
                return;
            }
            String endpoint = SensorHttpClient.validateEndpoint(screen.endpoint.getText().toString());
            getPreferences(MODE_PRIVATE).edit().putString("endpoint", endpoint).apply();
            measurements.start(endpoint);
        } catch (Exception error) {
            screen.endpoint.setError(getString(R.string.invalid_url));
            toast(getString(R.string.invalid_url));
        }
    }

    private void render() {
        if (!destroyed && screen != null && measurements != null) {
            screen.render(measurements, summary, fileBusy);
        }
    }

    private void saveReading() {
        if (fileBusy) return;
        ReadingSnapshot snapshot = measurements.snapshot();
        if (!measurements.isRunning() || !snapshot.hasValues()) {
            toast(getString(R.string.record_empty));
            return;
        }
        Instant recordedAt = Instant.now();
        fileBusy = true;
        render();
        files.execute(() -> {
            try {
                RecordStore.Summary saved = records.append(snapshot, recordedAt);
                finishFileWork(() -> {
                    summary = saved;
                    toast(getString(R.string.record_saved));
                });
            } catch (Exception error) {
                finishFileWork(() -> toast(getString(R.string.record_failed, error.getMessage())));
            }
        });
    }

    private void exportCsv() {
        if (fileBusy) return;
        if (summary.count == 0) {
            toast(getString(R.string.no_records));
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, examplePreview ? "dish-dry-example-readings.csv" : "dish-dry-readings.csv");
        startActivityForResult(intent, EXPORT_CSV);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_CSV) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            toast(getString(R.string.export_cancelled));
            return;
        }
        Uri destination = data.getData();
        fileBusy = true;
        render();
        files.execute(() -> {
            try {
                try (OutputStream output = getContentResolver().openOutputStream(destination, "wt")) {
                    records.copyTo(output);
                }
                finishFileWork(() -> toast(getString(R.string.export_saved)));
            } catch (Exception error) {
                finishFileWork(() -> toast(getString(R.string.export_failed, error.getMessage())));
            }
        });
    }

    private void finishFileWork(Runnable result) {
        if (destroyed) return;
        ui.post(() -> {
            if (destroyed) return;
            fileBusy = false;
            result.run();
            render();
        });
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override protected void onPause() {
        measurements.stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        measurements.close();
        files.shutdown();
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
