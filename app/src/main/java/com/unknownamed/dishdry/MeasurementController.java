package com.unknownamed.dishdry;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns sensor registration, polling, and rejection of replies from a stopped session. */
final class MeasurementController implements SensorEventListener, AutoCloseable {
    enum Connection { IDLE, PHONE_ONLY, CONNECTING, CONNECTED, FAILED, RETRYING, EXAMPLE }
    private static final long POLL_MS = 5_000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final SensorHttpClient client = new SensorHttpClient();
    private final MeasurementState state = new MeasurementState();
    private final SensorManager sensors;
    private final Sensor light;
    private final Sensor accelerometer;
    private final Runnable changed;
    private final boolean examplePreview;
    private int session;
    private int pendingSession = -1;
    private volatile boolean closed;
    private String endpoint = "";
    private Connection connection = Connection.IDLE;
    private String detail = "";
    private SensorHttpClient.RequestFailure failure;

    MeasurementController(Context context, Runnable changed, boolean examplePreview) {
        this.changed = changed;
        this.examplePreview = examplePreview;
        sensors = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        light = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_LIGHT);
        accelerometer = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!isRunning() || closed) return;
            if (examplePreview) fillExampleValues();
            else if (!endpoint.isEmpty() && pendingSession != session) fetchReading();
            changed.run();
            ui.postDelayed(this, POLL_MS);
        }
    };

    void start(String endpoint) {
        if (closed) return;
        ui.removeCallbacks(poll);
        session++;
        this.endpoint = endpoint;
        state.start();
        if (!examplePreview && light != null) sensors.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL);
        if (!examplePreview && accelerometer != null) sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        connection = examplePreview ? Connection.EXAMPLE
                : endpoint.isEmpty() ? Connection.PHONE_ONLY : Connection.CONNECTING;
        if (examplePreview) fillExampleValues();
        detail = "";
        failure = null;
        changed.run();
        ui.post(poll);
    }

    void stop() {
        session++;
        state.stop();
        ui.removeCallbacks(poll);
        if (sensors != null) sensors.unregisterListener(this);
        connection = Connection.IDLE;
        detail = "";
        failure = null;
        if (!closed) changed.run();
    }

    boolean isRunning() { return state.isRunning(); }
    boolean hasLightSensor() { return examplePreview || light != null; }
    boolean hasMotionSensor() { return examplePreview || accelerometer != null; }
    boolean isExamplePreview() { return examplePreview; }
    Connection connection() { return connection; }
    String detail() { return detail; }
    SensorHttpClient.RequestFailure failure() { return failure; }
    ReadingSnapshot snapshot() {
        if (examplePreview && isRunning()) fillExampleValues();
        return state.snapshot(SystemClock.elapsedRealtime());
    }

    private void fillExampleValues() {
        long now = SystemClock.elapsedRealtime();
        state.receive(SensorReading.validated(25, 50, 1000, 0), now);
        state.updateLight(320f);
        state.updateMotion(0.12f, now);
    }

    private void fetchReading() {
        final int requestedSession = session;
        final String requestedUrl = endpoint;
        pendingSession = requestedSession;
        if (connection == Connection.FAILED) connection = Connection.RETRYING;
        network.execute(() -> {
            SensorReading reading = null;
            SensorHttpClient.RequestFailure requestFailure = null;
            try {
                reading = client.fetch(requestedUrl);
            } catch (SensorHttpClient.RequestFailure error) {
                requestFailure = error;
            } catch (Exception error) {
                requestFailure = new SensorHttpClient.RequestFailure(
                        SensorHttpClient.Failure.COMMUNICATION, 0, error);
            }
            final SensorReading result = reading;
            final SensorHttpClient.RequestFailure failed = requestFailure;
            if (closed) return;
            ui.post(() -> {
                if (pendingSession == requestedSession) pendingSession = -1;
                if (closed || !isRunning() || requestedSession != session) return;
                state.receive(result, SystemClock.elapsedRealtime());
                connection = result == null ? Connection.FAILED : Connection.CONNECTED;
                failure = failed;
                detail = result == null ? "" : LocalTime.now().format(TIME);
                changed.run();
            });
        });
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (!isRunning() || examplePreview) return;
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            state.updateLight(event.values[0]);
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            double magnitude = Math.sqrt(event.values[0] * event.values[0]
                    + event.values[1] * event.values[1] + event.values[2] * event.values[2]);
            state.updateMotion((float) Math.abs(magnitude - SensorManager.GRAVITY_EARTH),
                    SystemClock.elapsedRealtime());
        }
        changed.run();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override public void close() {
        closed = true;
        stop();
        network.shutdownNow();
        ui.removeCallbacksAndMessages(null);
    }
}
