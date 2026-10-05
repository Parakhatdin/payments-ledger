package dev.parakhatdin.ledger.reconciliation;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /reconciliations} with the settlement report as {@code text/csv} → the reconciliation report. */
@RestController
public class ReconciliationController {

    private final SettlementReconciler reconciler;

    public ReconciliationController(SettlementReconciler reconciler) {
        this.reconciler = reconciler;
    }

    @PostMapping(path = "/reconciliations", consumes = "text/csv")
    public ReconciliationReport reconcile(@RequestBody String csv) {
        return reconciler.reconcile(SettlementCsvParser.parse(csv));
    }
}
