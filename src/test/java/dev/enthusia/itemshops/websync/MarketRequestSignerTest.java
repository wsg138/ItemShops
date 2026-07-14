package dev.enthusia.itemshops.websync;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MarketRequestSignerTest {
    private static final String BODY = "{\"schemaVersion\":1,\"serverId\":\"enthusia-main\",\"serverEpoch\":\"epoch\",\"eventId\":\"00000000-0000-4000-8000-000000000001\",\"sentAt\":\"2024-07-03T09:46:40Z\",\"probe\":\"random\"}";

    @Test
    void matchesBackendCrossLanguageVector() {
        String signature = sign("/internal/v1/test", BODY);
        assertEquals("v1=afdc4cf12ead0bac0797b24fad10d26de7397545a7a77f1a4b6bd34e3970c751", signature);
    }

    @Test
    void exactBodyAndPathAreAuthenticated() {
        String signature = sign("/internal/v1/test", BODY);
        assertNotEquals(signature, sign("/internal/v1/test", BODY + " "));
        assertNotEquals(signature, sign("/internal/v1/other", BODY));
    }

    private static String sign(String path, String body) {
        return MarketRequestSigner.sign("local-test-secret-with-sufficient-entropy", "POST", path, "enthusia-main",
                "1720000000000", "00000000-0000-4000-8000-000000000001", body.getBytes(StandardCharsets.UTF_8));
    }
}
