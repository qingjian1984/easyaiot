package com.basiclab.iot.device.service.event;

import com.basiclab.iot.device.event.PowerModelEventEnvelope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR-014 §事件契约：Envelope 编解码合同。
 * 正例解析 + 畸形/缺字段/不变量违规全部 fail-closed（稳定码）。
 */
class PowerModelEventEnvelopeCodecTest {

    private static final String VALID = "{"
            + "\"eventId\":\"00000000-0000-0000-0000-000000000001\","
            + "\"eventType\":\"POWER_MODEL_TEMPLATE_PUBLISHED_V1\","
            + "\"schemaVersion\":1,"
            + "\"tenantId\":\"1\","
            + "\"aggregateType\":\"power_model_template\","
            + "\"aggregateId\":\"1001\","
            + "\"occurredAt\":\"2026-08-08T00:00:00Z\","
            + "\"requestId\":\"req-1\","
            + "\"traceId\":\"\","
            + "\"data\":{\"templateCode\":\"power.high_voltage_cabinet\",\"lifecycle\":\"PUBLISHED\"}"
            + "}";

    @Test
    void validMessageParses() {
        PowerModelEventEnvelope envelope = PowerModelEventEnvelopeCodec.parse(VALID);
        assertEquals("00000000-0000-0000-0000-000000000001", envelope.eventId());
        assertEquals(PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1, envelope.eventType());
        assertEquals("power.high_voltage_cabinet", envelope.data().get("templateCode"));
        assertTrue(PowerModelEventEnvelopeCodec.dataJson(envelope).contains("templateCode"));
    }

    @Test
    void malformedJsonRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PowerModelEventEnvelopeCodec.parse("{not-json"));
        assertTrue(error.getMessage().startsWith(PowerModelEventEnvelopeCodec.CODE_MALFORMED));
    }

    @Test
    void nonObjectTopLevelRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PowerModelEventEnvelopeCodec.parse("[1,2,3]"));
        assertTrue(error.getMessage().startsWith(PowerModelEventEnvelopeCodec.CODE_MALFORMED));
    }

    @Test
    void blankMessageRejected() {
        assertThrows(IllegalArgumentException.class, () -> PowerModelEventEnvelopeCodec.parse("  "));
        assertThrows(IllegalArgumentException.class, () -> PowerModelEventEnvelopeCodec.parse(null));
    }

    @Test
    void missingDataObjectRejected() {
        String noData = VALID.replace(
                ",\"data\":{\"templateCode\":\"power.high_voltage_cabinet\",\"lifecycle\":\"PUBLISHED\"}", "");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PowerModelEventEnvelopeCodec.parse(noData));
        assertTrue(error.getMessage().startsWith("MODEL_EVENT_ENVELOPE_INVALID"));
    }

    @Test
    void nonIntegerSchemaVersionRejected() {
        String bad = VALID.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\"");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PowerModelEventEnvelopeCodec.parse(bad));
        assertTrue(error.getMessage().startsWith("MODEL_EVENT_ENVELOPE_INVALID"));
    }

    @Test
    void invariantViolationPropagates() {
        String bad = VALID.replace("POWER_MODEL_TEMPLATE_PUBLISHED_V1",
                "POWER_MODEL_TEMPLATE_PUBLISHED");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PowerModelEventEnvelopeCodec.parse(bad));
        assertTrue(error.getMessage().startsWith("MODEL_EVENT_ENVELOPE_INVALID"));
    }
}
