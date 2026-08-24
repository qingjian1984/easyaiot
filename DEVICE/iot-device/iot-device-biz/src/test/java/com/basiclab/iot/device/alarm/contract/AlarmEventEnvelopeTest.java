package com.basiclab.iot.device.alarm.contract;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TD-006 Envelope 级不变量合同。 */
class AlarmEventEnvelopeTest {

    private static final String EVENT_ID = "00000000-0000-4000-8000-000000000001";
    private static final OffsetDateTime OCCURRED_AT =
            OffsetDateTime.parse("2026-08-24T08:00:00+08:00");
    private static final OffsetDateTime RECORDED_AT =
            OffsetDateTime.parse("2026-08-24T08:00:01+08:00");

    @Test
    void validEnvelopeUsesFrozenContract() {
        AlarmEventEnvelope envelope = validEnvelope();

        assertEquals(EVENT_ID, envelope.eventId());
        assertEquals("1.0", envelope.eventVersion());
        assertEquals("device.alarm.created.v1", envelope.eventType());
        assertEquals(AlarmEventType.CREATED, envelope.eventTypeEnum());
        assertEquals("1", envelope.tenantId());
        assertEquals(OCCURRED_AT, envelope.occurredAt());
        assertEquals(RECORDED_AT, envelope.recordedAt());
        assertEquals("iot-device", envelope.source());
        assertEquals("corr-1", envelope.correlationId());
        assertEquals("trace-1", envelope.traceId());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "00000000-0000-4000-8000-00000000000A",
            "00000000-0000-4000-8000-000000000001 ",
            "not-a-uuid",
            "00000000000040008000000000000001"
    })
    void eventIdMustBeLowercaseCanonicalUuid(String eventId) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> envelope(eventId, "1.0", "device.alarm.created.v1", "1",
                        "iot-device", OCCURRED_AT, RECORDED_AT, "corr-1", null, data()));
        assertTrue(error.getMessage().startsWith(AlarmEventEnvelope.ERROR_ENVELOPE_INVALID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "01", "-1", "1.0", "tenant"})
    void tenantIdMustBePositiveDecimalString(String tenantId) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> envelope(EVENT_ID, "1.0", "device.alarm.created.v1", tenantId,
                        "iot-device", OCCURRED_AT, RECORDED_AT, "corr-1", null, data()));
        assertTrue(error.getMessage().startsWith(AlarmEventEnvelope.ERROR_TENANT_INVALID));
    }

    @Test
    void versionEventTypeAndSourceAreStrict() {
        assertTrue(AlarmEventType.isKnown("device.alarm.occurrence-recorded.v1"));
        assertTrue(AlarmEventType.isKnown("device.alarm.suppression-decided.v1"));
        assertFalse(AlarmEventType.isKnown("device.alarm.raised.v1"));

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> envelope(EVENT_ID, "1.1", "device.alarm.created.v1", "1",
                        "iot-device", OCCURRED_AT, RECORDED_AT, "corr-1", null, data()))
                .getMessage().startsWith(AlarmEventEnvelope.ERROR_VERSION_INVALID));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> envelope(EVENT_ID, "1.0", "device.alarm.raised.v1", "1",
                        "iot-device", OCCURRED_AT, RECORDED_AT, "corr-1", null, data()))
                .getMessage().startsWith(AlarmEventEnvelope.ERROR_EVENT_TYPE_UNKNOWN));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> envelope(EVENT_ID, "1.0", "device.alarm.created.v1", "1",
                        "other-service", OCCURRED_AT, RECORDED_AT, "corr-1", null, data()))
                .getMessage().startsWith(AlarmEventEnvelope.ERROR_SOURCE_INVALID));
    }

    @Test
    void timeMustCarryOffsetAndRequiredCorrelationMustBePresent() {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> AlarmEventEnvelope.of(EVENT_ID, "1.0", "device.alarm.created.v1", "1",
                        "2026-08-24T08:00:00", "2026-08-24T08:00:01+08:00",
                        "iot-device", "corr-1", null, data()))
                .getMessage().startsWith(AlarmEventEnvelope.ERROR_TIME_INVALID));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> envelope(EVENT_ID, "1.0", "device.alarm.created.v1", "1",
                        "iot-device", OCCURRED_AT, RECORDED_AT, " ", null, data()))
                .getMessage().startsWith(AlarmEventEnvelope.ERROR_CORRELATION_INVALID));
    }

    @Test
    void payloadIsDeeplyDefensivelyCopied() {
        Map<String, Object> nested = new LinkedHashMap<>();
        List<Object> nestedList = new ArrayList<>();
        nestedList.add("before");
        nested.put("items", nestedList);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nested", nested);

        AlarmEventEnvelope envelope = envelope(EVENT_ID, "1.0", "device.alarm.created.v1", "1",
                "iot-device", OCCURRED_AT, RECORDED_AT, "corr-1", null, payload);
        nestedList.add("after");
        nested.put("changed", true);
        payload.put("outside", true);

        @SuppressWarnings("unchecked")
        Map<String, Object> copiedNested = (Map<String, Object>) envelope.payload().get("nested");
        @SuppressWarnings("unchecked")
        List<Object> copiedList = (List<Object>) copiedNested.get("items");
        assertNotSame(payload, envelope.payload());
        assertFalse(envelope.payload().containsKey("outside"));
        assertFalse(copiedNested.containsKey("changed"));
        assertEquals(List.of("before"), copiedList);
        assertThrows(UnsupportedOperationException.class,
                () -> envelope.payload().put("new", true));
        assertThrows(UnsupportedOperationException.class,
                () -> copiedNested.put("new", true));
        assertThrows(UnsupportedOperationException.class,
                () -> copiedList.add("new"));
    }

    @Test
    void nullPayloadAndTraceAreHandledByContract() {
        AlarmEventEnvelope nullTrace = envelope(EVENT_ID, "1.0", "device.alarm.created.v1", "1",
                "iot-device", OCCURRED_AT, RECORDED_AT, "corr-1", null, data());
        assertEquals(null, nullTrace.traceId());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> envelope(EVENT_ID, "1.0", "device.alarm.created.v1", "1",
                        "iot-device", OCCURRED_AT, RECORDED_AT, "corr-1", null, null));
        assertTrue(error.getMessage().startsWith(AlarmEventEnvelope.ERROR_PAYLOAD_INVALID));
    }

    private static AlarmEventEnvelope validEnvelope() {
        return envelope(EVENT_ID, "1.0", "device.alarm.created.v1", "1", "iot-device",
                OCCURRED_AT, RECORDED_AT, "corr-1", "trace-1", data());
    }

    private static AlarmEventEnvelope envelope(String eventId, String eventVersion,
                                               String eventType, String tenantId,
                                               String source, OffsetDateTime occurredAt,
                                               OffsetDateTime recordedAt,
                                               String correlationId, String traceId,
                                               Map<String, ?> payload) {
        return AlarmEventEnvelope.of(eventId, eventVersion, eventType, tenantId,
                occurredAt, recordedAt, source, correlationId, traceId, payload);
    }

    private static Map<String, Object> data() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alarmId", "1001");
        payload.put("status", "ACTIVE");
        payload.put("version", 1);
        return payload;
    }
}
