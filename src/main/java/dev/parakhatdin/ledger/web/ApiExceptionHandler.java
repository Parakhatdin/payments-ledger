package dev.parakhatdin.ledger.web;

import dev.parakhatdin.ledger.ledger.UnbalancedTransactionException;
import dev.parakhatdin.ledger.money.CurrencyMismatchException;
import dev.parakhatdin.ledger.reconciliation.InvalidReportException;
import dev.parakhatdin.ledger.webhook.InvalidSignatureException;
import dev.parakhatdin.ledger.webhook.MalformedEventException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain exceptions to RFC 9457 problem responses, so every error has the same JSON shape. */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidSignatureException.class)
    ProblemDetail invalidSignature(InvalidSignatureException e) {
        // Don't echo details to an unauthenticated caller; the reason is logged server-side only.
        log.warn("Rejected Stripe webhook: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid Stripe signature");
    }

    @ExceptionHandler({MalformedEventException.class, InvalidReportException.class})
    ProblemDetail badRequest(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({UnbalancedTransactionException.class, CurrencyMismatchException.class})
    ProblemDetail unprocessable(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }
}
