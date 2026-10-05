package dev.parakhatdin.ledger.webhook;

/** The request is not provably from Stripe. Answered with 400 and never processed. */
public class InvalidSignatureException extends RuntimeException {

    public InvalidSignatureException(String message) {
        super(message);
    }
}
