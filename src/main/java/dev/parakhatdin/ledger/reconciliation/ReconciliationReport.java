package dev.parakhatdin.ledger.reconciliation;

import dev.parakhatdin.ledger.money.Money;
import java.util.List;

/**
 * Result of comparing a settlement report with the ledger. Every reference lands in exactly one bucket.
 *
 * @param matched          same reference, same net amount
 * @param mismatches       same reference, different amount (e.g. a fee booked wrong)
 * @param missingInLedger  the processor settled it, we never booked it (e.g. a lost webhook)
 * @param missingInReport  we booked it, the processor never settled it (e.g. a payment that was reversed)
 */
public record ReconciliationReport(
        List<String> matched,
        List<Mismatch> mismatches,
        List<SettlementRow> missingInLedger,
        List<LedgerOnly> missingInReport) {

    public record Mismatch(String reference, Money report, Money ledger) {}

    public record LedgerOnly(String reference, Money ledger) {}

    public boolean isClean() {
        return mismatches.isEmpty() && missingInLedger.isEmpty() && missingInReport.isEmpty();
    }
}
