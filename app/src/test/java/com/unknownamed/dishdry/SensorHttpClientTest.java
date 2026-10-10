package com.unknownamed.dishdry;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;
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
        HttpServer server = server();
        server.createContext("/missing", exchange -> { exchange.sendResponseHeaders(404, -1); exchange.close(); });
        server.createContext("/sensor", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.createContext("/error", exchange -> { exchange.sendResponseHeaders(500, -1); exchange.close(); });
        server.start();
        try {
            assertFailure(server, "/missing", SensorHttpClient.Failure.ADDRESS_NOT_FOUND, 404);
            assertFailure(server, "/sensor", SensorHttpClient.Failure.SENSOR_UNAVAILABLE, 503);
            assertFailure(server, "/error", SensorHttpClient.Failure.HTTP_RESPONSE, 500);
        } finally { server.stop(0); }
    }

    @Test public void redirectIsReportedWithoutRequestingAnotherAddress() throws Exception {
        HttpServer server = server();
        AtomicInteger redirectedRequests = new AtomicInteger();
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/target", exchange -> {
            redirectedRequests.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            assertFailure(server, "/redirect", SensorHttpClient.Failure.HTTP_RESPONSE, 302);
            assertEquals(0, redirectedRequests.get());
        } finally { server.stop(0); }
    }

    @Test public void oversizedResponseIsRejectedBeforeParsing() throws Exception {
        HttpServer server = server();
        server.createContext("/large", exchange -> {
            byte[] body = new byte[SensorHttpClient.MAX_BODY_BYTES + 1];
            exchange.sendResponseHeaders(200, body.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) { output.write(body); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            assertFailure(server, "/large", SensorHttpClient.Failure.RESPONSE_TOO_LARGE, 0);
        } finally { server.stop(0); }
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
        HttpServer server = server();
        server.createContext("/slow", exchange -> {
            try { Thread.sleep(SensorHttpClient.TIMEOUT_MS + 500L); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            assertFailure(server, "/slow", SensorHttpClient.Failure.TIMEOUT, 0);
        } finally { server.stop(0); }
    }

    private static HttpServer server() throws IOException {
        return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    private static void assertFailure(HttpServer server, String path,
                                      SensorHttpClient.Failure reason, int status) {
        SensorHttpClient.RequestFailure failure = assertThrows(SensorHttpClient.RequestFailure.class,
                () -> new SensorHttpClient().fetch("http://127.0.0.1:"
                        + server.getAddress().getPort() + path));
        assertEquals(reason, failure.reason);
        assertEquals(status, failure.httpStatus);
    }
}
