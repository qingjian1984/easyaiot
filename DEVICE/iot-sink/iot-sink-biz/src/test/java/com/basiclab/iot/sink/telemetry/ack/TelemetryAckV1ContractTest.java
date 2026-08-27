package com.basiclab.iot.sink.telemetry.ack;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryAckV1ContractTest {

    private static final String MESSAGE_36 = "2ca80f25-4b6c-443f-a114-1b3df0a8cdf9";
    private static final String MESSAGE_32 = "2ca80f254b6c443fa1141b3df0a8cdf9";
    private static final String REQUEST = "a9afddc7-02ee-4df3-905b-ec3e4107f25d";
    private final TelemetryAckV1Codec codec = new TelemetryAckV1Codec();

    @Test
    void acceptedDurableRoundTripsSevenFields() {
        TelemetryAckV1 ack = new TelemetryAckV1(
                "1.0", MESSAGE_36, REQUEST, TelemetryAckStatus.ACCEPTED_DURABLE,
                0, "OK", 1_753_850_400_123L);

        String wire = codec.encodeToString(ack);
        assertEquals("{\"schemaVersion\":\"1.0\",\"messageId\":\""
                + MESSAGE_36 + "\",\"requestId\":\"" + REQUEST
                + "\",\"status\":\"ACCEPTED_DURABLE\",\"code\":0,"
                + "\"reasonCode\":\"OK\",\"persistedAt\":\"2025-07-30T04:40:00.123Z\"}", wire);
        assertEquals(ack, codec.decode(wire));
    }

    @Test
    void duplicateRoundTripsWithItsOwnSuccessTriple() {
        TelemetryAckV1 ack = new TelemetryAckV1(
                "1.0", MESSAGE_36, REQUEST, TelemetryAckStatus.DUPLICATE,
                1001, "DUPLICATE", 0L);

        assertTrue(codec.encodeToString(ack).contains("\"persistedAt\":\"1970-01-01T00:00:00.000Z\""));
        assertEquals(ack, codec.decode(codec.encode(ack)));
    }

    @Test
    void thirtyTwoCharacterMessageIdIsEchoedByteForByte() {
        TelemetryAckV1 ack = new TelemetryAckV1(
                "1.0", MESSAGE_32, REQUEST, TelemetryAckStatus.DUPLICATE,
                1001, "DUPLICATE", 1_753_850_400_999L);
        byte[] wire = codec.encode(ack);
        assertTrue(new String(wire, StandardCharsets.UTF_8).contains(MESSAGE_32));
        assertEquals(MESSAGE_32, codec.decode(wire).messageId());
    }

    @Test
    void unknownOptionalFieldIsIgnoredWithoutChangingRequiredMeaning() {
        String payload = "{\"status\":\"ACCEPTED_DURABLE\",\"futureFlag\":true,"
                + "\"schemaVersion\":\"1.0\",\"requestId\":\"" + REQUEST + "\","
                + "\"messageId\":\"" + MESSAGE_36 + "\",\"code\":0,"
                + "\"reasonCode\":\"OK\",\"persistedAt\":\"2026-01-01T00:00:00.000Z\"}";
        TelemetryAckV1 ack = codec.decode(payload);
        assertEquals(TelemetryAckStatus.ACCEPTED_DURABLE, ack.status());
        assertEquals(MESSAGE_36, ack.messageId());
        assertEquals(1_767_225_600_000L, ack.persistedAtMs());
    }

    @Test
    void everyRequiredFieldRejectsMissingNullAndWrongType() {
        String valid = validPayload();
        String persistedAt = TelemetryAckV1Codec.formatPersistedAt(1_753_850_400_123L);
        String[] invalid = {
                valid.replace("\"schemaVersion\":\"1.0\",", ""),
                valid.replace("\"schemaVersion\":\"1.0\"", "\"schemaVersion\":null"),
                valid.replace("\"schemaVersion\":\"1.0\"", "\"schemaVersion\":1"),
                valid.replace("\"messageId\":\"" + MESSAGE_36 + "\",", ""),
                valid.replace("\"messageId\":\"" + MESSAGE_36 + "\"", "\"messageId\":null"),
                valid.replace("\"messageId\":\"" + MESSAGE_36 + "\"", "\"messageId\":123"),
                valid.replace("\"requestId\":\"" + REQUEST + "\",", ""),
                valid.replace("\"requestId\":\"" + REQUEST + "\"", "\"requestId\":null"),
                valid.replace("\"requestId\":\"" + REQUEST + "\"", "\"requestId\":true"),
                valid.replace("\"status\":\"ACCEPTED_DURABLE\",", ""),
                valid.replace("\"status\":\"ACCEPTED_DURABLE\"", "\"status\":null"),
                valid.replace("\"status\":\"ACCEPTED_DURABLE\"", "\"status\":false"),
                valid.replace("\"code\":0,", ""),
                valid.replace("\"code\":0", "\"code\":null"),
                valid.replace("\"code\":0", "\"code\":\"0\""),
                valid.replace("\"reasonCode\":\"OK\",", ""),
                valid.replace("\"reasonCode\":\"OK\"", "\"reasonCode\":null"),
                valid.replace("\"reasonCode\":\"OK\"", "\"reasonCode\":false"),
                valid.replace(",\"persistedAt\":\"" + persistedAt + "\"", ""),
                valid.replace("\"persistedAt\":\"" + persistedAt + "\"", "\"persistedAt\":null"),
                valid.replace("\"persistedAt\":\"" + persistedAt + "\"", "\"persistedAt\":0")
        };
        for (String payload : invalid) {
            assertRejects(payload);
        }
    }

    @Test
    void encodedWireContainsOnlyTheSevenRequiredFields() {
        String wire = codec.encodeToString(new TelemetryAckV1(
                "1.0", MESSAGE_36, REQUEST, TelemetryAckStatus.ACCEPTED_DURABLE,
                0, "OK", 1_753_850_400_123L));
        assertEquals(7, wire.substring(0, wire.lastIndexOf("\"persistedAt\""))
                .chars().filter(c -> c == ':').count() + 1);
        assertFalse(wire.contains("resultCode"));
        assertFalse(wire.contains("errorCode"));
        assertFalse(wire.contains("observedAt"));
        assertFalse(wire.contains("payload"));
        assertFalse(wire.contains("tenantId"));
    }

    @Test
    void missingNullAndWrongTypesAreRejected() {
        String valid = codec.encodeToString(new TelemetryAckV1(
                "1.0", MESSAGE_36, REQUEST, TelemetryAckStatus.ACCEPTED_DURABLE,
                0, "OK", 1_753_850_400_123L));
        String[] invalid = {
                valid.replace("\"reasonCode\":\"OK\",", ""),
                valid.replace("\"reasonCode\":\"OK\"", "\"reasonCode\":null"),
                valid.replace("\"code\":0", "\"code\":\"0\""),
                valid.replace("\"status\":\"ACCEPTED_DURABLE\"", "\"status\":true"),
                valid.replace("\"persistedAt\":\""
                        + TelemetryAckV1Codec.formatPersistedAt(1_753_850_400_123L) + "\"",
                        "\"persistedAt\":0")
        };
        for (String payload : invalid) {
            assertThrows(TelemetryAckCodecException.class, () -> codec.decode(payload));
        }
    }

    @Test
    void duplicateKeysAreRejected() {
        String duplicate = "{\"schemaVersion\":\"1.0\",\"messageId\":\"" + MESSAGE_36
                + "\",\"messageId\":\"" + MESSAGE_32 + "\",\"requestId\":\"" + REQUEST
                + "\",\"status\":\"ACCEPTED_DURABLE\",\"code\":0,"
                + "\"reasonCode\":\"OK\",\"persistedAt\":\"2026-01-01T00:00:00.000Z\"}";
        TelemetryAckCodecException failure = assertThrows(TelemetryAckCodecException.class,
                () -> codec.decode(duplicate));
        assertEquals("ACK_JSON_MALFORMED", failure.errorCode());
    }

    @Test
    void unknownMajorVersionAndLegacyWireAreRejected() {
        String version = "{\"schemaVersion\":\"2.0\",\"messageId\":\"" + MESSAGE_36
                + "\",\"requestId\":\"" + REQUEST + "\",\"status\":\"ACCEPTED_DURABLE\","
                + "\"code\":0,\"reasonCode\":\"OK\",\"persistedAt\":\"2026-01-01T00:00:00.000Z\"}";
        assertEquals("ACK_SCHEMA_UNSUPPORTED", assertThrows(TelemetryAckCodecException.class,
                () -> codec.decode(version)).errorCode());

        String legacy = "{\"messageId\":\"" + MESSAGE_36
                + "\",\"resultCode\":\"ACCEPTED_DURABLE\",\"errorCode\":\"OK\","
                + "\"observedAt\":0}";
        assertEquals("ACK_LEGACY_WIRE_UNSUPPORTED", assertThrows(TelemetryAckCodecException.class,
                () -> codec.decode(legacy)).errorCode());
    }

    @Test
    void invalidIdentityStatusAndCodeReasonCombinationsAreRejected() {
        String valid = validPayload();
        String[] invalid = {
                valid.replace(MESSAGE_36, "not-a-message-id"),
                valid.replace(REQUEST, MESSAGE_32),
                valid.replace("\"status\":\"ACCEPTED_DURABLE\"", "\"status\":\"UNKNOWN\""),
                valid.replace("\"code\":0", "\"code\":1001"),
                valid.replace("\"reasonCode\":\"OK\"", "\"reasonCode\":\"DUPLICATE\"")
        };
        for (String payload : invalid) {
            assertRejects(payload);
        }
    }

    @Test
    void persistedAtUsesUtcMillisecondBoundaries() {
        TelemetryAckV1 atEpoch = new TelemetryAckV1(
                "1.0", MESSAGE_36, REQUEST, TelemetryAckStatus.ACCEPTED_DURABLE,
                0, "OK", 0L);
        TelemetryAckV1 atLastMillisecond = new TelemetryAckV1(
                "1.0", MESSAGE_36, REQUEST, TelemetryAckStatus.ACCEPTED_DURABLE,
                0, "OK", 999L);

        assertTrue(codec.encodeToString(atEpoch).contains("1970-01-01T00:00:00.000Z"));
        assertTrue(codec.encodeToString(atLastMillisecond).contains("1970-01-01T00:00:00.999Z"));
        assertEquals(atEpoch, codec.decode(codec.encode(atEpoch)));
        assertEquals(atLastMillisecond, codec.decode(codec.encode(atLastMillisecond)));
    }

    @Test
    void onlyTheTwoSuccessTriplesCanBeConstructed() {
        assertThrows(IllegalArgumentException.class, () -> new TelemetryAckV1(
                "1.0", MESSAGE_36, REQUEST, TelemetryAckStatus.ACCEPTED_DURABLE,
                1001, "DUPLICATE", 0L));
        assertThrows(IllegalArgumentException.class, () -> new TelemetryAckV1(
                "1.0", MESSAGE_36, REQUEST, TelemetryAckStatus.DUPLICATE,
                0, "OK", 0L));
        assertThrows(IllegalArgumentException.class, () -> new TelemetryAckV1(
                "1.0", MESSAGE_36, REQUEST, TelemetryAckStatus.REJECTED_FINAL,
                4001, "MALFORMED_ENVELOPE", 0L));
        assertThrows(IllegalArgumentException.class, () -> new TelemetryAckV1(
                "1.0", MESSAGE_36, REQUEST, TelemetryAckStatus.ACCEPTED_DURABLE,
                0, "OK", -1L));
    }

    @Test
    void malformedUtf8AndBomAreRejected() {
        assertEquals("ACK_UTF8_INVALID", assertThrows(TelemetryAckCodecException.class,
                () -> codec.decode(new byte[]{'{', (byte) 0xC3, (byte) 0x28})).errorCode());
        byte[] withBom = ("\ufeff" + codec.encodeToString(new TelemetryAckV1(
                "1.0", MESSAGE_36, REQUEST, TelemetryAckStatus.ACCEPTED_DURABLE,
                0, "OK", 0L))).getBytes(StandardCharsets.UTF_8);
        assertEquals("ACK_UTF8_BOM_FORBIDDEN", assertThrows(TelemetryAckCodecException.class,
                () -> codec.decode(withBom)).errorCode());
    }

    @Test
    void persistedAtRequiresUtcAndExactlyMilliseconds() {
        String valid = codec.encodeToString(new TelemetryAckV1(
                "1.0", MESSAGE_36, REQUEST, TelemetryAckStatus.ACCEPTED_DURABLE,
                0, "OK", 1_753_850_400_123L));
        assertThrows(TelemetryAckCodecException.class,
                () -> codec.decode(valid.replace(".123Z", "+08:00")));
        assertThrows(TelemetryAckCodecException.class,
                () -> codec.decode(valid.replace(".123Z", ".12Z")));
        assertThrows(TelemetryAckCodecException.class,
                () -> codec.decode(valid.replace(".123Z", ".1234Z")));
    }

    private String validPayload() {
        return codec.encodeToString(new TelemetryAckV1(
                "1.0", MESSAGE_36, REQUEST, TelemetryAckStatus.ACCEPTED_DURABLE,
                0, "OK", 1_753_850_400_123L));
    }

    private void assertRejects(String payload) {
        assertThrows(TelemetryAckCodecException.class, () -> codec.decode(payload));
    }
}
