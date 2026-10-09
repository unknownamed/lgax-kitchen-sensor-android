package com.unknownamed.dishdry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** One bounded HTTP request. Scheduling and Activity lifecycle belong to the controller. */
final class SensorHttpClient {
    static final int TIMEOUT_MS = 2_500;
    static final int MAX_BODY_BYTES = 16_384;

    static String validateEndpoint(String value) throws IOException {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return trimmed;
        URL url = new URL(trimmed);
        if ((!"http".equals(url.getProtocol()) && !"https".equals(url.getProtocol()))
                || url.getHost().isEmpty() || url.getUserInfo() != null) {
            throw new IOException("http 또는 https 측정 주소를 입력하세요.");
        }
        return trimmed;
    }

    SensorReading fetch(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + responseCode);
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream body = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("요청 중지됨");
                    if (body.size() + count > MAX_BODY_BYTES) throw new IOException("응답 크기 초과");
                    body.write(buffer, 0, count);
                }
                return SensorReading.parse(body.toString(StandardCharsets.UTF_8.name()));
            }
        } finally {
            connection.disconnect();
        }
    }
}
