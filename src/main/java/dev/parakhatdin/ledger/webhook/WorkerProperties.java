package dev.parakhatdin.ledger.webhook;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed config under {@code ledger.webhooks.worker}. */
@ConfigurationProperties("ledger.webhooks.worker")
public record WorkerProperties(boolean enabled, Duration pollInterval, int batchSize, int maxAttempts) {

    public WorkerProperties {
        if (pollInterval == null) {
            pollInterval = Duration.ofSeconds(1);
        }
        if (batchSize <= 0) {
            batchSize = 20;
        }
        if (maxAttempts <= 0) {
            maxAttempts = 5;
        }
    }
}
