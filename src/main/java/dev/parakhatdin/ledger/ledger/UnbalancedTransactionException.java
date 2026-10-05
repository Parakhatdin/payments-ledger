package dev.parakhatdin.ledger.ledger;

/** Thrown when a posting breaks the double-entry rules. Nothing is written when this is thrown. */
public class UnbalancedTransactionException extends RuntimeException {

    public UnbalancedTransactionException(String message) {
        super(message);
    }
}
