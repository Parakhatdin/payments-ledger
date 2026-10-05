package dev.parakhatdin.ledger.ledger;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Posts balanced, idempotent double-entry transactions.
 *
 * <p>The two invariants this class protects:
 * <ol>
 *   <li>Every transaction balances (sum of debits == sum of credits) — checked before anything is written.</li>
 *   <li>The same idempotency key is booked at most once — guaranteed by a UNIQUE constraint in PostgreSQL,
 *       not by an application-level "check, then insert" (that has a race window between the two steps).</li>
 * </ol>
 *
 * <p>Flow: fast path looks the key up first (cheap, covers normal retries). If it is new, the insert runs in
 * its own transaction ({@link LedgerWriter}). If a concurrent request wins the race, the database rejects our
 * insert with a unique violation, our transaction rolls back, and we return the winner's row instead.
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerWriter writer;
    private final LedgerTransactionRepository transactions;

    public LedgerService(LedgerWriter writer, LedgerTransactionRepository transactions) {
        this.writer = writer;
        this.transactions = transactions;
    }

    public PostedTransaction post(String idempotencyKey, String description, String reference, List<LedgerLine> lines) {
        BalanceRules.assertBalanced(lines);

        var existing = transactions.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return PostedTransaction.from(existing.get(), true);
        }

        try {
            return writer.insert(idempotencyKey, description, reference, lines);
        } catch (DataIntegrityViolationException raceLost) {
            // Another worker booked the same key between our lookup and our insert. Their row is the truth.
            log.info("Idempotency race on key {} — returning the existing transaction", idempotencyKey);
            return transactions.findByIdempotencyKey(idempotencyKey)
                    .map(winner -> PostedTransaction.from(winner, true))
                    .orElseThrow(() -> raceLost); // a different constraint failed: do not hide it
        }
    }
}
