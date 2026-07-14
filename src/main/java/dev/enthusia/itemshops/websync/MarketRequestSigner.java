package dev.enthusia.itemshops.websync;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class MarketRequestSigner {
    private static final HexFormat HEX = HexFormat.of();

    private MarketRequestSigner() {}

    public static String sign(String secret, String method, String pathname, String serverId,
                              String timestamp, String eventId, byte[] exactBody) {
        if (secret == null || secret.isEmpty()) throw new IllegalArgumentException("Market sync secret is not configured");
        String bodyHash = sha256(exactBody);
        String canonical = String.join("\n", "v1", method, pathname, serverId, timestamp, eventId, bodyHash);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "v1=" + HEX.formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
