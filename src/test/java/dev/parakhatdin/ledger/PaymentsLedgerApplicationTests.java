package dev.parakhatdin.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentsLedgerApplicationTests extends IntegrationTest {

    @Test
    void contextLoadsAndFlywaySeedsTheChartOfAccounts() {
        assertThat(jdbc.queryForList("SELECT code FROM accounts ORDER BY code", String.class))
                .containsExactly("assets:stripe_cash", "expenses:stripe_fees", "revenue:sales");
    }
}
