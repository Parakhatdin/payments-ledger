package dev.parakhatdin.ledger.ledger;

import dev.parakhatdin.ledger.money.CurrencyMismatchException;
import java.util.List;

/**
 * The double-entry invariants, as pure functions so they can be unit-tested without a database.
 *
 * <p>A posting is valid only if it has at least two lines, every line is a positive amount, all lines
 * share one currency, and the sum of debits equals the sum of credits.
 */
final class BalanceRules {

    private BalanceRules() {}

    static void assertBalanced(List<LedgerLine> lines) {
        if (lines == null || lines.size() < 2) {
            throw new UnbalancedTransactionException("A ledger transaction needs at least two lines");
        }

        String currency = lines.getFirst().amount().currency();
        long debits = 0;
        long credits = 0;

        for (LedgerLine line : lines) {
            if (!line.amount().currency().equals(currency)) {
                throw new CurrencyMismatchException(currency, line.amount().currency());
            }
            if (!line.amount().isPositive()) {
                throw new UnbalancedTransactionException("Ledger line amounts must be positive, got "
                        + line.amount().minorUnits() + " on " + line.accountCode());
            }
            switch (line.direction()) {
                case DEBIT -> debits = Math.addExact(debits, line.amount().minorUnits());
                case CREDIT -> credits = Math.addExact(credits, line.amount().minorUnits());
            }
        }

        if (debits != credits) {
            throw new UnbalancedTransactionException(
                    "Unbalanced transaction: debits (" + debits + ") != credits (" + credits + ")");
        }
    }
}
