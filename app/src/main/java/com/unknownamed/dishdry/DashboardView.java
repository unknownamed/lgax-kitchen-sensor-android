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
    private final TextView dryingStatus;
    private final TextView connectionBadge;
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
        dryingStatus = activity.findViewById(R.id.drying_status);
        connectionBadge = activity.findViewById(R.id.connection_badge);
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
        text(dryingStatus, string(reading != null ? R.string.drying_ready
                : controller.isRunning() ? R.string.drying_waiting : R.string.drying_idle));
        if (progress.getProgress() != index) progress.setProgress(index);
        text(recordCount, activity.getString(R.string.record_count, summary.count));
        text(lastRecord, summary.lastRecord == null ? string(R.string.records_none)
                : activity.getString(R.string.last_record,
                        RECORD_TIME.withZone(ZoneId.systemDefault()).format(summary.lastRecord)));
        text(status, connectionText(controller));
        text(connectionBadge, string(connectionBadgeText(controller)));
        text(toggle, string(controller.isRunning() ? R.string.stop : R.string.start));
        endpoint.setEnabled(!controller.isRunning());
        save.setEnabled(!busy && controller.isRunning() && snapshot.hasValues());
        export.setEnabled(!busy && summary.count > 0);
    }

    private int connectionBadgeText(MeasurementController controller) {
        switch (controller.connection()) {
            case PHONE_ONLY: return R.string.badge_phone;
            case CONNECTING: return R.string.badge_connecting;
            case CONNECTED: return R.string.badge_connected;
            case FAILED: return R.string.badge_retry;
            case RETRYING: return R.string.badge_retrying;
            default: return R.string.badge_idle;
        }
    }

    private String connectionText(MeasurementController controller) {
        switch (controller.connection()) {
            case PHONE_ONLY: return string(R.string.phone_only);
            case CONNECTING: return string(R.string.connecting);
            case CONNECTED: return activity.getString(R.string.connected, controller.detail());
            case FAILED: return activity.getString(R.string.connection_failed, failureText(controller.failure()))
                    + "\n" + string(R.string.retry_scheduled);
            case RETRYING: return string(R.string.retry_connecting)
                    + "\n" + failureText(controller.failure());
            default: return string(R.string.idle);
        }
    }

    private String failureText(SensorHttpClient.RequestFailure failure) {
        if (failure == null) return string(R.string.failure_communication);
        switch (failure.reason) {
            case TIMEOUT: return string(R.string.failure_timeout);
            case UNREACHABLE: return string(R.string.failure_unreachable);
            case ADDRESS_NOT_FOUND: return string(R.string.failure_address);
            case SENSOR_UNAVAILABLE: return string(R.string.failure_sensor_unavailable);
            case HTTP_RESPONSE: return activity.getString(R.string.failure_http, failure.httpStatus);
            case INVALID_READING: return string(R.string.failure_reading);
            case RESPONSE_TOO_LARGE: return string(R.string.failure_response_size);
            default: return string(R.string.failure_communication);
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
