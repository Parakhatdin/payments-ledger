package dev.parakhatdin.ledger.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalisesCurrencyCode() {
        assertThat(Money.of(100, "eur")).isEqualTo(Money.of(100, "EUR"));
    }

    @Test
    void rejectsInvalidCurrency() {
        assertThatThrownBy(() -> Money.of(100, "EURO")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addsAndSubtractsExactly() {
        Money a = Money.of(10, "EUR"); // 0.10
        Money b = Money.of(20, "EUR"); // 0.20
        assertThat(a.plus(b)).isEqualTo(Money.of(30, "EUR")); // exactly 0.30 — unlike 0.1 + 0.2 in double
        assertThat(b.minus(a)).isEqualTo(Money.of(10, "EUR"));
    }

    @Test
    void refusesToMixCurrencies() {
        assertThatThrownBy(() -> Money.of(1, "EUR").plus(Money.of(1, "USD")))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("EUR").hasMessageContaining("USD");
    }

    @Test
    void parsesDecimalsUsingTheCurrencyPrecision() {
        assertThat(Money.parse("96.80", "eur")).isEqualTo(Money.of(9680, "EUR"));
        assertThat(Money.parse("1500", "JPY")).isEqualTo(Money.of(1500, "JPY")); // yen has no minor unit
    }

    @Test
    void rejectsMorePrecisionThanTheCurrencyHas() {
        assertThatThrownBy(() -> Money.parse("1.005", "EUR")).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void detectsOverflowInsteadOfWrappingAround() {
        assertThatThrownBy(() -> Money.of(Long.MAX_VALUE, "EUR").plus(Money.of(1, "EUR")))
                .isInstanceOf(ArithmeticException.class);
    }
}
