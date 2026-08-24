package com.basiclab.iot.sink.telemetry.inbox;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** LC02-08-14: the product identity is an exact, validated route component. */
class InboxEnvelopeProductIdentityContractTest {

    @Test
    void preservesAuthorizedProductIdentityWithoutChangingPayloadBytes() {
        byte[] payload = "{\"productIdentification\":\"forged\"}".getBytes(StandardCharsets.UTF_8);
        InboxEnvelope envelope = new InboxEnvelope(
                "message-1", "request-1", "920006001", "product-1", "site-1",
                "device-1", "property-1", payload, "a".repeat(64), 1L, 2L,
                "collector", 3L);

        assertEquals("product-1", envelope.productIdentification());
        payload[0] = 'X';
        assertArrayEquals("{\"productIdentification\":\"forged\"}".getBytes(StandardCharsets.UTF_8),
                envelope.canonicalBytes());
    }

    @Test
    void rejectsMissingOrInvalidProductTopicLevelValues() {
        assertInvalid(null);
        assertInvalid("");
        assertInvalid(" ");
        assertInvalid("product/other");
        assertInvalid("product+other");
        assertInvalid("product#other");
        assertInvalid("product\u0000");
        assertInvalid("x".repeat(129));
    }

    @Test
    void keepsUnicodeCodePointBoundaryAndOriginalSpelling() {
        String product = "🙂".repeat(128);
        InboxEnvelope envelope = new InboxEnvelope(
                "message-2", "request-2", "920006001", product, "site-1", "device-1",
                "property-1", new byte[]{1}, "b".repeat(64), 1L, 2L, "collector", 3L);

        assertEquals(product, envelope.productIdentification());
    }

    @Test
    void rejectsOneCodePointAboveMaximum() {
        assertInvalid("🙂".repeat(129));
    }

    private static void assertInvalid(String product) {
        assertThrows(IllegalArgumentException.class, () -> new InboxEnvelope(
                "message-invalid", "request-invalid", "920006001", product, "site-1",
                "device-1", "property-1", new byte[]{1}, "c".repeat(64), 1L, 2L,
                "collector", 3L));
    }
}
