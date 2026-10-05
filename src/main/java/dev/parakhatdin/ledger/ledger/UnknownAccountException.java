package dev.parakhatdin.ledger.ledger;

/** Thrown when a posting refers to an account code that is not in the chart of accounts. */
public class UnknownAccountException extends RuntimeException {

    public UnknownAccountException(String code) {
        super("Unknown ledger account: " + code);
    }
}
