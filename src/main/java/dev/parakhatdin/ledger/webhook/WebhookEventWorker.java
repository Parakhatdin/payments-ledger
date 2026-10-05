package dev.parakhatdin.ledger.webhook;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes stored Stripe events in batches.
 *
 * <p>Each batch runs in one transaction that holds row locks ({@code FOR UPDATE SKIP LOCKED}) on its events,
 * so two application instances never process the same event at the same time. A failing event does not stop
 * the batch: it records the error and is retried on a later poll, up to {@code max-attempts}, then parked as
 * FAILED for a human to look at.
 *
 * <p>Ledger postings run in their own inner transaction (see {@code LedgerWriter}). If the worker crashes
 * after posting but before marking the event PROCESSED, the event is retried and the idempotency key turns
 * the second posting into a no-op. That is "at-least-once processing + idempotent effect" = effectively once.
 */
@Component
public class WebhookEventWorker {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventWorker.class);

    private final WebhookEventRepository events;
    private final StripeEventHandler handler;
    private final WorkerProperties properties;
    private final Clock clock;

    public WebhookEventWorker(WebhookEventRepository events, StripeEventHandler handler,
                              WorkerProperties properties, Clock clock) {
        this.events = events;
        this.handler = handler;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return how many events were picked up in this batch */
    @Transactional
    public int processPendingBatch() {
        var batch = events.lockPendingBatch(properties.batchSize());
        for (WebhookEvent event : batch) {
            try {
                handler.handle(event.getType(), event.getPayload());
                event.markProcessed(clock.instant());
            } catch (RuntimeException e) {
                log.warn("Processing Stripe event {} failed (attempt {}): {}",
                        event.getEventId(), event.getAttempts() + 1, e.getMessage());
                event.markFailedAttempt(e.getClass().getSimpleName() + ": " + e.getMessage(), properties.maxAttempts());
            }
        }
        return batch.size(); // changes to managed entities are flushed on commit
    }
}
