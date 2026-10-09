package com.unknownamed.dishdry;

/** Uses monotonic milliseconds, so changing the phone clock cannot revive old samples. */
final class MeasurementState {
    static final long EXTERNAL_MAX_AGE_MS = 10_000;
    static final long MOTION_MAX_AGE_MS = 2_000;
    private boolean running;
    private SensorReading external;
    private long sampledAt;
    private Float lux;
    private Float movement;
    private long motionAt;

    void start() {
        clear();
        running = true;
    }

    void stop() {
        running = false;
        clear();
    }

    boolean isRunning() { return running; }

    void receive(SensorReading reading, long receivedAt) {
        if (!running) return;
        external = reading;
        sampledAt = reading == null ? 0 : receivedAt - reading.sampleAgeMs;
    }

    void updateLight(float value) {
        if (running && Float.isFinite(value) && value >= 0) lux = value;
    }

    void updateMotion(float value, long now) {
        if (running && Float.isFinite(value) && value >= 0) {
            movement = value;
            motionAt = now;
        }
    }

    ReadingSnapshot snapshot(long now) {
        SensorReading validExternal = running && external != null
                && now - sampledAt <= EXTERNAL_MAX_AGE_MS ? external : null;
        Float validMotion = running && movement != null
                && now - motionAt <= MOTION_MAX_AGE_MS ? movement : null;
        return new ReadingSnapshot(validExternal, running ? lux : null, validMotion);
    }

    private void clear() {
        external = null;
        lux = null;
        movement = null;
    }
}
