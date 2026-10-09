package com.unknownamed.dishdry;

import org.junit.Test;
import java.io.IOException;
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
}
