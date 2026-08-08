package com.basiclab.iot.device.service.event;

import com.basiclab.iot.device.event.PowerModelEventEnvelope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR-014 §事件契约：Envelope 不变量合同（iot-device-api 共享类型，经 biz 模块测试）。
 * 逐事件 data 载荷严格校验由 CI 门禁（Ajv Draft 2020-12 strict）承担。
 */
class PowerModelEventEnvelopeTest {

    private static final String EVENT_ID = "00000000-0000-0000-0000-000000000001";

    @Test
    void validEnvelopeBuilds() {
        PowerModelEventEnvelope envelope = validEnvelope();
        assertEquals(EVENT_ID, envelope.eventId());
        assertEquals(PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1, envelope.eventType());
        assertEquals(1, envelope.schemaVersion());
        assertEquals(Instant.parse("2026-08-08T00:00:00Z"), envelope.occurredAt());
        assertEquals("", envelope.traceId(), "traceId 允许空串");
    }

    @Test
    void topicKeyFollowsTenantAggregateFormat() {
        assertEquals("1:power_model_template:1001", validEnvelope().topicKey());
    }

    @Test
    void eventIdMustBeLowercaseUuid() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> envelopeWith("NOT-A-UUID", PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1, 1));
        assertTrue(error.getMessage().startsWith("MODEL_EVENT_ENVELOPE_INVALID"));
    }

    @Test
    void eventTypeMustCarryVersionSuffix() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> envelopeWith(EVENT_ID, "POWER_MODEL_TEMPLATE_PUBLISHED", 1));
        assertTrue(error.getMessage().startsWith("MODEL_EVENT_ENVELOPE_INVALID"));
    }

    @Test
    void versionSuffixMustMatchSchemaVersion() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> envelopeWith(EVENT_ID, PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1, 2));
        assertTrue(error.getMessage().startsWith("MODEL_EVENT_VERSION_SUFFIX_MISMATCH"));
    }

    @Test
    void blankRequiredFieldsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PowerModelEventEnvelope.of(EVENT_ID,
                        PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1, 1,
                        " ", "power_model_template", "1001",
                        "2026-08-08T00:00:00Z", "req-1", "", data()));
        assertThrows(IllegalArgumentException.class,
                () -> PowerModelEventEnvelope.of(EVENT_ID,
                        PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1, 1,
                        "1", "power_model_template", "1001",
                        "not-a-time", "req-1", "", data()));
    }

    @Test
    void majorVersionParsing() {
        assertEquals(1, PowerModelEventEnvelope.majorVersionOf(
                PowerModelEventEnvelope.EVENT_BINDING_APPLIED_V1));
        assertEquals(12, PowerModelEventEnvelope.majorVersionOf("POWER_X_V12"));
        assertEquals(-1, PowerModelEventEnvelope.majorVersionOf("POWER_X"));
        assertEquals(-1, PowerModelEventEnvelope.majorVersionOf(null));
    }

    @Test
    void payloadHashMatchesContractFormat() {
        // SHA-256("") 公认向量。
        String hash = PowerModelEventEnvelope.payloadHash("");
        assertEquals("sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
        assertTrue(PowerModelEventEnvelope.HASH_PATTERN.matcher(hash).matches());
    }

    @Test
    void dataMapIsDefensivelyCopied() {
        Map<String, Object> data = data();
        PowerModelEventEnvelope envelope = PowerModelEventEnvelope.of(EVENT_ID,
                PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1, 1,
                "1", "power_model_template", "1001",
                "2026-08-08T00:00:00Z", "req-1", "", data);
        data.put("mutated", true);
        assertTrue(!envelope.data().containsKey("mutated"), "构造后外部变更不得影响 Envelope");
        assertThrows(UnsupportedOperationException.class,
                () -> envelope.data().put("x", 1));
    }

    @Test
    void constantsAlignWithAdr014() {
        assertEquals(1, PowerModelEventEnvelope.SUPPORTED_MAJOR_VERSION);
        assertEquals("power-model-release-v1", PowerModelEventEnvelope.TOPIC_V1);
        assertEquals("power-model-release-v1-dlq", PowerModelEventEnvelope.DLQ_TOPIC_V1);
        assertEquals("iot-device-power-model-release", PowerModelEventEnvelope.CONSUMER_GROUP);
        assertEquals(2097152L, PowerModelEventEnvelope.MAX_PAYLOAD_BYTES);
    }

    private static PowerModelEventEnvelope validEnvelope() {
        return envelopeWith(EVENT_ID, PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1, 1);
    }

    private static PowerModelEventEnvelope envelopeWith(String eventId, String eventType, int schemaVersion) {
        return PowerModelEventEnvelope.of(eventId, eventType, schemaVersion,
                "1", "power_model_template", "1001",
                "2026-08-08T00:00:00Z", "req-1", null, data());
    }

    private static Map<String, Object> data() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("templateCode", "power.high_voltage_cabinet");
        data.put("templateVersion", "1.0.0");
        data.put("lifecycle", "PUBLISHED");
        return data;
    }
}
