package dev.parakhatdin.ledger.webhook;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed config under {@code ledger.stripe}. */
@ConfigurationProperties("ledger.stripe")
public record StripeProperties(String webhookSecret, Duration signatureTolerance) {

    public StripeProperties {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("ledger.stripe.webhook-secret must be set");
        }
        if (signatureTolerance == null) {
            signatureTolerance = Duration.ofMinutes(5);
        }
    }
}
