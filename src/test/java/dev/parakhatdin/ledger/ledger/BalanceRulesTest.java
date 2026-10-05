package dev.parakhatdin.ledger.ledger;

import static dev.parakhatdin.ledger.ledger.LedgerLine.credit;
import static dev.parakhatdin.ledger.ledger.LedgerLine.debit;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.parakhatdin.ledger.money.CurrencyMismatchException;
import dev.parakhatdin.ledger.money.Money;
import java.util.List;
import org.junit.jupiter.api.Test;

class BalanceRulesTest {

    private static Money eur(long cents) {
        return Money.of(cents, "EUR");
    }

    @Test
    void acceptsABalancedThreeLegPayment() {
        assertThatCode(() -> BalanceRules.assertBalanced(List.of(
                debit(Accounts.STRIPE_CASH, eur(9680)),
                debit(Accounts.STRIPE_FEES, eur(320)),
                credit(Accounts.SALES_REVENUE, eur(10000))))).doesNotThrowAnyException();
    }

    @Test
    void rejectsDebitsNotEqualToCredits() {
        assertThatThrownBy(() -> BalanceRules.assertBalanced(List.of(
                debit(Accounts.STRIPE_CASH, eur(9999)),
                credit(Accounts.SALES_REVENUE, eur(10000)))))
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessageContaining("9999").hasMessageContaining("10000");
    }

    @Test
    void rejectsASingleLine() {
        assertThatThrownBy(() -> BalanceRules.assertBalanced(List.of(debit(Accounts.STRIPE_CASH, eur(1)))))
                .isInstanceOf(UnbalancedTransactionException.class);
    }

    @Test
    void rejectsZeroOrNegativeAmounts() {
        assertThatThrownBy(() -> BalanceRules.assertBalanced(List.of(
                debit(Accounts.STRIPE_CASH, eur(0)),
                credit(Accounts.SALES_REVENUE, eur(0)))))
                .isInstanceOf(UnbalancedTransactionException.class);
    }

    @Test
    void rejectsMixedCurrencies() {
        assertThatThrownBy(() -> BalanceRules.assertBalanced(List.of(
                debit(Accounts.STRIPE_CASH, Money.of(100, "USD")),
                credit(Accounts.SALES_REVENUE, eur(100)))))
                .isInstanceOf(CurrencyMismatchException.class);
    }
}
