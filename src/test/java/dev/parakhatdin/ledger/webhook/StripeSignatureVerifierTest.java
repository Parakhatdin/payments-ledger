package dev.parakhatdin.ledger.webhook;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class StripeSignatureVerifierTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final String PAYLOAD = "{\"id\":\"evt_1\",\"type\":\"payment_intent.succeeded\"}";

    private final StripeSignatureVerifier verifier = verifier("whsec_test");

    private static StripeSignatureVerifier verifier(String secret) {
        return new StripeSignatureVerifier(new StripeProperties(secret, Duration.ofMinutes(5)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void acceptsAValidSignature() {
        assertThatCode(() -> verifier.verify(PAYLOAD, verifier.header(PAYLOAD, NOW))).doesNotThrowAnyException();
    }

    @Test
    void acceptsWhenAnyOfSeveralSignaturesMatches() {
        // During secret rotation Stripe sends one v1 per active secret.
        String header = verifier.header(PAYLOAD, NOW) + ",v1=" + "00".repeat(32);
        assertThatCode(() -> verifier.verify(PAYLOAD, header)).doesNotThrowAnyException();
    }

    @Test
    void rejectsATamperedBody() {
        String header = verifier.header(PAYLOAD, NOW);
        assertThatThrownBy(() -> verifier.verify(PAYLOAD.replace("evt_1", "evt_2"), header))
                .isInstanceOf(InvalidSignatureException.class);
    }

    @Test
    void rejectsASignatureMadeWithAnotherSecret() {
        String header = verifier("whsec_attacker").header(PAYLOAD, NOW);
        assertThatThrownBy(() -> verifier.verify(PAYLOAD, header)).isInstanceOf(InvalidSignatureException.class);
    }

    @Test
    void rejectsAReplayedOldRequest() {
        String header = verifier.header(PAYLOAD, NOW.minus(Duration.ofMinutes(6)));
        assertThatThrownBy(() -> verifier.verify(PAYLOAD, header))
                .isInstanceOf(InvalidSignatureException.class)
                .hasMessageContaining("tolerance");
    }

    @Test
    void rejectsMissingOrMalformedHeaders() {
        assertThatThrownBy(() -> verifier.verify(PAYLOAD, null)).isInstanceOf(InvalidSignatureException.class);
        assertThatThrownBy(() -> verifier.verify(PAYLOAD, "garbage")).isInstanceOf(InvalidSignatureException.class);
        assertThatThrownBy(() -> verifier.verify(PAYLOAD, "t=abc,v1=00")).isInstanceOf(InvalidSignatureException.class);
        assertThatThrownBy(() -> verifier.verify(PAYLOAD, "t=" + NOW.getEpochSecond() + ",v1=not-hex"))
                .isInstanceOf(InvalidSignatureException.class);
    }
}
