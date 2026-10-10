package com.unknownamed.dishdry;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** One bounded HTTP request. Scheduling and Activity lifecycle belong to the controller. */
final class SensorHttpClient {
    static final int TIMEOUT_MS = 2_500;
    static final int MAX_BODY_BYTES = 16_384;

    enum Failure { TIMEOUT, UNREACHABLE, ADDRESS_NOT_FOUND, SENSOR_UNAVAILABLE,
        HTTP_RESPONSE, INVALID_READING, RESPONSE_TOO_LARGE, COMMUNICATION }

    static final class RequestFailure extends IOException {
        final Failure reason;
        final int httpStatus;

        RequestFailure(Failure reason, int httpStatus, Throwable cause) {
            super(reason.name(), cause);
            this.reason = reason;
            this.httpStatus = httpStatus;
        }

        static RequestFailure http(int status) {
            Failure reason = status == 404 ? Failure.ADDRESS_NOT_FOUND
                    : status == 503 ? Failure.SENSOR_UNAVAILABLE : Failure.HTTP_RESPONSE;
            return new RequestFailure(reason, status, null);
        }
    }

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

    SensorReading fetch(String endpoint) throws RequestFailure {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) throw RequestFailure.http(responseCode);
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream body = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("요청 중지됨");
                    if (body.size() + count > MAX_BODY_BYTES) {
                        throw new RequestFailure(Failure.RESPONSE_TOO_LARGE, 0, null);
                    }
                    body.write(buffer, 0, count);
                }
                return SensorReading.parse(body.toString(StandardCharsets.UTF_8.name()));
            }
        } catch (RequestFailure error) {
            throw error;
        } catch (SocketTimeoutException error) {
            throw new RequestFailure(Failure.TIMEOUT, 0, error);
        } catch (UnknownHostException | ConnectException | NoRouteToHostException error) {
            throw new RequestFailure(Failure.UNREACHABLE, 0, error);
        } catch (JSONException error) {
            throw new RequestFailure(Failure.INVALID_READING, 0, error);
        } catch (IOException error) {
            throw new RequestFailure(Failure.COMMUNICATION, 0, error);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
