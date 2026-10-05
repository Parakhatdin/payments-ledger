package dev.parakhatdin.ledger.webhook;

/** A correctly signed request whose body is not a usable Stripe event. */
public class MalformedEventException extends RuntimeException {

    public MalformedEventException(String message) {
        super(message);
    }
}
