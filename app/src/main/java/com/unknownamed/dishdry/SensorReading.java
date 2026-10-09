package com.unknownamed.dishdry;

import org.json.JSONException;
import org.json.JSONObject;

final class SensorReading {
    final double temperature;
    final double humidity;
    final int lightAdc;

    private SensorReading(double temperature, double humidity, int lightAdc) {
        this.temperature = temperature;
        this.humidity = humidity;
        this.lightAdc = lightAdc;
    }

    static SensorReading parse(String body) throws JSONException {
        JSONObject json = new JSONObject(body);
        if (!"dishdry-esp32".equals(json.getString("device"))) {
            throw new JSONException("지원하지 않는 센서");
        }
        double temperature = json.getDouble("temperature_c");
        double humidity = json.getDouble("humidity_percent");
        int lightAdc = json.getInt("light_adc");
        long sampleAge = json.getLong("sample_age_ms");
        if (!Double.isFinite(temperature) || temperature < -40 || temperature > 80
                || !Double.isFinite(humidity) || humidity < 0 || humidity > 100
                || lightAdc < 0 || lightAdc > 4095 || sampleAge < 0 || sampleAge > 10000) {
            throw new JSONException("센서 범위 오류 또는 오래된 측정값");
        }
        return new SensorReading(temperature, humidity, lightAdc);
    }

    int dryingIndex() {
        // An uncalibrated environmental score; there is no dish moisture sensor.
        double score = (100 - humidity) * 0.8 + (temperature - 15) * 1.5
                + (lightAdc / 4095.0) * 10;
        return (int) Math.max(0, Math.min(100, Math.round(score)));
    }
}
