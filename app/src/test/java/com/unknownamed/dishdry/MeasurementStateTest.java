package com.unknownamed.dishdry;

import org.junit.Test;
import static org.junit.Assert.*;

public class MeasurementStateTest {
    @Test public void packetAgeCountsTowardExpiry() {
        MeasurementState state = new MeasurementState();
        state.start();
        state.receive(SensorReading.validated(25, 50, 1000, 9000), 20_000);
        assertNotNull(state.snapshot(21_000).external);
        assertNull(state.snapshot(21_001).external);
    }

    @Test public void stopAndRestartNeverReusePreviousValues() {
        MeasurementState state = new MeasurementState();
        state.start();
        state.updateLight(42);
        state.updateMotion(0.5f, 20_000);
        state.stop();
        state.receive(SensorReading.validated(25, 50, 1000, 0), 20_000);
        assertFalse(state.snapshot(20_000).hasValues());
        state.start();
        assertFalse(state.snapshot(20_000).hasValues());
    }

    @Test public void failedRequestClearsExternalValueButKeepsPhoneValues() {
        MeasurementState state = new MeasurementState();
        state.start();
        state.receive(SensorReading.validated(25, 50, 1000, 0), 20_000);
        state.updateLight(42);
        state.receive(null, 21_000);
        assertNull(state.snapshot(21_000).external);
        assertEquals(42f, state.snapshot(21_000).lux, 0f);
    }

    @Test public void motionExpiresAndNonFinitePhoneValuesAreIgnored() {
        MeasurementState state = new MeasurementState();
        state.start();
        state.updateLight(Float.NaN);
        state.updateMotion(Float.POSITIVE_INFINITY, 20_000);
        assertFalse(state.snapshot(20_000).hasValues());
        state.updateMotion(0.5f, 20_000);
        assertNotNull(state.snapshot(22_000).movement);
        assertNull(state.snapshot(22_001).movement);
    }
}
