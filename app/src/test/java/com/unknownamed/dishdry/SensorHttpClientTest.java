package com.unknownamed.dishdry;

import org.junit.Test;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class SensorHttpClientTest {
    @Test public void blankAddressUsesPhoneOnlyAndHttpAddressIsTrimmed() throws Exception {
        assertEquals("", SensorHttpClient.validateEndpoint("  "));
        assertEquals("http://192.168.4.1/readings", SensorHttpClient.validateEndpoint(" http://192.168.4.1/readings "));
        assertEquals("https://example.com/readings", SensorHttpClient.validateEndpoint("https://example.com/readings"));
    }

    @Test public void invalidHostProtocolAndEmbeddedCredentialsAreRejected() {
        assertThrows(IOException.class, () -> SensorHttpClient.validateEndpoint("http:///readings"));
        assertThrows(IOException.class, () -> SensorHttpClient.validateEndpoint("file:///readings"));
        assertThrows(IOException.class, () -> SensorHttpClient.validateEndpoint("http://name:password@example.com/readings"));
    }

    @Test public void httpErrorsDistinguishMissingAddressAndUnavailableSensor() throws Exception {
        try (ResponseServer server = new ResponseServer(404, 0, 0, "")) {
            assertFailure(server, SensorHttpClient.Failure.ADDRESS_NOT_FOUND, 404);
        }
        try (ResponseServer server = new ResponseServer(503, 0, 0, "")) {
            assertFailure(server, SensorHttpClient.Failure.SENSOR_UNAVAILABLE, 503);
        }
        try (ResponseServer server = new ResponseServer(500, 0, 0, "")) {
            assertFailure(server, SensorHttpClient.Failure.HTTP_RESPONSE, 500);
        }
    }

    @Test public void redirectIsReportedWithoutRequestingAnotherAddress() throws Exception {
        try (ResponseServer server = new ResponseServer(302, 0, 0,
                "Location: http://127.0.0.1:1/target\r\n")) {
            assertFailure(server, SensorHttpClient.Failure.HTTP_RESPONSE, 302);
        }
    }

    @Test public void oversizedResponseIsRejectedBeforeParsing() throws Exception {
        try (ResponseServer server = new ResponseServer(200, SensorHttpClient.MAX_BODY_BYTES + 1, 0, "")) {
            assertFailure(server, SensorHttpClient.Failure.RESPONSE_TOO_LARGE, 0);
        }
    }

    @Test public void closedPortIsReportedAsUnreachable() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            port = socket.getLocalPort();
        }
        SensorHttpClient.RequestFailure failure = assertThrows(SensorHttpClient.RequestFailure.class,
                () -> new SensorHttpClient().fetch("http://127.0.0.1:" + port + "/readings"));
        assertEquals(SensorHttpClient.Failure.UNREACHABLE, failure.reason);
        assertEquals(0, failure.httpStatus);
    }

    @Test public void delayedResponseIsReportedAsTimeout() throws Exception {
        try (ResponseServer server = new ResponseServer(503, 0, SensorHttpClient.TIMEOUT_MS + 500L, "")) {
            assertFailure(server, SensorHttpClient.Failure.TIMEOUT, 0);
        }
    }

    private static void assertFailure(ResponseServer server, SensorHttpClient.Failure reason, int status) {
        SensorHttpClient.RequestFailure failure = assertThrows(SensorHttpClient.RequestFailure.class,
                () -> new SensorHttpClient().fetch("http://127.0.0.1:"
                        + server.listener.getLocalPort() + "/readings"));
        assertEquals(reason, failure.reason);
        assertEquals(status, failure.httpStatus);
    }

    /** One local HTTP reply; no Android or external sensor is involved. */
    private static final class ResponseServer implements AutoCloseable {
        final ServerSocket listener;
        final Thread worker;

        ResponseServer(int status, int bodySize, long delayMs, String headers) throws IOException {
            listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            worker = new Thread(() -> {
                try (Socket connection = listener.accept()) {
                    connection.setSoTimeout(1_000);
                    BufferedReader input = new BufferedReader(new InputStreamReader(
                            connection.getInputStream(), StandardCharsets.US_ASCII));
                    String line;
                    while ((line = input.readLine()) != null && !line.isEmpty()) { }
                    if (delayMs > 0) Thread.sleep(delayMs);
                    OutputStream output = connection.getOutputStream();
                    output.write(("HTTP/1.1 " + status + " Test\r\nContent-Length: " + bodySize
                            + "\r\nConnection: close\r\n" + headers + "\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    output.write(new byte[bodySize]);
                    output.flush();
                } catch (IOException ignored) {
                    // A timed-out client or close() can close the socket first.
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            });
            worker.setDaemon(true);
            worker.start();
        }

        @Override public void close() throws Exception {
            listener.close();
            worker.interrupt();
            worker.join(1_000);
        }
    }
}
