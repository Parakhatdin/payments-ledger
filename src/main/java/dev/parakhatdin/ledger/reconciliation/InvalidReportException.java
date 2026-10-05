package dev.parakhatdin.ledger.reconciliation;

/** The uploaded settlement report cannot be parsed. */
public class InvalidReportException extends RuntimeException {

    public InvalidReportException(String message) {
        super(message);
    }
}
