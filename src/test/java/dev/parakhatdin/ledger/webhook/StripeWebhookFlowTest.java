package dev.parakhatdin.ledger.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.parakhatdin.ledger.IntegrationTest;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** End to end: HTTP request → inbox → worker → ledger, against a real PostgreSQL. */
class StripeWebhookFlowTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    StripeSignatureVerifier signer;

    @Autowired
    WebhookEventWorker worker;

    @Autowired
    WebhookEventRepository events;

    @Autowired
    Clock clock;

    private static String paymentSucceeded(String eventId, String intentId, long amount, long fee) {
        return """
                {"id":"%s","type":"payment_intent.succeeded","data":{"object":{
                  "id":"%s","object":"payment_intent","amount":%d,"amount_received":%d,"currency":"eur",
                  "latest_charge":{"id":"ch_1","balance_transaction":{"id":"txn_1","fee":%d}}}}}
                """.formatted(eventId, intentId, amount, amount, fee);
    }

    private ResultActions send(String payload) throws Exception {
        return send(payload, signer.header(payload, clock.instant()));
    }

    private ResultActions send(String payload, String signature) throws Exception {
        return mvc.perform(post("/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", signature)
                .content(payload));
    }

    private Map<String, Long> cashFeesRevenue() {
        Map<String, Long> totals = new java.util.HashMap<>();
        jdbc.query("""
                SELECT a.code, sum(e.amount) AS total FROM ledger_entries e
                JOIN accounts a ON a.id = e.account_id GROUP BY a.code
                """, rs -> {
            totals.put(rs.getString("code"), rs.getLong("total"));
        });
        return totals;
    }

    @Test
    void aSignedPaymentIsStoredThenBookedAsNetCashPlusFee() throws Exception {
        send(paymentSucceeded("evt_1", "pi_1", 10_000, 320))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false));

        // Nothing is booked inside the HTTP request…
        assertThat(cashFeesRevenue()).isEmpty();

        // …the worker does it.
        assertThat(worker.processPendingBatch()).isEqualTo(1);

        assertThat(cashFeesRevenue()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "assets:stripe_cash", 9_680L,
                "expenses:stripe_fees", 320L,
                "revenue:sales", 10_000L));
        assertThat(events.findByEventId("evt_1")).get()
                .extracting(WebhookEvent::getStatus).isEqualTo(WebhookEvent.Status.PROCESSED);
    }

    @Test
    void aRedeliveredEventIsAcknowledgedButStoredAndBookedOnce() throws Exception {
        String payload = paymentSucceeded("evt_1", "pi_1", 10_000, 320);
        send(payload).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(false));
        send(payload).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));

        worker.processPendingBatch();

        assertThat(events.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_transactions", Long.class)).isEqualTo(1);
    }

    @Test
    void twoDifferentEventsAboutTheSamePaymentStillBookItOnce() throws Exception {
        // Stripe can send distinct events (different evt_ ids) for the same PaymentIntent.
        send(paymentSucceeded("evt_1", "pi_1", 10_000, 320)).andExpect(status().isOk());
        send(paymentSucceeded("evt_2", "pi_1", 10_000, 320)).andExpect(status().isOk());

        worker.processPendingBatch();

        assertThat(events.count()).isEqualTo(2); // both events stored and processed…
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_transactions", Long.class))
                .isEqualTo(1);                       // …but the money is booked once (key = PaymentIntent id)
    }

    @Test
    void anUnsignedOrForgedRequestIsRejectedAndNotStored() throws Exception {
        String payload = paymentSucceeded("evt_forged", "pi_x", 1_000_000, 0);

        send(payload, "t=" + clock.instant().getEpochSecond() + ",v1=" + "ab".repeat(32))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid Stripe signature"));
        mvc.perform(post("/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());

        assertThat(events.count()).isZero();
    }

    @Test
    void unhandledEventTypesAreAcknowledgedAndMarkedProcessedWithoutBooking() throws Exception {
        send("""
                {"id":"evt_c","type":"customer.created","data":{"object":{"id":"cus_1"}}}
                """).andExpect(status().isOk());

        worker.processPendingBatch();

        assertThat(events.findByEventId("evt_c")).get()
                .extracting(WebhookEvent::getStatus).isEqualTo(WebhookEvent.Status.PROCESSED);
        assertThat(cashFeesRevenue()).isEmpty();
    }

    @Test
    void anEventThatKeepsFailingIsRetriedThenParkedAsFailed() throws Exception {
        // Signed and valid JSON, but the payment has no currency → the handler throws every time.
        send("""
                {"id":"evt_bad","type":"payment_intent.succeeded","data":{"object":{"id":"pi_bad","amount_received":100}}}
                """).andExpect(status().isOk());

        for (int i = 0; i < 5; i++) {
            worker.processPendingBatch();
        }

        WebhookEvent event = events.findByEventId("evt_bad").orElseThrow();
        assertThat(event.getStatus()).isEqualTo(WebhookEvent.Status.FAILED);
        assertThat(event.getAttempts()).isEqualTo(5);
        assertThat(event.getLastError()).contains("currency");
        assertThat(worker.processPendingBatch()).isZero(); // parked, no longer picked up
    }

    @Test
    void checkoutSessionEventsDoNotDoubleCountTheSale() throws Exception {
        send("""
                {"id":"evt_cs","type":"checkout.session.completed","data":{"object":{
                  "id":"cs_1","payment_status":"paid","amount_total":10000,"currency":"eur","payment_intent":"pi_1"}}}
                """).andExpect(status().isOk());
        send(paymentSucceeded("evt_pi", "pi_1", 10_000, 320)).andExpect(status().isOk());

        worker.processPendingBatch();

        assertThat(cashFeesRevenue()).containsEntry("revenue:sales", 10_000L); // not 20_000
    }
}
