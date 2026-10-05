package dev.parakhatdin.ledger.reconciliation;

import dev.parakhatdin.ledger.ledger.Accounts;
import dev.parakhatdin.ledger.ledger.LedgerTransactionRepository;
import dev.parakhatdin.ledger.ledger.LedgerTransactionRepository.NetByReference;
import dev.parakhatdin.ledger.money.Money;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compares the processor's settlement report with the net cash the ledger booked per reference.
 *
 * <p>This is the safety net for everything the webhook path can get wrong: a lost or never-delivered event,
 * a fee booked incorrectly, a payment the processor reversed later. Run it per payout; anything that is not
 * {@link ReconciliationReport#isClean() clean} needs a human.
 */
@Service
public class SettlementReconciler {

    private final LedgerTransactionRepository transactions;

    public SettlementReconciler(LedgerTransactionRepository transactions) {
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public ReconciliationReport reconcile(List<SettlementRow> report) {
        Map<String, Money> ledger = transactions.netByReference(Accounts.STRIPE_CASH).stream()
                .collect(Collectors.toMap(
                        NetByReference::getReference,
                        row -> Money.of(row.getNetAmount(), row.getCurrency()),
                        Money::plus,           // same reference in two transactions → add them up
                        LinkedHashMap::new));

        Map<String, SettlementRow> reported = report.stream()
                .collect(Collectors.toMap(SettlementRow::reference, Function.identity(),
                        (a, b) -> new SettlementRow(a.reference(), a.net().plus(b.net())),
                        LinkedHashMap::new));

        List<String> matched = new ArrayList<>();
        List<ReconciliationReport.Mismatch> mismatches = new ArrayList<>();
        List<SettlementRow> missingInLedger = new ArrayList<>();

        reported.values().forEach(row -> {
            Money booked = ledger.get(row.reference());
            if (booked == null) {
                missingInLedger.add(row);
            } else if (booked.equals(row.net())) {
                matched.add(row.reference());
            } else {
                mismatches.add(new ReconciliationReport.Mismatch(row.reference(), row.net(), booked));
            }
        });

        List<ReconciliationReport.LedgerOnly> missingInReport = ledger.entrySet().stream()
                .filter(e -> !reported.containsKey(e.getKey()))
                .map(e -> new ReconciliationReport.LedgerOnly(e.getKey(), e.getValue()))
                .toList();

        return new ReconciliationReport(matched, mismatches, missingInLedger, missingInReport);
    }
}
