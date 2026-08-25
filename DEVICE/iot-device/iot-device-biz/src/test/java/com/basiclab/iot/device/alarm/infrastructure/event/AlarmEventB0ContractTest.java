package com.basiclab.iot.device.alarm.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P02-M2-02B B0：七个正式 Schema、规范摘要与 Inbox 纯裁决合同。 */
class AlarmEventB0ContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EVENT_ID = "00000000-0000-4000-8000-000000000001";
    private static final String HASH_A = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_B = "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @ParameterizedTest
    @MethodSource("validEvents")
    void allSevenFormalSchemasAcceptPositiveFixture(String eventType, Map<String, Object> payload) {
        AlarmEventEnvelopeContract.assertValid(AlarmEventCodec.parse(json(eventType, payload)));
    }

    @Test
    void additiveFieldIsAllowedButCriticalMissingFieldIsRejected() {
        ObjectNode event = node(json("device.alarm.created.v1", createdPayload()));
        event.put("futureOptionalField", "accepted");
        assertTrue(AlarmEventCodec.parse(event.toString()).payload().containsKey("alarmId"));

        event.remove("eventId");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AlarmEventCodec.parse(event.toString()));
        assertTrue(error.getMessage().startsWith(AlarmEventSchemaValidator.ERROR_SCHEMA_INVALID));
    }

    @Test
    void uuidTenantIdAndDomainIdsRemainCanonicalStrings() {
        ObjectNode uppercaseUuid = node(json("device.alarm.created.v1", createdPayload()));
        uppercaseUuid.put("eventId", "00000000-0000-4000-8000-00000000000A");
        assertSchemaInvalid(uppercaseUuid);

        ObjectNode numericTenant = node(json("device.alarm.created.v1", createdPayload()));
        numericTenant.put("tenantId", 1);
        assertSchemaInvalid(numericTenant);

        ObjectNode numericAlarmId = node(json("device.alarm.created.v1", createdPayload()));
        numericAlarmId.with("payload").put("alarmId", 1001);
        assertSchemaInvalid(numericAlarmId);
    }

    @Test
    void offsetTimeAndUnknownMajorHaveStableRejectionCodes() {
        ObjectNode noOffset = node(json("device.alarm.created.v1", createdPayload()));
        noOffset.put("occurredAt", "2026-08-24T08:00:00");
        assertSchemaInvalid(noOffset);

        ObjectNode unknownMajor = node(json("device.alarm.created.v1", createdPayload()));
        unknownMajor.put("eventType", "device.alarm.created.v2");
        unknownMajor.put("eventVersion", "2.0");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AlarmEventCodec.parse(unknownMajor.toString()));
        assertTrue(error.getMessage().startsWith(AlarmEventSchemaValidator.ERROR_UNKNOWN_MAJOR));
    }

    @Test
    void envelopeHashUsesCanonicalEnvelopeBytesNotPayloadHash() {
        ObjectNode first = node(json("device.alarm.source-event.v1", sourcePayload()));
        ObjectNode second = MAPPER.createObjectNode();
        first.fields().forEachRemaining(entry -> second.set(entry.getKey(), entry.getValue()));
        String firstHash = AlarmEventHash.envelopeHash(first);
        String secondHash = AlarmEventHash.envelopeHash(second);
        assertEquals(firstHash, secondHash);
        assertTrue(AlarmEventHash.isValid(firstHash));
        assertNotEquals(first.path("payload").path("payloadHash").textValue(), firstHash);

        second.with("payload").put("sourceId", "different");
        assertNotEquals(firstHash, AlarmEventHash.envelopeHash(second));
    }

    @Test
    void inboxReplayAndHashConflictNeverOverwriteFirstHash() {
        AlarmInboxArbiter.Existing processed =
                new AlarmInboxArbiter.Existing(HASH_A, AlarmInboxArbiter.Status.PROCESSED);
        AlarmInboxArbiter.Existing received =
                new AlarmInboxArbiter.Existing(HASH_A, AlarmInboxArbiter.Status.RECEIVED);
        AlarmInboxArbiter.Existing quarantined =
                new AlarmInboxArbiter.Existing(HASH_A, AlarmInboxArbiter.Status.QUARANTINED);
        assertEquals(AlarmInboxArbiter.Decision.PROCESS,
                AlarmInboxArbiter.decide(null, 1, HASH_A, false));
        assertEquals(AlarmInboxArbiter.Decision.DUPLICATE,
                AlarmInboxArbiter.decide(processed, 1, HASH_A, false));
        assertEquals(AlarmInboxArbiter.Decision.PROCESS,
                AlarmInboxArbiter.decide(received, 1, HASH_A, false));
        assertEquals(AlarmInboxArbiter.Decision.REJECT_FINAL,
                AlarmInboxArbiter.decide(quarantined, 1, HASH_A, false));
        assertEquals(AlarmInboxArbiter.Decision.QUARANTINE_HASH_CONFLICT,
                AlarmInboxArbiter.decide(processed, 1, HASH_B, false));
        assertEquals(AlarmInboxArbiter.Decision.REJECT_UNKNOWN_MAJOR,
                AlarmInboxArbiter.decide(processed, 2, HASH_A, false));
        assertEquals(AlarmInboxArbiter.Decision.REJECT_FINAL,
                AlarmInboxArbiter.decide(processed, 1, HASH_A, true));
        assertEquals(HASH_A, processed.envelopeHash());
    }

    private static Stream<Arguments> validEvents() {
        return Stream.of(
                Arguments.of("device.alarm.source-event.v1", sourcePayload()),
                Arguments.of("device.alarm.created.v1", createdPayload()),
                Arguments.of("device.alarm.occurrence-recorded.v1", occurrencePayload()),
                Arguments.of("device.alarm.recovered.v1", recoveredPayload()),
                Arguments.of("device.alarm.status-changed.v1", statusChangedPayload()),
                Arguments.of("device.alarm.escalated.v1", escalatedPayload()),
                Arguments.of("device.alarm.suppression-decided.v1", suppressionPayload()));
    }

    private static String json(String eventType, Map<String, Object> payload) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("eventId", EVENT_ID);
        root.put("eventVersion", "1.0");
        root.put("eventType", eventType);
        root.put("tenantId", "1");
        root.put("occurredAt", "2026-08-24T08:00:00+08:00");
        root.put("recordedAt", "2026-08-24T08:00:01+08:00");
        root.put("source", "iot-device");
        root.put("correlationId", "corr-1");
        root.set("payload", MAPPER.valueToTree(payload));
        return root.toString();
    }

    private static ObjectNode node(String json) {
        try {
            return (ObjectNode) MAPPER.readTree(json);
        } catch (Exception error) {
            throw new AssertionError("fixture JSON 无法解析", error);
        }
    }

    private static Map<String, Object> sourcePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceType", "VIDEO");
        payload.put("sourceAction", "RAISED");
        payload.put("sourceId", "source-1");
        payload.put("cycleKey", "cycle-1");
        payload.put("sourceObjectId", "object-1");
        payload.put("siteId", "1");
        payload.put("deviceId", "device-1");
        payload.put("severity", "NORMAL");
        payload.put("occurredAt", "2026-08-24T08:00:00+08:00");
        payload.put("payloadHash", HASH_A);
        return payload;
    }

    private static Map<String, Object> createdPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alarmId", "1001");
        payload.put("status", "ACTIVE");
        payload.put("severity", "NORMAL");
        payload.put("sourceType", "VIDEO");
        payload.put("sourceId", "source-1");
        payload.put("cycleKey", "cycle-1");
        payload.put("siteId", "1");
        payload.put("deviceId", "device-1");
        payload.put("propertyCode", "voltage");
        payload.put("ruleId", "10");
        payload.put("ruleVersion", "1");
        payload.put("firstOccurredAt", "2026-08-24T08:00:00+08:00");
        payload.put("occurrenceCount", 1);
        return payload;
    }

    private static Map<String, Object> occurrencePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alarmId", "1001");
        payload.put("status", "ACTIVE");
        payload.put("occurrenceCount", 2);
        payload.put("lastOccurredAt", "2026-08-24T08:00:00+08:00");
        payload.put("sourceMessageId", "00000000-0000-4000-8000-000000000002");
        return payload;
    }

    private static Map<String, Object> recoveredPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alarmId", "1001");
        payload.put("fromStatus", "ACTIVE");
        payload.put("status", "RECOVERED");
        payload.put("recoveredAt", "2026-08-24T08:00:00+08:00");
        payload.put("version", 1);
        return payload;
    }

    private static Map<String, Object> statusChangedPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alarmId", "1001");
        payload.put("action", "ACKNOWLEDGE");
        payload.put("fromStatus", "ACTIVE");
        payload.put("status", "ACKNOWLEDGED");
        payload.put("operatorId", "operator-1");
        payload.put("reasonCode", "ACK");
        payload.put("version", 1);
        return payload;
    }

    private static Map<String, Object> escalatedPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alarmId", "1001");
        payload.put("status", "ACTIVE");
        payload.put("fromLevel", 0);
        payload.put("toLevel", 1);
        payload.put("policyId", "20");
        payload.put("policyVersion", "1");
        payload.put("escalatedAt", "2026-08-24T08:00:00+08:00");
        payload.put("version", 1);
        return payload;
    }

    private static Map<String, Object> suppressionPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alarmId", "1001");
        payload.put("status", "ACTIVE");
        payload.put("decision", "NOT_SUPPRESSED");
        payload.put("maintenanceContextId", null);
        payload.put("policyId", "20");
        payload.put("policyVersion", "1");
        payload.put("reasonCode", "NONE");
        payload.put("decidedAt", "2026-08-24T08:00:00+08:00");
        payload.put("version", 1);
        return payload;
    }

    private static void assertSchemaInvalid(JsonNode node) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AlarmEventCodec.parse(node.toString()));
        assertTrue(error.getMessage().startsWith(AlarmEventSchemaValidator.ERROR_SCHEMA_INVALID));
    }

    /** 避免测试因 API envelope 的 accessor 变化而丢失七 Schema 的正向断言。 */
    private static final class AlarmEventEnvelopeContract {
        private static void assertValid(com.basiclab.iot.device.alarm.contract.AlarmEventEnvelope envelope) {
            assertEquals("1.0", envelope.eventVersion());
            assertEquals("1", envelope.tenantId());
            assertEquals("iot-device", envelope.source());
        }
    }
}
