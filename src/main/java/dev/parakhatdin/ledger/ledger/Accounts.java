package dev.parakhatdin.ledger.ledger;

/**
 * Chart-of-accounts codes. Seeded by the Flyway migration {@code V3__chart_of_accounts.sql} and referenced
 * from code through these constants, so the two can never drift apart silently.
 */
public final class Accounts {

    /** Money sitting in the Stripe balance (net of fees). */
    public static final String STRIPE_CASH = "assets:stripe_cash";

    /** What customers paid (gross). */
    public static final String SALES_REVENUE = "revenue:sales";

    /** Stripe's processing fees. */
    public static final String STRIPE_FEES = "expenses:stripe_fees";

    private Accounts() {}
}
