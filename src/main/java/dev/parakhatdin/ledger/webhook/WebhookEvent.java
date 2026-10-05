package dev.parakhatdin.ledger.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A Stripe event in the inbox, waiting to be (or already) processed. */
@Entity
@Table(name = "webhook_events")
public class WebhookEvent {

    public enum Status { RECEIVED, PROCESSED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected WebhookEvent() {} // for JPA

    void markProcessed(Instant at) {
        status = Status.PROCESSED;
        processedAt = at;
        lastError = null;
    }

    /** Records a failure. Stays RECEIVED (retried on the next poll) until the attempt budget is used up. */
    void markFailedAttempt(String error, int maxAttempts) {
        attempts++;
        lastError = error;
        if (attempts >= maxAttempts) {
            status = Status.FAILED;
        }
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
