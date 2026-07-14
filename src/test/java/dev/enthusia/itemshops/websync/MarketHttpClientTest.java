package dev.enthusia.itemshops.websync;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketHttpClientTest {
    @Test
    void sendsExactBodyAndRequiredSignedHeaders() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> signature = new AtomicReference<>();
        server.createContext("/internal/v1/test", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            signature.set(exchange.getRequestHeaders().getFirst("X-Enthusia-Signature"));
            assertEquals("enthusia-main", exchange.getRequestHeaders().getFirst("X-Enthusia-Server-Id"));
            assertEquals("event-1", exchange.getRequestHeaders().getFirst("X-Enthusia-Event-Id"));
            exchange.sendResponseHeaders(200, 0); exchange.close();
        });
        server.start();
        try {
            byte[] exact = "{\"probe\":\"exact\"}".getBytes(StandardCharsets.UTF_8);
            MarketHttpClient client = new MarketHttpClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()),
                    "enthusia-main", "local-test-secret-with-sufficient-entropy", Duration.ofSeconds(2), Duration.ofSeconds(2));
            assertTrue(client.send("POST", "/internal/v1/test", "event-1", exact).successful());
            assertEquals(new String(exact, StandardCharsets.UTF_8), body.get());
            assertTrue(signature.get().matches("v1=[0-9a-f]{64}"));
        } finally { server.stop(0); }
    }
}
