package com.basiclab.iot.sink.telemetry.outbox;

import com.basiclab.iot.sink.telemetry.envelope.DataPriority;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryQuality;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TelemetryOutboxBatchContractTest {

    @Test
    void preservesProductIdentityExactlyAndDefensivelyCopiesEnvelopes() {
        String product = "  Product/电力  ";
        List<TelemetryEnvelope> source = new ArrayList<>(List.of(envelope("message-1", "device-1")));

        TelemetryOutboxBatch batch = new TelemetryOutboxBatch(product, source);
        source.clear();

        assertEquals(product, batch.productIdentification());
        assertEquals(1, batch.envelopes().size());
        assertThrows(UnsupportedOperationException.class, () -> batch.envelopes().clear());
    }

    @Test
    void rejectsInvalidProductIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new TelemetryOutboxBatch(null, List.of(envelope())));
        assertThrows(IllegalArgumentException.class, () -> new TelemetryOutboxBatch("", List.of(envelope())));
        assertThrows(IllegalArgumentException.class, () -> new TelemetryOutboxBatch(" \t\n", List.of(envelope())));
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryOutboxBatch("x".repeat(129), List.of(envelope())));
        assertEquals(128, new TelemetryOutboxBatch("x".repeat(128), List.of(envelope()))
                .productIdentification().length());
    }

    @Test
    void rejectsInvalidEnvelopeCollectionAndMixedDevices() {
        assertThrows(IllegalArgumentException.class, () -> new TelemetryOutboxBatch("product", null));
        assertThrows(IllegalArgumentException.class, () -> new TelemetryOutboxBatch("product", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryOutboxBatch("product", java.util.Arrays.asList(envelope(), null)));
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryOutboxBatch("product", List.of(
                        envelope("message-1", "device-1"), envelope("message-2", "device-2"))));
    }

    @Test
    void acceptsMultiplePropertiesFromTheSameDevice() {
        TelemetryOutboxBatch batch = new TelemetryOutboxBatch("product",
                List.of(envelope("message-1", "device-1"), envelope("message-2", "device-1")));
        assertEquals(2, batch.envelopes().size());
    }

    @Test
    void preservesNfdProductIdentityWithoutUnicodeNormalization() {
        String nfd = "cafe\u0301-meter";
        String nfc = "caf\u00e9-meter";
        assertNotEquals(nfd, nfc);

        TelemetryOutboxBatch batch = new TelemetryOutboxBatch(nfd, List.of(envelope()));

        assertArrayEquals(nfd.toCharArray(), batch.productIdentification().toCharArray());
        assertNotEquals(nfc, batch.productIdentification());
    }

    private static TelemetryEnvelope envelope() {
        return envelope("message-1", "device-1");
    }

    private static TelemetryEnvelope envelope(String messageId, String deviceIdentification) {
        return new TelemetryEnvelope(
                TelemetryEnvelope.SCHEMA_VERSION, TelemetryEnvelope.CANONICALIZATION_VERSION,
                messageId, "request-" + messageId, "tenant-1", "site-1", deviceIdentification,
                "voltage-a", "220.5", TelemetryEnvelope.VALUE_ENCODING_DECIMAL_STRING,
                TelemetryQuality.GOOD, DataPriority.NORMAL_TELEMETRY,
                "2026-08-20T00:00:00Z", "2026-08-20T00:00:00Z", 1,
                "modbus-rtu", 1);
    }
}
