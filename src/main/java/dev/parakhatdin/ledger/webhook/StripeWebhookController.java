package dev.parakhatdin.ledger.webhook;

import java.time.Clock;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Receives Stripe webhooks.
 *
 * <p>The request does as little as possible: verify the signature, store the event, answer 200. The actual
 * booking happens later in {@link WebhookEventWorker}. Stripe expects a quick 2xx and retries otherwise, so
 * slow or failing business logic must not sit inside the HTTP request.
 */
@RestController
public class StripeWebhookController {

    private final StripeSignatureVerifier verifier;
    private final WebhookEventRepository events;
    private final ObjectMapper json;
    private final Clock clock;

    public StripeWebhookController(StripeSignatureVerifier verifier, WebhookEventRepository events,
                                   ObjectMapper json, Clock clock) {
        this.verifier = verifier;
        this.events = events;
        this.json = json;
        this.clock = clock;
    }

    @PostMapping(path = "/webhooks/stripe", consumes = "application/json")
    public ResponseEntity<Map<String, Object>> receive(
            @RequestBody String payload,  // raw body as a String: the signature is over these exact bytes
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {

        verifier.verify(payload, signature); // throws → 400, before we trust a single field

        JsonNode event;
        try {
            event = json.readTree(payload);
        } catch (JacksonException e) {
            throw new MalformedEventException("Body is not valid JSON");
        }
        String eventId = event.path("id").asString();
        String type = event.path("type").asString();
        if (eventId.isBlank() || type.isBlank()) {
            throw new MalformedEventException("Event id and type are required");
        }

        boolean stored = events.insertIfAbsent(eventId, type, payload, clock.instant()) == 1;
        // Duplicates still get 200: the event is safely stored, so Stripe should stop retrying.
        return ResponseEntity.ok(Map.of("received", true, "duplicate", !stored));
    }
}
