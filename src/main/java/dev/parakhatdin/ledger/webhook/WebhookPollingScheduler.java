package dev.parakhatdin.ledger.webhook;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the inbox on a fixed delay. Kept apart from {@link WebhookEventWorker} so tests can switch polling off
 * ({@code ledger.webhooks.worker.enabled=false}) and drive the worker by hand, deterministically.
 */
@Component
@ConditionalOnProperty(name = "ledger.webhooks.worker.enabled", havingValue = "true", matchIfMissing = true)
class WebhookPollingScheduler {

    private final WebhookEventWorker worker;

    WebhookPollingScheduler(WebhookEventWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${ledger.webhooks.worker.poll-interval:1s}")
    void poll() {
        // One batch per poll. Not a "drain until empty" loop: an event that keeps failing stays RECEIVED until
        // its attempts run out, and a tight loop would retry it hundreds of times per second.
        worker.processPendingBatch();
    }
}
