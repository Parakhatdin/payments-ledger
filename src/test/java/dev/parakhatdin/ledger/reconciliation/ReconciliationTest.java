package dev.parakhatdin.ledger.reconciliation;

import static dev.parakhatdin.ledger.ledger.LedgerLine.credit;
import static dev.parakhatdin.ledger.ledger.LedgerLine.debit;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.parakhatdin.ledger.IntegrationTest;
import dev.parakhatdin.ledger.ledger.Accounts;
import dev.parakhatdin.ledger.ledger.LedgerService;
import dev.parakhatdin.ledger.money.Money;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class ReconciliationTest extends IntegrationTest {

    @Autowired
    LedgerService ledger;

    @Autowired
    MockMvc mvc;

    private void bookPayment(String intentId, long gross, long fee) {
        ledger.post("stripe:pi:" + intentId, "Payment " + intentId, intentId, List.of(
                debit(Accounts.STRIPE_CASH, Money.of(gross - fee, "EUR")),
                debit(Accounts.STRIPE_FEES, Money.of(fee, "EUR")),
                credit(Accounts.SALES_REVENUE, Money.of(gross, "EUR"))));
    }

    @Test
    void sortsEveryReferenceIntoExactlyOneBucket() throws Exception {
        bookPayment("pi_ok", 10_000, 320);       // report agrees              → matched
        bookPayment("pi_fee", 5_000, 175);       // report says another fee    → mismatch
        bookPayment("pi_reversed", 2_000, 88);   // never settled              → missing in report

        String report = """
                reference,net,currency
                pi_ok,96.80,EUR
                pi_fee,48.00,EUR
                pi_lost,25.00,EUR
                """;                              // pi_lost: settled, but we never booked it (lost webhook)

        mvc.perform(post("/reconciliations").contentType("text/csv").content(report))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clean").value(false))
                .andExpect(jsonPath("$.matched[0]").value("pi_ok"))
                .andExpect(jsonPath("$.mismatches[0].reference").value("pi_fee"))
                .andExpect(jsonPath("$.mismatches[0].report.minorUnits").value(4_800))
                .andExpect(jsonPath("$.mismatches[0].ledger.minorUnits").value(4_825))
                .andExpect(jsonPath("$.missingInLedger[0].reference").value("pi_lost"))
                .andExpect(jsonPath("$.missingInReport[0].reference").value("pi_reversed"));
    }

    @Test
    void aFullyMatchingReportIsClean() throws Exception {
        bookPayment("pi_1", 10_000, 320);
        bookPayment("pi_2", 5_000, 175);

        mvc.perform(post("/reconciliations").contentType("text/csv").content("""
                        reference,net,currency
                        pi_1,96.80,EUR
                        pi_2,48.25,EUR
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clean").value(true))
                .andExpect(jsonPath("$.matched.length()").value(2));
    }

    @Test
    void aBrokenReportIsA400WithTheReason() throws Exception {
        mvc.perform(post("/reconciliations").contentType("text/csv").content("reference,net\npi_1,1.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Missing column: currency"));
    }
}
