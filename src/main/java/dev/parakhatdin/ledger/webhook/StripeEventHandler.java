package dev.parakhatdin.ledger.webhook;

import dev.parakhatdin.ledger.ledger.Accounts;
import dev.parakhatdin.ledger.ledger.LedgerLine;
import dev.parakhatdin.ledger.ledger.LedgerService;
import dev.parakhatdin.ledger.money.Money;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns a verified Stripe event into a ledger posting.
 *
 * <p>A successful payment is booked as three legs, so the cash account holds the <b>net</b> amount that
 * Stripe actually pays out:
 * <pre>
 *   DEBIT  assets:stripe_cash     net   (gross - fee)
 *   DEBIT  expenses:stripe_fees   fee   (omitted when zero)
 *   CREDIT revenue:sales          gross (what the customer paid)
 * </pre>
 * Booking cash as net is what makes reconciliation work: Stripe's settlement report is net of fees too.
 *
 * <p>Only {@code payment_intent.succeeded} moves money here. {@code checkout.session.completed} is
 * deliberately ignored: a paid Checkout Session also produces a succeeded PaymentIntent, so booking both
 * would count the same sale twice.
 *
 * <p>The idempotency key is {@code stripe:pi:<id>}: one key per PaymentIntent, not per event. Stripe can send
 * different events about the same payment, and the money must still be booked once.
 */
@Component
public class StripeEventHandler {

    static final String PAYMENT_INTENT_SUCCEEDED = "payment_intent.succeeded";

    private static final Logger log = LoggerFactory.getLogger(StripeEventHandler.class);

    private final LedgerService ledger;
    private final ObjectMapper json;

    public StripeEventHandler(LedgerService ledger, ObjectMapper json) {
        this.ledger = ledger;
        this.json = json;
    }

    public void handle(String type, String payload) {
        if (!PAYMENT_INTENT_SUCCEEDED.equals(type)) {
            log.debug("Ignoring Stripe event type {}", type);
            return;
        }
        JsonNode intent = json.readTree(payload).path("data").path("object");
        bookPayment(intent);
    }

    private void bookPayment(JsonNode intent) {
        String id = intent.path("id").asString();
        String currency = intent.path("currency").asString();
        long gross = intent.has("amount_received") ? intent.path("amount_received").asLong() : intent.path("amount").asLong();

        if (id.isBlank() || currency.isBlank()) {
            throw new IllegalArgumentException("payment_intent without id or currency");
        }
        if (gross <= 0) {
            log.info("PaymentIntent {} has no received amount, nothing to book", id);
            return;
        }

        long fee = Math.clamp(feeOf(intent), 0, gross);
        long net = gross - fee;

        List<LedgerLine> lines = new ArrayList<>();
        if (net > 0) {
            lines.add(LedgerLine.debit(Accounts.STRIPE_CASH, Money.of(net, currency)));
        }
        if (fee > 0) {
            lines.add(LedgerLine.debit(Accounts.STRIPE_FEES, Money.of(fee, currency)));
        }
        lines.add(LedgerLine.credit(Accounts.SALES_REVENUE, Money.of(gross, currency)));

        ledger.post("stripe:pi:" + id, "Stripe payment " + id, id, lines);
    }

    /**
     * The processor fee lives on the charge's balance transaction. With {@code expand[]=latest_charge.balance_transaction}
     * it is inlined in the payload; older API versions put charges under {@code charges.data}.
     */
    private static long feeOf(JsonNode intent) {
        JsonNode fromLatestCharge = intent.path("latest_charge").path("balance_transaction").path("fee");
        if (fromLatestCharge.isNumber()) {
            return fromLatestCharge.asLong();
        }
        JsonNode fromCharges = intent.path("charges").path("data").path(0).path("balance_transaction").path("fee");
        return fromCharges.isNumber() ? fromCharges.asLong() : 0;
    }
}
