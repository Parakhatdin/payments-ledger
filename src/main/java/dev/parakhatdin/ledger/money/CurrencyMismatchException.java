package dev.parakhatdin.ledger.money;

/** Thrown when two amounts in different currencies are combined. Converting currencies is never implicit. */
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(String expected, String actual) {
        super("Currency mismatch: expected " + expected + " but got " + actual);
    }
}
