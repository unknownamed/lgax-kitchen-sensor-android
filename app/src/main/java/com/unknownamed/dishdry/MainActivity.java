package com.unknownamed.dishdry;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int EXPORT_CSV = 21;
    private static final long POLL_MS = 5000;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private SensorManager sensors;
    private Sensor lightSensor;
    private Sensor accelerometer;
    private EditText endpoint;
    private Button toggle;
    private TextView status;
    private File logFile;
    private boolean running;
    private boolean requestPending;
    private int session;
    private SensorReading external;
    private long receivedAt;
    private Float lux;
    private Float movement;
    private long motionAt;
    private String activeUrl;
    private int recordCount;
    private Instant lastRecord;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (!activeUrl.isEmpty() && !requestPending) fetchReading();
            render();
            ui.postDelayed(this, POLL_MS);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
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
        endpoint = findViewById(R.id.endpoint);
        toggle = findViewById(R.id.toggle);
        status = findViewById(R.id.status);
        endpoint.setText(getPreferences(MODE_PRIVATE).getString("endpoint", ""));
        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        lightSensor = sensors.getDefaultSensor(Sensor.TYPE_LIGHT);
        accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        logFile = new File(getFilesDir(), "readings.csv");
        loadRecordSummary();
        toggle.setOnClickListener(view -> { if (running) stop(); else start(); });
        findViewById(R.id.save).setOnClickListener(view -> saveReading());
        findViewById(R.id.export).setOnClickListener(view -> exportCsv());
        render();
    }

    private void start() {
        activeUrl = endpoint.getText().toString().trim();
        if (!activeUrl.isEmpty()) {
            try {
                URL url = new URL(activeUrl);
                if ((!url.getProtocol().equals("http") && !url.getProtocol().equals("https"))
                        || url.getHost().isEmpty() || url.getUserInfo() != null) {
                    throw new IllegalArgumentException();
                }
            } catch (Exception error) {
                toast(getString(R.string.invalid_url));
                return;
            }
        }
        getPreferences(MODE_PRIVATE).edit().putString("endpoint", activeUrl).apply();
        session++;
        running = true;
        lux = null;
        movement = null;
        external = null;
        if (lightSensor != null) sensors.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        if (accelerometer != null) sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        endpoint.setEnabled(false);
        toggle.setText(R.string.stop);
        status.setText(activeUrl.isEmpty() ? R.string.phone_only : R.string.connecting);
        ui.post(poll);
    }

    private void stop() {
        running = false;
        session++;
        ui.removeCallbacks(poll);
        sensors.unregisterListener(this);
        external = null;
        lux = null;
        movement = null;
        endpoint.setEnabled(true);
        toggle.setText(R.string.start);
        status.setText(R.string.idle);
        render();
    }

    private void fetchReading() {
        requestPending = true;
        final int requestedSession = session;
        final String requestedUrl = activeUrl;
        network.execute(() -> {
            SensorReading reading = null;
            String failure = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(requestedUrl).openConnection();
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(2500);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "application/json");
                if (connection.getResponseCode() != 200) {
                    throw new java.io.IOException("HTTP " + connection.getResponseCode());
                }
                try (InputStream input = connection.getInputStream();
                     ByteArrayOutputStream body = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (body.size() + count > 16384) throw new java.io.IOException("응답 크기 초과");
                        body.write(buffer, 0, count);
                    }
                    reading = SensorReading.parse(body.toString(StandardCharsets.UTF_8.name()));
                }
            } catch (Exception error) {
                failure = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            } finally {
                if (connection != null) connection.disconnect();
            }
            final SensorReading result = reading;
            final String message = failure;
            ui.post(() -> {
                requestPending = false;
                if (!running || requestedSession != session) return;
                external = result;
                receivedAt = SystemClock.elapsedRealtime();
                status.setText(result == null
                        ? getString(R.string.connection_failed, message)
                        : getString(R.string.connected, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))));
                render();
            });
        });
    }

    private SensorReading currentExternal() {
        return running && external != null && SystemClock.elapsedRealtime() - receivedAt <= 10000
                ? external : null;
    }

    private Float currentMovement() {
        return running && movement != null && SystemClock.elapsedRealtime() - motionAt <= 2000
                ? movement : null;
    }

    private void render() {
        SensorReading reading = currentExternal();
        text(R.id.temperature, number(reading == null ? null : reading.temperature));
        text(R.id.humidity, number(reading == null ? null : reading.humidity));
        text(R.id.ldr, reading == null ? getString(R.string.not_measured) : String.valueOf(reading.lightAdc));
        text(R.id.light, lightSensor == null ? getString(R.string.sensor_absent) : number(lux));
        text(R.id.motion, accelerometer == null ? getString(R.string.sensor_absent) : number(currentMovement()));
        text(R.id.drying, reading == null ? getString(R.string.reading_placeholder) : String.valueOf(reading.dryingIndex()));
        ((ProgressBar) findViewById(R.id.drying_progress)).setProgress(reading == null ? 0 : reading.dryingIndex());
        text(R.id.record_count, getString(R.string.record_count, recordCount));
        text(R.id.last_record, lastRecord == null ? getString(R.string.records_none)
                : getString(R.string.last_record, DateTimeFormatter.ofPattern("MM.dd HH:mm")
                        .withZone(ZoneId.systemDefault()).format(lastRecord)));
        findViewById(R.id.save).setEnabled(running && (reading != null || lux != null || currentMovement() != null));
        findViewById(R.id.export).setEnabled(recordCount > 0);
    }

    private void text(int id, String value) {
        ((TextView) findViewById(id)).setText(value);
    }

    private String number(Number value) {
        return value == null ? getString(R.string.not_measured) : String.format(Locale.US, "%.1f", value.doubleValue());
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (!running) return;
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            lux = event.values[0];
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            double magnitude = Math.sqrt(event.values[0] * event.values[0]
                    + event.values[1] * event.values[1] + event.values[2] * event.values[2]);
            movement = (float) Math.abs(magnitude - SensorManager.GRAVITY_EARTH);
            motionAt = SystemClock.elapsedRealtime();
        }
        render();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private String csv(Number value) {
        return value == null ? "" : String.format(Locale.US, "%.3f", value.doubleValue());
    }

    private void saveReading() {
        SensorReading reading = currentExternal();
        Float motion = currentMovement();
        if (!running || (reading == null && lux == null && motion == null)) {
            toast(getString(R.string.record_empty));
            return;
        }
        Instant recordedAt = Instant.now();
        String row = recordedAt + "," + csv(reading == null ? null : reading.temperature)
                + "," + csv(reading == null ? null : reading.humidity)
                + "," + (reading == null ? "" : reading.lightAdc)
                + "," + csv(lux) + "," + csv(motion)
                + "," + (reading == null ? "" : reading.dryingIndex()) + "\n";
        try (FileOutputStream output = new FileOutputStream(logFile, true)) {
            if (logFile.length() == 0) {
                output.write("recorded_at_utc,temperature_c,humidity_percent,light_adc,phone_lux,phone_motion_m_s2,drying_index\n".getBytes(StandardCharsets.UTF_8));
            }
            output.write(row.getBytes(StandardCharsets.UTF_8));
            recordCount++;
            lastRecord = recordedAt;
            render();
            toast(getString(R.string.record_saved));
        } catch (Exception error) {
            toast(getString(R.string.record_failed, error.getMessage()));
        }
    }

    private void loadRecordSummary() {
        if (!logFile.exists()) return;
        try (BufferedReader input = new BufferedReader(new InputStreamReader(
                new FileInputStream(logFile), StandardCharsets.UTF_8))) {
            input.readLine();
            String line;
            while ((line = input.readLine()) != null) {
                if (line.isEmpty()) continue;
                recordCount++;
                try { lastRecord = Instant.parse(line.split(",", 2)[0]); }
                catch (java.time.format.DateTimeParseException ignored) { }
            }
        } catch (java.io.IOException error) {
            toast(getString(R.string.record_failed, error.getMessage()));
        }
    }

    private void exportCsv() {
        if (!logFile.exists() || logFile.length() == 0) {
            toast(getString(R.string.no_records));
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "dish-dry-readings.csv");
        startActivityForResult(intent, EXPORT_CSV);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_CSV || resultCode != RESULT_OK || data == null) return;
        Uri destination = data.getData();
        if (destination == null) return;
        try (InputStream input = new FileInputStream(logFile);
             OutputStream output = getContentResolver().openOutputStream(destination, "wt")) {
            if (output == null) throw new java.io.IOException("저장 위치를 열 수 없음");
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            toast(getString(R.string.export_saved));
        } catch (Exception error) {
            toast(getString(R.string.export_failed, error.getMessage()));
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override protected void onPause() {
        stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        network.shutdownNow();
        super.onDestroy();
    }
}
