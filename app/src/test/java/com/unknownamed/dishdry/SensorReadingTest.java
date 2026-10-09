package com.unknownamed.dishdry;

import org.junit.Test;
import static org.junit.Assert.*;

public class SensorReadingTest {
    @Test public void dryingIndexStaysWithinDisplayRange() {
        assertEquals(0, SensorReading.validated(-40, 100, 0, 0).dryingIndex());
        assertEquals(100, SensorReading.validated(80, 0, 4095, 0).dryingIndex());
        assertEquals(58, SensorReading.validated(25, 50, 1000, 0).dryingIndex());
    }

    @Test public void invalidAndStalePacketsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SensorReading.validated(Double.NaN, 50, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> SensorReading.validated(25, 101, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> SensorReading.validated(25, 50, 4096, 0));
        assertThrows(IllegalArgumentException.class, () -> SensorReading.validated(25, 50, 0, 10_001));
        assertThrows(IllegalArgumentException.class, () -> SensorReading.validated(25, 50, 0, -1));
    }
}
