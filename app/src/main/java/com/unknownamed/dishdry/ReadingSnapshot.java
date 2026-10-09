package com.unknownamed.dishdry;

import java.time.Instant;
import java.util.Locale;

/** Values that are still valid at one moment, shared by the screen and the CSV writer. */
final class ReadingSnapshot {
    final SensorReading external;
    final Float lux;
    final Float movement;

    ReadingSnapshot(SensorReading external, Float lux, Float movement) {
        this.external = external;
        this.lux = lux;
        this.movement = movement;
    }

    boolean hasValues() {
        return external != null || lux != null || movement != null;
    }

    String csvRow(Instant recordedAt) {
        return recordedAt + "," + decimal(external == null ? null : external.temperature)
                + "," + decimal(external == null ? null : external.humidity)
                + "," + (external == null ? "" : external.lightAdc)
                + "," + decimal(lux) + "," + decimal(movement)
                + "," + (external == null ? "" : external.dryingIndex()) + "\n";
    }

    private static String decimal(Number value) {
        return value == null ? "" : String.format(Locale.US, "%.3f", value.doubleValue());
    }
}
