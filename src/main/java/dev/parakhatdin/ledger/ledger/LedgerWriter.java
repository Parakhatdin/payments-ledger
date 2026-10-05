package dev.parakhatdin.ledger.ledger;

import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one posting in its own transaction ({@code REQUIRES_NEW}).
 *
 * <p>Why a separate bean: if the insert hits the UNIQUE idempotency constraint, only this inner transaction is
 * rolled back. A caller that is itself inside a transaction (e.g. the webhook worker) is not poisoned and can
 * carry on. It also has to be a separate bean because Spring's {@code @Transactional} works through a proxy:
 * a call from inside the same class would skip the proxy and the annotation would be ignored.
 */
@Component
class LedgerWriter {

    private final AccountRepository accounts;
    private final LedgerTransactionRepository transactions;
    private final Clock clock;

    LedgerWriter(AccountRepository accounts, LedgerTransactionRepository transactions, Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PostedTransaction insert(String idempotencyKey, String description, String reference, List<LedgerLine> lines) {
        String currency = lines.getFirst().amount().currency();
        var tx = new LedgerTransaction(idempotencyKey, reference, description, currency, clock.instant());

        for (LedgerLine line : lines) {
            Account account = accounts.findByCode(line.accountCode())
                    .orElseThrow(() -> new UnknownAccountException(line.accountCode()));
            tx.addEntry(account, line.direction(), line.amount().minorUnits());
        }

        // saveAndFlush: run the INSERT now, so a unique violation surfaces here and not at commit time.
        return PostedTransaction.from(transactions.saveAndFlush(tx), false);
    }
}
