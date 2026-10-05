package dev.parakhatdin.ledger.money;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

/**
 * An amount of money in integer minor units (cents for EUR/USD, yen for JPY).
 *
 * <p>Money is never stored as a floating-point number: {@code 0.1 + 0.2} is not {@code 0.3} in binary
 * floating point, and in a ledger a one-cent drift is a bug. Using {@code long} minor units keeps every
 * addition exact.
 *
 * <p>Immutable: every operation returns a new instance.
 */
public record Money(long minorUnits, String currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
        currency = currency.toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must be a 3-letter ISO 4217 code, got: " + currency);
        }
    }

    public static Money of(long minorUnits, String currency) {
        return new Money(minorUnits, currency);
    }

    /**
     * Parses a decimal amount such as {@code "12.34"} exactly, using the currency's number of minor digits
     * (2 for EUR, 0 for JPY). Rejects amounts with more precision than the currency allows.
     */
    public static Money parse(String decimalAmount, String currency) {
        String code = currency.toUpperCase(Locale.ROOT);
        int fractionDigits = Currency.getInstance(code).getDefaultFractionDigits();
        BigDecimal minor = new BigDecimal(decimalAmount.trim()).movePointRight(fractionDigits);
        // longValueExact() throws if the amount had more decimals than the currency allows (e.g. "1.234" EUR).
        return new Money(minor.longValueExact(), code);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), currency);
    }

    @JsonIgnore // a helper, not part of the JSON shape
    public boolean isPositive() {
        return minorUnits > 0;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        return minorUnits + " " + currency + " (minor units)";
    }
}
