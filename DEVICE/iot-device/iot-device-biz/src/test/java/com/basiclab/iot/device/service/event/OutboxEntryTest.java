package com.basiclab.iot.device.service.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TD-005 migration §4.6 / ADR-014：Outbox 条目不变量合同。
 */
class OutboxEntryTest {

    private static final String EVENT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String AUDIT_ID = "00000000-0000-0000-0000-0000000000aa";

    @Test
    void validEntryComputesHashAndKey() {
        OutboxEntry entry = entry("{\"k\":1}");
        assertEquals(EVENT_ID, entry.eventId());
        assertEquals(com.basiclab.iot.device.event.PowerModelEventEnvelope.payloadHash("{\"k\":1}"),
                entry.payloadHash());
        assertEquals("1:power_model_template:1001", entry.topicKey());
        assertEquals(12, entry.maxRetries());
    }

    @Test
    void versionSuffixMustMatchSchemaVersion() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> OutboxEntry.of(1L, EVENT_ID, 1L, AUDIT_ID,
                        "power_model_template", "1001",
                        "POWER_MODEL_TEMPLATE_PUBLISHED_V1", 2, "{\"k\":1}", 12));
        assertTrue(error.getMessage().startsWith("MODEL_EVENT_VERSION_SUFFIX_MISMATCH"));
    }

    @Test
    void payloadBoundEnforced() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2 * 1024 * 1024 + 1; i++) {
            sb.append('x');
        }
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> OutboxEntry.of(1L, EVENT_ID, 1L, AUDIT_ID,
                        "power_model_template", "1001",
                        "POWER_MODEL_TEMPLATE_PUBLISHED_V1", 1, sb.toString(), 12));
        assertTrue(error.getMessage().startsWith("MODEL_EVENT_PAYLOAD_TOO_LARGE"));
    }

    @Test
    void uuidAndBlankFieldsValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> OutboxEntry.of(1L, "bad-uuid", 1L, AUDIT_ID,
                        "power_model_template", "1001",
                        "POWER_MODEL_TEMPLATE_PUBLISHED_V1", 1, "{\"k\":1}", 12));
        assertThrows(IllegalArgumentException.class,
                () -> OutboxEntry.of(1L, EVENT_ID, 1L, AUDIT_ID,
                        " ", "1001",
                        "POWER_MODEL_TEMPLATE_PUBLISHED_V1", 1, "{\"k\":1}", 12));
        assertThrows(IllegalArgumentException.class,
                () -> OutboxEntry.of(1L, EVENT_ID, 1L, AUDIT_ID,
                        "power_model_template", "1001",
                        "POWER_MODEL_TEMPLATE_PUBLISHED_V1", 1, " ", 12));
        assertThrows(IllegalArgumentException.class,
                () -> OutboxEntry.of(1L, EVENT_ID, 1L, AUDIT_ID,
                        "power_model_template", "1001",
                        "POWER_MODEL_TEMPLATE_PUBLISHED_V1", 1, "{\"k\":1}", 0));
    }

    private static OutboxEntry entry(String payload) {
        return OutboxEntry.of(1L, EVENT_ID, 1L, AUDIT_ID,
                "power_model_template", "1001",
                "POWER_MODEL_TEMPLATE_PUBLISHED_V1", 1, payload, 12);
    }
}
