package com.unknownamed.dishdry;

import android.app.Activity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Binds native views once and only updates text when the displayed value changes. */
final class DashboardView {
    private static final DateTimeFormatter RECORD_TIME = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    final EditText endpoint;
    final Button toggle;
    final Button save;
    final Button export;
    private final Activity activity;
    private final TextView status;
    private final TextView temperature;
    private final TextView humidity;
    private final TextView ldr;
    private final TextView light;
    private final TextView motion;
    private final TextView drying;
    private final TextView recordCount;
    private final TextView lastRecord;
    private final ProgressBar progress;

    DashboardView(Activity activity) {
        this.activity = activity;
        endpoint = activity.findViewById(R.id.endpoint);
        toggle = activity.findViewById(R.id.toggle);
        save = activity.findViewById(R.id.save);
        export = activity.findViewById(R.id.export);
        status = activity.findViewById(R.id.status);
        temperature = activity.findViewById(R.id.temperature);
        humidity = activity.findViewById(R.id.humidity);
        ldr = activity.findViewById(R.id.ldr);
        light = activity.findViewById(R.id.light);
        motion = activity.findViewById(R.id.motion);
        drying = activity.findViewById(R.id.drying);
        recordCount = activity.findViewById(R.id.record_count);
        lastRecord = activity.findViewById(R.id.last_record);
        progress = activity.findViewById(R.id.drying_progress);
    }

    void render(MeasurementController controller, RecordStore.Summary summary, boolean busy) {
        ReadingSnapshot snapshot = controller.snapshot();
        SensorReading reading = snapshot.external;
        text(temperature, number(reading == null ? null : reading.temperature));
        text(humidity, number(reading == null ? null : reading.humidity));
        text(ldr, reading == null ? string(R.string.not_measured) : String.valueOf(reading.lightAdc));
        text(light, controller.hasLightSensor() ? number(snapshot.lux) : string(R.string.sensor_absent));
        text(motion, controller.hasMotionSensor() ? number(snapshot.movement) : string(R.string.sensor_absent));
        int index = reading == null ? 0 : reading.dryingIndex();
        text(drying, reading == null ? string(R.string.reading_placeholder) : String.valueOf(index));
        if (progress.getProgress() != index) progress.setProgress(index);
        text(recordCount, activity.getString(R.string.record_count, summary.count));
        text(lastRecord, summary.lastRecord == null ? string(R.string.records_none)
                : activity.getString(R.string.last_record,
                        RECORD_TIME.withZone(ZoneId.systemDefault()).format(summary.lastRecord)));
        text(status, connectionText(controller));
        text(toggle, string(controller.isRunning() ? R.string.stop : R.string.start));
        endpoint.setEnabled(!controller.isRunning());
        save.setEnabled(!busy && controller.isRunning() && snapshot.hasValues());
        export.setEnabled(!busy && summary.count > 0);
    }

    private String connectionText(MeasurementController controller) {
        switch (controller.connection()) {
            case PHONE_ONLY: return string(R.string.phone_only);
            case CONNECTING: return string(R.string.connecting);
            case CONNECTED: return activity.getString(R.string.connected, controller.detail());
            case FAILED: return activity.getString(R.string.connection_failed, controller.detail());
            default: return string(R.string.idle);
        }
    }

    private String string(int resource) { return activity.getString(resource); }

    private String number(Number value) {
        return value == null ? string(R.string.not_measured) : String.format(Locale.US, "%.1f", value.doubleValue());
    }

    private static void text(TextView view, String value) {
        if (!value.contentEquals(view.getText())) view.setText(value);
    }
}
