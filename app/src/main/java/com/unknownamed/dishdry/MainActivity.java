package com.unknownamed.dishdry;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowInsets;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.OutputStream;
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
                finishFileWork(() -> summary = loaded);
            } catch (Exception error) {
                finishFileWork(() -> toast(getString(R.string.record_failed, error.getMessage())));
            }
        });
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
