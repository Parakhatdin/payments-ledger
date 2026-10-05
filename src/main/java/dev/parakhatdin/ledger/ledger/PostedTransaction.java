package dev.parakhatdin.ledger.ledger;

import java.time.Instant;
import java.util.List;

/**
 * Read-only result of {@link LedgerService#post}. A plain record instead of the JPA entity, so callers can
 * never trigger lazy loading outside a transaction or modify the ledger by accident.
 *
 * @param duplicate {@code true} if this idempotency key was already booked and nothing new was written
 */
public record PostedTransaction(
        long id,
        String idempotencyKey,
        String reference,
        String currency,
        Instant postedAt,
        boolean duplicate,
        List<Entry> entries) {

    public record Entry(String accountCode, Direction direction, long amount) {}

    static PostedTransaction from(LedgerTransaction tx, boolean duplicate) {
        List<Entry> entries = tx.getEntries().stream()
                .map(e -> new Entry(e.getAccount().getCode(), e.getDirection(), e.getAmount()))
                .toList();
        return new PostedTransaction(
                tx.getId(), tx.getIdempotencyKey(), tx.getReference(), tx.getCurrency(), tx.getPostedAt(), duplicate, entries);
    }
}
