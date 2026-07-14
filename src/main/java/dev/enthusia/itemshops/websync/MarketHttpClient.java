package dev.enthusia.itemshops.websync;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class MarketHttpClient {
    private final URI endpoint;
    private final String serverId;
    private final String secret;
    private final Duration requestTimeout;
    private final HttpClient client;

    public MarketHttpClient(WebsiteSyncSettings.Values settings) {
        this(URI.create(settings.endpoint()), settings.serverId(), settings.secret(),
                Duration.ofSeconds(settings.connectTimeoutSeconds()), Duration.ofSeconds(settings.requestTimeoutSeconds()));
    }

    public MarketHttpClient(URI endpoint, String serverId, String secret, Duration connectTimeout, Duration requestTimeout) {
        if (endpoint.getScheme() == null || endpoint.getHost() == null) throw new IllegalArgumentException("Market endpoint must be an absolute URI");
        this.endpoint = endpoint;
        this.serverId = serverId;
        this.secret = secret;
        this.requestTimeout = requestTimeout;
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public Result send(String method, String pathname, String eventId, byte[] exactBody) throws IOException, InterruptedException {
        String timestamp = Long.toString(System.currentTimeMillis());
        String signature = MarketRequestSigner.sign(secret, method, pathname, serverId, timestamp, eventId, exactBody);
        HttpRequest request = HttpRequest.newBuilder(endpoint.resolve(pathname))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("X-Enthusia-Server-Id", serverId)
                .header("X-Enthusia-Timestamp", timestamp)
                .header("X-Enthusia-Event-Id", eventId)
                .header("X-Enthusia-Signature", signature)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(exactBody))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), response.statusCode() >= 200 && response.statusCode() < 300);
    }

    public record Result(int statusCode, boolean successful) {}
}
