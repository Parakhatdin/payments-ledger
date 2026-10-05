package dev.parakhatdin.ledger.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Verifies the {@code Stripe-Signature} header, following Stripe's documented scheme.
 *
 * <p>The header looks like {@code t=1700000000,v1=5257a8...,v1=...}. Stripe computes
 * {@code HMAC-SHA256(secret, t + "." + rawBody)}. We recompute it and compare. Three details matter:
 * <ul>
 *   <li>Use the <b>raw</b> request body. Re-serialised JSON would differ by a single space and fail.</li>
 *   <li>Compare in constant time ({@link MessageDigest#isEqual}), so response timing leaks nothing.</li>
 *   <li>Reject old timestamps, so a captured request cannot be replayed later.</li>
 * </ul>
 * There may be several {@code v1} values while a secret is being rotated; any match is accepted.
 */
@Component
public class StripeSignatureVerifier {

    private final byte[] secret;
    private final Duration tolerance;
    private final Clock clock;

    public StripeSignatureVerifier(StripeProperties properties, Clock clock) {
        this.secret = properties.webhookSecret().getBytes(StandardCharsets.UTF_8);
        this.tolerance = properties.signatureTolerance();
        this.clock = clock;
    }

    public void verify(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new InvalidSignatureException("Missing Stripe-Signature header");
        }

        Long timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            switch (kv[0]) {
                case "t" -> timestamp = parseTimestamp(kv[1]);
                case "v1" -> signatures.add(kv[1]);
                default -> { } // other schemes (e.g. v0 test signatures) are ignored
            }
        }

        if (timestamp == null || signatures.isEmpty()) {
            throw new InvalidSignatureException("Malformed Stripe-Signature header");
        }

        Instant signedAt = Instant.ofEpochSecond(timestamp);
        if (Duration.between(signedAt, clock.instant()).abs().compareTo(tolerance) > 0) {
            throw new InvalidSignatureException("Signature timestamp outside tolerance");
        }

        byte[] expected = sign(timestamp + "." + payload);
        for (String candidate : signatures) {
            byte[] actual;
            try {
                actual = HexFormat.of().parseHex(candidate);
            } catch (IllegalArgumentException notHex) {
                continue;
            }
            if (MessageDigest.isEqual(expected, actual)) {
                return;
            }
        }
        throw new InvalidSignatureException("No matching v1 signature");
    }

    /** Builds a valid header for a payload. Used by tests and the local demo script. */
    public String header(String payload, Instant at) {
        long t = at.getEpochSecond();
        return "t=" + t + ",v1=" + HexFormat.of().formatHex(sign(t + "." + payload));
    }

    private byte[] sign(String signedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static long parseTimestamp(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new InvalidSignatureException("Malformed signature timestamp");
        }
    }
}
